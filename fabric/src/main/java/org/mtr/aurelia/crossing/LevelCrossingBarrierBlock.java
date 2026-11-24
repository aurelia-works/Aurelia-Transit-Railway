package org.mtr.aurelia.crossing;

import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Map;

/**
 * A level crossing barrier: a post by the road whose striped arm, {@link #LENGTH} blocks long, points the way the block
 * faces when it is down and stands up when it is raised. The post is the block's model; the arm is drawn by
 * {@code LevelCrossingBarrierRenderer}, swinging between the two. Using the barrier with an empty hand changes the arm's
 * length. Only the block the post stands in is solid where the arm is: the arm over the road is a sign to stop.
 */
public final class LevelCrossingBarrierBlock extends HorizontalFacingBlock implements BlockEntityProvider {

	public static final BooleanProperty LOWERED = BooleanProperty.of("lowered");
	public static final IntProperty LENGTH = IntProperty.of("length", 2, 8);

	/** The arm's pivot above the ground, in blocks, and the arm's thickness. */
	public static final float PIVOT = 0.8F;
	public static final float ARM = 0.14F;

	private static final VoxelShape POST = Block.createCuboidShape(6, 0, 6, 10, 15, 10);
	private static final Map<Direction, VoxelShape> RAISED = Shapes.horizontalMap(VoxelShapes.union(POST, Block.createCuboidShape(7, 15, 7, 9, 16, 9)));
	private static final Map<Direction, VoxelShape> DOWN = Shapes.horizontalMap(VoxelShapes.union(POST, Block.createCuboidShape(6.9, 11.6, 0, 9.1, 14, 8)));

	public LevelCrossingBarrierBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LOWERED, false).with(LENGTH, 4));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, LOWERED, LENGTH);
	}

	/** The arm points away from the player, across the road they look over. */
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
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
		final boolean closed = LevelCrossings.closed(world, pos);
		if (closed != state.get(LOWERED)) {
			world.setBlockState(pos, state.with(LOWERED, closed), Block.NOTIFY_ALL);
		}
		world.scheduleBlockTick(pos, this, LevelCrossings.CHECK_TICKS);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (!player.getStackInHand(hand).isEmpty() || !player.canModifyBlocks()) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			final BlockState next = state.cycle(LENGTH);
			world.setBlockState(pos, next, Block.NOTIFY_ALL);
			player.sendMessage(Text.translatable("message.mtr.level_crossing_barrier_length", next.get(LENGTH)), true);
		}
		return ActionResult.success(world.isClient);
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return (state.get(LOWERED) ? DOWN : RAISED).get(state.get(FACING));
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

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new LevelCrossingBarrierBlockEntity(pos, state);
	}
}
