package com.aureliatransit.architecture.station;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * Fitting out (Jah, 10-21: "a better concourse and better entrance"): an upper concourse's boards, shops, kiosks and seats
 * (see {@link ConcourseFit}), and a street entrance that reads as the way in: a portal round the doors, a canopy, the name
 * over it and a pylon outside.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanFitOut extends PlanLandmark {

	PlanFitOut(StationSpec spec) {
		super(spec);
	}

	/** Shop names over the units' fronts, in turn: what each one's back shelves hold, low and high, and its fascia's colour. */
	static final List<String[]> SHOPS = List.of(
			new String[]{"Coffee", "minecraft:smoker", "minecraft:barrel", "minecraft:green_concrete"},
			new String[]{"Bakery", "minecraft:barrel", "minecraft:hay_block", "minecraft:orange_concrete"},
			new String[]{"Books & News", "minecraft:bookshelf", "minecraft:bookshelf", "minecraft:blue_concrete"},
			new String[]{"Flowers", "minecraft:moss_block", "minecraft:potted_flowering_azalea_bush", "minecraft:pink_concrete"});

	/** A kiosk's fascia. */
	static final String KIOSK_FASCIA = "minecraft:yellow_concrete";

	/** A kiosk: its name, what its back shelves hold and its fascia. */
	static final String[] KIOSK_SHOP = {"Kiosk", "minecraft:barrel", "minecraft:barrel", KIOSK_FASCIA};

	static final String SHOP_SIGN = "aurelia_transit_architecture:wall_wayfinding_sign";

	static final String CONCOURSE_BOARD = "aurelia_transit_architecture:concourse_board";

	/**
	 * Fits the upper concourse out (see {@link ConcourseFit}): departure boards, shops and kiosks along its walls and islands
	 * of seats, wherever the walks between its stairs, lifts, gates and ways out leave room. Only big floors (a mezzanine, a
	 * hall over the tracks, a station building's upper floor); a plain footbridge keeps its benches.
	 */
	void concourseFit() {
		if (!spec.furniture() || !site.bridge() || spec.options().underground()) {
			return;
		}
		final int floorY = StationGeometry.UPPER_FLOOR_Y;
		final int y = StationGeometry.UPPER_WALK_Y;
		final Set<ConcourseFit.Spot> floor = new HashSet<>();
		final Set<ConcourseFit.Spot> keep = new HashSet<>();
		for (final PlannedBlock block : cells.values()) {
			if (block.y() == floorY && block.role().flatFloor() && at(block.u(), y, block.v()) == null && at(block.u(), y + 1, block.v()) == null
					&& at(block.u(), y + 2, block.v()) == null) {
				final ConcourseFit.Spot spot = new ConcourseFit.Spot(block.u(), block.v());
				floor.add(spot);
				if (cleared(block.u(), y, block.v()) || taken(block.u(), y + 3, block.v())) {
					keep.add(spot);
				}
			}
		}
		final Set<ConcourseFit.Spot> doors = new HashSet<>();
		final Set<ConcourseFit.Spot> walls = new HashSet<>();
		for (final ConcourseFit.Spot spot : floor) {
			for (final LocalDir dir : LocalDir.values()) {
				final ConcourseFit.Spot next = spot.step(dir);
				if (floor.contains(next)) {
					continue;
				}
				final PlannedBlock walk = at(next.u(), y, next.v());
				final PlannedBlock head = at(next.u(), y + 1, next.v());
				final PlannedBlock deck = at(next.u(), floorY, next.v());
				final PlannedBlock below = at(next.u(), floorY - 1, next.v());
				if (walk != null && (walk.role() == Role.GATE || walk.role() == Role.LIFT || sloped(walk.role()))
						|| walk == null && deck != null && sloped(deck.role())
						|| walk == null && deck == null && below != null && sloped(below.role())) {
					doors.add(spot);
				} else if (walk != null && head != null && wallLike(walk.role()) && wallLike(head.role())) {
					walls.add(next);
				}
			}
		}
		final StylePalette.Building b = palette.building();
		int shop = 0;
		for (final ConcourseFit.Unit unit : ConcourseFit.fit(floor, doors, walls, keep)) {
			switch (unit.kind()) {
				case BOARD -> board(unit, y, b);
				case SEATS -> seats(unit, y, b);
				default -> {
					final boolean kiosk = unit.kind() == ConcourseFit.Kind.KIOSK;
					shopUnit(unit, y, kiosk ? KIOSK_SHOP : SHOPS.get(shop++ % SHOPS.size()), kiosk, b);
				}
			}
		}
	}

	private static boolean sloped(Role role) {
		return role == Role.STAIR || role == Role.ESCALATOR || role == Role.RAMP_LOWER || role == Role.RAMP_UPPER;
	}

	private static boolean wallLike(Role role) {
		return role == Role.WALL || role == Role.WINDOW;
	}

	/** Along a unit from its first cell: 0 to length - 1. */
	private static int along(ConcourseFit.Unit unit, int u, int v) {
		return unit.front() == LocalDir.PLUS_U || unit.front() == LocalDir.MINUS_U ? v - unit.vMin() : u - unit.uMin();
	}

	/** A departure board two high on a plinth against the wall. */
	private void board(ConcourseFit.Unit unit, int y, StylePalette.Building b) {
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				put(Role.WALL, u, y, v, unit.front(), b.plinth());
				put(Role.FIXTURE, u, y + 1, v, unit.front(), CONCOURSE_BOARD);
				put(Role.FIXTURE, u, y + 2, v, unit.front(), CONCOURSE_BOARD);
			}
		}
	}

	/** Benches back to back, a bin and a planter at the end. */
	private void seats(ConcourseFit.Unit unit, int y, StylePalette.Building b) {
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			final boolean end = u == unit.uMax();
			put(end ? Role.BIN : Role.BENCH, u, y, unit.vMin(), LocalDir.MINUS_V, end ? palette.blockId(Role.BIN) : b.bench());
			put(end ? Role.FIXTURE : Role.BENCH, u, y, unit.vMax(), LocalDir.PLUS_V, end ? "minecraft:potted_flowering_azalea_bush" : b.bench());
		}
	}

	/**
	 * A shop (glazed front, a doorway two wide in the middle, shelves along the back, a counter) or, with {@code counter}, a
	 * kiosk or ticket office (a counter under a booth window all along the front); clad end walls, a ceiling with a light, a
	 * fascia in the shop's colour over the front and its name on it. {@code shop} is the name, the back shelves low and high,
	 * and the fascia.
	 */
	@Override
	void shopUnit(ConcourseFit.Unit unit, int y, String[] shop, boolean counter, StylePalette.Building b) {
		final LocalDir front = unit.front();
		final int du = ConcourseFit.du(front);
		final int dv = ConcourseFit.dv(front);
		final int length = unit.length();
		final String fascia = shop[3];
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				final int i = along(unit, u, v);
				final boolean end = i == 0 || i == length - 1;
				final boolean inFront = !unit.contains(u + du, v + dv);
				final boolean atBack = !unit.contains(u - du, v - dv);
				final int top = y + 3;
				if (end) {
					for (int h = y; h <= top; h++) {
						put(Role.WALL, u, h, v, front, h == top && inFront ? fascia : b.trim());
					}
					continue;
				}
				put(Role.WALL, u, top, v, front, inFront ? fascia : i == length / 2 && !atBack ? b.light() : b.interiorWall());
				if (inFront) {
					if (counter) {
						put(Role.WALL, u, y, v, front, b.trim());
						put(Role.FIXTURE, u, y + 1, v, front, "aurelia_transit_architecture:booth_window");
						put(Role.WINDOW, u, y + 2, v, front, b.window());
					} else if (i != length / 2 && i != length / 2 - 1) {
						for (int h = y; h < top; h++) {
							put(Role.WINDOW, u, h, v, front, b.window());
						}
					}
				} else if (atBack) {
					put(Role.FIXTURE, u, y, v, front, shop[1]);
					put(Role.FIXTURE, u, y + 1, v, front, shop[2]);
				} else if (!counter && i == length - 2 && !unit.contains(u + 2 * du, v + 2 * dv)) {
					// the counter, beside the doorway just inside the glass
					put(Role.WALL, u, y, v, front, b.trim());
				}
			}
		}
		// the name on the fascia, over the middle of the front
		for (int u = unit.uMin(); u <= unit.uMax(); u++) {
			for (int v = unit.vMin(); v <= unit.vMax(); v++) {
				if (along(unit, u, v) == length / 2 && !unit.contains(u + du, v + dv)) {
					putText(Role.FIXTURE, u + du, y + 3, v + dv, front, SHOP_SIGN, "NONE:" + shop[0]);
				}
			}
		}
	}

	/** How far an entrance canopy reaches out from the wall: over the ramps and the forecourt, to the edge of the site. */
	static final int CANOPY_DEPTH = StationGeometry.APRON;

	/** The canopy's height: over the doors and MTR's name board ({@link StationPlanner#MTR_NAME_Y}) under it. */
	static final int CANOPY_Y = MTR_NAME_Y + 1;

	static final String CANOPY = "aurelia_transit_architecture:canopy_flat";
	static final String CANOPY_EDGE = "aurelia_transit_architecture:canopy_edge";
	static final String CANOPY_LIGHT = "aurelia_transit_architecture:canopy_light";
	/** The station's name on its facade. */
	static final String LETTERING = "aurelia_transit_architecture:station_lettering";
	/** A wall-hung poster case for notices and adverts. */
	static final String INFO_CASE = "aurelia_transit_architecture:information_case";
	/** Clear glass for a glazed street front at street level: big panes between the trim, not the storey windows' gridded glass. */
	static final String FRONT_GLASS = "aurelia_transit_architecture:clear_float_glass";
	/** Tinted glass above the ground storey, so the front reads as a facade from afar, not an open frame of floors. */
	static final String UPPER_GLASS = "aurelia_transit_architecture:grey_tinted_glass";
	/**
	 * The glazed front's mullions and transoms: dark steel round the glass, like anodised frames. The style's own trim stays on
	 * the rendered walls, where a dark grid round small windows reads as a waffle (11-11).
	 */
	static final String FRAME = "aurelia_transit_architecture:steel_panel_dark";

	/**
	 * Every street entrance, the side building's and the terminus head's: the street front glazed from the plinth to the
	 * roof, a glass bay over the doorway between trim posts, a canopy out over the ramps and the paved forecourt with posts
	 * under its outer corners, rails beside the ramps, bollards along the kerb, the station's name on the wall over the
	 * canopy and again under its front edge, and an entrance pylon beside the ramps. A real station's own front (its landmark
	 * sheet) keeps its walls. The side building's concourse is opened into a hall afterwards.
	 */
	void entrances() {
		if (spec.options().underground()) {
			return;
		}
		if (site.building() && spec.sideBuilding()) {
			entrance(sideFront(), 0, entranceHalf(), landmark != null && landmark.anchor() == Landmark.Anchor.SIDE);
			sideHall();
		}
		if (layout.terminus()) {
			final Front front = headFront();
			entrance(front, front.cu() - headDoor(), headEntranceHalf(), landmark != null && landmark.anchor() == Landmark.Anchor.HEAD);
		}
	}

	/** One entrance in {@code f}'s frame (z = 0 the street wall, out at z < 0), its doorway {@code half} either side of {@code x0}. */
	void entrance(Front f, int x0, int half, boolean sheet) {
		final StylePalette.Building b = palette.building();
		final LocalDir out = f.dir(PlanLandmark.Dir.OUT);
		final int door = StationGeometry.DOOR_HEIGHT;
		final int wide = half + 1 + Math.max(CANOPY_WING, half);
		forecourt(f, x0, wide, out);
		if (!sheet) {
			glassBay(f, x0, half, wide, b, out);
			shopfront(f, x0, half, b, out);
		}
		// the portal: jambs either side of the doorway, glass over the opening up to a lintel under the canopy, and a glass
		// screen either side out to a post at the canopy's corner
		for (int x = x0 - wide; x <= x0 + wide; x++) {
			final int side = Math.abs(x - x0);
			if (sheet && side > half + 1) {
				// a real station's own front keeps its walls beside the doorway
				continue;
			}
			final boolean post = side == half + 1 || side == wide;
			for (int y = side <= half ? door + 1 : 1; y <= CANOPY_Y; y++) {
				final boolean glass = !post && y < CANOPY_Y;
				face(f.u(x, 0), y, f.v(x, 0), out, glass ? Role.WINDOW : Role.WALL, glass ? FRONT_GLASS : FRAME);
			}
		}
		// the canopy over the ramps and the forecourt, its lights down the middle and an edge along the front
		for (int z = -1; z >= -CANOPY_DEPTH; z--) {
			for (int x = x0 - wide; x <= x0 + wide; x++) {
				final String block = z == -CANOPY_DEPTH ? CANOPY_EDGE : Math.floorMod(x - x0, 2) == 0 ? CANOPY_LIGHT : CANOPY;
				put(Role.BUILDING_ROOF, f.u(x, z), CANOPY_Y, f.v(x, z), out, block);
			}
		}
		porch(f, x0, half, wide, b, out);
		// the name, over the canopy
		if (!spec.name().isEmpty()) {
			for (int x = x0 - half; x <= x0 + half; x++) {
				if (taken(f.u(x, 0), CANOPY_Y + 1, f.v(x, 0))) {
					// lettering fixed flat to the wall, not the operator's platform name board on its legs (Jah, 10-22: that
					// belongs inside); a fixture, so the platform sign rules (one row, on a floor) are not about it
					putText(Role.FIXTURE, f.u(x, -1), CANOPY_Y + 1, f.v(x, -1), out, LETTERING, spec.name());
				}
			}
			nameBand(f, x0, wide, out);
			// the pylon right beside the canopy's end, its faces to the street, on a paved footing (Jah, 10-22: it stood a
			// cell further off and turned along the wall)
			final int x = x0 + wide + 1;
			final int z = -StationGeometry.RAMP_LENGTH - 1;
			final int u = f.u(x, z);
			final int v = f.v(x, z);
			if (inside(f, x, z) && !taken(u, StationGeometry.PLATFORM_Y, v) && !taken(u, 1, v) && !taken(u, 2, v)) {
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, out, b.plinth());
				putText(Role.FIXTURE, u, 1, v, out, PlanUnderground.PYLON, "NONE:" + spec.name());
				put(Role.FIXTURE, u, 2, v, out, PlanUnderground.PYLON, "half=upper");
			}
		}
	}

	/**
	 * The name again along the canopy's front, hung under its edge between the corner posts, where it reads from across the
	 * street. Only when the band leaves full headroom over the forecourt.
	 */
	private void nameBand(Front f, int x0, int wide, LocalDir out) {
		final int y = CANOPY_Y - 1;
		final int z = -CANOPY_DEPTH;
		if (y - StationGeometry.WALK_Y < StationGeometry.HEADROOM) {
			return;
		}
		for (int x = x0 - wide + 1; x <= x0 + wide - 1; x++) {
			final int u = f.u(x, z);
			final int v = f.v(x, z);
			final PlannedBlock roof = at(u, CANOPY_Y, v);
			if (inside(f, x, z) && roof != null && roof.role().covers() && !taken(u, y, v)) {
				putText(Role.FIXTURE, u, y, v, out, LETTERING, spec.name());
			}
		}
	}

	/** How far either side of the doorway the forecourt is paved, past the canopy posts. */
	static final int FORECOURT_REACH = 6;
	/** Cells between the forecourt's bollards. */
	static final int BOLLARD_PITCH = 3;
	/** How far the canopy and the glass front reach past each jamb of the doorway, at least: a wide portal gets wider wings. */
	static final int CANOPY_WING = 2;

	/**
	 * The paved forecourt past the ramps (z = -RAMP_LENGTH-1 .. -APRON, at grade): light paving with a dark kerb row at the
	 * street edge and bollards along that edge beside the canopy.
	 */
	private void forecourt(Front f, int x0, int wide, LocalDir out) {
		final int near = -StationGeometry.RAMP_LENGTH - 1;
		final int far = -StationGeometry.APRON;
		final int reach = wide + FORECOURT_REACH;
		for (int x = x0 - reach; x <= x0 + reach; x++) {
			for (int z = near; z >= far; z--) {
				if (!inside(f, x, z) || !taken(f.u(x, 0), 1, f.v(x, 0)) && Math.abs(x - x0) > wide) {
					// off the site, or past the end of the building's front
					continue;
				}
				final int u = f.u(x, z);
				final int v = f.v(x, z);
				if (taken(u, StationGeometry.PLATFORM_Y, v)) {
					continue;
				}
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y - 1, v, out, z == far ? Planning.LOT_MARK : Planning.LOT_PAVING);
			}
			if (Math.abs(x - x0) > wide && Math.floorMod(x - x0, BOLLARD_PITCH) == 0) {
				// bollards along the kerb beside the canopy, the way in under it left open
				stand(f, x, far, out, Planning.LOT_BOLLARD, Role.FIXTURE);
			}
		}
	}

	/** One forecourt fitting on the paving, when the cell and the two over it are free. */
	private void stand(Front f, int x, int z, LocalDir out, String blockId, Role role) {
		if (!inside(f, x, z)) {
			return;
		}
		final int u = f.u(x, z);
		final int v = f.v(x, z);
		final PlannedBlock ground = at(u, StationGeometry.PLATFORM_Y - 1, v);
		if (ground == null || ground.role() != Role.FLOOR || taken(u, StationGeometry.PLATFORM_Y, v) || taken(u, StationGeometry.WALK_Y, v)) {
			return;
		}
		put(role, u, StationGeometry.PLATFORM_Y, v, out, blockId);
	}

	/**
	 * The street front glazed from the plinth to the roof between its pilasters and slab bands, out to both corners of the
	 * building: a station's street side reads as a glass hall, not an office block with punched windows. The plinth course
	 * and the trim stay; the end walls keep their windows, so the stairs and lift behind them stay out of sight.
	 */
	private void shopfront(Front f, int x0, int half, StylePalette.Building b, LocalDir out) {
		for (final int step : new int[]{-1, 1}) {
			for (int x = x0; ; x += step) {
				final int u = f.u(x, 0);
				final int v = f.v(x, 0);
				final PlannedBlock base = at(u, 2, v);
				if (base == null || base.role() != Role.WALL && base.role() != Role.WINDOW) {
					if (Math.abs(x - x0) > half) {
						break;
					}
					// the doorway itself
					continue;
				}
				glaze(u, v, out, b);
			}
		}
	}

	/** Clear at street level, tinted above. */
	private static String glass(int y) {
		return y < StationGeometry.UPPER_FLOOR_Y ? FRONT_GLASS : UPPER_GLASS;
	}

	/** Glass up one column of a wall from the storey over the plinth, its trim turned to frame. */
	private void glaze(int u, int v, LocalDir out, StylePalette.Building b) {
		int y = 2;
		for (PlannedBlock block = at(u, y, v); block != null && (block.role() == Role.WALL || block.role() == Role.WINDOW); block = at(u, ++y, v)) {
			face(u, y, v, out, frame(block, b) ? Role.WALL : Role.WINDOW, frame(block, b) ? FRAME : glass(y));
		}
	}

	/** Whether a wall cell is trim (or already frame) that stays solid when its wall is glazed. */
	private static boolean frame(PlannedBlock block, StylePalette.Building b) {
		return block.blockId().equals(b.trim()) || block.blockId().equals(FRAME);
	}

	/**
	 * The front over the doorway turned to glass from the canopy to the top of the wall, between trim posts a cell outside the
	 * doorway: a station entrance reads from the street as a glazed bay. Trim already on the wall (the floor bands, a cornice)
	 * stays, so the storeys still show through it.
	 */
	private void glassBay(Front f, int x0, int half, int wide, StylePalette.Building b, LocalDir out) {
		for (int x = x0 - wide; x <= x0 + wide; x++) {
			final boolean post = Math.abs(x - x0) == half + 1 || Math.abs(x - x0) == wide;
			final int u = f.u(x, 0);
			final int v = f.v(x, 0);
			for (int y = CANOPY_Y + 1; ; y++) {
				final PlannedBlock block = at(u, y, v);
				if (block == null || block.role() != Role.WALL && block.role() != Role.WINDOW) {
					break;
				}
				if (post || frame(block, b)) {
					face(u, y, v, out, Role.WALL, FRAME);
				} else {
					face(u, y, v, out, Role.WINDOW, glass(y));
				}
			}
		}
	}

	/** Re-faces a wall cell of the street front: only a wall or window, never a doorway or a sign. The transom stays glass. */
	private void face(int u, int y, int v, LocalDir out, Role role, String blockId) {
		final PlannedBlock block = at(u, y, v);
		if (block != null && (block.role() == Role.WALL || block.role() == Role.WINDOW)) {
			cells.put(new Cell(u, y, v), new PlannedBlock(u, y, v, role, blockId, out, "", ""));
		}
	}

	/**
	 * Posts under the canopy's outer corners, a fascia on them, a rail beside each ramp, jambs just inside the door and a
	 * plinth threshold. The opening itself stays air, so a passenger still walks through and a fare gate still sees them.
	 */
	private void porch(Front f, int x0, int half, int wide, StylePalette.Building b, LocalDir out) {
		final int front = -CANOPY_DEPTH;
		canopyPost(f, x0 - wide, front, b, out);
		canopyPost(f, x0 + wide, front, b, out);
		cheek(f, x0, x0 - half - 1, b, out);
		cheek(f, x0, x0 + half + 1, b, out);
		doorReturn(f, x0 - half - 1, b);
		doorReturn(f, x0 + half + 1, b);
		for (int x = x0 - half; x <= x0 + half; x++) {
			retile(f.u(x, 1), f.v(x, 1), b.plinth());
		}
	}

	/** A frame post on a plinth under the canopy, or false when the cell is off the site, a ramp, or already built on. */
	private boolean canopyPost(Front f, int x, int z, StylePalette.Building b, LocalDir out) {
		if (!inside(f, x, z)) {
			return false;
		}
		final int u = f.u(x, z);
		final int v = f.v(x, z);
		final PlannedBlock ground = at(u, StationGeometry.PLATFORM_Y, v);
		if (ground == null) {
			if (taken(u, StationGeometry.PLATFORM_Y, v) || !put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, out, b.plinth())) {
				return false;
			}
		} else if (ground.role() != Role.FLOOR && !ground.role().platformFloor()) {
			return false;
		}
		final PlannedBlock roof = at(u, CANOPY_Y, v);
		if (roof == null || !roof.role().covers()) {
			return false;
		}
		return pierUp(u, v, out, FRAME);
	}

	/** A rail at the side of the ramp, facing the doorway, on a plinth when the apron is bare there. */
	private void cheek(Front f, int x0, int x, StylePalette.Building b, LocalDir out) {
		for (int z = -1; z >= -StationGeometry.RAMP_LENGTH; z--) {
			cheek(f, x0, x, z, b, out);
		}
	}

	private void cheek(Front f, int x0, int x, int z, StylePalette.Building b, LocalDir out) {
		if (!inside(f, x, z)) {
			return;
		}
		final int u = f.u(x, z);
		final int v = f.v(x, z);
		final PlannedBlock ground = at(u, StationGeometry.PLATFORM_Y, v);
		if (ground == null) {
			if (taken(u, StationGeometry.PLATFORM_Y, v) || !put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, out, b.plinth())) {
				return;
			}
		} else if (ground.role() != Role.FLOOR && !ground.role().platformFloor()) {
			return;
		}
		if (taken(u, StationGeometry.WALK_Y, v)) {
			return;
		}
		final LocalDir face = x > x0 ? f.dir(PlanLandmark.Dir.LEFT) : f.dir(PlanLandmark.Dir.RIGHT);
		put(Role.RAILING, u, StationGeometry.WALK_Y, v, face);
	}

	/** A pier just inside the jamb, or a short frame wall when the pier will not fit. The doorway stays open. */
	private void doorReturn(Front f, int x, StylePalette.Building b) {
		final int z = 1;
		if (!inside(f, x, z)) {
			return;
		}
		final int u = f.u(x, z);
		final int v = f.v(x, z);
		if (pierUp(u, v, f.dir(PlanLandmark.Dir.IN), FRAME)) {
			return;
		}
		final PlannedBlock ground = at(u, StationGeometry.PLATFORM_Y, v);
		if (ground == null || ground.role() != Role.FLOOR && !ground.role().platformFloor()) {
			return;
		}
		for (int y = 1; y <= StationGeometry.DOOR_HEIGHT; y++) {
			if (taken(u, y, v)) {
				return;
			}
		}
		final LocalDir out = f.dir(PlanLandmark.Dir.OUT);
		for (int y = 1; y <= StationGeometry.DOOR_HEIGHT; y++) {
			put(Role.WALL, u, y, v, out, FRAME);
		}
	}

	/** True when the frame cell is inside the station's footprint. */
	private boolean inside(Front f, int x, int z) {
		final int u = f.u(x, z);
		final int v = f.v(x, z);
		return u >= 0 && u < layout.width() && v >= layout.vMin() && v <= layout.vMax();
	}

	/**
	 * The side building's concourse: the upper floor opened over the way in from the door, with a glass rail around the
	 * hole, and benches along the glass upstairs.
	 */
	void sideHall() {
		if (!site.building()) {
			return;
		}
		final StylePalette.Building b = palette.building();
		final int uS = streetWall();
		final int uP = site.platformWall(layout);
		final int vEntr = site.entranceV();
		final int half = entranceHalf();
		int gateU = uS + 4;
		for (final PlannedBlock block : cells.values()) {
			if (block.role() == Role.GATE && block.y() == StationGeometry.WALK_Y && block.u() > uS && block.u() < uP) {
				gateU = block.u();
				break;
			}
		}
		openVoid(uS + 1, Math.min(uS + 4, gateU - 2), vEntr - half, vEntr + half);
		windowSeats(uS, site.vStart(), site.vEnd(), vEntr, half, b);
		stairPosters(uP - 3);
	}

	/** A poster case for every {@link #POSTER_PITCH} steps of the stair's solid side. */
	static final int POSTER_PITCH = 3;

	/**
	 * Poster cases at eye height on the inner stair's solid side ({@code uWall}, facing the hall), where it stands a storey
	 * high beside the gates: a station hangs its notices and adverts on a wall like that, it is not left a blank partition.
	 */
	private void stairPosters(int uWall) {
		for (int step = POSTER_PITCH; step <= StationGeometry.STAIR_STEPS; step += POSTER_PITCH) {
			final int v = site.innerStairV(step);
			final int y = StationGeometry.WALK_Y + 1;
			final PlannedBlock wall = at(uWall, y, v);
			final PlannedBlock floor = at(uWall - 1, StationGeometry.WALK_Y - 1, v);
			if (wall == null || wall.role() != Role.WALL || floor == null || !floor.role().flatFloor()
					|| taken(uWall - 1, StationGeometry.WALK_Y, v) || taken(uWall - 1, y, v)) {
				continue;
			}
			put(Role.FIXTURE, uWall - 1, y, v, LocalDir.MINUS_U, INFO_CASE);
		}
	}

	/**
	 * Benches along the glass of the upper floor, facing out over the street, in pairs with a gap, clear of the opened slab
	 * over the door: the glazed front shows a used floor, not an empty one.
	 */
	private void windowSeats(int uS, int vS, int vE, int vEntr, int half, StylePalette.Building b) {
		final int u = uS + 1;
		final int y = StationGeometry.UPPER_WALK_Y;
		for (int v = vS + 2; v <= vE - 2; v++) {
			if (Math.abs(v - vEntr) <= half + 1 || Math.floorMod(v - vS, 3) == 1) {
				continue;
			}
			final PlannedBlock slab = at(u, StationGeometry.UPPER_FLOOR_Y, v);
			if (slab == null || !slab.role().flatFloor() || taken(u, y, v) || taken(u, y + 1, v) || taken(u + 1, y, v)) {
				continue;
			}
			put(Role.BENCH, u, y, v, LocalDir.MINUS_U, b.bench());
		}
	}

	/** Takes the upper slab off the street end of the aisle, rails the floor that remains, and lights one cell of the ceiling. */
	private void openVoid(int u0, int u1, int v0, int v1) {
		if (u0 > u1 || v0 > v1) {
			return;
		}
		final Set<Long> holes = new HashSet<>();
		int lightU = Integer.MAX_VALUE;
		int lightV = 0;
		int lightD = Integer.MAX_VALUE;
		final int mid = (v0 + v1) / 2;
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				if (!openSlab(u, v)) {
					continue;
				}
				holes.add(((long) u << 32) ^ (v & 0xffffffffL));
				final int d = Math.abs(v - mid);
				if (u < lightU || u == lightU && d < lightD) {
					lightU = u;
					lightV = v;
					lightD = d;
				}
			}
		}
		for (final long hole : holes) {
			final int u = (int) (hole >> 32);
			final int v = (int) hole;
			railBeside(u + 1, v, LocalDir.MINUS_U);
			railBeside(u - 1, v, LocalDir.PLUS_U);
			railBeside(u, v + 1, LocalDir.MINUS_V);
			railBeside(u, v - 1, LocalDir.PLUS_V);
		}
		if (lightU != Integer.MAX_VALUE) {
			skylight(lightU, lightV);
		}
	}

	/** Removes one upper-floor cell when the shaft above it is a simple ceiling and nothing is standing on it. */
	private boolean openSlab(int u, int v) {
		final PlannedBlock slab = at(u, StationGeometry.UPPER_FLOOR_Y, v);
		if (slab == null || slab.role() != Role.FLOOR && slab.role() != Role.LIGHT_PANEL) {
			return false;
		}
		if (taken(u, StationGeometry.UPPER_WALK_Y, v) || taken(u, StationGeometry.UPPER_WALK_Y + 1, v)) {
			return false;
		}
		for (int y = StationGeometry.WALK_Y; y < StationGeometry.UPPER_FLOOR_Y; y++) {
			final PlannedBlock below = at(u, y, v);
			if (below != null && (below.role() == Role.COLUMN || below.role() == Role.COLUMN_CAP || below.role() == Role.WALL)) {
				return false;
			}
		}
		if (coverClear(u, StationGeometry.UPPER_FLOOR_Y, v) < 0) {
			return false;
		}
		cells.remove(new Cell(u, StationGeometry.UPPER_FLOOR_Y, v));
		keepClear(u, StationGeometry.UPPER_WALK_Y, v);
		keepClear(u, StationGeometry.UPPER_WALK_Y + 1, v);
		return true;
	}

	/** Glass on the remaining upper floor, facing the hole, so the opened slab is not a drop. */
	private void railBeside(int u, int v, LocalDir towardHole) {
		final PlannedBlock slab = at(u, StationGeometry.UPPER_FLOOR_Y, v);
		if (slab == null || !slab.role().flatFloor() || taken(u, StationGeometry.UPPER_WALK_Y, v)) {
			return;
		}
		put(Role.BARRIER, u, StationGeometry.UPPER_WALK_Y, v, towardHole);
	}

	/** One cell of the ceiling over the void becomes a light, when that cell is a plain floor or roof. */
	private void skylight(int u, int v) {
		final int cover = coverClear(u, StationGeometry.UPPER_FLOOR_Y, v);
		if (cover < 0) {
			return;
		}
		final PlannedBlock block = at(u, cover, v);
		if (block == null || block.role() != Role.FLOOR && block.role() != Role.BUILDING_ROOF) {
			return;
		}
		if (block.blockId().contains("light") || block.blockId().contains("stair")) {
			return;
		}
		cells.put(new Cell(u, cover, v), new PlannedBlock(u, cover, v, Role.LIGHT_PANEL, palette.building().light(),
				block.front(), block.text(), block.state()));
	}
}
