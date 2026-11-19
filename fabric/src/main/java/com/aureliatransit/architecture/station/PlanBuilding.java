package com.aureliatransit.architecture.station;

import java.util.HashSet;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * The station building: storeys, roofs, towers and wings.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanBuilding extends PlanPlatforms {

	PlanBuilding(StationSpec spec) {
		super(spec);
	}

	/** An octagonal tower with its cap on a roof at {@code top} ({@link PlanHead#octTower}). */
	abstract void octTower(int cu, int cv, int r, int top, int height, PlanHead.OctCap cap, boolean clock);

	/**
	 * The crown of a grand side building's end pavilions for a look whose head building wears domes, or null: Tokyo's
	 * Marunouchi building ends in two octagonal pavilions under slate domes with no tower over its middle, Kuala Lumpur's
	 * in open domed pavilions, Mumbai's in onion domes (the same caps {@link PlanHead#headCrown} puts on their termini).
	 */
	static PlanHead.OctCap pavilionCap(BuildingStyle style) {
		return switch (style) {
			case TOKYO -> PlanHead.OctCap.DOME;
			case KUALA_LUMPUR -> PlanHead.OctCap.CHHATRI;
			case MUMBAI -> PlanHead.OctCap.ONION;
			default -> null;
		};
	}

	/** True when this grand side building ends in domed pavilions, which take the place of the clock tower over its middle. */
	boolean domedPavilions() {
		return scale == StationScale.GRAND && wingBays >= 2 && pavilionCap(spec.building()) != null;
	}

	/** The dark course round a hall's floor. */
	static final String FLOOR_BORDER = "aurelia_transit_architecture:metro_granite_floor";

	/**
	 * Whether a hall ceiling cell, counted from the building's street corner, is a light: lines of light along the hall every
	 * fourth row, broken at each bay line, rather than single panels dotted over the whole ceiling.
	 */
	/** A window cell of a long wall: the bay centre, and with framed windows the cell after it too. */
	private boolean glazed(int v, boolean framed) {
		return isBayCentre(v) || framed && isBayCentre(v - 1);
	}

	static boolean ceilingLight(int du, int dv) {
		return du % 4 == 2 && dv % StationGeometry.BAY != 0;
	}

	/** True when a Victorian suburban station has side platforms only, so both platforms are already step-free via ramps. */
	boolean heritageSidePlatforms() {
		return spec.building() == BuildingStyle.VICTORIAN
				&& layout.platforms().stream().noneMatch(TrackLayout.PlatformBand::island);
	}

	/** The building lift when one is actually built (omitted on step-free side-platform Victorian stations). */
	StationLayout.Lift activeBuildingLift() {
		return heritageSidePlatforms() ? null : site.buildingLift(layout);
	}

	/**
	 * The station building shell beside the tracks: floor, entrance ramps, perimeter walls with pilasters and windows,
	 * the band and upper slab with lights, the flat roof with a cornice parapet, the street entrance and platform
	 * doorways, and the inner stair to the upper floor. Interior fitting is in {@link #interior()}.
	 */
	void building() {
		if (!site.building()) {
			return;
		}
		final boolean sheet = landmark != null && landmark.anchor() == Landmark.Anchor.SIDE;
		if (palette.framedWindows() && scale == StationScale.COMPACT && !sheet) {
			victorianBuilding();
			return;
		}
		final StylePalette.Building b = palette.building();
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		final int vS = site.vStart();
		final int vE = site.vEnd();
		final int vEntr = site.entranceV();
		final int uA = uP - 2;
		final int uB = uP - 1;
		final int door = entranceHalf();
		// reserve the openings before any wall goes up
		for (int v = vEntr - door; v <= vEntr + door; v++) {
			for (int y = 1; y <= 3; y++) {
				keepClear(uS, y, v);
			}
		}
		if (site.bridge()) {
			// (a wide corridor's mouth leaves the lift shaft's side wall standing)
			final StationLayout.Lift shaft = activeBuildingLift();
			for (int v = site.bridgeV() - bridgeHalf(); v <= site.bridgeV() + bridgeHalf(); v++) {
				if (shaft != null && v >= shaft.vMin() && v <= shaft.vMax()) {
					continue;
				}
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					keepClear(uP, y, v);
				}
			}
		}
		for (int bay = 0; bay < site.bays(); bay++) {
			final int centre = site.bayCentre(bay);
			final StationLayout.Lift lift = activeBuildingLift();
			final boolean clearOfLift = lift == null || centre + 1 < lift.vMin() || centre - 1 > lift.vMax();
			if (clearOfLift && (centre + 1 < site.innerStairMin() || centre - 1 > site.innerStairMax())) {
				platformDoorway(uP, centre, b.window());
			}
		}
		// with wings, the paid side opens into their waiting rooms through a three-wide doorway in each end wall; the wall
		// stays down to the floor either side of it
		if (wingBays > 0) {
			final int doorCentre = (uS + 6 + uP - 2) / 2;
			for (int u = doorCentre - 1; u <= doorCentre + 1; u++) {
				for (int y = 1; y <= 3; y++) {
					keepClear(u, y, vS);
					keepClear(u, y, vE);
				}
			}
		}
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int u = uA; u <= uB; u++) {
				keepClear(u, y + 1, v);
				keepClear(u, y + 2, v);
			}
		}
		final int vBot = site.innerStairV(1) + site.innerStairSide();
		for (int u = uA; u <= uB; u++) {
			for (int y = 1; y <= 3; y++) {
				keepClear(u, y, vBot);
			}
		}
		if (sheet && landmark.usesEnd()) {
			// the sheet's wings stand over the platform doorways of their waiting rooms: reserved before its walls go up
			for (final int side : new int[]{-1, 1}) {
				final int inner = side < 0 ? site.vStart() : site.vEnd();
				for (int j = 0; j < wingBays; j++) {
					platformDoorway(uP, inner + side * (StationGeometry.BAY * j + 3), b.window());
				}
			}
		}
		// a real station's own building first, so the plain walls below only fill what its sheet leaves open
		landmarkSide();
		// ground floor and entrance ramps on the apron
		for (int u = uS; u <= uP; u++) {
			for (int v = vS; v <= vE; v++) {
				// the plinth under the walls, so the patterned floor does not show as a band along the base
				final boolean edge = u == uS || u == uP || v == vS || v == vE;
				// a dark course round the hall, one cell in from its walls, as a stone floor has at its edge
				final boolean border = !edge && (u == uS + 1 || u == uP - 1 || v == vS + 1 || v == vE - 1);
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : border ? FLOOR_BORDER : b.floor());
			}
		}
		// the ramps against the wall; the forecourt past them is paved in entrances()
		final int uRamp = uS - StationGeometry.RAMP_LENGTH;
		for (int v = vEntr - door; v <= vEntr + door; v++) {
			put(Role.RAMP_LOWER, uRamp, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
			put(Role.RAMP_UPPER, uRamp + 1, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
			for (int y = 1; y <= 3; y++) {
				keepClear(uRamp, y, v);
				keepClear(uRamp + 1, y, v);
			}
		}
		// perimeter walls, ground storey y=1..4
		final boolean framed = palette.framedWindows();
		for (int v = vS; v <= vE; v++) {
			final boolean pilaster = framed ? v == vS || v == vE : (v - vS) % StationGeometry.BAY == 0;
			// a framed window is two cells wide (a 2-over-2 sash is about 1.3 m) with a stucco sill and lintel its own width; no
			// side posts, as a block-thick jamb read as a slab of render round a slit, and wider heads ran into stripes (11-17)
			final boolean glazed = glazed(v, framed);
			final boolean head = framed && glazed;
			for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
				final boolean dressed = pilaster || head && (windowRow(y - 1) || windowRow(y + 1)) && !windowRow(y);
				if (y == 1) {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, pilaster ? b.trim() : b.plinth());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, pilaster ? b.trim() : b.plinth());
				} else if (!windowRow(y)) {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, dressed ? b.trim() : b.wall());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, dressed ? b.trim() : b.wall());
				} else if (glazed) {
					put(Role.WINDOW, uS, y, v, LocalDir.MINUS_U, b.window());
					put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
				} else {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, dressed ? b.trim() : b.wall());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, dressed ? b.trim() : b.wall());
				}
			}
			// band at the slab level (leaving the bridge doorway open)
			put(Role.WALL, uS, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.MINUS_U, b.trim());
			if (!(site.bridge() && v >= site.bridgeV() - bridgeHalf() && v <= site.bridgeV() + bridgeHalf())) {
				put(Role.WALL, uP, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_U, b.trim());
			}
			// the upper storey, windows in its middle rows
			for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
				final boolean dressed = pilaster || head && (windowRow(y - 1) || windowRow(y + 1)) && !windowRow(y);
				if (windowRow(y) && glazed) {
					put(Role.WINDOW, uS, y, v, LocalDir.MINUS_U, b.window());
					put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
				} else {
					put(Role.WALL, uS, y, v, LocalDir.MINUS_U, dressed ? b.trim() : b.wall());
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, dressed ? b.trim() : b.wall());
				}
			}
		}
		for (int u = uS; u <= uP; u++) {
			final boolean pilaster = framed ? u == uS || u == uP : (u - uS) % 3 == 0;
			for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
				if (y == 1) {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.plinth());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.plinth());
				} else if (!windowRow(y)) {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
				} else if (u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0) {
					put(Role.WINDOW, u, y, vS, LocalDir.MINUS_V, b.window());
					put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
				} else {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
				}
			}
			put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vS, LocalDir.MINUS_V, b.trim());
			put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vE, LocalDir.PLUS_V, b.trim());
			for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
				if (windowRow(y) && u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0) {
					put(Role.WINDOW, u, y, vS, LocalDir.MINUS_V, b.window());
					put(Role.WINDOW, u, y, vE, LocalDir.PLUS_V, b.window());
				} else {
					put(Role.WALL, u, y, vS, LocalDir.MINUS_V, pilaster ? b.trim() : b.wall());
					put(Role.WALL, u, y, vE, LocalDir.PLUS_V, pilaster ? b.trim() : b.wall());
				}
			}
		}
		// upper slab with light panels, leaving the stair well open over the flight only (the top step is part of the slab), and
		// running on past both ends of the flight, so there is no gap to fall through. A panel that falls
		// in the well moves to the slab beside it, so the stair's landing is not left dark
		final int wellMin = Math.min(site.innerStairV(StationGeometry.STAIR_STEPS), site.innerStairV(1));
		final int wellMax = Math.max(site.innerStairV(StationGeometry.STAIR_STEPS), site.innerStairV(1));
		final Set<Cell> moved = new HashSet<>();
		for (int u = uA; u <= uB; u++) {
			for (int v = wellMin; v <= wellMax; v++) {
				if (!ceilingLight(u - uS, v - vS)) {
					continue;
				}
				for (final int[] c : new int[][]{{uA - 1, v}, {uB + 1, v}, {u, wellMin - 1}, {u, wellMax + 1}}) {
					if (c[0] > uS && c[0] < uP && c[1] > vS && c[1] < vE && !(c[0] >= uA && c[0] <= uB && c[1] >= wellMin
							&& c[1] <= wellMax)) {
						moved.add(new Cell(c[0], StationGeometry.UPPER_FLOOR_Y, c[1]));
						break;
					}
				}
			}
		}
		for (int u = uS + 1; u <= uP - 1; u++) {
			for (int v = vS + 1; v <= vE - 1; v++) {
				if (u >= uA && u <= uB && v >= wellMin && v <= wellMax) {
					continue;
				}
				if (ceilingLight(u - uS, v - vS) || moved.contains(new Cell(u, StationGeometry.UPPER_FLOOR_Y, v))) {
					put(Role.LIGHT_PANEL, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
				} else {
					put(Role.FLOOR, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.upperFloor());
				}
			}
		}
		// storeys above the second (a city or grand station), the clock tower, then the roof and cornice parapet
		for (int storey = 0; storey < scale.extraStoreys(); storey++) {
			storey(StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * storey, uS, uP, vS, vE, true);
		}
		final int top = roofY();
		if (scale == StationScale.GRAND && !sheet && !domedPavilions()) {
			// the Amsterdam look has two towers flanking the entrance, as its head building does (Amsterdam Centraal's
			// clock on the right one, a wind dial on the left), and so does Hamburg Hbf, which RealStation gives two 45 m
			// towers; one over the entrance when the block is too short for both
			final boolean twoTowers = spec.building() == BuildingStyle.AMSTERDAM || spec.real() == RealStation.HAMBURG_HBF;
			final int flank = door + 5;
			if (twoTowers && vEntr - flank - 2 > vS && vEntr + flank + 2 < vE) {
				clockTower(top, uS, uP, vEntr - flank, LocalDir.PLUS_U);
				clockTower(top, uS, uP, vEntr + flank, LocalDir.PLUS_U);
			} else {
				clockTower(top, uS, uP, vEntr, LocalDir.PLUS_U);
			}
		}
		roof(top, uS, uP, vS, vE, sheet ? null : pitchedRoof());
		// inner stair flights (two columns wide)
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int u = uA; u <= uB; u++) {
				put(Role.STAIR, u, y, v, site.innerStairFront(), b.stair());
				// the space under the flight is closed in to its soffit, as a hall's stair is: nobody walks in under its low end,
				// and the steps' dark undersides don't hang over a dead-end pocket at its high end
				for (int below = StationGeometry.WALK_Y; below < y; below++) {
					put(Role.WALL, u, below, v, site.innerStairFront(), b.interiorWall());
				}
			}
		}
		// the flight's open side: a wall under the steps with a glass barrier along them, carried on round the well upstairs
		// and across its far end, so nobody steps off the stair or the upper floor into the drop
		final int side = site.innerStairSide();
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int h = 1; h <= y; h++) {
				put(Role.WALL, uA - 1, h, v, LocalDir.MINUS_U, b.interiorWall());
			}
			put(Role.BARRIER, uA - 1, y + 1, v, LocalDir.PLUS_U);
			if (y < StationGeometry.STAIR_STEPS) {
				put(Role.BARRIER, uA - 1, StationGeometry.UPPER_WALK_Y, v, LocalDir.PLUS_U);
			}
		}
		for (int u = uA; u <= uB; u++) {
			put(Role.BARRIER, u, StationGeometry.UPPER_WALK_Y, site.innerStairV(1) + side, side < 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V);
		}
	}

	/** y of the building block's roof: above its two storeys and any storeys a city or grand station adds. */
	int roofY() {
		return StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY * scale.extraStoreys();
	}

	/**
	 * One more storey on a building box from {@code floorY}: the slab (a trim band round the edge, the floor with light
	 * panels inside, which light the storey below too) and three wall rows with a window row in the middle, pilasters
	 * every bay along the long walls. {@code endWindows} puts windows in the end walls too (a wing's end walls are shared).
	 */
	void storey(int floorY, int uS, int uP, int vS, int vE, boolean endWindows) {
		storey(floorY, uS, uP, vS, vE, endWindows, false);
	}

	/**
	 * {@link #storey(int, int, int, int, int, boolean)}; with {@code allRound} the side walls take the end walls' rhythm (a
	 * window every other cell, trim every third), so a head building's street front carries on round its corners.
	 */
	void storey(int floorY, int uS, int uP, int vS, int vE, boolean endWindows, boolean allRound) {
		final StylePalette.Building b = palette.building();
		for (int u = uS; u <= uP; u++) {
			for (int v = vS; v <= vE; v++) {
				final boolean edge = u == uS || u == uP || v == vS || v == vE;
				if (edge) {
					put(Role.WALL, u, floorY, v, outward(u, v, uS, uP, vS, vE), b.trim());
				} else {
					slabCell(u, floorY, v);
				}
			}
		}
		for (int y = floorY + 1; y < floorY + StationGeometry.STOREY; y++) {
			final boolean windowRow = windowRow(y);
			for (int v = vS; v <= vE; v++) {
				final boolean pilaster = allRound ? (v - vS) % 3 == 0 : Math.floorMod(v - vS, StationGeometry.BAY) == 0;
				final boolean window = allRound ? windowRow && v >= vS + 2 && v <= vE - 2 && (v - vS) % 2 == 0
						: windowRow && !pilaster && Math.abs(Math.floorMod(v - vS, StationGeometry.BAY) - 3) <= 1;
				for (final int u : new int[]{uS, uP}) {
					final LocalDir front = u == uS ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					if (window) {
						put(Role.WINDOW, u, y, v, front, b.window());
					} else {
						put(Role.WALL, u, y, v, front, pilaster ? b.trim() : b.wall());
					}
				}
			}
			for (int u = uS + 1; u <= uP - 1; u++) {
				final boolean window = endWindows && windowRow && u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0;
				for (final int v : new int[]{vS, vE}) {
					final LocalDir front = v == vS ? LocalDir.MINUS_V : LocalDir.PLUS_V;
					put(window ? Role.WINDOW : Role.WALL, u, y, v, front, window ? b.window() : (u - uS) % 3 == 0 ? b.trim() : b.wall());
				}
			}
		}
	}

	/** A floor slab cell: a light panel on every fourth diagonal, so no floor cell above or below is more than 2 from one. */
	void slabCell(int u, int y, int v) {
		final StylePalette.Building b = palette.building();
		if (Math.floorMod(u + v, 4) == 0) {
			put(Role.LIGHT_PANEL, u, y, v, LocalDir.PLUS_V, b.light());
		} else {
			put(Role.FLOOR, u, y, v, LocalDir.PLUS_V, b.upperFloor());
		}
	}

	static LocalDir outward(int u, int v, int uS, int uP, int vS, int vE) {
		if (u == uS) {
			return LocalDir.MINUS_U;
		}
		if (u == uP) {
			return LocalDir.PLUS_U;
		}
		return v == vS ? LocalDir.MINUS_V : LocalDir.PLUS_V;
	}

	/**
	 * The roof of a building box at {@code top}: a flat roof with a cornice parapet, or for a masonry look at city or grand
	 * scale a hipped roof of stairs inside the cornice, over an attic lit through its floor.
	 */
	void roof(int top, int uS, int uP, int vS, int vE) {
		roof(top, uS, uP, vS, vE, pitchedRoof());
	}

	/** {@link #roof(int, int, int, int, int)} with the given pitched-roof blocks, or flat for null. */
	void roof(int top, int uS, int uP, int vS, int vE, String[] pitched) {
		final StylePalette.Building b = palette.building();
		for (int u = uS; u <= uP; u++) {
			for (int v = vS; v <= vE; v++) {
				final boolean edge = u == uS || u == uP || v == vS || v == vE;
				if (pitched != null && !edge && Math.floorMod(u + v, 4) == 0) {
					put(Role.LIGHT_PANEL, u, top, v, LocalDir.PLUS_V, b.light());
				} else {
					// under a pitched roof the wall runs on up to the cornice, not a band of slate showing on the front
					put(Role.BUILDING_ROOF, u, top, v, LocalDir.PLUS_V, pitched == null ? flatRoof() : edge ? b.wall() : b.roof());
				}
			}
		}
		for (int v = vS; v <= vE; v++) {
			put(Role.BUILDING_ROOF, uS, top + 1, v, LocalDir.MINUS_U, b.cornice());
			put(Role.BUILDING_ROOF, uP, top + 1, v, LocalDir.PLUS_U, b.cornice());
		}
		for (int u = uS; u <= uP; u++) {
			put(Role.BUILDING_ROOF, u, top + 1, vS, LocalDir.MINUS_V, b.cornice());
			put(Role.BUILDING_ROOF, u, top + 1, vE, LocalDir.PLUS_V, b.cornice());
		}
		if (pitched == null) {
			return;
		}
		if (palette.framedWindows()) {
			chimneys(top, uS, uP, vS, vE);
		}
		hipped(uS + 1, uP - 1, vS + 1, vE - 1, top + 1, pitched[0], pitched[1], Integer.MAX_VALUE);
	}

	/**
	 * Six brick chimney stacks through a hipped roof, as Teddington's house has (Historic England 1410351): a pair over each end
	 * and over the middle, on the front and back slopes, two cells long, all brick, standing two above the ridge with a pot on top.
	 * Put before the roof tiles so the stacks own their cells.
	 */
	private void chimneys(int top, int uS, int uP, int vS, int vE) {
		final StylePalette.Building b = palette.building();
		final int rings = Math.min((uP - uS - 2) / 2, (vE - vS - 2) / 2);
		final int cap = top + 1 + rings + 2;
		final int mid = (vS + vE) / 2;
		for (final int v : new int[]{vS + 4, mid, vE - 5}) {
			for (final int u : new int[]{uS + 3, uP - 3}) {
				for (int y = top + 1; y < cap; y++) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, b.wall());
					put(Role.BUILDING_ROOF, u, y, v + 1, LocalDir.PLUS_V, b.wall());
				}
				// an all-brick stack: a stucco cap read as a white block on every chimney (judge, 11-17)
				put(Role.BUILDING_ROOF, u, cap, v, LocalDir.PLUS_V, b.wall());
				put(Role.BUILDING_ROOF, u, cap, v + 1, LocalDir.PLUS_V, b.wall());
				put(Role.BUILDING_ROOF, u, cap + 1, v, LocalDir.PLUS_V, "minecraft:flower_pot");
				put(Role.BUILDING_ROOF, u, cap + 1, v + 1, LocalDir.PLUS_V, "minecraft:flower_pot");
			}
		}
	}

	/**
	 * A hipped roof over u0..u1 x v0..v1 from {@code y}: rings of stairs stepping in one cell and up one each, every stair
	 * rising towards the middle, at most {@code rings} rings, then a full-block top. Each ring above the first rests on a
	 * full block, the roof's thickness, so every tile touches the one below it face to face.
	 */
	void hipped(int u0, int u1, int v0, int v1, int y, String stair, String solid, int rings) {
		for (int k = 0; ; k++) {
			final int a0 = u0 + k;
			final int a1 = u1 - k;
			final int b0 = v0 + k;
			final int b1 = v1 - k;
			final int at = y + k;
			if (a0 >= a1 || b0 >= b1 || k == rings) {
				for (int u = a0; u <= a1; u++) {
					for (int v = b0; v <= b1; v++) {
						if (k > 0) {
							put(Role.BUILDING_ROOF, u, at - 1, v, LocalDir.PLUS_V, solid);
						}
						put(Role.BUILDING_ROOF, u, at, v, LocalDir.PLUS_V, solid);
					}
				}
				return;
			}
			for (int u = a0; u <= a1; u++) {
				for (int v = b0; v <= b1; v++) {
					if (u != a0 && u != a1 && v != b0 && v != b1) {
						continue;
					}
					if (k > 0) {
						put(Role.BUILDING_ROOF, u, at - 1, v, LocalDir.PLUS_V, solid);
					}
					putRoofStair(u, at, v, a0, a1, b0, b1, stair);
				}
			}
		}
	}

	/** Places a straight or 45-degree mitred outer-corner stair on a rectangular hipped roof ring. */
	private void putRoofStair(int u, int y, int v, int a0, int a1, int b0, int b1, String stair) {
		if (u == a0 && v == b0) {
			put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_U, stair, "half=bottom,shape=outer_left");
		} else if (u == a0 && v == b1) {
			put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_U, stair, "half=bottom,shape=outer_right");
		} else if (u == a1 && v == b0) {
			put(Role.BUILDING_ROOF, u, y, v, LocalDir.MINUS_U, stair, "half=bottom,shape=outer_right");
		} else if (u == a1 && v == b1) {
			put(Role.BUILDING_ROOF, u, y, v, LocalDir.MINUS_U, stair, "half=bottom,shape=outer_left");
		} else {
			final LocalDir up = v == b0 ? LocalDir.PLUS_V : v == b1 ? LocalDir.MINUS_V : u == a0 ? LocalDir.PLUS_U : LocalDir.MINUS_U;
			put(Role.BUILDING_ROOF, u, y, v, up, stair, PITCH_STATE);
		}
	}

	private static final String UK_QUOIN = "aurelia_transit_architecture:uk_stucco_quoin";
	private static final String UK_DRESSED = "aurelia_transit_architecture:uk_stucco_dressed";
	private static final String UK_STRING_COURSE = "aurelia_transit_architecture:uk_brick_string_course";
	private static final String UK_LINTEL = "aurelia_transit_architecture:uk_window_lintel";
	private static final String UK_SILL = "aurelia_transit_architecture:uk_window_sill";
	private static final String UK_SLATE_RIDGE = "aurelia_transit_architecture:uk_slate_ridge";
	private static final String ARCH_SHOULDER = "minecraft:smooth_quartz_stairs";

	/**
	 * Victorian Italianate suburban station house after Teddington (1863, LSWR, Historic England 1410351): a symmetric
	 * two-storey yellow-stock-brick house whose three central entrance bays step forward under a hipped slate roof with
	 * overhanging bracketed eaves and six brick chimney stacks, flanked by recessed two-storey bays and single-storey arched
	 * brick side wings.
	 */
	private void victorianBuilding() {
		final StylePalette.Building b = palette.building();
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		final int vS = site.vStart();
		final int vE = site.vEnd();
		final int vEntr = site.entranceV();
		final int uA = uP - 2;
		final int uB = uP - 1;
		final int door = entranceHalf();

		int canopyLo = 0;
		int canopyHi = length - 1;
		for (final TrackLayout.PlatformBand band : layout.platforms()) {
			if (band.uMin() == layout.buildingWidth() && roof(band) == RoofType.CANOPY) {
				canopyLo = canopyStart(band);
				canopyHi = canopyEnd(band);
				break;
			}
		}
		// Symmetric 7-bay 2-storey house (21 cells each side of entranceV) when platform canopy gives room, else site.vStart..vEnd
		final boolean fullSevenBay = wingBays == 0 && vEntr - 21 >= vS && vEntr + 21 <= canopyHi - 4;
		final int vHouseS = fullSevenBay ? vEntr - 21 : vS;
		final int vHouseE = fullSevenBay ? vEntr + 21 : vE;
		final boolean wideFront = vHouseE - vHouseS >= 30;
		final int vProjLo = wideFront ? vEntr - 9 : Math.max(vHouseS + 4, vEntr - 4);
		final int vProjHi = wideFront ? vEntr + 9 : Math.min(vHouseE - 4, vEntr + 4);

		// Reserve street entrance arch (3-wide clear doorway centred on vEntr; outer cells become stucco arch piers)
		final int clearDoor = Math.min(door, 1);
		for (int v = vEntr - clearDoor; v <= vEntr + clearDoor; v++) {
			for (int y = 1; y <= 3; y++) {
				keepClear(uS, y, v);
			}
		}
		if (site.bridge()) {
			final StationLayout.Lift shaft = activeBuildingLift();
			for (int v = site.bridgeV() - bridgeHalf(); v <= site.bridgeV() + bridgeHalf(); v++) {
				if (shaft != null && v >= shaft.vMin() && v <= shaft.vMax()) {
					continue;
				}
				for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
					keepClear(uP, y, v);
				}
			}
		}
		// Central arched platform doorway aligned with the street entrance for a straight sightline through the hall
		final StationLayout.Lift lift = activeBuildingLift();
		if (lift == null || vEntr + 1 < lift.vMin() || vEntr - 1 > lift.vMax()) {
			platformDoorway(uP, vEntr, b.window());
			if (platformBesideBuilding()) {
				cells.put(new Cell(uP, 4, vEntr - 1), new PlannedBlock(uP, 4, vEntr - 1, Role.WALL, ARCH_SHOULDER, LocalDir.MINUS_V, "", "half=top,shape=straight"));
				cells.put(new Cell(uP, 4, vEntr + 1), new PlannedBlock(uP, 4, vEntr + 1, Role.WALL, ARCH_SHOULDER, LocalDir.PLUS_V, "", "half=top,shape=straight"));
			}
		}
		// Secondary platform doorways only in outer flanking rooms, keeping the central booking hall symmetric
		for (final int outerDoor : new int[]{vHouseS + 3, vHouseE - 3}) {
			if (Math.abs(outerDoor - vEntr) >= 12
					&& (lift == null || outerDoor + 1 < lift.vMin() || outerDoor - 1 > lift.vMax())
					&& (outerDoor + 1 < site.innerStairMin() || outerDoor - 1 > site.innerStairMax())) {
				platformDoorway(uP, outerDoor, b.window());
			}
		}

		// Inner stair clearances
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int u = uA; u <= uB; u++) {
				keepClear(u, y + 1, v);
				keepClear(u, y + 2, v);
			}
		}
		final int vBot = site.innerStairV(1) + site.innerStairSide();
		for (int u = uA; u <= uB; u++) {
			for (int y = 1; y <= 3; y++) {
				keepClear(u, y, vBot);
			}
		}

		if (vHouseE > vE) {
			extraSupport.add(new Box(0, -1, vE + 1, layout.buildingWidth() - 1, -1, vHouseE));
			extraHeadroom.add(new Box(0, StationGeometry.WALK_Y, vE + 1, layout.buildingWidth() - 1, StationGeometry.BUILDING_ROOF_Y + 7, vHouseE));
		}

		// Ground floor tiles across the T-shaped 2-storey footprint (projecting 3 bays at uS..uP, recessed wings at uS+1..uP)
		for (int v = vHouseS; v <= vHouseE; v++) {
			final int uFront = v >= vProjLo && v <= vProjHi ? uS : uS + 1;
			for (int u = uFront; u <= uP; u++) {
				final boolean edge = u == uFront || u == uP || v == vHouseS || v == vHouseE
						|| u == uS && (v == vProjLo || v == vProjHi);
				final boolean border = !edge && (u == uFront + 1 || u == uP - 1 || v == vProjLo + 1 || v == vProjHi - 1);
				final boolean porchPlinth = u == uS && v >= vProjLo && v <= vProjHi;
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V,
						porchPlinth ? UK_DRESSED : edge ? b.plinth() : border ? FLOOR_BORDER : b.floor());
			}
		}

		// Street entrance ramps at uS - 2 .. uS - 1
		final int uRamp = uS - StationGeometry.RAMP_LENGTH;
		for (int v = vEntr - door; v <= vEntr + door; v++) {
			put(Role.RAMP_LOWER, uRamp, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
			put(Role.RAMP_UPPER, uRamp + 1, StationGeometry.PLATFORM_Y, v, LocalDir.MINUS_U);
			for (int y = 1; y <= 3; y++) {
				keepClear(uRamp, y, v);
				keepClear(uRamp + 1, y, v);
			}
		}

		// Long walls (street facade with 3-bay central projection, and platform facade)
		for (int v = vHouseS; v <= vHouseE; v++) {
			final boolean inProj = v >= vProjLo && v <= vProjHi;
			final int uFront = inProj ? uS : uS + 1;
			final boolean streetQuoin = v == vHouseS || v == vHouseE || v == vProjLo || v == vProjHi;
			final boolean platQuoin = v == vHouseS || v == vHouseE;
			final LocalDir streetQuoinDir = (v == vHouseS || v == vProjLo) ? LocalDir.MINUS_U : LocalDir.PLUS_V;
			final LocalDir platQuoinDir = v == vHouseS ? LocalDir.MINUS_V : LocalDir.PLUS_U;

			// 2-wide sash windows centred in each 6-block bay
			final int rel = Math.floorMod(v - (vEntr - 3), StationGeometry.BAY);
			final boolean sashPair = (rel == 2 || rel == 3) && v > vHouseS + 1 && v < vHouseE - 1 && v != vProjLo && v != vProjHi;
			final boolean centerDoorBay = Math.abs(v - vEntr) <= 2;
			final boolean porchFlankArch = inProj && !centerDoorBay && sashPair;
			// the platform front's ground-storey windows are round-headed in cream stucco surrounds (geograph 2670756)
			final boolean platJamb = !inProj && !platQuoin && (rel == 1 || rel == 4) && v > vHouseS + 1 && v < vHouseE - 1
					&& Math.abs(v - vEntr) > 3;

			for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
				// Street front ground storey: Teddington's projecting 3-bay porch front is dressed in cream rusticated stucco (geograph 6134993)
				if (inProj) {
					if (porchFlankArch) {
						if (y == 1) {
							put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, UK_DRESSED);
						} else if (y <= 3) {
							put(Role.WINDOW, uFront, y, v, LocalDir.MINUS_U, b.window());
						} else if (y == 4) {
							final LocalDir jamb = rel == 2 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
							put(Role.WALL, uFront, y, v, jamb, ARCH_SHOULDER, "half=top,shape=straight");
						} else {
							put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, UK_DRESSED);
						}
					} else {
						put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, UK_DRESSED);
					}
				} else if (streetQuoin) {
					putQuoin(uFront, y, v, streetQuoinDir);
				} else if (sashPair && !centerDoorBay) {
					if (y == 1) {
						put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, UK_SILL);
					} else if (windowRow(y)) {
						put(Role.WINDOW, uFront, y, v, LocalDir.MINUS_U, b.window());
					} else {
						put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, UK_LINTEL);
					}
				} else {
					put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, b.wall());
				}

				// Platform front ground storey (matching arched windows flanking the central platform doorway, geograph 2670756)
				if (platQuoin) {
					putQuoin(uP, y, v, platQuoinDir);
				} else if (porchFlankArch) {
					if (y == 1) {
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, UK_SILL);
					} else if (y <= 3) {
						put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
					} else if (y == 4) {
						final LocalDir jamb = rel == 2 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
						put(Role.WALL, uP, y, v, jamb, ARCH_SHOULDER, "half=top,shape=straight");
					} else {
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, b.wall());
					}
				} else if (sashPair && !centerDoorBay) {
					if (y == 1) {
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, UK_SILL);
					} else if (windowRow(y) && windowRow(y + 1)) {
						put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
					} else if (windowRow(y)) {
						// the top light's corners filled by the arch head, as on the porch windows
						final LocalDir jamb = rel == 2 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
						put(Role.WALL, uP, y, v, jamb, ARCH_SHOULDER, "half=top,shape=straight");
					} else {
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, UK_LINTEL);
					}
				} else if (platJamb && y >= 2 && windowRow(y)) {
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, UK_DRESSED);
				} else {
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, b.wall());
				}
			}

			// Slim moulded cream string course at upper slab level
			put(Role.WALL, uFront, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.MINUS_U, streetQuoin ? UK_DRESSED : UK_STRING_COURSE);
			if (!(site.bridge() && v >= site.bridgeV() - bridgeHalf() && v <= site.bridgeV() + bridgeHalf())) {
				put(Role.WALL, uP, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_U, platQuoin ? UK_DRESSED : UK_STRING_COURSE);
			}

			// Upper storey (y = 7..11): 7 crisp white-painted 2-wide sash windows with navy sills & cream lintels
			for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
				if (streetQuoin) {
					putQuoin(uFront, y, v, streetQuoinDir);
				} else if (sashPair) {
					if (y == StationGeometry.UPPER_WALK_Y) {
						put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, UK_SILL);
					} else if (windowRow(y)) {
						put(Role.WINDOW, uFront, y, v, LocalDir.MINUS_U, b.window());
					} else {
						put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, UK_LINTEL);
					}
				} else {
					put(Role.WALL, uFront, y, v, LocalDir.MINUS_U, b.wall());
				}

				if (platQuoin) {
					putQuoin(uP, y, v, platQuoinDir);
				} else if (sashPair) {
					if (y == StationGeometry.UPPER_WALK_Y) {
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, UK_SILL);
					} else if (windowRow(y)) {
						put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
					} else {
						put(Role.WALL, uP, y, v, LocalDir.PLUS_U, UK_LINTEL);
					}
				} else {
					put(Role.WALL, uP, y, v, LocalDir.PLUS_U, b.wall());
				}
			}
		}

		// Close the 1-block return walls where the central 3 bays step forward from uS+1 to uS
		for (final int vRet : new int[]{vProjLo, vProjHi}) {
			final LocalDir outDir = vRet == vProjLo ? LocalDir.MINUS_V : LocalDir.PLUS_V;
			for (int y = 1; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
				put(Role.WALL, uS + 1, y, vRet, outDir, y == StationGeometry.UPPER_FLOOR_Y ? UK_STRING_COURSE : b.wall());
			}
		}

		// End walls of the 2-storey house (vHouseS & vHouseE): one centred 2-wide upper sash window (geograph 6134993)
		final int uMid = (uS + 1 + uP) / 2;
		for (int u = uS + 2; u <= uP - 1; u++) {
			final boolean endSash = u == uMid - 1 || u == uMid;
			for (int y = 1; y < StationGeometry.UPPER_FLOOR_Y; y++) {
				put(Role.WALL, u, y, vHouseS, LocalDir.MINUS_V, b.wall());
				put(Role.WALL, u, y, vHouseE, LocalDir.PLUS_V, b.wall());
			}
			put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vHouseS, LocalDir.MINUS_V, UK_STRING_COURSE);
			put(Role.WALL, u, StationGeometry.UPPER_FLOOR_Y, vHouseE, LocalDir.PLUS_V, UK_STRING_COURSE);
			for (int y = StationGeometry.UPPER_WALK_Y; y <= StationGeometry.BUILDING_ROOF_Y - 1; y++) {
				for (final int vEnd : new int[]{vHouseS, vHouseE}) {
					final LocalDir dir = vEnd == vHouseS ? LocalDir.MINUS_V : LocalDir.PLUS_V;
					if (endSash) {
						if (y == StationGeometry.UPPER_WALK_Y) {
							put(Role.WALL, u, y, vEnd, dir, UK_SILL);
						} else if (windowRow(y)) {
							put(Role.WINDOW, u, y, vEnd, dir, b.window());
						} else {
							put(Role.WALL, u, y, vEnd, dir, UK_LINTEL);
						}
					} else {
						put(Role.WALL, u, y, vEnd, dir, b.wall());
					}
				}
			}
		}

		// Upper floor slab with light panels (every indoor ground cell within Manhattan distance <= 4 of a LIGHT_PANEL)
		final int wellMin = Math.min(site.innerStairV(StationGeometry.STAIR_STEPS), site.innerStairV(1));
		final int wellMax = Math.max(site.innerStairV(StationGeometry.STAIR_STEPS), site.innerStairV(1));
		for (int v = vHouseS + 1; v <= vHouseE - 1; v++) {
			final int uFront = v > vProjLo && v < vProjHi ? uS : uS + 1;
			for (int u = uFront + 1; u <= uP - 1; u++) {
				if (u >= uA && u <= uB && v >= wellMin && v <= wellMax) {
					continue;
				}
				final boolean nearWell = (u == uA - 1 && (v == wellMin || v == wellMax));
				if (Math.floorMod(u + v, 4) == 0 || nearWell) {
					put(Role.LIGHT_PANEL, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.light());
				} else {
					put(Role.FLOOR, u, StationGeometry.UPPER_FLOOR_Y, v, LocalDir.PLUS_V, b.upperFloor());
				}
			}
		}

		// Bracketed Italianate eaves + compound hipped slate roof + 6 brick chimneys
		final int top = roofY();
		victorianCompoundRoof(top, uS, uP, vHouseS, vHouseE, vProjLo, vProjHi);

		// Inner stair flights, cleanly enclosed behind a plaster partition wall so no raw stair steps cut into the hall
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int u = uA; u <= uB; u++) {
				put(Role.STAIR, u, y, v, site.innerStairFront(), b.stair());
				for (int below = StationGeometry.WALK_Y; below < y; below++) {
					put(Role.WALL, u, below, v, site.innerStairFront(), b.interiorWall());
				}
			}
		}
		final int side = site.innerStairSide();
		for (int y = 1; y <= StationGeometry.STAIR_STEPS; y++) {
			final int v = site.innerStairV(y);
			for (int h = 1; h < StationGeometry.UPPER_FLOOR_Y; h++) {
				put(Role.WALL, uA - 1, h, v, LocalDir.MINUS_U, b.interiorWall());
			}
			put(Role.BARRIER, uA - 1, StationGeometry.UPPER_WALK_Y, v, LocalDir.PLUS_U);
		}
		for (int u = uA; u <= uB; u++) {
			put(Role.BARRIER, u, StationGeometry.UPPER_WALK_Y, site.innerStairV(1) + side, side < 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V);
		}

		// 1-storey arched yellow-stock-brick side wings (Teddington's right-hand arched wing & left-hand footbridge wing)
		if (platformBesideBuilding() && wingBays == 0 && uP - (uS + 2) >= 5) {
			final int rightSpan = vHouseS - 12 >= Math.max(2, canopyLo + 3) ? 12 : vHouseS - 6 >= Math.max(2, canopyLo + 2) ? 6 : 0;
			if (rightSpan > 0) {
				victorianSideWing(uS + 2, uP, vHouseS - rightSpan, vHouseS - 1, -1);
			}
			if (vHouseE + 6 <= Math.min(length - 3, canopyHi - 3)) {
				victorianSideWing(uS + 2, uP, vHouseE + 1, vHouseE + 6, 1);
			}
		}
	}

	private void putQuoin(int u, int y, int v, LocalDir cornerDir) {
		put(Role.WALL, u, y, v, cornerDir, (y & 1) == 1 ? UK_DRESSED : UK_QUOIN);
	}

	/**
	 * Single-storey arched yellow-stock-brick wing beside the two-storey house (geograph 6134993, 4331043): round-headed
	 * arched windows, bracketed cream cornice at y=5, recessed ceiling lights over wooden waiting benches, and a low hipped
	 * slate roof at y=6..7.
	 */
	private void victorianSideWing(int u0, int uP, int v0, int v1, int endSide) {
		final StylePalette.Building b = palette.building();
		extraSupport.add(new Box(0, -1, v0, layout.buildingWidth() - 1, -1, v1));
		extraHeadroom.add(new Box(0, StationGeometry.WALK_Y, v0, layout.buildingWidth() - 1, 9, v1));

		final int vEnd = endSide < 0 ? v0 : v1;
		final LocalDir endDir = endSide < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
		final int doorMid = (v0 + v1) / 2;
		final boolean hasDoor = platformBesideBuilding() && v1 - v0 >= 5;
		if (hasDoor) {
			for (int dv = -1; dv <= 1; dv++) {
				for (int y = 1; y <= 3; y++) {
					keepClear(uP, y, doorMid + dv);
				}
			}
		}

		for (int u = u0; u <= uP; u++) {
			for (int v = v0; v <= v1; v++) {
				final boolean edge = u == u0 || u == uP || v == vEnd;
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : b.floor());
			}
		}

		for (int v = v0; v <= v1; v++) {
			final boolean outerCorner = v == vEnd;
			final int local = Math.floorMod(v - v0, 6);
			final boolean archWin = (local == 2 || local == 3) && v > v0 && v < v1;
			for (int y = 1; y <= 4; y++) {
				if (outerCorner) {
					putQuoin(u0, y, v, endSide < 0 ? LocalDir.MINUS_U : LocalDir.PLUS_V);
					putQuoin(uP, y, v, endSide < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_U);
				} else {
					if (archWin) {
						if (y == 1) {
							put(Role.WALL, u0, y, v, LocalDir.MINUS_U, UK_SILL);
						} else if (y <= 3) {
							put(Role.WINDOW, u0, y, v, LocalDir.MINUS_U, b.window());
						} else {
							cells.put(new Cell(u0, y, v), new PlannedBlock(u0, y, v, Role.WALL, ARCH_SHOULDER,
									local == 2 ? LocalDir.MINUS_V : LocalDir.PLUS_V, "", "half=top,shape=straight"));
						}
					} else {
						put(Role.WALL, u0, y, v, LocalDir.MINUS_U, b.wall());
					}
					final boolean inDoor = hasDoor && Math.abs(v - doorMid) <= 1;
					if (!inDoor) {
						if (archWin) {
							if (y == 1) {
								put(Role.WALL, uP, y, v, LocalDir.PLUS_U, UK_SILL);
							} else if (y <= 3) {
								put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
							} else {
								cells.put(new Cell(uP, y, v), new PlannedBlock(uP, y, v, Role.WALL, ARCH_SHOULDER,
										local == 2 ? LocalDir.MINUS_V : LocalDir.PLUS_V, "", "half=top,shape=straight"));
							}
						} else {
							put(Role.WALL, uP, y, v, LocalDir.PLUS_U, b.wall());
						}
					} else if (y == 4) {
						if (v == doorMid) {
							put(Role.WINDOW, uP, y, v, LocalDir.PLUS_U, b.window());
						} else {
							cells.put(new Cell(uP, y, v), new PlannedBlock(uP, y, v, Role.WALL, ARCH_SHOULDER,
									v < doorMid ? LocalDir.MINUS_V : LocalDir.PLUS_V, "", "half=top,shape=straight"));
						}
					}
				}
			}
			// Waiting bench along the inside of the wing's street wall
			if (spec.furniture() && archWin && !taken(u0 + 1, StationGeometry.WALK_Y, v)) {
				put(Role.BENCH, u0 + 1, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, b.bench());
			}
		}

		// End wall of the 1-storey wing
		final int uWingMid = (u0 + uP) / 2;
		for (int u = u0 + 1; u <= uP - 1; u++) {
			final boolean win = u == uWingMid || u == uWingMid - 1;
			for (int y = 1; y <= 4; y++) {
				if (win && y == 1) {
					put(Role.WALL, u, y, vEnd, endDir, UK_SILL);
				} else if (win && (y == 2 || y == 3)) {
					put(Role.WINDOW, u, y, vEnd, endDir, b.window());
				} else if (win && y == 4) {
					put(Role.WALL, u, y, vEnd, endDir, UK_LINTEL);
				} else {
					put(Role.WALL, u, y, vEnd, endDir, b.wall());
				}
			}
		}

		// Eaves & lit ceiling at y = 5, plus low hipped slate roof at y = 6..7
		final int wingTop = 5;
		for (int u = u0; u <= uP; u++) {
			for (int v = v0; v <= v1; v++) {
				final boolean outerEdge = u == u0 || u == uP || v == vEnd;
				if (outerEdge) {
					final LocalDir dir = u == u0 ? LocalDir.MINUS_U : u == uP ? LocalDir.PLUS_U : endDir;
					put(Role.BUILDING_ROOF, u, wingTop, v, dir, b.cornice());
				} else if (Math.floorMod(u + v, 3) == 0) {
					put(Role.LIGHT_PANEL, u, wingTop, v, LocalDir.PLUS_V, b.light());
				} else {
					put(Role.BUILDING_ROOF, u, wingTop, v, LocalDir.PLUS_V, b.interiorWall());
				}
			}
		}
		final String[] tiles = pitchedRoof();
		if (tiles != null) {
			for (int u = u0; u <= uP; u++) {
				for (int v = v0; v <= v1; v++) {
					if (u == u0 || u == uP || v == v0 || v == v1) {
						putRoofStair(u, wingTop + 1, v, u0, uP, v0, v1, tiles[0]);
					} else {
						put(Role.BUILDING_ROOF, u, wingTop + 1, v, LocalDir.PLUS_V, tiles[1]);
						put(Role.BUILDING_ROOF, u, wingTop + 2, v, LocalDir.PLUS_V, UK_SLATE_RIDGE);
					}
				}
			}
		}
	}

	/**
	 * Overhanging Italianate bracketed eaves at {@code top}, six yellow-brick chimney stacks, and a compound hipped slate roof
	 * over the T-shaped two-storey house (with 45-degree outer hip and inner valley stairs and a smooth slate-and-lead ridge).
	 */
	private void victorianCompoundRoof(int top, int uS, int uP, int vHouseS, int vHouseE, int vProjLo, int vProjHi) {
		final StylePalette.Building b = palette.building();
		final String[] pitched = pitchedRoof();
		for (int v = vHouseS; v <= vHouseE; v++) {
			final int uFront = v >= vProjLo && v <= vProjHi ? uS : uS + 1;
			for (int u = uFront; u <= uP; u++) {
				final boolean outer = u == uFront || u == uP || v == vHouseS || v == vHouseE
						|| u == uS && (v == vProjLo || v == vProjHi);
				if (outer) {
					final LocalDir dir = u == uFront ? LocalDir.MINUS_U : u == uP ? LocalDir.PLUS_U
							: (v == vHouseS || v == vProjLo) ? LocalDir.MINUS_V : LocalDir.PLUS_V;
					put(Role.BUILDING_ROOF, u, top, v, dir, b.cornice());
				} else if (Math.floorMod(u + v, 4) == 0) {
					put(Role.LIGHT_PANEL, u, top, v, LocalDir.PLUS_V, b.light());
				} else {
					put(Role.BUILDING_ROOF, u, top, v, LocalDir.PLUS_V, b.roof());
				}
			}
		}
		if (pitched == null) {
			return;
		}
		final String stair = pitched[0];
		final String solid = pitched[1];

		// Compute exact Chebyshev roof ring height h(u, v) >= 0 inside the T-shaped footprint
		final int uSpan = uP - uS + 1;
		final int vSpan = vHouseE - vHouseS + 1;
		final int[][] h = new int[uSpan][vSpan];
		int maxH = 0;
		for (int u = uS; u <= uP; u++) {
			for (int v = vHouseS; v <= vHouseE; v++) {
				if (!inVictorianHouse(u, v, uS, uP, vHouseS, vHouseE, vProjLo, vProjHi)) {
					h[u - uS][v - vHouseS] = -1;
					continue;
				}
				int ring = 0;
				while (ring < 16) {
					boolean outside = false;
					final int r = ring + 1;
					for (int du = -r; du <= r && !outside; du++) {
						for (int dv = -r; dv <= r && !outside; dv++) {
							if (Math.max(Math.abs(du), Math.abs(dv)) == r
									&& !inVictorianHouse(u + du, v + dv, uS, uP, vHouseS, vHouseE, vProjLo, vProjHi)) {
								outside = true;
							}
						}
					}
					if (outside) {
						break;
					}
					ring++;
				}
				h[u - uS][v - vHouseS] = ring;
				maxH = Math.max(maxH, ring);
			}
		}

		// Six yellow-stock-brick chimney stacks with a slim stucco necking course and terracotta chimney pots
		final int capY = top + 1 + maxH + 2;
		final int midV = (vProjLo + vProjHi) / 2;
		final int[] chimVs = vHouseE - vHouseS >= 28
				? new int[]{vHouseS + 4, midV - 1, vHouseE - 5}
				: new int[]{vHouseS + 3, vHouseE - 4};
		for (final int cv : chimVs) {
			for (final int cu : new int[]{uS + 3, uP - 3}) {
				if (cu <= uS + 1 || cu >= uP - 1 || cv <= vHouseS + 1 || cv + 1 >= vHouseE - 1) {
					continue;
				}
				for (int y = top + 1; y <= capY; y++) {
					put(Role.BUILDING_ROOF, cu, y, cv, LocalDir.PLUS_V, b.wall());
					put(Role.BUILDING_ROOF, cu, y, cv + 1, LocalDir.PLUS_V, b.wall());
				}
				put(Role.BUILDING_ROOF, cu, capY + 1, cv, LocalDir.PLUS_V, "minecraft:flower_pot");
				put(Role.BUILDING_ROOF, cu, capY + 1, cv + 1, LocalDir.PLUS_V, "minecraft:flower_pot");
			}
		}

		// Lay compound hipped slate slopes with exact straight / outer-hip / inner-valley stairs + smooth slate ridge cap
		for (int u = uS; u <= uP; u++) {
			for (int v = vHouseS; v <= vHouseE; v++) {
				final int k = h[u - uS][v - vHouseS];
				if (k < 0) {
					continue;
				}
				final int y = top + 1 + k;
				if (k > 0) {
					put(Role.BUILDING_ROOF, u, y - 1, v, LocalDir.PLUS_V, solid);
				}
				final boolean upPu = roofHigher(h, u + 1, v, uS, uP, vHouseS, vHouseE, k);
				final boolean upMu = roofHigher(h, u - 1, v, uS, uP, vHouseS, vHouseE, k);
				final boolean upPv = roofHigher(h, u, v + 1, uS, uP, vHouseS, vHouseE, k);
				final boolean upMv = roofHigher(h, u, v - 1, uS, uP, vHouseS, vHouseE, k);

				if (!upPu && !upMu && !upPv && !upMv) {
					// Ridge peak: smooth low-profile Welsh slate & lead roll cap instead of a blocky stair step
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, UK_SLATE_RIDGE);
				} else if (upPu && upPv) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_U, stair, "half=bottom,shape=inner_left");
				} else if (upPu && upMv) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_U, stair, "half=bottom,shape=inner_right");
				} else if (upMu && upPv) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.MINUS_U, stair, "half=bottom,shape=inner_right");
				} else if (upMu && upMv) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.MINUS_U, stair, "half=bottom,shape=inner_left");
				} else if (upPu) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_U, stair, PITCH_STATE);
				} else if (upMu) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.MINUS_U, stair, PITCH_STATE);
				} else if (upPv) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, stair, PITCH_STATE);
				} else if (upMv) {
					put(Role.BUILDING_ROOF, u, y, v, LocalDir.MINUS_V, stair, PITCH_STATE);
				}
			}
		}
		// Second pass for outer hip corners (where neither cardinal neighbour is higher, only a diagonal neighbour is k + 1)
		for (int u = uS; u <= uP; u++) {
			for (int v = vHouseS; v <= vHouseE; v++) {
				final int k = h[u - uS][v - vHouseS];
				if (k < 0 || k == maxH) {
					continue;
				}
				final boolean anyCard = roofHigher(h, u + 1, v, uS, uP, vHouseS, vHouseE, k)
						|| roofHigher(h, u - 1, v, uS, uP, vHouseS, vHouseE, k)
						|| roofHigher(h, u, v + 1, uS, uP, vHouseS, vHouseE, k)
						|| roofHigher(h, u, v - 1, uS, uP, vHouseS, vHouseE, k);
				if (anyCard) {
					continue;
				}
				final int y = top + 1 + k;
				final PlannedBlock cur = at(u, y, v);
				if (cur == null || !cur.blockId().equals(UK_SLATE_RIDGE)) {
					continue;
				}
				final boolean dPP = roofHigher(h, u + 1, v + 1, uS, uP, vHouseS, vHouseE, k);
				final boolean dPM = roofHigher(h, u + 1, v - 1, uS, uP, vHouseS, vHouseE, k);
				final boolean dMP = roofHigher(h, u - 1, v + 1, uS, uP, vHouseS, vHouseE, k);
				final boolean dMM = roofHigher(h, u - 1, v - 1, uS, uP, vHouseS, vHouseE, k);
				if (dPP && !dPM && !dMP && !dMM) {
					cells.put(new Cell(u, y, v), new PlannedBlock(u, y, v, Role.BUILDING_ROOF, stair, LocalDir.PLUS_U, "", "half=bottom,shape=outer_left"));
				} else if (dPM && !dPP && !dMP && !dMM) {
					cells.put(new Cell(u, y, v), new PlannedBlock(u, y, v, Role.BUILDING_ROOF, stair, LocalDir.PLUS_U, "", "half=bottom,shape=outer_right"));
				} else if (dMP && !dPP && !dPM && !dMM) {
					cells.put(new Cell(u, y, v), new PlannedBlock(u, y, v, Role.BUILDING_ROOF, stair, LocalDir.MINUS_U, "", "half=bottom,shape=outer_right"));
				} else if (dMM && !dPP && !dPM && !dMP) {
					cells.put(new Cell(u, y, v), new PlannedBlock(u, y, v, Role.BUILDING_ROOF, stair, LocalDir.MINUS_U, "", "half=bottom,shape=outer_left"));
				}
			}
		}
	}

	private static boolean inVictorianHouse(int u, int v, int uS, int uP, int vHouseS, int vHouseE, int vProjLo, int vProjHi) {
		if (v < vHouseS || v > vHouseE || u > uP) {
			return false;
		}
		return u >= (v >= vProjLo && v <= vProjHi ? uS : uS + 1);
	}

	private static boolean roofHigher(int[][] h, int u, int v, int uS, int uP, int vHouseS, int vHouseE, int k) {
		if (u < uS || u > uP || v < vHouseS || v > vHouseE) {
			return false;
		}
		return h[u - uS][v - vHouseS] > k;
	}

	/**
	 * Stairs and the matching full block for a pitched roof, or null for a flat one (modern and concrete looks). A compact
	 * building is pitched too: a town's brick station house has a slate roof, not a flat one (Teddington, 10-24).
	 */
	String[] pitchedRoof() {
		return roofTiles(spec.building());
	}

	/** A look's roof tiles (stairs, then the full block), or null for a look with flat roofs. */
	static String[] roofTiles(BuildingStyle style) {
		return switch (style) {
			case ANTWERP, BEAUX_ARTS -> new String[]{"minecraft:oxidized_cut_copper_stairs", "minecraft:oxidized_cut_copper"};
			case AMSTERDAM, GOTHIC_BRICK, SANDSTONE, CLINKER, TOKYO, ZURICH, VICTORIAN -> new String[]{"minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles"};
			// Mumbai's red clay tiles
			case MUMBAI -> new String[]{"minecraft:brick_stairs", "minecraft:bricks"};
			case NONE, MODERN, CONCRETE, PARIS, MILAN, BERLIN, HONG_KONG, BEIJING, BANGKOK, KUALA_LUMPUR, ART_DECO, MODERNIST -> null;
		};
	}

	/** A flat roof's membrane where the look has no roofing of its own (seen from above, a grey roof, not a brick field). */
	static final String ROOF_MEMBRANE = "minecraft:gray_concrete";

	/**
	 * The top of a flat roof: the look's roof block, unless that is one of its facade blocks (most masonry looks), which laid
	 * flat over a whole building reads as one solid lump; then the look's roof tiles, or a grey membrane.
	 */
	String flatRoof() {
		return flatRoof(palette.building(), spec.building());
	}

	static String flatRoof(StylePalette.Building b, BuildingStyle style) {
		final String roof = b.roof();
		if (!roof.equals(b.wall()) && !roof.equals(b.trim()) && !roof.equals(b.plinth()) && !roof.equals(b.interiorWall())) {
			return roof;
		}
		final String[] tiles = roofTiles(style);
		return tiles != null ? tiles[1] : ROOF_MEMBRANE;
	}

	/**
	 * A grand station's clock tower centred on {@code vMid}, standing on the street wall {@code u0}: a hollow square shaft five
	 * cells across rising {@link #TOWER_HEIGHT} above the roof, louvred openings, a clock face on every side but the one facing
	 * {@code inward} (into the building), a cornice and a pyramid cap with a finial. Planned before the roof so the roof leaves
	 * the tower open. A real station's tower is as high as the real one and as stout as a clock tower ({@link #tallTowerSize}),
	 * as far as the building {@code u0..uP} holds it.
	 */
	void clockTower(int top, int u0, int uP, int vMid, LocalDir inward) {
		final int real = spec.real().towerTopY();
		int size = real > 0 ? tallTowerSize(real) : 5;
		while (size > 5 && (u0 + size > uP || vMid - size / 2 <= site.vStart() || vMid + size / 2 >= site.vEnd())) {
			size -= 2;
		}
		// the pyramid's point at the real height (Hamburg's 45 m), not its cornice
		final int height = real > 0 ? real - top - (size - 1) / 2 - 1 : TOWER_HEIGHT;
		tower(top, u0, vMid - size / 2, Math.max(TOWER_HEIGHT, height), inward, Cap.PYRAMID, true, false, size);
	}

	/**
	 * Width of a real station's tall clock tower over the street to {@code topY}: an eighth of its height, as stout as the
	 * slimmest real ones (Big Ben is 12 m across and 96 m high), odd so the clock sits in the middle. Hamburg's 45 m: 9 cells.
	 */
	static int tallTowerSize(int topY) {
		final int size = Math.max(5, (topY + 7) / 8);
		return size % 2 == 0 ? size + 1 : size;
	}

	/** Copper domes, except white ones in Kuala Lumpur, stone ones in Mumbai and dark slate ones in Tokyo. */
	String domeBlock() {
		return switch (spec.building()) {
			case KUALA_LUMPUR -> "minecraft:calcite";
			case MUMBAI -> "minecraft:cut_sandstone";
			case TOKYO -> "minecraft:deepslate_tiles";
			default -> DOME;
		};
	}

	/** How a tower ends: a pyramid, a pyramid with a tall spire, a copper dome, or flat (a glass tower). */
	enum Cap {
		PYRAMID, SPIRE, DOME, FLAT
	}

	/** Storey height of a tall tower, between its string courses. */
	static final int TALL_TOWER_STOREY = 8;

	/**
	 * A tower five cells across standing on the roof at {@code top}, {@code height} high, like {@link #clockTower}: clock faces
	 * when {@code clock}, glass between the corners and bands when {@code glass} (a modern glass tower), and its {@code cap}.
	 */
	void tower(int top, int u0, int v0, int height, LocalDir inward, Cap cap, boolean clock, boolean glass) {
		tower(top, u0, v0, height, inward, cap, clock, glass, 5);
	}

	/** The same, {@code size} cells across (odd); a wider tower's openings are three cells wide and its pyramid rises higher. */
	void tower(int top, int u0, int v0, int height, LocalDir inward, Cap cap, boolean clock, boolean glass, int size) {
		final StylePalette.Building b = palette.building();
		final int u1 = u0 + size - 1;
		final int v1 = v0 + size - 1;
		final int uC = u0 + size / 2;
		final int vC = v0 + size / 2;
		final int opening = (size - 3) / 4;
		final int rings = (size - 1) / 2;
		final int crown = top + height;
		for (int u = u0 + 1; u <= u1 - 1; u++) {
			for (int v = v0 + 1; v <= v1 - 1; v++) {
				// the roof under the tower is lit so the closed shaft stays dark-free; the rest of the shaft stays empty
				if (u == uC && v == vC) {
					put(Role.LIGHT_PANEL, u, top, v, LocalDir.PLUS_V, b.light());
				}
				for (int y = top + 1; y <= crown; y++) {
					keepClear(u, y, v);
				}
			}
		}
		// a tall tower (a real station's) is stone storeys with string courses and narrow windows, its belfry under the clock,
		// so it does not read as a chimney
		final boolean tall = height > TOWER_HEIGHT;
		for (int y = top + 1; y <= crown; y++) {
			final int storey = (y - top - 1) % TALL_TOWER_STOREY;
			final boolean band = y == top + 1 || y == crown || tall && storey == 0;
			final boolean louvre = tall ? y >= crown - 7 && y <= crown - 5 || storey >= 3 && storey <= 5 && y < crown - 9
					: y >= top + 3 && y <= top + 5;
			for (int u = u0; u <= u1; u++) {
				for (int v = v0; v <= v1; v++) {
					final boolean ring = u == u0 || u == u1 || v == v0 || v == v1;
					if (!ring) {
						continue;
					}
					final boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
					final boolean middle = Math.abs(u - uC) <= opening || Math.abs(v - vC) <= opening;
					final LocalDir front = outward(u, v, u0, u1, v0, v1);
					if (band || corner) {
						put(Role.WALL, u, y, v, front, b.trim());
					} else if (glass || louvre && middle) {
						put(Role.WINDOW, u, y, v, front, b.window());
					} else {
						put(Role.WALL, u, y, v, front, b.wall());
					}
				}
			}
		}
		// clock faces on the outer sides, one row under the cornice
		final int clockY = crown - 2;
		final String clockFace = ata("amsterdam_clock_face_panel");
		for (final LocalDir side : LocalDir.values()) {
			if (!clock || side == inward) {
				continue;
			}
			final int cu = side == LocalDir.MINUS_U ? u0 - 1 : side == LocalDir.PLUS_U ? u1 + 1 : uC;
			final int cv = side == LocalDir.MINUS_V ? v0 - 1 : side == LocalDir.PLUS_V ? v1 + 1 : vC;
			// on a head building as narrow as the tower a side face would hang outside the station's footprint
			if (cu >= 0 && cu < layout.width() && cv >= layout.vMin() && cv <= layout.vMax()) {
				put(Role.FIXTURE, cu, clockY, cv, side, clockFace);
			}
		}
		// the cap: a closed slab, then a pyramid of rings and a point, then the finial
		final String[] pitched = pitchedRoof();
		final String stair = pitched != null ? pitched[0] : "minecraft:stone_brick_stairs";
		final String solid = pitched != null ? pitched[1] : b.trim();
		for (int u = u0 + 1; u <= u1 - 1; u++) {
			for (int v = v0 + 1; v <= v1 - 1; v++) {
				air.remove(new Cell(u, crown, v));
				put(Role.BUILDING_ROOF, u, crown, v, LocalDir.PLUS_V, b.roof());
			}
		}
		switch (cap) {
			case PYRAMID -> {
				hipped(u0, u1, v0, v1, crown + 1, stair, solid, rings);
				put(Role.BUILDING_ROOF, uC, crown + rings + 2, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
			}
			case SPIRE -> {
				hipped(u0, u1, v0, v1, crown + 1, stair, solid, rings);
				for (int y = crown + rings + 2; y <= crown + rings + 4; y++) {
					put(Role.BUILDING_ROOF, uC, y, vC, LocalDir.PLUS_V, solid);
				}
				put(Role.BUILDING_ROOF, uC, crown + rings + 5, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
			}
			case DOME -> {
				// a round copper dome: the ring without its corners, the middle three by three, then the top and a finial
				for (int u = u0; u <= u1; u++) {
					for (int v = v0; v <= v1; v++) {
						final boolean corner = (u == u0 || u == u1) && (v == v0 || v == v1);
						if (!corner) {
							put(Role.BUILDING_ROOF, u, crown + 1, v, LocalDir.PLUS_V, domeBlock());
						}
						if (u > u0 && u < u1 && v > v0 && v < v1) {
							put(Role.BUILDING_ROOF, u, crown + 2, v, LocalDir.PLUS_V, domeBlock());
						}
					}
				}
				put(Role.BUILDING_ROOF, uC, crown + 3, vC, LocalDir.PLUS_V, domeBlock());
				put(Role.BUILDING_ROOF, uC, crown + 4, vC, LocalDir.PLUS_V, "minecraft:lightning_rod");
			}
			case FLAT -> {
				// a parapet round the flat top
				for (int u = u0; u <= u1; u++) {
					for (int v = v0; v <= v1; v++) {
						if (u == u0 || u == u1 || v == v0 || v == v1) {
							put(Role.BUILDING_ROOF, u, crown + 1, v, outward(u, v, u0, u1, v0, v1), b.cornice());
						}
					}
				}
			}
		}
	}

	/**
	 * The wings of a city or grand station: lower blocks along the platform on both sides of the building block, whole bays
	 * long, two storeys with the same cornice height as a compact building. Their ground floors are waiting rooms on the
	 * paid side, opening onto platform 1 through a doorway in every bay, with benches along the street wall; the upper
	 * storeys are closed. A grand station's wings end in pavilions a storey higher. The block's own end walls separate them.
	 */
	void wings() {
		if (wingBays == 0) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		// a real station's own wings and pavilions (its sheet) have their own roofs and domes: only floors and walls are left here
		final boolean sheet = landmark != null && landmark.anchor() == Landmark.Anchor.SIDE && landmark.usesEnd();
		final boolean pavilions = scale == StationScale.GRAND && wingBays >= 2 && !sheet;
		// a domed pavilion is two bays long where the wing has room, so its dome is the size of the real ones, not a cupola
		final PlanHead.OctCap cap = domedPavilions() && !sheet ? pavilionCap(spec.building()) : null;
		final int pavilionBays = cap != null && wingBays >= 4 ? 2 : 1;
		final String[] tiles = sheet ? null : pitchedRoof();
		for (final int side : new int[]{-1, 1}) {
			final int inner = side < 0 ? site.vStart() : site.vEnd();
			final int end = inner + side * wingBays * StationGeometry.BAY;
			final int pavilionFrom = inner + side * (wingBays - pavilionBays) * StationGeometry.BAY;
			for (int j = 0; j < wingBays; j++) {
				platformDoorway(uP, inner + side * (StationGeometry.BAY * j + 3), b.window());
			}
			final int lo = Math.min(inner + side, end);
			final int hi = Math.max(inner + side, end);
			// the wing's own end wall and, before a pavilion, the cross wall above the wing roof
			final int pavilionRoof = StationGeometry.BUILDING_ROOF_Y + StationGeometry.STOREY;
			for (int u = uS + 1; u <= uP - 1; u++) {
				for (int y = 1; y < (pavilions ? pavilionRoof : StationGeometry.BUILDING_ROOF_Y); y++) {
					final boolean band = y == StationGeometry.UPPER_FLOOR_Y || y == StationGeometry.BUILDING_ROOF_Y;
					final boolean window = !band && windowRow(y)
							&& u >= uS + 2 && u <= uP - 2 && (u - uS) % 2 == 0;
					final String id = y == 1 ? b.plinth() : band || (u - uS) % 3 == 0 ? b.trim() : b.wall();
					put(window ? Role.WINDOW : Role.WALL, u, y, end, side < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V, window ? b.window() : id);
				}
				if (pavilions) {
					for (int y = StationGeometry.BUILDING_ROOF_Y + 1; y < pavilionRoof; y++) {
						put(Role.WALL, u, y, pavilionFrom, side < 0 ? LocalDir.PLUS_V : LocalDir.MINUS_V, b.wall());
					}
				}
			}
			for (int v = lo; v <= hi; v++) {
				final int along = Math.abs(v - inner);
				final boolean pilaster = along % StationGeometry.BAY == 0;
				final boolean inPavilion = pavilions && along >= Math.abs(pavilionFrom - inner);
				final int roofAt = inPavilion ? pavilionRoof : StationGeometry.BUILDING_ROOF_Y;
				final boolean windowBay = !pilaster && Math.abs(along % StationGeometry.BAY - 3) <= 1;
				for (int u = uS; u <= uP; u++) {
					final boolean edge = u == uS || u == uP || v == end;
					put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, edge ? b.plinth() : b.floor());
				}
				for (final int u : new int[]{uS, uP}) {
					final LocalDir front = u == uS ? LocalDir.MINUS_U : LocalDir.PLUS_U;
					for (int y = 1; y < roofAt; y++) {
						final boolean band = y == StationGeometry.UPPER_FLOOR_Y || y == StationGeometry.BUILDING_ROOF_Y;
						if (windowBay && windowRow(y) && !band) {
							put(Role.WINDOW, u, y, v, front, b.window());
						} else {
							put(Role.WALL, u, y, v, front, band || pilaster ? b.trim() : y == 1 ? b.plinth() : b.wall());
						}
					}
				}
				for (int u = uS + 1; u <= uP - 1; u++) {
					slabCell(u, StationGeometry.UPPER_FLOOR_Y, v);
					if (inPavilion) {
						slabCell(u, StationGeometry.BUILDING_ROOF_Y, v);
					}
				}
				if (!inPavilion && tiles == null && !sheet) {
					for (int u = uS; u <= uP; u++) {
						put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y, v, LocalDir.PLUS_V, flatRoof());
					}
					put(Role.BUILDING_ROOF, uS, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.MINUS_U, b.cornice());
					put(Role.BUILDING_ROOF, uP, StationGeometry.BUILDING_ROOF_Y + 1, v, LocalDir.PLUS_U, b.cornice());
				}
				// benches against the street wall, either side of each bay's middle
				if (!pilaster && Math.abs(along % StationGeometry.BAY - 3) == 1) {
					put(Role.BENCH, uS + 1, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, b.bench());
				}
			}
			if (tiles != null) {
				// a look with pitched roofs roofs its wings too (Amsterdam Centraal's slate), below the block's eaves
				final int wingEnd = pavilions ? pavilionFrom - side : end;
				final int a = Math.min(inner + side, wingEnd);
				final int z = Math.max(inner + side, wingEnd);
				roof(StationGeometry.BUILDING_ROOF_Y, uS, uP, a, z, null);
				hipped(uS + 1, uP - 1, a + 1, z - 1, StationGeometry.BUILDING_ROOF_Y + 1, tiles[0], tiles[1],
						Math.max(2, roofY() - StationGeometry.BUILDING_ROOF_Y - 1));
			}
			if (cap != null) {
				// a domed pavilion stands a storey higher still, its parapet level with the wing's ridge (lower, it sank behind
				// the slate); the drum and dome first, so its flat roof and parapet leave them open
				final int p0 = Math.min(pavilionFrom, end);
				final int p1 = Math.max(pavilionFrom, end);
				storey(pavilionRoof, uS, uP, p0, p1, true);
				final int domeRoof = pavilionRoof + StationGeometry.STOREY;
				final int r = Math.max(2, Math.min(5, Math.min(pavilionBays * StationGeometry.BAY / 2 - 1, (uP - uS) / 2 - 2)));
				octTower((uS + uP) / 2, (pavilionFrom + end) / 2, r, domeRoof, cap == PlanHead.OctCap.DOME ? 5 : 4, cap, false);
				roof(domeRoof, uS, uP, p0, p1, null);
			} else if (pavilions) {
				roof(pavilionRoof, uS, uP, Math.min(pavilionFrom, end), Math.max(pavilionFrom, end));
			} else if (tiles == null && !sheet) {
				for (int u = uS; u <= uP; u++) {
					put(Role.BUILDING_ROOF, u, StationGeometry.BUILDING_ROOF_Y + 1, end, side < 0 ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.cornice());
				}
			}
		}
	}
}
