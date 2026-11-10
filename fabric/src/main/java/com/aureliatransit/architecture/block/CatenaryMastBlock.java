package com.aureliatransit.architecture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * The overhead-line mast. With {@code ballast=true} its foot stands in the half block of gravel ballast that a raised
 * shoulder is made of, so a mast on the shoulder keeps the shoulder unbroken instead of standing on top of it (Jah 11-10).
 * Placed by hand it has no ballast; old worlds load as before.
 */
public class CatenaryMastBlock extends AxisShapedBlock {

	public static final BooleanProperty BALLAST = BooleanProperty.of("ballast");
	private static final VoxelShape SLAB = Block.createCuboidShape(0, 0, 0, 16, 8, 16);

	public CatenaryMastBlock(Settings settings, VoxelShape shapeAlongX) {
		super(settings, shapeAlongX);
		setDefaultState(getDefaultState().with(BALLAST, false));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(BALLAST);
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		final VoxelShape mast = super.getOutlineShape(state, world, pos, context);
		return state.get(BALLAST) ? VoxelShapes.union(mast, SLAB) : mast;
	}
}
