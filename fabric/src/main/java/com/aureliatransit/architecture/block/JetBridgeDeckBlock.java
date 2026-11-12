package com.aureliatransit.architecture.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import org.mtr.mod.block.PlatformHelper;

/**
 * The part of a docked jet bridge you walk on. The cab is only drawn, so while a plane is docked the bridge lays these under it: a
 * solid floor and side walls, and beside the plane's door a passable one. All are invisible and none can be targeted or broken by
 * hand. They are MTR platform blocks, so a plane's door opens beside them. The bridge removes them when it pulls back.
 */
public class JetBridgeDeckBlock extends Block implements PlatformHelper {

	/** False for the cell in the door opening: you walk through it, and it still opens the door. */
	public static final BooleanProperty SOLID = BooleanProperty.of("solid");

	public JetBridgeDeckBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(SOLID, true));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(SOLID);
	}

	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.INVISIBLE;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(SOLID) ? VoxelShapes.fullCube() : VoxelShapes.empty();
	}

	@Override
	public VoxelShape getCameraCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return VoxelShapes.empty();
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
