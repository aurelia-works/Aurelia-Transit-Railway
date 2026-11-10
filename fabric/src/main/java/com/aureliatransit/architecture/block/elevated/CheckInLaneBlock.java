package com.aureliatransit.architecture.block.elevated;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.block.FacingShapedBlock;
import com.aureliatransit.architecture.block.Placement;
import com.aureliatransit.architecture.block.elevated.ElevatedKinds.FareGateOpen;
import com.aureliatransit.architecture.block.mtr.MtrFareContract;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.block.Block.createCuboidShape;

/**
 * The way past a row of card readers (NS check-in poles): a line on the floor that a passenger cannot walk through until
 * they tap their card on a reader beside it (Jah 11-09: "a player who has not tapped is blocked from the station"). MTR's
 * fare system charges the tap, entry or exit as its record says, and the lane opens for that passenger like a fare gate:
 * it shuts once nobody stands in it, and anyone else slipping through while it is open is charged the maximum fare.
 *
 * <p>Shut, the lane stops walkers with a wall 1.5 blocks high that cannot be jumped; creative players and spectators pass,
 * so builders are not locked out of their own station. Without MTR's fare system the lane is only a floor line. No block
 * entity and no ticking: work happens only while a player touches the lane or taps a reader.
 */
public class CheckInLaneBlock extends FacingShapedBlock {

	public static final EnumProperty<FareGateOpen> OPEN = EnumProperty.of("open", FareGateOpen.class);
	public static final String TAP_MESSAGE = "message." + AureliaTransitArchitecture.MOD_ID + ".tap_in";
	/** How far from a tapped reader the lanes it opens may run, so one reader opens its whole row and nothing beyond. */
	public static final int REACH = 8;
	private static final int CLOSE_DELAY_TICKS = 60;
	private static final long PAYER_WINDOW_TICKS = 100;
	private static final Map<Direction, VoxelShape> SHUT = Shapes.horizontalMap(createCuboidShape(0, 0, 7, 16, 24, 9));
	private static final FarePayers PAYERS = new FarePayers();

	public CheckInLaneBlock(Settings settings) {
		super(settings, Placement.TOWARD_PLAYER, createCuboidShape(0, 0, 6, 16, 1, 10));
		setDefaultState(getDefaultState().with(OPEN, FareGateOpen.CLOSED));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(OPEN);
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		if (state.get(OPEN) == FareGateOpen.OPEN || !MtrFareContract.available() || exempt(context)) {
			return VoxelShapes.empty();
		}
		return SHUT.get(state.get(FACING));
	}

	private static boolean exempt(ShapeContext context) {
		return context instanceof EntityShapeContext entityContext && entityContext.getEntity() instanceof PlayerEntity player
				&& (player.isCreative() || player.isSpectator());
	}

	@Override
	public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
		if (world.isClient || !(entity instanceof PlayerEntity player) || player.isCreative() || player.isSpectator() || !MtrFareContract.available()) {
			return;
		}
		if (state.get(OPEN) != FareGateOpen.OPEN) {
			// walked into the shut lane: say what to do, about once a second
			if (world.getTime() % 20 == 0) {
				player.sendMessage(Text.translatable(TAP_MESSAGE).formatted(Formatting.YELLOW), true);
			}
			return;
		}
		final UUID payer = PAYERS.payerWithin(world, pos.asLong(), world.getTime());
		if (payer != null && !payer.equals(player.getUuid())) {
			// someone else slipping through behind the passenger who tapped: an incomplete journey
			PAYERS.forget(world, pos.asLong());
			final int charged = MtrFareContract.chargeMaximumFare(world, player);
			if (charged > 0) {
				player.sendMessage(Text.translatable("message." + AureliaTransitArchitecture.MOD_ID + ".maximum_fare", charged).formatted(Formatting.RED), true);
			}
		}
	}

	@Override
	public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		if (state.get(OPEN) == FareGateOpen.CLOSED) {
			return;
		}
		if (!world.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(0, 1, 0), player -> !player.isSpectator()).isEmpty()) {
			world.scheduleBlockTick(pos, this, CLOSE_DELAY_TICKS);
		} else {
			world.setBlockState(pos, state.with(OPEN, FareGateOpen.CLOSED));
			PAYERS.forget(world, pos.asLong());
		}
	}

	/**
	 * Opens the lanes in the row a reader at {@code reader} stands in, for {@code player}: every lane joined to a cell beside
	 * the reader, along the same level, up to {@link #REACH} blocks from it. Returns how many opened.
	 */
	public static int openBeside(World world, BlockPos reader, PlayerEntity player) {
		final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		final Set<BlockPos> seen = new HashSet<>();
		seen.add(reader);
		queue.add(reader);
		int opened = 0;
		while (!queue.isEmpty()) {
			final BlockPos at = queue.poll();
			for (final Direction side : Direction.Type.HORIZONTAL) {
				final BlockPos next = at.offset(side);
				if (next.getManhattanDistance(reader) > REACH || !seen.add(next)) {
					continue;
				}
				final BlockState state = world.getBlockState(next);
				if (!(state.getBlock() instanceof CheckInLaneBlock lane)) {
					continue;
				}
				world.setBlockState(next, state.with(OPEN, FareGateOpen.OPEN));
				world.scheduleBlockTick(next, lane, CLOSE_DELAY_TICKS);
				PAYERS.remember(world, next.asLong(), player.getUuid(), world.getTime() + PAYER_WINDOW_TICKS);
				queue.add(next);
				opened++;
			}
		}
		return opened;
	}
}
