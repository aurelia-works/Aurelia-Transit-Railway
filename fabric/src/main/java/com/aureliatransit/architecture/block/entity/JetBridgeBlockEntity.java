package com.aureliatransit.architecture.block.entity;

import com.aureliatransit.architecture.airport.AircraftSample;
import com.aureliatransit.architecture.airport.JetBridgeDeck;
import com.aureliatransit.architecture.airport.JetBridgeMotion;
import com.aureliatransit.architecture.block.JetBridgeBlock;
import com.aureliatransit.architecture.block.JetBridgeDeckBlock;
import com.aureliatransit.architecture.registry.AirportBlocks;
import com.aureliatransit.architecture.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds how far the tunnel has travelled, from 0 (retracted) to 1 (at the door), on the client; not saved, the renderer derives it
 * again from the airplanes the client can see. On the server it holds the deck laid under a docked cab (saved, so a deck left at a
 * restart is still lifted).
 */
public class JetBridgeBlockEntity extends BlockEntity {

	/** Ticks a deck stays after the last word from a client that the cab is docked. */
	public static final int DECK_HOLD_TICKS = 60;
	/** Ticks between a client's reports while the cab stays docked. */
	public static final int DOCK_REPORT_TICKS = 20;
	private static final String DECK_KEY = "atrDeck";
	private static final String DECK_UNTIL_KEY = "atrDeckUntil";

	private float extension;
	/** How far the cab runs out at full extension; kept while it pulls back, so it retracts along the same path. */
	private float travel;
	private long lastAdvance = Long.MIN_VALUE;
	private long lastReport = Long.MIN_VALUE;
	private final List<BlockPos> deck = new ArrayList<>();
	private long deckUntil;
	private int deckReach = -1;

	public JetBridgeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.JET_BRIDGE, pos, state);
	}

	public float extension() {
		return extension;
	}

	/** Blocks the cab has run out of the block's face now. */
	public float reach() {
		return extension * travel;
	}

	/**
	 * Steps the tunnel once per world tick, and at most five steps if several ticks passed since it was last drawn. Called from the
	 * renderer, so the block itself has no ticker. While the cab is docked it tells the server, every second, to keep the deck.
	 */
	public void advance(long time) {
		if (time == lastAdvance) {
			return;
		}
		final int steps = lastAdvance == Long.MIN_VALUE ? 1 : (int) Math.min(5L, time - lastAdvance);
		lastAdvance = time;
		for (int i = 0; i < Math.max(1, steps); i++) {
			drive(JetBridgeBlock.samples.get());
		}
		if (extension >= 1f && time - lastReport >= DOCK_REPORT_TICKS) {
			lastReport = time;
			JetBridgeBlock.docked.accept(getPos(), travel);
		}
	}

	/** One step toward the airplanes in {@code samples}. Game tests call this without a client. */
	public void drive(List<AircraftSample> samples) {
		final BlockPos at = getPos();
		final double target = JetBridgeMotion.travel(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, facing().getOffsetX(), facing().getOffsetZ(), samples);
		if (target >= 0) {
			travel = (float) target;
		}
		extension = JetBridgeMotion.step(extension, target >= 0);
	}

	/**
	 * Server: lays (or keeps) the deck under a cab docked {@code reach} blocks out, for {@link #DECK_HOLD_TICKS} more ticks. Cells
	 * only go where there is air or deck already, so nothing built there is replaced.
	 */
	public void dock(ServerWorld world, float reach) {
		deckUntil = world.getTime() + DECK_HOLD_TICKS;
		final int rounded = Math.round(reach * 4);
		if (rounded != deckReach) {
			lift(world);
			deckReach = rounded;
			final Direction facing = facing();
			final Direction across = facing.rotateYClockwise();
			final BlockPos walk = getPos().down(JetBridgeMotion.ROOF_ABOVE_WALK);
			for (final JetBridgeDeck cell : JetBridgeDeck.cells(reach)) {
				final BlockPos at = walk.offset(facing, cell.d()).offset(across, cell.x()).up(cell.y());
				final BlockState there = world.getBlockState(at);
				if (there.isAir() || there.isOf(AirportBlocks.JET_BRIDGE_DECK)) {
					world.setBlockState(at, AirportBlocks.JET_BRIDGE_DECK.getDefaultState().with(JetBridgeDeckBlock.SOLID, cell.solid()), Block.NOTIFY_LISTENERS);
					deck.add(at.toImmutable());
				}
			}
		}
		world.scheduleBlockTick(getPos(), getCachedState().getBlock(), DOCK_REPORT_TICKS);
		markDirty();
	}

	/** Server, from the block's scheduled tick: lifts the deck once no client has reported the cab docked for a while. */
	public boolean expire(ServerWorld world) {
		if (deck.isEmpty()) {
			return false;
		}
		if (world.getTime() <= deckUntil) {
			return true;
		}
		lift(world);
		markDirty();
		return false;
	}

	private void lift(ServerWorld world) {
		for (final BlockPos at : deck) {
			if (world.getBlockState(at).isOf(AirportBlocks.JET_BRIDGE_DECK)) {
				world.setBlockState(at, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		deck.clear();
		deckReach = -1;
	}

	private Direction facing() {
		return getCachedState().contains(JetBridgeBlock.FACING) ? getCachedState().get(JetBridgeBlock.FACING) : Direction.NORTH;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		if (!deck.isEmpty()) {
			nbt.putLongArray(DECK_KEY, deck.stream().mapToLong(BlockPos::asLong).toArray());
			nbt.putLong(DECK_UNTIL_KEY, deckUntil);
		}
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		deck.clear();
		for (final long packed : nbt.getLongArray(DECK_KEY)) {
			deck.add(BlockPos.fromLong(packed));
		}
		deckUntil = nbt.getLong(DECK_UNTIL_KEY);
		deckReach = -1;
	}
}
