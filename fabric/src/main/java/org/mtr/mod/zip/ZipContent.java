package org.mtr.mod.zip;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/** New ids only. Registered from the mod initializer, not from the MTR item list. */
public final class ZipContent {

	public static final Identifier POLE_ID = new Identifier("mtr", "zip_pole");
	public static final Identifier HANDLE_ID = new Identifier("mtr", "zip_handle");
	public static final Identifier SEAT_ID = new Identifier("mtr", "zip_seat");

	public static final Block POLE = Registry.register(Registries.BLOCK, POLE_ID, new ZipPoleBlock(AbstractBlock.Settings.create()
			.mapColor(MapColor.IRON_GRAY)
			.strength(3.0F, 6.0F)
			.sounds(BlockSoundGroup.METAL)
			.nonOpaque()));
	public static final Item POLE_ITEM = Registry.register(Registries.ITEM, POLE_ID, new ZipPoleItem(new Item.Settings()));
	public static final Item HANDLE = Registry.register(Registries.ITEM, HANDLE_ID, new ZipHandleItem(new Item.Settings().maxCount(1)));
	public static final Item SEAT = Registry.register(Registries.ITEM, SEAT_ID, new ZipSeatItem(new Item.Settings().maxCount(1)));

	private ZipContent() {
	}

	public static void register() {
		// Class loading registers the block and the items.
	}
}
