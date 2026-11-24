package org.mtr.aurelia.crossing;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.mtr.mapping.holder.World;
import org.mtr.mod.Init;
import org.mtr.mod.cargo.CargoService;

/**
 * Level crossings: a barrier whose striped arm lowers across a road, and a post of two red lights that flash in turn
 * with a bell. Both close by themselves when an MTR train comes near ({@link CrossingRule}, from the trains the cargo
 * service reads off the simulation: {@link CargoService} is a dependency of crossings), and whenever they are powered by
 * redstone, so an MTR train sensor up the line can close them earlier. With no fresh read of the trains they keep the
 * state they are in: a crossing never opens because it can't see. New ids only, in MTR's railway facilities tab;
 * registered from ATR's initialiser.
 */
public final class LevelCrossings {

	public static final Identifier BARRIER_ID = new Identifier("mtr", "level_crossing_barrier");
	public static final Identifier LIGHTS_ID = new Identifier("mtr", "level_crossing_lights");
	/** How often, in ticks, an open crossing looks for trains; also how long each lamp of the pair stays lit. */
	static final int CHECK_TICKS = 10;
	/** How often, in ticks, a closed crossing looks, so it opens soon after the train has gone. */
	static final int CLOSED_CHECK_TICKS = 2;

	public static final LevelCrossingBarrierBlock BARRIER = Registry.register(Registries.BLOCK, BARRIER_ID, new LevelCrossingBarrierBlock(metal()));
	public static final LevelCrossingLightsBlock LIGHTS = Registry.register(Registries.BLOCK, LIGHTS_ID, new LevelCrossingLightsBlock(metal()
			.luminance(LevelCrossingLightsBlock::light)));
	public static final BlockEntityType<LevelCrossingBarrierBlockEntity> BARRIER_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, BARRIER_ID,
			BlockEntityType.Builder.create(LevelCrossingBarrierBlockEntity::new, BARRIER).build(null));
	public static final Item BARRIER_ITEM = Registry.register(Registries.ITEM, BARRIER_ID, new BlockItem(BARRIER, new Item.Settings()));
	public static final Item LIGHTS_ITEM = Registry.register(Registries.ITEM, LIGHTS_ID, new BlockItem(LIGHTS, new Item.Settings()));

	private LevelCrossings() {
	}

	public static void register() {
		// Class loading registers the blocks, the block entity and the items.
		ItemGroupEvents.modifyEntriesEvent(RegistryKey.of(RegistryKeys.ITEM_GROUP, new Identifier("mtr", "railway_facilities"))).register(LevelCrossings::addToTab);
	}

	private static void addToTab(net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries entries) {
		entries.add(BARRIER_ITEM);
		entries.add(LIGHTS_ITEM);
	}

	/**
	 * Whether the crossing at {@code pos}, now {@code closedNow}, should be closed: powered by redstone, or a train near
	 * ({@link CrossingRule}); with no fresh read of the trains, as it is.
	 */
	static boolean closed(ServerWorld world, BlockPos pos, boolean closedNow) {
		return CrossingRule.closed(world.isReceivingRedstonePower(pos), CargoService.trains(Init.getWorldId(new World(world))),
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, closedNow);
	}

	/** Ticks until a crossing, now {@code closed}, looks again. */
	static int nextCheck(boolean closed) {
		return closed ? CLOSED_CHECK_TICKS : CHECK_TICKS;
	}

	private static AbstractBlock.Settings metal() {
		return AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(3.0F, 6.0F).sounds(BlockSoundGroup.METAL).nonOpaque();
	}
}
