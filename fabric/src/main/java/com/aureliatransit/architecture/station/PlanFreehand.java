package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.aureliatransit.architecture.station.StationPlanner.Cell;

/**
 * The platforms and buildings drawn by hand in the station editor ({@link DrawnShape}), planned after everything else as a
 * layer of their own (Jah's pick A, 2026-11-10): a shape only fills cells the station left free, so the station's own rules
 * never see it and a station without shapes plans exactly as before. Track corridors are cut out of every shape.
 *
 * <p>A platform gets its floor, a yellow edge where it meets a track, a canopy on columns (or lamp posts when open) with lights
 * under it, and benches. A building gets a floor, walls with windows and a doorway facing the station, and a flat roof with
 * light panels. Less detail than a planned platform, by design.
 */
final class PlanFreehand {

	/** Columns stand on this grid, then wherever a roof cell is still more than {@link StationGeometry#MAX_CANTILEVER} from one. */
	static final int COLUMN_GRID = StationGeometry.COLUMN_SPACING;
	/** One lamp post per this many blocks each way on an open platform. */
	static final int LAMP_GRID = 8;
	/** One bench (two seats) per this many blocks each way. */
	static final int BENCH_GRID = 12;
	/** A light panel in a building's roof every this many blocks each way. */
	static final int PANEL_GRID = 4;

	private final PlanState s;
	/** The cells the station planned before the shapes: what a shape must stay within reach of. */
	private final int[] station;

	private PlanFreehand(PlanState s) {
		this.s = s;
		this.station = stationBox(s);
	}

	/** Plans every drawn shape of the station that passes {@link DrawnShape#check}, in the order drawn. */
	static void plan(PlanState s) {
		final List<DrawnShape> shapes = s.spec.options().shapes();
		if (shapes.isEmpty() || s.spec.isAirport()) {
			return;
		}
		final PlanFreehand freehand = new PlanFreehand(s);
		for (final DrawnShape shape : shapes) {
			if (shape.check(freehand.station) != null) {
				continue;
			}
			final Set<Long> cells = freehand.free(shape.cells());
			if (cells.isEmpty()) {
				continue;
			}
			if (shape.kind() == DrawnShape.Kind.PLATFORM) {
				freehand.platform(cells, shape.open());
			} else {
				freehand.building(cells, shape.height());
			}
			freehand.ground(cells, shape.kind() == DrawnShape.Kind.PLATFORM ? StationGeometry.HEADROOM : shape.height());
		}
	}

	/** {uMin, uMax, vMin, vMax} of what the station planned. */
	private static int[] stationBox(PlanState s) {
		final int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
		for (final Cell cell : s.cells.keySet()) {
			box[0] = Math.min(box[0], cell.u());
			box[1] = Math.max(box[1], cell.u());
			box[2] = Math.min(box[2], cell.v());
			box[3] = Math.max(box[3], cell.v());
		}
		return box;
	}

	/** A shape's cells without the track corridors and without cells whose floor the station already took. */
	private Set<Long> free(Set<Long> cells) {
		final Set<Long> out = new LinkedHashSet<>();
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			if (!inCorridor(u, v) && !s.taken(u, StationGeometry.PLATFORM_Y, v)) {
				out.add(key);
			}
		}
		return out;
	}

	private boolean inCorridor(int u, int v) {
		for (final TrackLayout.Track track : s.layout.tracks()) {
			if (Math.abs(u - track.u()) <= StationGeometry.ENVELOPE_HALF_WIDTH && v >= s.layout.vMin() && v <= s.layout.trackEnd(track)) {
				return true;
			}
		}
		return false;
	}

	/** The way a platform edge at (u, v) faces its track, or null when no ground-level track runs {@link StationGeometry#EDGE_OFFSET} from it. */
	private LocalDir edgeFacing(int u, int v) {
		for (final TrackLayout.Track track : s.layout.tracks()) {
			if (track.level() != StationGeometry.PLATFORM_Y || v < s.layout.vMin() || v > s.layout.trackEnd(track)) {
				continue;
			}
			if (u == track.u() + StationGeometry.EDGE_OFFSET) {
				return LocalDir.MINUS_U;
			}
			if (u == track.u() - StationGeometry.EDGE_OFFSET) {
				return LocalDir.PLUS_U;
			}
		}
		return null;
	}

	// ---- platform -----------------------------------------------------------------------------------------------------

	private void platform(Set<Long> cells, boolean open) {
		final String edgeId = s.spec.features().edgeLines() ? s.palette.edgeId(s.spec.tactile()) : s.palette.surfaceId();
		final Set<Long> edges = new LinkedHashSet<>();
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			final LocalDir facing = edgeFacing(u, v);
			if (facing != null) {
				s.put(Role.PLATFORM_EDGE, u, StationGeometry.PLATFORM_Y, v, facing, edgeId);
				edges.add(key);
			} else {
				s.put(Role.PLATFORM_SURFACE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V);
			}
		}
		// where people may stand things: off the edge and the row behind it, so the walkway along the trains stays clear
		final List<long[]> walk = new ArrayList<>();
		final int[] box = box(cells);
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			if (!edges.contains(key) && !edges.contains(DrawnShape.key(u - 1, v)) && !edges.contains(DrawnShape.key(u + 1, v))) {
				walk.add(new long[]{u, v});
			}
		}
		if (open) {
			lampPosts(walk, box);
		} else {
			canopy(cells, walk, box);
		}
		if (s.spec.furniture()) {
			benches(cells, walk, box, edges);
		}
	}

	/**
	 * A flat canopy at {@link StationGeometry#CANOPY_Y} over the shape: columns on a {@link #COLUMN_GRID} grid, then more
	 * wherever a cell is still beyond a column's reach. A cell no column can reach stays open. Invisible lights under it.
	 */
	private void canopy(Set<Long> cells, List<long[]> walk, int[] box) {
		final int capY = StationGeometry.CANOPY_Y - 1;
		final int reach = StationGeometry.MAX_CANTILEVER;
		final List<int[]> columns = new ArrayList<>();
		final int half = COLUMN_GRID / 2;
		for (final long[] cell : walk) {
			final int u = (int) cell[0];
			final int v = (int) cell[1];
			if (Math.floorMod(u - box[0] - half, COLUMN_GRID) == 0 && Math.floorMod(v - box[2] - half, COLUMN_GRID) == 0 && column(u, v, capY)) {
				columns.add(new int[]{u, v});
			}
		}
		final Set<Long> roofed = new LinkedHashSet<>();
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			if (!reached(columns, u, v, reach)) {
				// the free walk cell nearest this one that can take a column
				int[] best = null;
				int bestDistance = Integer.MAX_VALUE;
				for (final long[] cell : walk) {
					final int d = Math.max(Math.abs((int) cell[0] - u), Math.abs((int) cell[1] - v));
					if (d <= reach && d < bestDistance && free(s, (int) cell[0], (int) cell[1], capY)) {
						best = new int[]{(int) cell[0], (int) cell[1]};
						bestDistance = d;
					}
				}
				if (best == null || !column(best[0], best[1], capY)) {
					continue;
				}
				columns.add(best);
			}
			roofed.add(key);
		}
		for (final long key : roofed) {
			s.put(Role.ROOF, DrawnShape.u(key), StationGeometry.CANOPY_Y, DrawnShape.v(key), LocalDir.PLUS_V);
		}
		if (!s.spec.furniture()) {
			return;
		}
		for (final long key : roofed) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			if (Math.floorMod(u, 2) == 0 && Math.floorMod(v, 2) == 0) {
				// right under the canopy, as the planner's own strips are, so the light is joined to the roof
				s.put(Role.FIXTURE, u, StationGeometry.CANOPY_Y - 1, v, LocalDir.PLUS_V, PlanRoofs.CANOPY_LIGHT, PlanRoofs.CANOPY_LIGHT_STATE);
			}
		}
	}

	private static boolean reached(List<int[]> columns, int u, int v, int reach) {
		for (final int[] c : columns) {
			if (Math.abs(c[0] - u) <= reach && Math.abs(c[1] - v) <= reach) {
				return true;
			}
		}
		return false;
	}

	/** True when cells from the walk level up to {@code top} are all free at (u, v). */
	private static boolean free(PlanState s, int u, int v, int top) {
		for (int y = StationGeometry.WALK_Y; y <= top; y++) {
			if (s.taken(u, y, v)) {
				return false;
			}
		}
		return true;
	}

	/** A canopy column at (u, v) from the walk level to its cap at {@code capY}, if the whole stack is free. */
	private boolean column(int u, int v, int capY) {
		if (!free(s, u, v, capY + 1)) {
			return false;
		}
		for (int y = StationGeometry.WALK_Y; y < capY; y++) {
			s.put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
		}
		s.put(Role.COLUMN_CAP, u, capY, v, LocalDir.PLUS_V);
		return true;
	}

	/** An open platform's lamp posts, one per {@link #LAMP_GRID} blocks each way. */
	private void lampPosts(List<long[]> walk, int[] box) {
		if (!s.spec.furniture()) {
			return;
		}
		final int half = LAMP_GRID / 2;
		for (final long[] cell : walk) {
			final int u = (int) cell[0];
			final int v = (int) cell[1];
			if (Math.floorMod(u - box[0] - half, LAMP_GRID) != 0 || Math.floorMod(v - box[2] - half, LAMP_GRID) != 0
					|| !free(s, u, v, PlanRoofs.LAMP_HEAD_Y)) {
				continue;
			}
			for (int y = StationGeometry.WALK_Y; y < PlanRoofs.LAMP_HEAD_Y; y++) {
				s.put(Role.FIXTURE, u, y, v, LocalDir.PLUS_V, StationPlanner.LAMP_POLE);
			}
			s.put(Role.LAMP, u, PlanRoofs.LAMP_HEAD_Y, v, LocalDir.PLUS_V);
		}
	}

	/** Two-seat benches along v, one per {@link #BENCH_GRID} blocks each way, facing the nearer track (or across the shape). */
	private void benches(Set<Long> cells, List<long[]> walk, int[] box, Set<Long> edges) {
		final Set<Long> standing = new LinkedHashSet<>();
		for (final long[] cell : walk) {
			standing.add(DrawnShape.key((int) cell[0], (int) cell[1]));
		}
		// rows 6 + 12k (and the seat after) never meet the column rows 4 + 8k
		for (final long[] cell : walk) {
			final int u = (int) cell[0];
			final int v = (int) cell[1];
			if (Math.floorMod(u - box[0] - BENCH_GRID / 3, BENCH_GRID) != 0 || Math.floorMod(v - box[2] - BENCH_GRID / 2, BENCH_GRID) != 0
					|| !standing.contains(DrawnShape.key(u, v + 1)) || s.taken(u, StationGeometry.WALK_Y, v)
					|| s.taken(u, StationGeometry.WALK_Y, v + 1)) {
				continue;
			}
			final LocalDir front = towardTrack(u, edges, cells);
			s.put(Role.BENCH, u, StationGeometry.WALK_Y, v, front);
			s.put(Role.BENCH, u, StationGeometry.WALK_Y, v + 1, front);
		}
	}

	/** Toward the platform's edge cells across u (the trains), else toward the wider side of the shape. */
	private static LocalDir towardTrack(int u, Set<Long> edges, Set<Long> cells) {
		int low = 0;
		int high = 0;
		for (final long key : edges) {
			if (DrawnShape.u(key) < u) {
				low++;
			} else {
				high++;
			}
		}
		if (low == 0 && high == 0) {
			for (final long key : cells) {
				if (DrawnShape.u(key) < u) {
					low++;
				} else if (DrawnShape.u(key) > u) {
					high++;
				}
			}
		}
		return high >= low ? LocalDir.PLUS_U : LocalDir.MINUS_U;
	}

	// ---- building -----------------------------------------------------------------------------------------------------

	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final LocalDir[] SIDE_DIRS = {LocalDir.PLUS_U, LocalDir.MINUS_U, LocalDir.PLUS_V, LocalDir.MINUS_V};

	private void building(Set<Long> cells, int height) {
		final int roofY = StationGeometry.WALK_Y + height;
		// the outline: cells with a side open to the outside, and which way it faces
		final List<long[]> outline = new ArrayList<>();
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			int open = 0;
			int way = -1;
			for (int i = 0; i < SIDES.length; i++) {
				if (!cells.contains(DrawnShape.key(u + SIDES[i][0], v + SIDES[i][1]))) {
					open++;
					way = i;
				}
			}
			if (open > 0) {
				outline.add(new long[]{u, v, way, open});
			}
		}
		final Set<Long> door = doorway(outline);
		for (final long key : cells) {
			s.put(Role.FLOOR, DrawnShape.u(key), StationGeometry.PLATFORM_Y, DrawnShape.v(key), LocalDir.PLUS_V);
		}
		for (final long[] cell : outline) {
			final int u = (int) cell[0];
			final int v = (int) cell[1];
			final LocalDir front = SIDE_DIRS[(int) cell[2]];
			final boolean corner = cell[3] > 1;
			for (int y = StationGeometry.WALK_Y; y < roofY; y++) {
				if (door.contains(DrawnShape.key(u, v)) && y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT) {
					s.air.add(new Cell(u, y, v));
					continue;
				}
				final int row = (y - StationGeometry.WALK_Y) % StationGeometry.STOREY;
				final boolean window = !corner && (row == 1 || row == 2) && y < roofY - 1 && Math.floorMod(u + v, 3) != 0;
				s.put(window ? Role.WINDOW : Role.WALL, u, y, v, front);
			}
		}
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			final boolean inner = cells.contains(DrawnShape.key(u - 1, v)) && cells.contains(DrawnShape.key(u + 1, v))
					&& cells.contains(DrawnShape.key(u, v - 1)) && cells.contains(DrawnShape.key(u, v + 1));
			final boolean panel = inner && Math.floorMod(u, PANEL_GRID) == PANEL_GRID / 2 && Math.floorMod(v, PANEL_GRID) == PANEL_GRID / 2;
			s.put(panel ? Role.LIGHT_PANEL : Role.BUILDING_ROOF, u, roofY, v, LocalDir.PLUS_V);
		}
	}

	/**
	 * A doorway two cells wide in the straight wall nearest the station's middle (one cell when the wall there is too short):
	 * the outline cells it takes.
	 */
	private Set<Long> doorway(List<long[]> outline) {
		final double midU = (station[0] + station[1]) / 2.0;
		final double midV = (station[2] + station[3]) / 2.0;
		long[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (final long[] cell : outline) {
			if (cell[3] != 1) {
				continue;
			}
			final double distance = Math.hypot(cell[0] - midU, cell[1] - midV);
			if (distance < bestDistance) {
				best = cell;
				bestDistance = distance;
			}
		}
		final Set<Long> door = new LinkedHashSet<>();
		if (best == null) {
			return door;
		}
		door.add(DrawnShape.key((int) best[0], (int) best[1]));
		// the next cell along the same wall (across the way it faces)
		final boolean facesU = best[2] < 2;
		for (final long[] cell : outline) {
			if (cell[2] == best[2] && cell[3] == 1 && (facesU ? cell[0] == best[0] && Math.abs(cell[1] - best[1]) == 1
					: cell[1] == best[1] && Math.abs(cell[0] - best[0]) == 1)) {
				door.add(DrawnShape.key((int) cell[0], (int) cell[1]));
				break;
			}
		}
		return door;
	}

	// ---- ground -------------------------------------------------------------------------------------------------------

	/** The support row under the shape and the space over its floor kept clear of terrain, one box per run of cells along u. */
	private void ground(Set<Long> cells, int clear) {
		final java.util.TreeMap<Integer, java.util.TreeSet<Integer>> rows = new java.util.TreeMap<>();
		for (final long key : cells) {
			rows.computeIfAbsent(DrawnShape.v(key), v -> new java.util.TreeSet<>()).add(DrawnShape.u(key));
		}
		for (final java.util.Map.Entry<Integer, java.util.TreeSet<Integer>> row : rows.entrySet()) {
			final int v = row.getKey();
			int from = Integer.MIN_VALUE;
			int last = Integer.MIN_VALUE;
			for (final int u : row.getValue()) {
				if (from != Integer.MIN_VALUE && u != last + 1) {
					runBoxes(from, last, v, clear);
					from = Integer.MIN_VALUE;
				}
				if (from == Integer.MIN_VALUE) {
					from = u;
				}
				last = u;
			}
			if (from != Integer.MIN_VALUE) {
				runBoxes(from, last, v, clear);
			}
		}
	}

	private void runBoxes(int from, int to, int v, int clear) {
		s.extraSupport.add(new Box(from, -1, v, to, -1, v));
		s.extraHeadroom.add(new Box(from, StationGeometry.WALK_Y, v, to, StationGeometry.WALK_Y + clear - 1, v));
	}

	/** {uMin, uMax, vMin, vMax} of a set of cells. */
	private static int[] box(Set<Long> cells) {
		final int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
		for (final long key : cells) {
			box[0] = Math.min(box[0], DrawnShape.u(key));
			box[1] = Math.max(box[1], DrawnShape.u(key));
			box[2] = Math.min(box[2], DrawnShape.v(key));
			box[3] = Math.max(box[3], DrawnShape.v(key));
		}
		return box;
	}
}
