package com.solarquarry.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solarquarry.SolarQuarry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server settings, stored in {@code config/solarquarry.json}. Every value is validated and clamped on load; a missing
 * or broken file is replaced by a fresh one with the defaults. The server sends the active values to every client on
 * join so that GUI numbers (energy per block, area limits) always match the server.
 */
public record SolarQuarryConfig(
	double energyCostMultiplier,
	double solarOutputMultiplier,
	int maxRangeTier,
	boolean allowChunkLoading,
	int chunkLoaderUpkeep,
	boolean respectProtection,
	int prospectorCooldownTicks,
	int terminalMaxQuarries,
	double machineSoundVolume
) {
	public static final SolarQuarryConfig DEFAULTS = new SolarQuarryConfig(1.0, 1.0, 2, true, 8, true, 100, 32, 1.0);
	public static final String FILE_NAME = "solarquarry.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Values loaded from this machine's file (restored on the client after leaving a server). */
	private static volatile SolarQuarryConfig local = DEFAULTS;
	/** Values in effect: the local file, or the server's values while connected to a server. */
	private static volatile SolarQuarryConfig active = DEFAULTS;
	private static volatile boolean fromServer;

	public static SolarQuarryConfig get() {
		return active;
	}

	/** Returns every value clamped into its allowed range (NaN becomes the default). */
	public SolarQuarryConfig validated() {
		return new SolarQuarryConfig(
			clamp(this.energyCostMultiplier, 0.1, 10.0, DEFAULTS.energyCostMultiplier),
			clamp(this.solarOutputMultiplier, 0.1, 10.0, DEFAULTS.solarOutputMultiplier),
			Math.clamp(this.maxRangeTier, 0, 2),
			this.allowChunkLoading,
			Math.clamp(this.chunkLoaderUpkeep, 0, 10_000),
			this.respectProtection,
			Math.clamp(this.prospectorCooldownTicks, 20, 1_200),
			Math.clamp(this.terminalMaxQuarries, 1, 64),
			clamp(this.machineSoundVolume, 0.0, 1.0, DEFAULTS.machineSoundVolume)
		);
	}

	private static double clamp(double value, double min, double max, double fallback) {
		return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
	}

	/** Reads (or creates) the config file. Never throws: problems are logged and the defaults are used for bad values. */
	public static SolarQuarryConfig load(Path configDir) {
		Path file = configDir.resolve(FILE_NAME);
		SolarQuarryConfig config = DEFAULTS;
		boolean rewrite = true;
		if (Files.exists(file)) {
			try {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				JsonElement json = JsonParser.parseString(text);
				if (json.isJsonObject()) {
					config = fromJson(json.getAsJsonObject());
					rewrite = !toJson(config).equals(json.getAsJsonObject());
				} else {
					SolarQuarry.LOGGER.warn("{} is not a JSON object; using defaults", file);
				}
			} catch (IOException | RuntimeException e) {
				SolarQuarry.LOGGER.warn("Could not read {} ({}); using defaults", file, e.getMessage());
				rewrite = false;
				config = DEFAULTS;
			}
		}

		if (rewrite) {
			try {
				Files.createDirectories(configDir);
				Files.writeString(file, GSON.toJson(toJson(config)) + System.lineSeparator(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				SolarQuarry.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
			}
		}

		local = config;
		active = config;
		SolarQuarry.LOGGER.info("Solar Quarry config: {}", config);
		return config;
	}

	/** Missing or malformed entries take their default value; everything is clamped. */
	public static SolarQuarryConfig fromJson(JsonObject json) {
		return new SolarQuarryConfig(
			readDouble(json, "energyCostMultiplier", DEFAULTS.energyCostMultiplier),
			readDouble(json, "solarOutputMultiplier", DEFAULTS.solarOutputMultiplier),
			readInt(json, "maxRangeTier", DEFAULTS.maxRangeTier),
			readBool(json, "allowChunkLoading", DEFAULTS.allowChunkLoading),
			readInt(json, "chunkLoaderUpkeep", DEFAULTS.chunkLoaderUpkeep),
			readBool(json, "respectProtection", DEFAULTS.respectProtection),
			readInt(json, "prospectorCooldownTicks", DEFAULTS.prospectorCooldownTicks),
			readInt(json, "terminalMaxQuarries", DEFAULTS.terminalMaxQuarries),
			readDouble(json, "machineSoundVolume", DEFAULTS.machineSoundVolume)
		).validated();
	}

	public static JsonObject toJson(SolarQuarryConfig config) {
		JsonObject json = new JsonObject();
		json.addProperty("energyCostMultiplier", config.energyCostMultiplier);
		json.addProperty("solarOutputMultiplier", config.solarOutputMultiplier);
		json.addProperty("maxRangeTier", config.maxRangeTier);
		json.addProperty("allowChunkLoading", config.allowChunkLoading);
		json.addProperty("chunkLoaderUpkeep", config.chunkLoaderUpkeep);
		json.addProperty("respectProtection", config.respectProtection);
		json.addProperty("prospectorCooldownTicks", config.prospectorCooldownTicks);
		json.addProperty("terminalMaxQuarries", config.terminalMaxQuarries);
		json.addProperty("machineSoundVolume", config.machineSoundVolume);
		return json;
	}

	private static double readDouble(JsonObject json, String key, double fallback) {
		try {
			return json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isNumber() ? json.get(key).getAsDouble() : fallback;
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static int readInt(JsonObject json, String key, int fallback) {
		double value = readDouble(json, key, fallback);
		return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : value <= Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) Math.round(value);
	}

	private static boolean readBool(JsonObject json, String key, boolean fallback) {
		return json.has(key) && json.get(key).isJsonPrimitive() && json.getAsJsonPrimitive(key).isBoolean() ? json.get(key).getAsBoolean() : fallback;
	}

	/** Client: adopt the server's values while connected. */
	public static void applyFromServer(SolarQuarryConfig config) {
		active = config.validated();
		fromServer = true;
	}

	/** Client: true once the connected server has sent its settings. */
	public static boolean isFromServer() {
		return fromServer;
	}

	/** Client: back to this machine's own file after leaving a server. */
	public static void restoreLocal() {
		active = local;
		fromServer = false;
	}

	/** Tests: set the active values directly. */
	public static void setActiveForTesting(SolarQuarryConfig config) {
		active = config.validated();
	}
}
