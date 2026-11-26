package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.CctvKind;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.shape.VoxelShapes;

import static net.minecraft.block.Block.createCuboidShape;

/** CCTV camera housing (A10). A prop: no camera function. Faces the way it was placed (toward the player). */
public class CctvCameraBlock extends FacingKindBlock<CctvKind> {

	public static final EnumProperty<CctvKind> KIND = EnumProperty.of("kind", CctvKind.class);

	public CctvCameraBlock(Settings settings) {
		super(settings, "cctv_camera", Placement.TOWARD_PLAYER, CctvKind.class, kind -> switch (kind) {
			case WALL -> VoxelShapes.union(createCuboidShape(6, 7, 12, 10, 12, 16), createCuboidShape(5.25, 10, 2.5, 10.75, 16, 13));
			case PENDANT -> VoxelShapes.union(createCuboidShape(7, 11, 7, 9, 16, 9), createCuboidShape(5.25, 6, 1.5, 10.75, 12, 12));
			case DOME -> createCuboidShape(4, 11, 4, 12, 16, 12);
		});
	}

	@Override
	protected EnumProperty<CctvKind> kindProperty() {
		return KIND;
	}
}
