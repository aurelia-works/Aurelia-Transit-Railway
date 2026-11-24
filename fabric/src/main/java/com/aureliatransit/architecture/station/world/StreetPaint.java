package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.WorldOp;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.block.StratosBlocks;
import org.mtr.aurelia.road.fabric.paint.PaintLines;
import org.mtr.aurelia.road.fabric.paint.PaintStore;
import org.mtr.aurelia.road.road.line.LineChords;
import org.mtr.aurelia.road.road.line.LineKind;

/**
 * Road lines the station planner paints on its streets' asphalt, as the road editor paints them: a cell's state carries
 * {@code stratos_paint=<line>} and the line runs through the middle of the cell, along the way the cell faces. Asphalt has
 * no such property, so the block state leaves it out and the build writes the line to the world's paint. If ATR's roads
 * stand down for the standalone Stratos mod, nothing here can paint, and a painted cell is built as a white block instead.
 */
public final class StreetPaint {

	public static final String KEY = "stratos_paint=";
	private static final String UNPAINTED = "minecraft:white_concrete";

	private StreetPaint() {
	}

	/** The line a planned cell's state paints, or null. */
	public static LineKind line(String state) {
		if (state == null || !state.contains(KEY)) {
			return null;
		}
		for (final String pair : state.split(",")) {
			if (pair.startsWith(KEY)) {
				final String id = pair.substring(KEY.length()).trim();
				for (final LineKind kind : LineKind.values()) {
					if (kind.id().equals(id)) {
						return kind;
					}
				}
			}
		}
		return null;
	}

	/** The block an op is built from: its own, or a white block for a painted cell when there is no paint to put it in. */
	static String blockId(WorldOp op) {
		return blockId(op.blockId(), op.state());
	}

	static String blockId(String blockId, String state) {
		return line(state) != null && StratosMod.standsDown() ? UNPAINTED : blockId;
	}

	/**
	 * Paints, or clears, the cell an op has just built: its line on ATR's asphalt, and nothing on asphalt that has none, so a
	 * line left from before does not show through.
	 */
	static void build(ServerWorld world, BlockPos pos, WorldOp op, BlockState placed) {
		if (StratosMod.standsDown() || !placed.isOf(StratosBlocks.ASPHALT)) {
			return;
		}
		final LineKind line = line(op.state());
		PaintStore.of(world).set(pos.asLong(), line == null ? 0 : PaintLines.value(line, chord(op.facing())));
	}

	/** A straight line through the middle of a cell, along {@code facing}. */
	static int chord(Heading facing) {
		final int middle = LineChords.snap(0.5);
		return facing != null && facing.alongX()
				? LineChords.index(LineChords.WEST, middle, LineChords.EAST, middle)
				: LineChords.index(LineChords.NORTH, middle, LineChords.SOUTH, middle);
	}
}
