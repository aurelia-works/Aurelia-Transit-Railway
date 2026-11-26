package org.mtr.mod.building;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * The retired building tools. They stay registered under their ids so old worlds and inventories keep the stacks, but they
 * do nothing and are not in the creative tab. Registered from the mod initializer, not from the MTR item list.
 */
public final class BuildContent {

	public static final Identifier STATION_ID = new Identifier("mtr", "station_guide");
	public static final Identifier RAIL_ID = new Identifier("mtr", "rail_stylus");
	public static final Identifier TUNNEL_ID = new Identifier("mtr", "box_tunnel");

	public static final Item STATION = Registry.register(Registries.ITEM, STATION_ID, new RetiredToolItem(new Item.Settings().maxCount(1)));
	public static final Item RAIL = Registry.register(Registries.ITEM, RAIL_ID, new RetiredToolItem(new Item.Settings().maxCount(1)));
	public static final Item TUNNEL = Registry.register(Registries.ITEM, TUNNEL_ID, new RetiredToolItem(new Item.Settings().maxCount(1)));

	private BuildContent() {
	}

	public static void register() {
		// Class loading registers the items.
	}
}
