package com.aureliatransit.architecture.registry;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.GlassFacingBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.ShapedBlock;
import com.aureliatransit.architecture.block.glass.ArchGlassBlock;
import com.aureliatransit.architecture.block.mtr.MtrPlatformContract;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.util.shape.VoxelShape;

import static com.aureliatransit.architecture.util.Shapes.box;
import static com.aureliatransit.architecture.util.Shapes.union;

/**
 * British suburban stations (Enviro's play-test, 2026-10-20; creative tab: European Stations): the materials of Teddington
 * (1863, yellow stock brick, timber canopies with a sawtooth valance), Surbiton (1937, white render, steel windows, concrete
 * slab canopies) and Coventry (1962, blue engineering brick, hardwood canopies with deep fascias), and the tarmac platforms
 * with white-and-yellow coping every British platform has.
 */
public final class StationBlocksUk {

	private static final String POINTS_AWAY = "tooltip." + AureliaTransitArchitecture.MOD_ID + ".points_away";
	private static final VoxelShape FULL = box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape PLATE = box(0, 0, 0, 16, 2, 16);
	private static final VoxelShape SLAB_PLATE = box(0, 0, 0, 16, 4, 16);
	private static final VoxelShape RIDGE_PLATE = box(0, 0, 0, 16, 7, 16);
	private static final VoxelShape BOOTH_SHAPE = union(box(0, 0, 6, 16, 16, 16), box(0, 2.5, 2, 16, 4, 6));

	public static final Block UK_YELLOW_STOCK_BRICK = ModBlocks.register("uk_yellow_stock_brick", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block UK_RED_BRICK_DRESSING = ModBlocks.register("uk_red_brick_dressing", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.RED)));
	public static final Block UK_BLUE_ENGINEERING_BRICK = ModBlocks.register("uk_blue_engineering_brick", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BLUE)));
	public static final Block UK_WHITE_RENDER = ModBlocks.register("uk_white_render", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE)));
	public static final Block UK_STUCCO_DRESSED = ModBlocks.register("uk_stucco_dressed", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.WHITE)));
	public static final Block UK_STUCCO_QUOIN = ModBlocks.register("uk_stucco_quoin", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.paving(MapColor.PALE_YELLOW), Placement.TOWARD_PLAYER, FULL));
	public static final Block UK_BRICK_STRING_COURSE = ModBlocks.register("uk_brick_string_course", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block UK_WINDOW_LINTEL = ModBlocks.register("uk_window_lintel", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block UK_WINDOW_SILL = ModBlocks.register("uk_window_sill", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.PALE_YELLOW)));
	public static final Block UK_EAVES_CORNICE = ModBlocks.register("uk_eaves_cornice", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.paving(MapColor.WHITE).nonOpaque(), Placement.TOWARD_PLAYER, FULL));
	public static final Block UK_STEEL_WINDOW = ModBlocks.register("uk_steel_window", BlockFamily.STATIONS_UK, RenderKind.TRANSLUCENT,
			new ArchGlassBlock(ModBlocks.glass()));
	public static final Block UK_SASH_WINDOW = ModBlocks.register("uk_sash_window", BlockFamily.STATIONS_UK, RenderKind.TRANSLUCENT,
			new GlassFacingBlock(ModBlocks.glass(), Placement.TOWARD_PLAYER, box(0, 0, 2.5, 16, 16, 6.5)));
	public static final Block UK_PAINTED_COLUMN = ModBlocks.register("uk_painted_column", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), box(5.5, 0, 5.5, 10.5, 16, 10.5)));
	public static final Block UK_PAINTED_COLUMN_BASE = ModBlocks.register("uk_painted_column_base", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), union(box(5, 0, 5, 11, 10.5, 11), box(5.5, 10.5, 5.5, 10.5, 16, 10.5))));
	public static final Block UK_PAINTED_COLUMN_CAP = ModBlocks.register("uk_painted_column_cap", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), union(box(5.5, 0, 5.5, 10.5, 9.5, 10.5), box(4.5, 9.5, 4.5, 11.5, 16, 11.5))));
	public static final Block UK_ARCHED_BOOTH_WINDOW = ModBlocks.register("uk_arched_booth_window", BlockFamily.STATIONS_UK, RenderKind.TRANSLUCENT,
			new FacingShapedBlock(ModBlocks.glass(), Placement.TOWARD_PLAYER, BOOTH_SHAPE));
	public static final Block UK_WOOD_WAINSCOT = ModBlocks.register("uk_wood_wainscot", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BROWN)));
	public static final Block UK_BOOKING_HALL_TILE = ModBlocks.register("uk_booking_hall_tile", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.OFF_WHITE)));
	public static final Block UK_BRIDGE_CLADDING = ModBlocks.register("uk_bridge_cladding", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.OFF_WHITE)));
	public static final Block UK_NAVY_FASCIA = ModBlocks.register("uk_navy_fascia", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new Block(ModBlocks.paving(MapColor.BLUE)));
	public static final Block UK_SLATE_RIDGE = ModBlocks.register("uk_slate_ridge", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.paving(MapColor.DEEPSLATE_GRAY).nonOpaque(), RIDGE_PLATE));
	public static final Block UK_PLATFORM_TARMAC = ModBlocks.register("uk_platform_tarmac", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			MtrPlatformContract.surface(ModBlocks.paving(MapColor.DEEPSLATE_GRAY)));
	public static final Block UK_PLATFORM_COPING = ModBlocks.register("uk_platform_coping", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			MtrPlatformContract.edge(ModBlocks.paving(MapColor.STONE_GRAY), Placement.AWAY_FROM_PLAYER, FULL), POINTS_AWAY);
	public static final Block UK_PLATFORM_COPING_TACTILE = ModBlocks.register("uk_platform_coping_tactile", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			MtrPlatformContract.edge(ModBlocks.paving(MapColor.STONE_GRAY), Placement.AWAY_FROM_PLAYER, FULL), POINTS_AWAY);
	public static final Block UK_TIMBER_CANOPY = ModBlocks.register("uk_timber_canopy", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), PLATE));
	public static final Block UK_CANOPY_VALANCE = ModBlocks.register("uk_canopy_valance", BlockFamily.STATIONS_UK, RenderKind.CUTOUT,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, union(PLATE, box(0, 0, 0, 16, 7, 1.75))), POINTS_AWAY);
	public static final Block UK_CONCRETE_CANOPY = ModBlocks.register("uk_concrete_canopy", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.paving(MapColor.WHITE).nonOpaque(), SLAB_PLATE));
	public static final Block UK_CONCRETE_CANOPY_EDGE = ModBlocks.register("uk_concrete_canopy_edge", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.paving(MapColor.WHITE).nonOpaque(), Placement.AWAY_FROM_PLAYER, union(SLAB_PLATE, box(0, 0, 0, 16, 5, 2))),
			POINTS_AWAY);
	public static final Block UK_HARDWOOD_CANOPY = ModBlocks.register("uk_hardwood_canopy", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new ShapedBlock(ModBlocks.metal(), PLATE));
	public static final Block UK_HARDWOOD_CANOPY_FASCIA = ModBlocks.register("uk_hardwood_canopy_fascia", BlockFamily.STATIONS_UK, RenderKind.SOLID,
			new FacingShapedBlock(ModBlocks.metal(), Placement.AWAY_FROM_PLAYER, union(PLATE, box(0, 0, 0, 16, 4, 2))), POINTS_AWAY);

	private StationBlocksUk() {
	}

	public static void init() {
		// Class loading registers everything above.
	}
}
