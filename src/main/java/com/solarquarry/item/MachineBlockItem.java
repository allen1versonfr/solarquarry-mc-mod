package com.solarquarry.item;

import com.solarquarry.util.EnergyFormat;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block item that summarizes machine state carried by a wrench. */
public class MachineBlockItem extends BlockItem implements TooltipLines {
	private final String tooltipKey;

	public MachineBlockItem(Block block, String tooltipKey, Properties properties) {
		super(block, properties);
		this.tooltipKey = tooltipKey;
	}

	@Override
	public void addTooltipLines(ItemStack itemStack, Consumer<Component> builder) {
		if (this.tooltipKey != null) {
			builder.accept(Component.translatable(this.tooltipKey).withStyle(ChatFormatting.GRAY));
		}

		TypedEntityData<BlockEntityType<?>> data = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
		if (data == null) {
			return;
		}

		CompoundTag tag = data.copyTagWithoutId();
		builder.accept(Component.translatable("tooltip.solarquarry.stored_energy", EnergyFormat.format(tag.getLongOr("Energy", 0L))).withStyle(ChatFormatting.AQUA));
		if (tag.contains("QuarryMode") || tag.contains("Layer")) {
			long mined = tag.getLongOr("BlocksMined", 0L);
			String mode = tag.getStringOr("QuarryMode", "strip_mine");
			builder.accept(Component.translatable("tooltip.solarquarry.stored_mode", Component.translatable("solarquarry.mode." + mode),
				tag.getIntOr("Width", 16), tag.getIntOr("Length", 16)).withStyle(ChatFormatting.GOLD));
			builder.accept(Component.translatable("tooltip.solarquarry.stored_progress", mined).withStyle(ChatFormatting.GOLD));
			int count = tag.getListOrEmpty("Items").size();
			builder.accept(Component.translatable("tooltip.solarquarry.stored_items", count).withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
