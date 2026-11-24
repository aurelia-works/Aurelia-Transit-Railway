package org.mtr.aurelia.crossing;

import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Map;

/**
 * A level crossing's warning lights: a post with two red lamps that face the way the block faces (towards the traffic
 * that must stop). While the crossing is closed the lamps flash in turn, {@link #FLASH} 1 and 2, and a bell rings with
 * each flash; open, both are dark.
 */
public final class LevelCrossingLightsBlock extends HorizontalFacingBlock {

	public static final IntProperty FLASH = IntProperty.of("flash", 0, 2);

	private static final Map<Direction, VoxelShape> SHAPES = Shapes.horizontalMap(Block.createCuboidShape(6, 0, 6, 10, 16, 10));

	public LevelCrossingLightsBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(FLASH, 0));
	}

	static int light(BlockState state) {
		return state.get(FLASH) > 0 ? 7 : 0;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, FLASH);
	}

	/** The lamps face the player who places them, standing where the traffic comes from. */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
		super.onBlockAdded(state, world, pos, oldState, notify);
		if (!world.isClient && !oldState.isOf(this)) {
			world.scheduleBlockTick(pos, this, 1);
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		final int flash = LevelCrossings.closed(world, pos) ? state.get(FLASH) == 1 ? 2 : 1 : 0;
		if (flash != state.get(FLASH)) {
			world.setBlockState(pos, state.with(FLASH, flash), Block.NOTIFY_ALL);
		}
		if (flash > 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.6F, flash == 1 ? 1.5F : 1.4F);
		}
		world.scheduleBlockTick(pos, this, LevelCrossings.CHECK_TICKS);
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPES.get(state.get(FACING));
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
