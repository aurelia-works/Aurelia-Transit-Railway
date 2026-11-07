package com.aureliatransit.architecture.registry;

import net.minecraft.block.Block;

/**
 * Plain metallic panels that fill the gap between ATA's structural steel and its station finishes. They are full cubes
 * with no block entity and no ticking, so a large wall costs the same as any other cube. The perforated panel uses the
 * cutout layer; the other four are opaque.
 */
public final class MetalBlocks {

	public static final Block STEEL_PANEL = ModBlocks.register("steel_panel", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new Block(ModBlocks.metalSolid()));
	public static final Block STEEL_PANEL_DARK = ModBlocks.register("steel_panel_dark", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new Block(ModBlocks.metalSolid()));
	public static final Block STEEL_PANEL_PERFORATED = ModBlocks.register("steel_panel_perforated", BlockFamily.ARCHITECTURE, RenderKind.CUTOUT,
			new Block(ModBlocks.metal()));
	public static final Block STEEL_PANEL_GALVANISED = ModBlocks.register("steel_panel_galvanised", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new Block(ModBlocks.metalSolid()));
	public static final Block STEEL_PANEL_BRUSHED = ModBlocks.register("steel_panel_brushed", BlockFamily.ARCHITECTURE, RenderKind.SOLID,
			new Block(ModBlocks.metalSolid()));

	private MetalBlocks() {
	}

	public static void init() {
		// Class loading registers everything declared here.
	}
}
