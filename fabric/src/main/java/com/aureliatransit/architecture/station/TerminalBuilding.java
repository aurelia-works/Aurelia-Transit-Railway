package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The airport terminal hall, for the planned rectangle and for a terminal drawn in the editor. One generator: a plinth and a
 * glazed band on the landside, full-height glass on the airside, an entrance canopy, columns and a roof a person can walk
 * under. The planned fit-out (check-in, security, gates) stays in {@link PlanAirport#terminal()}.
 */
final class TerminalBuilding {

	/** Steel mullions in the glass, one every this many cells along a wall (7.5 m at the airport's block scale). */
	static final int MULLION = 5;
	/** Half-block steps from the long walls up to the ridge. Two blocks at the centre. */
	static final int PITCH_RISE = 2;
	/** Extra clearance above a drawn terminal's walls so the pitch is not buried. The roof sits one above the walls, then this. */
	static final int ROOF_CLEAR = PITCH_RISE + 1;

	private static final String ROOF_SHEET = "minecraft:smooth_stone";
	private static final String ROOF_SHEET_STEP = "minecraft:smooth_stone_slab";
	private static final String ROOF_GLASS = "minecraft:light_blue_stained_glass";

	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	private static final LocalDir[] SIDE_DIRS = {LocalDir.PLUS_U, LocalDir.MINUS_U, LocalDir.PLUS_V, LocalDir.MINUS_V};

	private TerminalBuilding() {
	}

	/** Deck line of the entrance canopy, shared with the name band in {@link PlanAirport#landsideCanopyY()}. */
	static int canopyLine(int wallTop) {
		return Math.min(StationGeometry.WALK_Y + 6, wallTop - 1);
	}

	/** Y of the airport name on the landside, just under the eave or just above the canopy, whichever is higher. */
	static int nameBandY(int wallTop) {
		return Math.max(wallTop - 1, canopyLine(wallTop) + 1);
	}

	/** The planned terminal: the rectangle from {@link AirportGeometry}, landside toward {@code -u}, with gate doors. */
	static void planned(PlanAirport plan) {
		final AirportGeometry air = plan.air;
		final int u0 = air.terminalU0();
		final int u1 = air.terminalU1();
		final int v0 = air.terminalV0();
		final int v1 = air.terminalV1();
		final int door = plan.entranceV();
		final int half = air.dims().entrance() / 2;
		final Set<Long> cells = new LinkedHashSet<>();
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				cells.add(DrawnShape.key(u, v));
			}
		}
		final Set<Long> openings = new LinkedHashSet<>();
		for (int v = door - half; v <= door + half; v++) {
			openings.add(DrawnShape.key(u0, v));
		}
		final int gateHalf = air.dims().bridgeWidth() / 2;
		for (final int stand : air.standV()) {
			for (int v = stand - gateHalf; v <= stand + gateHalf; v++) {
				if (v >= v0 && v <= v1) {
					openings.add(DrawnShape.key(u1, v));
				}
			}
		}
		build(plan, cells, LocalDir.MINUS_U, air.dims().wallTop(), u0, door, half, openings, true);
	}

	/**
	 * A drawn terminal. Landside is {@code -u} when that face exists, otherwise the longest exterior. The door is the middle of
	 * the longest landside run. No gate doors: a drawn shape has no stands.
	 */
	static void drawn(PlanState plan, Set<Long> cells, int height) {
		final LocalDir landside = landsideOf(cells);
		final int[] run = longestRun(cells, landside);
		if (run == null) {
			return;
		}
		final int length = run[2] - run[1] + 1;
		int half = entranceHalf(length);
		int door = (run[1] + run[2]) / 2;
		int from = door - half;
		int to = door + half;
		if (from < run[1]) {
			to += run[1] - from;
			from = run[1];
		}
		if (to > run[2]) {
			from -= to - run[2];
			to = run[2];
		}
		from = Math.max(from, run[1]);
		to = Math.min(to, run[2]);
		door = (from + to) / 2;
		half = (to - from) / 2;
		final boolean perpU = perpIsU(landside);
		final Set<Long> openings = new LinkedHashSet<>();
		for (int along = from; along <= to; along++) {
			openings.add(DrawnShape.key(perpU ? run[0] : along, perpU ? along : run[0]));
		}
		build(plan, cells, landside, StationGeometry.WALK_Y + height - 1, run[0], door, half, openings, false);
	}

	/** Column feet of a planned hall, {@code {u, v}}, for the departures boards. The shell uses the same places. */
	static List<int[]> hallColumns(int u0, int u1, int v0, int v1, int doorV) {
		return columnFeet(u0, u1, v0, v1, doorV, true);
	}

	private static void build(PlanState plan, Set<Long> cells, LocalDir landside, int wallTop, int doorPerp, int doorAlong, int entranceHalf,
			Set<Long> openings, boolean reserveName) {
		final int clearTo = StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT - 1;
		for (final long key : openings) {
			for (int y = StationGeometry.WALK_Y; y <= clearTo; y++) {
				plan.keepClear(DrawnShape.u(key), y, DrawnShape.v(key));
			}
		}
		final StylePalette.Building b = plan.palette.building();
		final boolean perpU = perpIsU(landside);
		final int[] box = bounds(cells);
		final boolean rectangle = filled(cells, box);
		floor(plan, cells, b, perpU, doorAlong);
		walls(plan, cells, b, landside, wallTop, openings);
		final int deck = canopyDeck(wallTop);
		toe(plan, cells, b, landside, doorAlong, deck >= 0 ? entranceHalf + 2 : entranceHalf);
		if (deck >= 0) {
			canopy(plan, cells, b, landside, wallTop, doorPerp, doorAlong, entranceHalf, deck);
		}
		final List<int[]> columns = columns(plan, cells, b, landside, wallTop, box, doorAlong);
		ribs(plan, cells, b, landside, wallTop, columns);
		roof(plan, cells, b, landside, wallTop, box);
		overhang(plan, cells, b, landside, wallTop, box, rectangle, doorAlong, entranceHalf, reserveName);
		if (rectangle && box[1] - box[0] + 1 >= 16 && box[3] - box[2] + 1 >= 16) {
			pitch(plan, b, landside, wallTop, box);
		}
	}

	/** -1 when the wall is too low for a canopy over a doorway (the deck would sit on the door head). */
	private static int canopyDeck(int wallTop) {
		final int deck = canopyLine(wallTop);
		if (deck < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT + 1) {
			return -1;
		}
		return deck;
	}

	private static void floor(PlanState plan, Set<Long> cells, StylePalette.Building b, boolean perpU, int doorAlong) {
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			final int along = perpU ? v : u;
			final String id = Math.abs(along - doorAlong) <= 1 ? b.floor() : PlanAirport.TERMINAL_FLOOR;
			plan.put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, id);
		}
	}

	private static void walls(PlanState plan, Set<Long> cells, StylePalette.Building b, LocalDir landside, int wallTop, Set<Long> openings) {
		final int courses = wallTop - StationGeometry.WALK_Y + 1;
		final int fascia = Math.max(2, courses / 5);
		final int bandTop = wallTop - fascia;
		final int transom = StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT;
		final LocalDir airside = landside.opposite();
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			int open = 0;
			boolean landOpen = false;
			boolean airOpen = false;
			LocalDir front = null;
			for (int i = 0; i < SIDES.length; i++) {
				if (!cells.contains(DrawnShape.key(u + SIDES[i][0], v + SIDES[i][1]))) {
					open++;
					if (SIDE_DIRS[i] == landside) {
						landOpen = true;
					}
					if (SIDE_DIRS[i] == airside) {
						airOpen = true;
					}
					if (front == null) {
						front = SIDE_DIRS[i];
					}
				}
			}
			if (open == 0) {
				continue;
			}
			if (landOpen) {
				front = landside;
			} else if (airOpen) {
				front = airside;
			}
			final boolean opening = openings.contains(key);
			final boolean corner = open >= 2 && !opening;
			final boolean curtain = airOpen && !landOpen && !corner;
			final boolean jamb = !opening && besideOpening(openings, u, v);
			final int along = front == LocalDir.PLUS_U || front == LocalDir.MINUS_U ? v : u;
			final boolean mullion = Math.floorMod(along, MULLION) == 0;
			for (int y = StationGeometry.WALK_Y; y <= wallTop; y++) {
				if (opening && y < transom) {
					continue;
				}
				if (corner) {
					plan.put(Role.WALL, u, y, v, front, y == StationGeometry.WALK_Y ? b.plinth() : b.trim());
					continue;
				}
				// the door head, and the jambs beside it, are trim from the plinth course up through the lintel
				if (opening && y == transom || jamb && y <= transom) {
					plan.put(Role.WALL, u, y, v, front, b.trim());
					continue;
				}
				if (curtain) {
					final boolean metal = y == StationGeometry.WALK_Y || y == transom || y == wallTop || mullion;
					plan.put(metal ? Role.WALL : Role.WINDOW, u, y, v, front,
							y == StationGeometry.WALK_Y ? b.plinth() : metal ? b.trim() : b.window());
					continue;
				}
				if (y == StationGeometry.WALK_Y) {
					plan.put(Role.WALL, u, y, v, front, b.plinth());
				} else if (y == transom || y <= bandTop && mullion) {
					plan.put(Role.WALL, u, y, v, front, b.trim());
				} else if (y <= bandTop) {
					plan.put(Role.WINDOW, u, y, v, front, b.window());
				} else {
					plan.put(Role.WALL, u, y, v, front, b.wall());
				}
			}
		}
	}

	/** One course of plinth outside the landside wall, stopped across the entrance so the door stays walkable. */
	private static void toe(PlanState plan, Set<Long> cells, StylePalette.Building b, LocalDir landside, int doorAlong, int flank) {
		final int du = du(landside);
		final int dv = dv(landside);
		final boolean perpU = perpIsU(landside);
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			if (cells.contains(DrawnShape.key(u + du, v + dv))) {
				continue;
			}
			final int along = perpU ? v : u;
			if (Math.abs(along - doorAlong) <= flank) {
				continue;
			}
			plan.put(Role.WALL, u + du, StationGeometry.WALK_Y, v + dv, landside, b.plinth());
		}
	}

	/**
	 * The entrance canopy: a deck over the door only, on steel columns at its outer corners, with a glazed slot down the middle
	 * and braces on the two flanks. Narrower than the forecourt canopy, so those two columns stay free.
	 */
	private static void canopy(PlanState plan, Set<Long> cells, StylePalette.Building b, LocalDir landside, int wallTop, int doorPerp,
			int doorAlong, int entranceHalf, int deck) {
		final int reach = wallTop >= 12 ? 7 : 4;
		final int flank = entranceHalf + 2;
		final boolean perpU = perpIsU(landside);
		final int sign = perpU ? du(landside) : dv(landside);
		final List<int[]> posted = new ArrayList<>();
		for (final int side : new int[]{-1, 1}) {
			posted.add(atStep(perpU, doorPerp, sign, reach, doorAlong + side * flank));
			if (reach >= 6) {
				posted.add(atStep(perpU, doorPerp, sign, reach / 2, doorAlong + side * flank));
			}
		}
		for (final int[] post : posted) {
			if (post[1] == doorAlong || cells.contains(DrawnShape.key(post[0], post[1]))) {
				continue;
			}
			// the cap needs a cover at the deck; placeColumn refuses a post whose deck cell is taken
			placeColumn(plan, post[0], post[1], deck - 1, b.pier(), landside);
		}
		for (final int side : new int[]{-1, 1}) {
			int y = StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT;
			for (int step = 1; step <= reach && y < deck; step++, y++) {
				final int[] at = atStep(perpU, doorPerp, sign, step, doorAlong + side * flank);
				if (cells.contains(DrawnShape.key(at[0], at[1]))) {
					continue;
				}
				plan.put(Role.RIB, at[0], y, at[1], landside, PlanAirport.VIADUCT_BRACE, "kind=diagonal");
			}
		}
		for (int step = 1; step <= reach; step++) {
			for (int along = doorAlong - flank; along <= doorAlong + flank; along++) {
				final int[] at = atStep(perpU, doorPerp, sign, step, along);
				if (cells.contains(DrawnShape.key(at[0], at[1]))) {
					continue;
				}
				final boolean light = Math.floorMod(step + along, 3) == 0;
				plan.put(light ? Role.LIGHT_PANEL : Role.BUILDING_ROOF, at[0], deck - 1, at[1], landside,
						light ? PlanAirport.HALL_LIGHT : b.plinth());
				final boolean end = along == doorAlong - flank || along == doorAlong + flank;
				final boolean rim = step == reach;
				final boolean slot = !end && !rim && Math.abs(along - doorAlong) <= 1;
				final String id = end || rim ? b.trim() : slot ? b.window() : b.roof();
				plan.put(Role.BUILDING_ROOF, at[0], deck, at[1], landside, id);
				if (rim) {
					plan.put(Role.BUILDING_ROOF, at[0], deck + 1, at[1], landside, b.trim());
				}
			}
		}
	}

	/** Hall columns. A post is skipped whole when its shaft or the cell above its cap is taken: a broken column fails the audit. */
	private static List<int[]> columns(PlanState plan, Set<Long> cells, StylePalette.Building b, LocalDir landside, int wallTop, int[] box,
			int doorAlong) {
		final boolean perpU = perpIsU(landside);
		final int p0 = perpU ? box[0] : box[2];
		final int p1 = perpU ? box[1] : box[3];
		final int a0 = perpU ? box[2] : box[0];
		final int a1 = perpU ? box[3] : box[1];
		final List<int[]> placed = new ArrayList<>();
		for (final int[] foot : columnFeet(p0, p1, a0, a1, doorAlong, perpU)) {
			final int u = foot[0];
			final int v = foot[1];
			if (!interior(cells, u, v)) {
				continue;
			}
			if (!placeColumn(plan, u, v, wallTop, PlanAirport.HALL_COLUMN, LocalDir.PLUS_V)) {
				continue;
			}
			placed.add(foot);
			if (wallTop >= 8) {
				sconce(plan, cells, u, v, perpU);
			}
		}
		return placed;
	}

	private static void sconce(PlanState plan, Set<Long> cells, int u, int v, boolean perpU) {
		final int y = StationGeometry.WALK_Y + 4;
		final int[] first = perpU ? new int[]{u, v + 1} : new int[]{u + 1, v};
		final int[] second = perpU ? new int[]{u, v - 1} : new int[]{u - 1, v};
		final int[] at = sconceAt(plan, cells, first) ? first : sconceAt(plan, cells, second) ? second : null;
		if (at == null) {
			return;
		}
		final LocalDir face = perpU ? (at[1] > v ? LocalDir.PLUS_V : LocalDir.MINUS_V) : (at[0] > u ? LocalDir.PLUS_U : LocalDir.MINUS_U);
		plan.put(Role.FIXTURE, at[0], y, at[1], face, PlanAirport.HALL_LIGHT);
	}

	private static boolean sconceAt(PlanState plan, Set<Long> cells, int[] at) {
		if (!interior(cells, at[0], at[1]) || plan.taken(at[0], StationGeometry.WALK_Y + 4, at[1])) {
			return false;
		}
		final PlannedBlock foot = plan.at(at[0], StationGeometry.WALK_Y, at[1]);
		return foot == null || !PlanAirport.HALL_COLUMN.equals(foot.blockId());
	}

	/** Ribs at the column caps, along each line and across between the landside and airside lines. Not beams: a hall is wider than a beam may span. */
	private static void ribs(PlanState plan, Set<Long> cells, StylePalette.Building b, LocalDir landside, int wallTop, List<int[]> columns) {
		if (columns.isEmpty()) {
			return;
		}
		final boolean perpU = perpIsU(landside);
		final TreeMap<Integer, TreeSet<Integer>> byPerp = new TreeMap<>();
		int landP = 0;
		int airP = 0;
		boolean any = false;
		final boolean landLow = landside == LocalDir.MINUS_U || landside == LocalDir.MINUS_V;
		for (final int[] foot : columns) {
			final int perp = perpU ? foot[0] : foot[1];
			final int along = perpU ? foot[1] : foot[0];
			byPerp.computeIfAbsent(perp, key -> new TreeSet<>()).add(along);
			if (!any || landLow && perp < landP || !landLow && perp > landP) {
				landP = perp;
			}
			if (!any || landLow && perp > airP || !landLow && perp < airP) {
				airP = perp;
			}
			any = true;
		}
		for (final var line : byPerp.entrySet()) {
			final int perp = line.getKey();
			int previous = Integer.MIN_VALUE;
			for (final int along : line.getValue()) {
				if (previous != Integer.MIN_VALUE) {
					for (int at = previous + 1; at < along; at++) {
						rib(plan, cells, b, perpU, perp, at, wallTop, true);
					}
				}
				previous = along;
			}
		}
		if (landP == airP) {
			return;
		}
		final int from = Math.min(landP, airP);
		final int to = Math.max(landP, airP);
		final TreeSet<Integer> alongs = new TreeSet<>();
		for (final int[] foot : columns) {
			alongs.add(perpU ? foot[1] : foot[0]);
		}
		for (final int along : alongs) {
			for (int perp = from + 1; perp < to; perp++) {
				rib(plan, cells, b, perpU, perp, along, wallTop, false);
			}
		}
	}

	private static void rib(PlanState plan, Set<Long> cells, StylePalette.Building b, boolean perpU, int perp, int along, int y, boolean alongRib) {
		final int u = perpU ? perp : along;
		final int v = perpU ? along : perp;
		if (!cells.contains(DrawnShape.key(u, v))) {
			return;
		}
		plan.put(Role.RIB, u, y, v, alongRib ? LocalDir.PLUS_V : LocalDir.PLUS_U, b.trim());
	}

	/** High roof. Skylights are a roof block, not a window: a window does not cover the column cap under it. */
	private static void roof(PlanState plan, Set<Long> cells, StylePalette.Building b, LocalDir landside, int wallTop, int[] box) {
		final int roofY = wallTop + 1;
		final boolean perpU = perpIsU(landside);
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			final int perp = perpU ? u : v;
			final int along = perpU ? v : u;
			final int p0 = perpU ? box[0] : box[2];
			final int p1 = perpU ? box[1] : box[3];
			final int a0 = perpU ? box[2] : box[0];
			final boolean sky = interior(cells, u, v) && perp > p0 + 5 && perp < p1 - 5 && Math.floorMod(along - a0, 16) < 2;
			if (sky) {
				plan.put(Role.BUILDING_ROOF, u, roofY, v, LocalDir.PLUS_V, ROOF_GLASS);
			} else if (interior(cells, u, v) && Math.floorMod(u + v, 4) == 2) {
				plan.put(Role.LIGHT_PANEL, u, roofY, v, LocalDir.PLUS_V, PlanAirport.HALL_LIGHT);
			} else {
				plan.put(Role.BUILDING_ROOF, u, roofY, v, LocalDir.PLUS_V, b.roof());
			}
		}
	}

	private static void overhang(PlanState plan, Set<Long> cells, StylePalette.Building b, LocalDir landside, int wallTop, int[] box,
			boolean rectangle, int doorAlong, int entranceHalf, boolean reserveName) {
		final int roofY = wallTop + 1;
		final boolean holdName = reserveName && nameBandY(wallTop) == wallTop;
		if (rectangle) {
			final int u0 = box[0];
			final int u1 = box[1];
			final int v0 = box[2];
			final int v1 = box[3];
			for (int u = u0 - 2; u <= u1 + 2; u++) {
				for (int v = v0 - 2; v <= v1 + 2; v++) {
					if (u >= u0 && u <= u1 && v >= v0 && v <= v1) {
						continue;
					}
					final boolean rim = u == u0 - 2 || u == u1 + 2 || v == v0 - 2 || v == v1 + 2;
					final LocalDir front = u < u0 ? LocalDir.MINUS_U : u > u1 ? LocalDir.PLUS_U : v < v0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
					plan.put(Role.BUILDING_ROOF, u, roofY, v, front, rim ? b.trim() : b.roof());
					if (!holdSoffit(landside, u0, u1, v0, v1, u, v, doorAlong, entranceHalf, holdName) && !forecourtCap(plan, u, roofY - 1, v)) {
						plan.put(Role.BUILDING_ROOF, u, roofY - 1, v, front, b.plinth());
					}
				}
			}
			return;
		}
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			for (int i = 0; i < SIDES.length; i++) {
				final int nu = u + SIDES[i][0];
				final int nv = v + SIDES[i][1];
				if (cells.contains(DrawnShape.key(nu, nv))) {
					continue;
				}
				plan.put(Role.BUILDING_ROOF, nu, roofY, nv, SIDE_DIRS[i], b.trim());
				final boolean name = holdName && SIDE_DIRS[i] == landside && Math.abs(along(landside, nu, nv) - doorAlong) <= entranceHalf + 3;
				if (!name && !forecourtCap(plan, nu, roofY - 1, nv)) {
					plan.put(Role.BUILDING_ROOF, nu, roofY - 1, nv, SIDE_DIRS[i], b.plinth());
				}
			}
		}
	}

	/** The name is hung on the first landside eave cell. On a low hall that cell is the soffit, so the soffit stays off it. */
	private static boolean holdSoffit(LocalDir landside, int u0, int u1, int v0, int v1, int u, int v, int doorAlong, int entranceHalf, boolean holdName) {
		if (!holdName) {
			return false;
		}
		final boolean step1 = switch (landside) {
			case MINUS_U -> u == u0 - 1;
			case PLUS_U -> u == u1 + 1;
			case MINUS_V -> v == v0 - 1;
			case PLUS_V -> v == v1 + 1;
		};
		return step1 && Math.abs(along(landside, u, v) - doorAlong) <= entranceHalf + 3;
	}

	/** Shallow pitch over a filled rectangle, flat at both long walls. A skylight cell is raised in glass instead of stone. */
	private static void pitch(PlanState plan, StylePalette.Building b, LocalDir landside, int wallTop, int[] box) {
		final int roofY = wallTop + 1;
		final boolean perpU = perpIsU(landside);
		final boolean landLow = landside == LocalDir.MINUS_U || landside == LocalDir.MINUS_V;
		final int p0 = perpU ? box[0] : box[2];
		final int p1 = perpU ? box[1] : box[3];
		final int a0 = perpU ? box[2] : box[0];
		final int a1 = perpU ? box[3] : box[1];
		final int centre = (p0 + p1) / 2;
		final int landSpan = Math.max(1, centre - p0);
		final int airSpan = Math.max(1, p1 - centre);
		final int rise = 2 * PITCH_RISE;
		for (int p = p0; p <= p1; p++) {
			final boolean landHalf = landLow ? p <= centre : p >= centre;
			final int half = landHalf
					? Math.min(rise, rise * (landLow ? p - p0 : p1 - p) / (landLow ? landSpan : Math.max(1, p1 - centre)))
					: Math.min(rise, rise * (landLow ? p1 - p : p - p0) / (landLow ? airSpan : landSpan));
			if (half == 0) {
				continue;
			}
			final LocalDir front = landHalf ? landside : landside.opposite();
			for (int a = a0; a <= a1; a++) {
				final int u = perpU ? p : a;
				final int v = perpU ? a : p;
				final PlannedBlock deck = plan.at(u, roofY, v);
				final boolean glass = deck != null && ROOF_GLASS.equals(deck.blockId());
				if (glass) {
					for (int y = roofY + 1; y <= roofY + (half + 1) / 2; y++) {
						plan.put(Role.BUILDING_ROOF, u, y, v, front, ROOF_GLASS);
					}
					continue;
				}
				for (int y = roofY + 1; y <= roofY + half / 2; y++) {
					plan.put(Role.BUILDING_ROOF, u, y, v, front, ROOF_SHEET);
				}
				if (half % 2 == 1) {
					plan.put(Role.BUILDING_ROOF, u, roofY + 1 + half / 2, v, front, ROOF_SHEET_STEP, "type=bottom");
				}
			}
		}
	}

	/**
	 * Shaft from the walk up to one cap, and nothing if the floor or the cover cell is missing. The cover itself is the roof or
	 * the canopy deck, placed after this returns.
	 */
	private static boolean placeColumn(PlanState plan, int u, int v, int capY, String block, LocalDir front) {
		final PlannedBlock floor = plan.at(u, StationGeometry.PLATFORM_Y, v);
		if (floor == null) {
			if (!plan.put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, PlanAirport.SIDEWALK)) {
				return false;
			}
		} else if (floor.role() != Role.FLOOR && !floor.role().platformFloor()) {
			return false;
		}
		if (!plan.freeColumn(u, StationGeometry.WALK_Y, capY + 1, v)) {
			return false;
		}
		for (int y = StationGeometry.WALK_Y; y < capY; y++) {
			plan.put(Role.COLUMN, u, y, v, front, block);
		}
		plan.put(Role.COLUMN_CAP, u, capY, v, front, block);
		return true;
	}

	/** The forecourt canopy's column cap, which the eave soffit has to leave free. The forecourt is planned after the terminal. */
	private static boolean forecourtCap(PlanState plan, int u, int y, int v) {
		if (!(plan instanceof PlanAirport airport)) {
			return false;
		}
		return y == StationGeometry.WALK_Y + 5 && airport.forecourtColumn(u, v);
	}

	private static List<int[]> columnFeet(int p0, int p1, int a0, int a1, int doorAlong, boolean perpU) {
		final List<int[]> out = new ArrayList<>();
		final int depth = p1 - p0 + 1;
		if (depth < 3 || a1 - a0 < 6) {
			return out;
		}
		final int[] lines = depth >= 16 ? new int[]{p0 + depth / 3, p0 + 2 * depth / 3} : new int[]{p0 + depth / 2};
		for (final int perp : lines) {
			for (int along = a0 + 4; along < a1 - 2; along += 8) {
				if (along == doorAlong) {
					continue;
				}
				out.add(new int[]{perpU ? perp : along, perpU ? along : perp});
			}
		}
		return out;
	}

	private static int entranceHalf(int run) {
		final int width = run >= 21 ? 9 : run >= 15 ? 7 : run >= 9 ? 5 : run >= 3 ? 3 : 1;
		final int fit = Math.max(1, run % 2 == 0 ? run - 1 : run);
		return Math.min(width, fit) / 2;
	}

	/** {@code -u} when any cell faces that way; otherwise the exterior direction with the most open sides. */
	private static LocalDir landsideOf(Set<Long> cells) {
		if (faces(cells, LocalDir.MINUS_U)) {
			return LocalDir.MINUS_U;
		}
		int best = -1;
		LocalDir dir = LocalDir.PLUS_U;
		for (final LocalDir candidate : SIDE_DIRS) {
			final int n = openCount(cells, candidate);
			if (n > best) {
				best = n;
				dir = candidate;
			}
		}
		return dir;
	}

	private static boolean faces(Set<Long> cells, LocalDir dir) {
		return openCount(cells, dir) > 0;
	}

	private static int openCount(Set<Long> cells, LocalDir dir) {
		final int du = du(dir);
		final int dv = dv(dir);
		int n = 0;
		for (final long key : cells) {
			if (!cells.contains(DrawnShape.key(DrawnShape.u(key) + du, DrawnShape.v(key) + dv))) {
				n++;
			}
		}
		return n;
	}

	/** {@code {perp, alongStart, alongEnd}} of the longest contiguous landside run, or null. */
	private static int[] longestRun(Set<Long> cells, LocalDir landside) {
		final int du = du(landside);
		final int dv = dv(landside);
		final boolean perpU = perpIsU(landside);
		final TreeMap<Integer, TreeSet<Integer>> lines = new TreeMap<>();
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			if (cells.contains(DrawnShape.key(u + du, v + dv))) {
				continue;
			}
			lines.computeIfAbsent(perpU ? u : v, perp -> new TreeSet<>()).add(perpU ? v : u);
		}
		int bestPerp = 0;
		int bestFrom = 0;
		int bestTo = -1;
		for (final var line : lines.entrySet()) {
			int from = Integer.MIN_VALUE;
			int last = Integer.MIN_VALUE;
			for (final int along : line.getValue()) {
				if (from != Integer.MIN_VALUE && along != last + 1) {
					if (last - from > bestTo - bestFrom) {
						bestPerp = line.getKey();
						bestFrom = from;
						bestTo = last;
					}
					from = Integer.MIN_VALUE;
				}
				if (from == Integer.MIN_VALUE) {
					from = along;
				}
				last = along;
			}
			if (from != Integer.MIN_VALUE && last - from > bestTo - bestFrom) {
				bestPerp = line.getKey();
				bestFrom = from;
				bestTo = last;
			}
		}
		if (bestTo < bestFrom) {
			return null;
		}
		return new int[]{bestPerp, bestFrom, bestTo};
	}

	private static boolean besideOpening(Set<Long> openings, int u, int v) {
		return openings.contains(DrawnShape.key(u + 1, v)) || openings.contains(DrawnShape.key(u - 1, v))
				|| openings.contains(DrawnShape.key(u, v + 1)) || openings.contains(DrawnShape.key(u, v - 1));
	}

	private static boolean interior(Set<Long> cells, int u, int v) {
		return cells.contains(DrawnShape.key(u, v)) && cells.contains(DrawnShape.key(u - 1, v)) && cells.contains(DrawnShape.key(u + 1, v))
				&& cells.contains(DrawnShape.key(u, v - 1)) && cells.contains(DrawnShape.key(u, v + 1));
	}

	private static boolean filled(Set<Long> cells, int[] box) {
		final long width = (long) box[1] - box[0] + 1;
		final long depth = (long) box[3] - box[2] + 1;
		return width > 0 && depth > 0 && cells.size() == width * depth;
	}

	private static int[] bounds(Set<Long> cells) {
		int u0 = Integer.MAX_VALUE;
		int u1 = Integer.MIN_VALUE;
		int v0 = Integer.MAX_VALUE;
		int v1 = Integer.MIN_VALUE;
		for (final long key : cells) {
			final int u = DrawnShape.u(key);
			final int v = DrawnShape.v(key);
			u0 = Math.min(u0, u);
			u1 = Math.max(u1, u);
			v0 = Math.min(v0, v);
			v1 = Math.max(v1, v);
		}
		return new int[]{u0, u1, v0, v1};
	}

	private static int[] atStep(boolean perpU, int doorPerp, int sign, int step, int along) {
		final int perp = doorPerp + sign * step;
		return new int[]{perpU ? perp : along, perpU ? along : perp};
	}

	private static int along(LocalDir landside, int u, int v) {
		return perpIsU(landside) ? v : u;
	}

	private static boolean perpIsU(LocalDir landside) {
		return landside == LocalDir.PLUS_U || landside == LocalDir.MINUS_U;
	}

	private static int du(LocalDir dir) {
		return dir == LocalDir.PLUS_U ? 1 : dir == LocalDir.MINUS_U ? -1 : 0;
	}

	private static int dv(LocalDir dir) {
		return dir == LocalDir.PLUS_V ? 1 : dir == LocalDir.MINUS_V ? -1 : 0;
	}
}
