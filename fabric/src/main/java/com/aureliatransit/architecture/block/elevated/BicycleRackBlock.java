package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.shape.VoxelShape;

/**
 * A bike rack, full or empty. Full is the default, so racks in old worlds and new plans hold a bike; bike share
 * ({@code org.mtr.aurelia.vehicle.BikeShare}) empties it when a bike is hired and fills it when one is returned.
 */
public class BicycleRackBlock extends FacingShapedBlock {

	public static final BooleanProperty BIKE = BooleanProperty.of("bike");

	public BicycleRackBlock(Settings settings, Placement placement, VoxelShape northOutline) {
		super(settings, placement, northOutline);
		setDefaultState(getDefaultState().with(BIKE, true));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(BIKE);
	}
}
