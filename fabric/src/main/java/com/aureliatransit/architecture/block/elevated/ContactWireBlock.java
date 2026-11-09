package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.ContactWireKind;
import net.minecraft.state.property.EnumProperty;

import static net.minecraft.block.Block.createCuboidShape;

/** The overhead line's contact and messenger wires along the facing axis, with the messenger's sag and a dropper as kinds. */
public class ContactWireBlock extends FacingKindBlock<ContactWireKind> {

	public static final EnumProperty<ContactWireKind> KIND = EnumProperty.of("kind", ContactWireKind.class);

	public ContactWireBlock(Settings settings) {
		super(settings, "contact_wire", Placement.AWAY_FROM_PLAYER, ContactWireKind.class, kind -> createCuboidShape(7, 7, 0, 9, 16, 16));
	}

	@Override
	protected EnumProperty<ContactWireKind> kindProperty() {
		return KIND;
	}
}
