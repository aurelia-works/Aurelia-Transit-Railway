package com.aureliatransit.architecture.block;

import com.aureliatransit.architecture.block.entity.BridgeMemberBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

/**
 * Holds the parts of a rail bridge that blocks cannot draw straight: stays, main cables, hangers, truss diagonals, masts and the
 * overhead wire ({@link BridgeMemberBlockEntity}). It stands in a cell of the structure (a pylon head, a truss node, a parapet)
 * and draws that cell's block too, so it looks like the block it replaced; one with no such block is only a mast's plinth.
 * Placed by the wrench's bridge builder and taken away again by its undo or restyle.
 */
public class BridgeMemberBlock extends Block implements BlockEntityProvider {

	/** True when the block stands in for a full block of the structure; false for a mast plinth. */
	public static final BooleanProperty FULL = BooleanProperty.of("full");
	private static final VoxelShape PLINTH = Block.createCuboidShape(5, 0, 5, 11, 6, 11);

	public BridgeMemberBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(FULL, true));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FULL);
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new BridgeMemberBlockEntity(pos, state);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.ENTITYBLOCK_ANIMATED;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(FULL) ? VoxelShapes.fullCube() : PLINTH;
	}

	@Override
	public boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
		return true;
	}

	@Override
	public float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
		return 1f;
	}
}
