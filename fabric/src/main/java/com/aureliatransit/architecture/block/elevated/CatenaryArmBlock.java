package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.CatenaryArmKind;
import net.minecraft.state.property.EnumProperty;

import static net.minecraft.block.Block.createCuboidShape;

/** A piece of an overhead line cantilever; it faces from its mast towards the track it carries the wire over. */
public class CatenaryArmBlock extends FacingKindBlock<CatenaryArmKind> {

	public static final EnumProperty<CatenaryArmKind> KIND = EnumProperty.of("kind", CatenaryArmKind.class);

	public CatenaryArmBlock(Settings settings) {
		super(settings, "catenary_arm", Placement.AWAY_FROM_PLAYER, CatenaryArmKind.class, kind -> createCuboidShape(6, 2, 0, 10, 16, 16));
	}

	@Override
	protected EnumProperty<CatenaryArmKind> kindProperty() {
		return KIND;
	}
}
