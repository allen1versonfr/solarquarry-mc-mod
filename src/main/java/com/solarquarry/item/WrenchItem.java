package com.solarquarry.item;

import com.solarquarry.block.BatteryBlock;
import com.solarquarry.block.EnergyCableBlock;
import com.solarquarry.block.ItemPipeBlock;
import com.solarquarry.block.QuarryBlock;
import com.solarquarry.block.SolarPanelBlock;
import com.solarquarry.blockentity.BatteryBlockEntity;
import com.solarquarry.blockentity.QuarryBlockEntity;
import com.solarquarry.registry.ModSounds;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dismantles Solar Quarry blocks. Quarries and batteries keep their complete state in the dropped item.
 */
public class WrenchItem extends Item implements MachineTool, TooltipLines {
	public WrenchItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		boolean ours = block instanceof QuarryBlock || block instanceof BatteryBlock || block instanceof SolarPanelBlock
			|| block instanceof EnergyCableBlock || block instanceof ItemPipeBlock;
		if (!ours) {
			return InteractionResult.PASS;
		}

		Player player = context.getPlayer();
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		if (player != null && !player.mayBuild()) {
			return InteractionResult.FAIL;
		}

		if (player != null && !level.mayInteract(player, pos)) {
			return InteractionResult.FAIL;
		}

		ItemStack drop = dismantle(serverLevel, pos);
		if (drop.isEmpty()) {
			return InteractionResult.FAIL;
		}

		if (player == null || !player.getInventory().add(drop)) {
			Block.popResource(level, pos, drop);
		}

		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Removes a Solar Quarry block and returns it as an item. Quarries and batteries carry their full state;
	 * their block entity is emptied first, so the removal itself cannot drop or duplicate anything.
	 */
	public static ItemStack dismantle(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		ItemStack drop = new ItemStack(block.asItem());
		if (drop.isEmpty()) {
			return ItemStack.EMPTY;
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof QuarryBlockEntity quarry) {
			CompoundTag data = quarry.extractForPickup(level.registryAccess());
			drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(be.getType(), data));
		} else if (be instanceof BatteryBlockEntity battery) {
			CompoundTag data = battery.extractForPickup(level.registryAccess());
			drop.set(DataComponents.BLOCK_ENTITY_DATA, TypedEntityData.of(be.getType(), data));
		}

		level.removeBlock(pos, false);
		level.playSound(null, pos, ModSounds.WRENCH, SoundSource.BLOCKS, 0.8F, 1.0F);
		return drop;
	}

	@Override
	public void addTooltipLines(ItemStack itemStack, Consumer<Component> builder) {
		builder.accept(Component.translatable("tooltip.solarquarry.wrench").withStyle(ChatFormatting.GRAY));
	}
}
