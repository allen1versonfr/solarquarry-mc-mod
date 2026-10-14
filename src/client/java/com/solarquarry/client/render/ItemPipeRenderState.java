package com.solarquarry.client.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class ItemPipeRenderState extends BlockEntityRenderState {
	public final List<Entry> entries = new ArrayList<>();

	public static final class Entry {
		public final ItemStackRenderState item = new ItemStackRenderState();
		public float x;
		public float y;
		public float z;
		public float spin;
	}
}
