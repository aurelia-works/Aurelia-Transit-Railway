package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aureliatransit.architecture.station.StationPlanner.*;

/**
 * An airport (words: "airport", "2 runways", "6 gates"): runways with markings and lights, a parallel taxiway with links to the
 * runway, an apron with aircraft stands, and a terminal behind the apron with a boarding bridge to every stand, on the same
 * pipeline as a station. The geometry is {@link AirportGeometry}; this layer paints it. Nothing here runs for a railway station.
 * <p>
 * Pavement is {@link Role#PLATFORM_SURFACE} (a flat walkable floor), lights are {@link Role#LIGHT_PANEL}, the terminal, bridges and
 * support buildings use the building roles; the block ids are chosen here, not by the style palette. The airfield itself is flush
 * with the ground (y = 0); the terminal, the bridges, the control tower, the fire station, the hangar, the fuel tanks and the
 * floodlight masts stand up.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanAirport extends PlanFitOut {

	static final String RUNWAY_SURFACE = "minecraft:black_concrete";
	static final String BLAST_SURFACE = "minecraft:polished_andesite";
	static final String TAXI_SURFACE = "minecraft:gray_concrete";
	static final String APRON_SURFACE = "minecraft:light_gray_concrete";
	/**
	 * A joint between two apron slabs: a shade darker than the slabs, so the apron reads as concrete panels. Gray concrete
	 * was too dark: a block-wide joint in it read as a road grid from the ground.
	 */
	static final String APRON_JOINT = "minecraft:tuff";
	/** The landside forecourt's footways and kerbs. */
	static final String SIDEWALK = "minecraft:smooth_stone";
	static final String KERB = "minecraft:stone_brick_slab";
	/** The car park's row-end planters: a soil bed under oak hedges, with one oak tree in each. */
	static final String PLANTER_SOIL = "minecraft:dirt";
	static final String HEDGE = "minecraft:oak_leaves";
	static final String TRUNK = "minecraft:oak_log";
	/** The car park across u: a bay row 5 deep, then a 6-wide aisle, starting past the outer footway and its kerb. */
	static final int CAR_BAY = 5;
	static final int CAR_AISLE = 6;
	static final int CAR_FIRST_BAY = 3;
	/**
	 * The apron's concrete as an MTR platform block, beside each stand's platform rail so the plane's doors open.
	 * Ported from Aurelia Transit Architecture pull request 7 ({@code apron_platform} and {@code gatePlatforms}), which
	 * is not in this bundled copy.
	 */
	static final String APRON_PLATFORM = "aurelia_transit_architecture:apron_platform";
	static final String ROAD_SURFACE = "minecraft:gray_concrete";
	static final String PAINT_WHITE = "minecraft:white_concrete";
	static final String PAINT_YELLOW = "minecraft:yellow_concrete";
	/** An equipment restraint area's boundary: red and white hatched lines. */
	static final String PAINT_RED = "minecraft:red_concrete";
	/** Runway edge lights: white, flush in the pavement. */
	static final String EDGE_LIGHT = "aurelia_transit_architecture:runway_edge_light";
	/** Runway threshold lights: green. */
	static final String THRESHOLD_LIGHT = "aurelia_transit_architecture:runway_threshold_light";
	/** Runway end lights: red, one row outside each threshold, glowing the other way down the approach. */
	static final String END_LIGHT = "minecraft:redstone_lamp";
	/** Taxiway edge lights: blue. */
	static final String TAXI_LIGHT = "aurelia_transit_architecture:taxiway_edge_light";
	/** The perimeter service road's darker asphalt, and the mown grass between the runway and taxiway. */
	static final String SERVICE_SURFACE = "minecraft:andesite";
	static final String GRASS_SURFACE = "minecraft:grass_block";
	/** The perimeter fence: iron bars between stone brick posts. */
	static final String FENCE_BAR = "minecraft:iron_bars";
	static final String FENCE_POST = "minecraft:stone_brick_wall";
	/** The control tower: a white shaft, a light grey cab floor and roof, and glass walls round the cab. */
	static final String TOWER_SHAFT = "minecraft:white_concrete";
	static final String TOWER_FLOOR = "minecraft:light_gray_concrete";
	static final String TOWER_GLASS = "minecraft:glass";
	/** The tower shaft's window band and recess, dark as at Southend. */
	static final String TOWER_DARK_GLASS = "minecraft:gray_stained_glass";
	/** The railing round the tower's balcony. */
	static final String TOWER_RAILING = "minecraft:iron_bars";
	/** The tower's beacon on the cab roof. */
	static final String TOWER_BEACON = "minecraft:sea_lantern";
	/** A rooftop plant box's grille and vent: plain iron, no block entity. */
	static final String PLANT_GRILLE = "minecraft:iron_trapdoor";
	static final String PLANT_VENT = "minecraft:iron_bars";
	static final String MAST_LAMP = "aurelia_transit_architecture:apron_floodlight";
	static final String CHECK_IN_DESK = "aurelia_transit_architecture:check_in_desk";
	static final String DEPARTURES_BOARD = "aurelia_transit_architecture:departures_board";
	static final String JET_BRIDGE = "aurelia_transit_architecture:jet_bridge";
	/** The metal band between a jet bridge's telescoping sections, and its drive column. */
	static final String BRIDGE_BAND = "minecraft:light_gray_concrete";
	static final String BRIDGE_COLUMN = "minecraft:iron_block";
	static final String BRIDGE_WHEEL = "minecraft:black_concrete";
	/** The landside canopy's diagonal brace: a slim 45-degree steel bar that rises the way it faces. */
	static final String VIADUCT_BRACE = "aurelia_transit_architecture:viaduct_brace";
	/** Rows between the airside wall's mullions, each carrying a roof strut. */
	static final int AIRSIDE_BAY = 6;
	static final String BAGGAGE_BELT = "aurelia_transit_architecture:baggage_belt";
	static final String WINDSOCK = "aurelia_transit_architecture:windsock";
	static final String TERMINAL_FLOOR = "aurelia_transit_architecture:terminal_terrazzo";
	static final String SECURITY_ARCH = "aurelia_transit_architecture:security_arch";
	static final String XRAY_SCANNER = "aurelia_transit_architecture:xray_scanner";
	static final String SECURITY_DESK = "aurelia_transit_architecture:security_desk";
	static final String QUEUE_POST = "aurelia_transit_architecture:queue_post";
	static final String KIOSK = "aurelia_transit_architecture:self_check_in_kiosk";
	static final String BAG_DROP = "aurelia_transit_architecture:bag_drop";
	static final String CAROUSEL = "aurelia_transit_architecture:baggage_carousel";
	static final String CAROUSEL_CORNER = "aurelia_transit_architecture:baggage_carousel_corner";
	static final String GATE_DESK = "aurelia_transit_architecture:gate_desk";
	static final String TROLLEY = "aurelia_transit_architecture:luggage_trolley";
	static final String AIRPORT_SEAT = "aurelia_transit_architecture:airport_seat";
	/** Rows a security lane takes along the line: the arch, the scanner, then glass up to the next lane. */
	static final int LANE_PITCH = 6;
	/** A baggage carousel's length in rows, and the rows from one to the next. */
	static final int CAROUSEL_LENGTH = 10;
	static final int CAROUSEL_PITCH = 15;
	/** The airport's geometry; null for a railway station. */
	final AirportGeometry air;
	/** The railway station under the terminal ({@link AirportStation}); null without one. */
	final AirportStation rail;
	/** The station's terrain to dig, track envelopes and supports, moved under the terminal by {@link #railStation}. */
	final List<Box> railHeadroom = new ArrayList<>();
	final List<Box> railEnvelopes = new ArrayList<>();
	final List<Box> railSupports = new ArrayList<>();
	/** The airport's own support cells over the dug box: not asked for, the box is there. */
	final Set<Cell> railHoles = new HashSet<>();

	PlanAirport(StationSpec spec) {
		super(spec);
		this.air = spec.isAirport() ? AirportGeometry.of(spec.airport()) : null;
		this.rail = spec.isAirport() ? AirportStation.of(spec) : null;
	}

	/** The terminal's roof, one above its walls. */
	int terminalRoofY() {
		return air.dims().wallTop() + 1;
	}

	/** The landside canopy's deck: level with the forecourt canopy, and below the name band on a compact terminal. */
	int landsideCanopyY() {
		return Math.min(StationGeometry.WALK_Y + 6, air.dims().wallTop() - 1);
	}

	/** The airport's rules, in order. Structures first: a cell keeps the first block planned in it, so paint comes before pavement. */
	void airport() {
		if (air == null) {
			return;
		}
		if (rail != null) {
			railStation();
		}
		if (spec.airport().terminal()) {
			terminal();
			boardingBridges();
		}
		if (spec.airport().controlTower()) {
			controlTower();
		}
		windsock();
		masts();
		if (spec.airport().supportBuildings()) {
			supportBuildings();
		}
		if (spec.airport().perimeter()) {
			perimeter();
		}
		if (spec.airport().apronMarkings()) {
			standMarks();
			equipmentLines();
		}
		if (spec.airport().apronLanes()) {
			apronLanes();
			apronLaneDetails();
		}
		gatePlatforms();
		for (int i = 0; i < spec.airport().runways(); i++) {
			runwayMarks(i);
		}
		taxiMarks();
		if (spec.airport().turnPads()) {
			turnPadMarks();
		}
		if (spec.airport().taxiwaySigns()) {
			taxiwaySigns();
		}
		if (spec.airport().approachLights()) {
			approachLights();
		}
		if (spec.airport().terminal() && spec.airport().forecourt()) {
			// the access road first: its mouth takes the forecourt's outer footway and kerb
			airportAccessRoad();
			airportForecourt();
			if (spec.airport().landTransit()) {
				landTransit();
			}
			airportCarPark();
		}
		pavement();
	}

	// ---- cells --------------------------------------------------------------------------------------------------------

	private void flat(Role role, int u, int v, String id) {
		put(role, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, id);
	}

	private void paint(int u, int v, String id) {
		flat(Role.PLATFORM_SURFACE, u, v, id);
	}

	private void light(int u, int v, String id) {
		flat(Role.LIGHT_PANEL, u, v, id);
	}

	private void light(int u, int v, String id, String state) {
		put(Role.LIGHT_PANEL, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, id, state);
	}

	private void rect(int u0, int u1, int v0, int v1, String id) {
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				paint(u, v, id);
			}
		}
	}

	// ---- runways ------------------------------------------------------------------------------------------------------

	/**
	 * One runway's paint and lights: edge lines and flush edge lights, green threshold lights, threshold stripes, the numbers, and a
	 * dashed centreline. The runway lies along v; its low end carries the spec's number ("09" unless the words gave one) and its
	 * high end that plus 18 ("27"); with two, the first is "09L / 27R" and the second "09R / 27L" (left and right as a pilot looking along +v sees them).
	 */
	void runwayMarks(int i) {
		final AirportGeometry.Dims d = air.dims();
		final int u0 = air.runwayU0().get(i);
		final int u1 = air.runwayU1(i);
		final int c = air.runwayCentre(i);
		final int v0 = air.runwayV0();
		final int v1 = air.runwayV1();
		for (int v = v0; v <= v1; v++) {
			if (v % d.edgeLightEvery() == 0) {
				light(u0, v, EDGE_LIGHT);
				light(u1, v, EDGE_LIGHT);
			}
		}
		for (int u = u0; u <= u1; u += 2) {
			light(u, v0, THRESHOLD_LIGHT);
			light(u, v1, THRESHOLD_LIGHT);
		}
		if (!spec.airport().runwayMarkings()) {
			return;
		}
		for (int v = v0 + 2; v <= v1 - 2; v++) {
			paint(u0 + 1, v, PAINT_WHITE);
			paint(u1 - 1, v, PAINT_WHITE);
		}
		if (air.dims() == AirportGeometry.REAL) {
			thresholdStripes(u0, c, v0, v1);
		} else {
			for (int u = u0 + 3; u <= u1 - 3; u++) {
				// piano keys either side of the centre, leaving the centreline's own column bare
				if ((u - u0) % 2 == 1 && Math.abs(u - c) >= 1) {
					for (int k = 0; k < d.thresholdLength(); k++) {
						paint(u, v0 + 2 + k, PAINT_WHITE);
						paint(u, v1 - 2 - k, PAINT_WHITE);
					}
				}
			}
		}
		final int scale = numberScale();
		final boolean pair = spec.airport().runways() > 1;
		final String low = pair ? (i == 0 ? "L" : "R") : "";
		final String high = pair ? (i == 0 ? "R" : "L") : "";
		paintText(number(spec.airport().runwayNumber(false), low), scale, c, v0 + d.numberOffset(), 0, 1, PAINT_WHITE);
		paintText(number(spec.airport().runwayNumber(true), high), scale, c, v1 - d.numberOffset(), 0, -1, PAINT_WHITE);
		touchdownMarks(i);
		final int clear = d.numberOffset() + 3 * AirportGlyphs.height(scale) + 6;
		final int start = v0 + clear;
		final int end = v1 - clear;
		for (int v = start; v <= end; v++) {
			if ((v - start) % (d.dash() + d.dashGap()) < d.dash()) {
				paint(c, v, PAINT_WHITE);
			}
		}
	}

	/**
	 * Approach lighting at both ends and the visual glidepath beside the touchdown zone: a line of white lights up the extended centreline
	 * with five crossbars, a red end-light row one cell outside each threshold, and four PAPI boxes on the west side. The bars sit on
	 * the narrow grass strips the geometry keeps clear for them; the red end lights sit on the blast pads, set flush.
	 */
	void approachLights() {
		final int c = air.runwayCentre(0);
		final int blink = air.dims().blast();
		for (final boolean high : new boolean[]{false, true}) {
			final int from = high ? air.runwayV1() + blink + 2 : air.runwayV0() - blink - 2;
			final int step = high ? 1 : -1;
			for (int k = 0; k <= 20; k++) {
				final int v = from + step * k;
				light(c, v, EDGE_LIGHT);
				if (k % 4 == 0) {
					for (int du = -2; du <= 2; du++) {
						light(c + du, v, EDGE_LIGHT);
					}
				}
			}
			// the red runway end lights, one row beyond each green threshold line, every two cells across
			final int end = high ? air.runwayV1() + 1 : air.runwayV0() - 1;
			for (int u = air.runwayU0().get(0) + 1; u < air.runwayU1(0); u += 2) {
				light(u, end, END_LIGHT, "lit=true");
			}
		}
		// four PAPI boxes beside the touchdown zone, one cell outside the runway shoulder
		final int u = air.runwayU0().get(0) - air.dims().shoulder() - 1;
		int v0 = air.runwayV0() + Math.max(44, spec.airport().runwayLength() / 10);
		for (int attempt = 0; attempt < 4; attempt++) {
			boolean clear = true;
			for (int k = 0; k < 4; k++) {
				clear &= !onTaxiway(u, v0 + 4 * k);
			}
			if (clear) {
				break;
			}
			v0 += 8;
		}
		for (int k = 0; k < 4; k++) {
			final int v = v0 + 4 * k;
			put(Role.FIXTURE, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_U, SIDEWALK);
			put(Role.FIXTURE, u, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, "minecraft:white_concrete");
			put(Role.FIXTURE, u, StationGeometry.WALK_Y + 1, v, LocalDir.PLUS_U, END_LIGHT, "lit=true");
		}
	}

	/** The scale of a runway's painted numbers: as large as the runway is wide enough for. */
	int numberScale() {
		final int w = spec.airport().runwayWidth();
		return w >= 40 ? 3 : w >= 20 ? 2 : 1;
	}

	/** Threshold stripes on a real-size runway: how many ICAO gives for its width in metres, 4 to 16. */
	static int thresholdStripes(int runwayWidth) {
		final double metres = runwayWidth / 1.5;
		return metres >= 60 ? 16 : metres >= 45 ? 12 : metres >= 30 ? 8 : metres >= 23 ? 6 : 4;
	}

	/**
	 * The columns of one side's threshold stripes, from the edge in, as {@code {first, last}} pairs: half of {@link #thresholdStripes},
	 * each 3 blocks wide (1.8 m at 1.5 blocks a metre, rounded up) where they fit, else 2, spread evenly from 3 columns in from the edge to
	 * 3 columns short of the centreline. The other side mirrors them about the centre.
	 */
	static List<int[]> stripeColumns(int u0, int c, int runwayWidth) {
		final int k = thresholdStripes(runwayWidth) / 2;
		final int from = u0 + 3;
		final int span = (c - 3) - from + 1;
		final int width = k * 3 + (k - 1) <= span ? 3 : 2;
		final int gap = k > 1 ? Math.max(1, (span - k * width) / (k - 1)) : 0;
		final List<int[]> out = new ArrayList<>();
		for (int i = 0; i < k; i++) {
			final int first = from + i * (width + gap);
			if (first + width - 1 > c - 2) {
				break;
			}
			out.add(new int[]{first, first + width - 1});
		}
		return out;
	}

	private void thresholdStripes(int u0, int c, int v0, int v1) {
		final int length = air.dims().thresholdLength();
		final int u1 = air.runwayU1(0) - air.runwayU0().get(0) + u0;
		for (final int[] stripe : stripeColumns(u0, c, spec.airport().runwayWidth())) {
			for (int u = stripe[0]; u <= stripe[1]; u++) {
				// the mirror about the runway's middle; an even width has its middle between c and c + 1
				final int mirror = u0 + u1 - u;
				for (int k = 0; k < length; k++) {
					paint(u, v0 + 2 + k, PAINT_WHITE);
					paint(u, v1 - 2 - k, PAINT_WHITE);
					paint(mirror, v0 + 2 + k, PAINT_WHITE);
					paint(mirror, v1 - 2 - k, PAINT_WHITE);
				}
			}
		}
	}

	/** One pair of touchdown or aiming bars: how far from the threshold, how many stripes each side, how long and how wide each stripe is. */
	record Bars(int from, int stripes, int length, int width) {
	}

	/**
	 * The aiming point and touchdown-zone bars of a real-size runway, from each threshold, after ICAO Annex 14 at 1.5 blocks a
	 * metre. The landing distance sets the aiming point (150 m short of 800 m, 250 m to 1,200 m, 300 m to 2,400 m, 400 m beyond)
	 * and the pairs of touchdown bars (1, 2, 3, 4 or 6 pairs below 900, 1,200, 1,500, 2,400 m and beyond), every 150 m from 150 m,
	 * a pair within 50 m of the aiming point left out; the bars go 3, 2, 2, 1, 1, 1 stripes, each 22.5 m by 3 m. The aiming point is
	 * one stripe 45 m by 6 m (30 m by 4 m on a runway under 1,200 m). Each runway gets its pairs as far as half its length.
	 */
	static List<Bars> touchdownBars(int runwayLength) {
		if (runwayLength < 900) {
			return shortRunwayBars(runwayLength);
		}
		final double metres = runwayLength / 1.5;
		final int aim = metres < 800 ? 150 : metres < 1200 ? 250 : metres < 2400 ? 300 : 400;
		final int pairs = metres < 900 ? 1 : metres < 1200 ? 2 : metres < 1500 ? 3 : metres < 2400 ? 4 : 6;
		final int[] stripes = {3, 2, 2, 1, 1, 1};
		final List<Bars> out = new ArrayList<>();
		out.add(metres < 1200 ? new Bars(blocks(aim), 1, blocks(30), blocks(4)) : new Bars(blocks(aim), 1, blocks(45), blocks(6)));
		int at = 150;
		while (out.size() - 1 < pairs) {
			if (Math.abs(at - aim) > 50) {
				out.add(new Bars(blocks(at), stripes[out.size() - 1], blocks(22.5), 3));
			}
			at += 150;
		}
		return out;
	}

	/**
	 * Marks for a game-scale runway under 900 blocks (600 m), where ICAO's distances leave no room: the aiming point at a tenth of the
	 * runway and one pair of touchdown bars at a quarter, the bars a twelfth of the runway long (at most 34 blocks, the real 22.5 m
	 * bar) and the aiming point in the short runway's 30 m by 4 m form.
	 */
	private static List<Bars> shortRunwayBars(int runwayLength) {
		final int length = Math.max(8, Math.min(34, runwayLength / 12));
		final int aim = Math.max(6, runwayLength / 10);
		final List<Bars> out = new ArrayList<>();
		out.add(new Bars(aim, 1, length + 11, 6));
		out.add(new Bars(Math.max(aim + length + 12, runwayLength / 4), 3, length, 3));
		return out;
	}

	private static int blocks(double metres) {
		return (int) Math.round(metres * 1.5);
	}

	/**
	 * Columns out from the centre to the inner edge of every bar: ICAO's 18 m (27 blocks) between the aiming point's inner edges on a
	 * runway 60 blocks (40 m) or wider; a narrower one brings them in so the outer stripes stay inside the edge line (19 blocks on a 30 m one).
	 */
	static int barsInner(int runwayWidth) {
		return Math.min(13, Math.max(3, runwayWidth / 2 - 13));
	}

	private void touchdownMarks(int i) {
		final int c = air.runwayCentre(i);
		final int u0 = air.runwayU0().get(i);
		final int u1 = air.runwayU1(i);
		final int half = spec.airport().runwayLength() / 2;
		final int inner = barsInner(spec.airport().runwayWidth());
		for (final Bars bars : touchdownBars(spec.airport().runwayLength())) {
			if (bars.from() + bars.length() > half - 20) {
				continue;
			}
			for (int s = 0; s < bars.stripes(); s++) {
				// stripes 3 wide with 1 bare between them, out from the inner edge (ICAO's 1.5 m gap would push the third of a
				// 30 m runway's into the edge line); one that would still reach it is left out. The right side mirrors the left, so
				// the left's test holds for both (testing c + 1 + far dropped the third stripe on an odd width, review 10-27)
				final int near = inner + s * (bars.width() + 1);
				final int far = near + bars.width() - 1;
				if (c - far < u0 + 3) {
					break;
				}
				for (int k = 0; k < bars.length(); k++) {
					for (int w = near; w <= far; w++) {
						// left of the centre from c, right of it from the column past the middle, so both sides match on an even width
						final int left = c - w;
						final int right = u0 + u1 - left;
						for (final int u : new int[]{left, right}) {
							paint(u, air.runwayV0() + bars.from() + k, PAINT_WHITE);
							paint(u, air.runwayV1() - bars.from() - k, PAINT_WHITE);
						}
					}
				}
			}
		}
	}

	private static List<String> number(String number, String letter) {
		return letter.isEmpty() ? List.of(number) : List.of(number, letter);
	}

	/**
	 * Paints lines of text on the pavement, upright for someone looking along ({@code upU}, {@code upV}): the first line farthest
	 * from the viewer, centred on column {@code ou} and row {@code ov}, which is the text's near edge. A viewer looking along +v has
	 * +u on the right; the right hand is a quarter turn clockwise from the viewing direction.
	 */
	private void paintText(List<String> lines, int scale, int ou, int ov, int upU, int upV, String id) {
		for (final int[] cell : textCells(lines, scale, ou, ov, upU, upV)) {
			paint(cell[0], cell[1], id);
		}
	}

	/** The cells {@code {u, v}} that {@link #paintText} paints for these lines, in the same frame. Static so the tests can check a glyph's orientation. */
	static List<int[]> textCells(List<String> lines, int scale, int ou, int ov, int upU, int upV) {
		final int rightU = upV != 0 ? upV : 0;
		final int rightV = upU != 0 ? -upU : 0;
		final int h = AirportGlyphs.height(scale);
		final int gap = 2 * scale;
		final int total = lines.size() * h + (lines.size() - 1) * gap;
		final List<int[]> out = new ArrayList<>();
		int row0 = 0;
		for (final String line : lines) {
			final int w = AirportGlyphs.width(line, scale);
			for (final int[] px : AirportGlyphs.pixels(line, scale)) {
				final int d = total - 1 - (row0 + px[1]);
				final int x = px[0] - w / 2;
				out.add(new int[]{ou + d * upU + x * rightU, ov + d * upV + x * rightV});
			}
			row0 += h + gap;
		}
		return out;
	}

	// ---- taxiways -----------------------------------------------------------------------------------------------------

	/** Yellow centrelines and edge lines, blue edge lights and green centreline lights, and a hold line before every runway. */
	void taxiMarks() {
		final int centre = air.taxiCentre();
		final int greenEvery = Math.max(1, air.dims().taxiLightEvery() / 2);
		for (int v = air.taxiV0(); v <= air.taxiV1(); v++) {
			// a green centreline light every 30 m; the yellow paint fills the cells between them
			if (spec.airport().taxiwayLights() && v % greenEvery == 0) {
				light(centre, v, THRESHOLD_LIGHT);
			}
			paint(centre, v, PAINT_YELLOW);
			if (spec.airport().taxiwayLights() && v % air.dims().taxiLightEvery() == 0) {
				light(air.taxiU0(), v, TAXI_LIGHT);
				light(air.taxiU1(), v, TAXI_LIGHT);
			}
			// yellow edge lines one cell in from the pavement's edge; they break where a link joins
			if (!onLink(v)) {
				paint(air.taxiU0() + 1, v, PAINT_YELLOW);
				paint(air.taxiU1() - 1, v, PAINT_YELLOW);
			}
		}
		final int gapFrom = air.taxiU1() + 1;
		final int gapTo = air.runwayU0().get(0) - 1;
		for (final int[] link : air.links()) {
			final int middle = (link[0] + link[1]) / 2;
			for (int u = gapFrom; u <= gapTo; u++) {
				// a link is an ordinary taxiway: yellow centre and edges, green lead-on lights and blue edge lights. The green
				// lights stop a cell short of the runway, so none touches its pavement (they share the threshold lights' block)
				if (spec.airport().taxiwayLights() && u % greenEvery == 0 && u < gapTo) {
					light(u, middle, THRESHOLD_LIGHT);
				}
				paint(u, middle, PAINT_YELLOW);
				if (spec.airport().taxiwayLights() && u % air.dims().taxiLightEvery() == 0) {
					light(u, link[0], TAXI_LIGHT);
					light(u, link[1], TAXI_LIGHT);
				}
				paint(u, link[0], PAINT_YELLOW);
				paint(u, link[1], PAINT_YELLOW);
			}
			// the runway holding position: two solid and two dashed yellow lines, solid side to the runway, 14 cells outside the strip
			holdingPositionV(air.holdU(), link[0], link[1]);
		}
		// rapid exits: the same centreline, edge lines, edge lights and hold-short bar as the links, along each exit's diagonal strip
		final int half = air.exitHalf();
		for (final int start : air.rapidExits()) {
			for (int u = gapFrom; u <= gapTo; u++) {
				final int c = air.exitCentre(start, u);
				paint(u, c, PAINT_YELLOW);
				paint(u, c - half, PAINT_YELLOW);
				paint(u, c + half, PAINT_YELLOW);
				if (spec.airport().taxiwayLights() && u % air.dims().taxiLightEvery() == 0) {
					light(u, c - half, TAXI_LIGHT);
					light(u, c + half, TAXI_LIGHT);
				}
			}
		}
		if (spec.airport().runways() > 1) {
			final int far = air.runwayCentre(1);
			final int[] around = air.endAround();
			final int[] stub = air.stub();
			for (final boolean high : new boolean[]{false, true}) {
				final int a = high ? air.mirror(around[1]) : around[0];
				final int b = high ? air.mirror(around[0]) : around[1];
				final int s0 = high ? air.mirror(stub[1]) : stub[0];
				final int s1 = high ? air.mirror(stub[0]) : stub[1];
				for (int u = air.taxiU0(); u <= far; u++) {
					paint(u, (a + b) / 2, PAINT_YELLOW);
				}
				for (int v = s0; v <= s1; v++) {
					paint(far, v, PAINT_YELLOW);
				}
				// the hold line before the second runway's blast pad
				holdingPositionH(high ? s0 : s1, high ? -1 : 1, far - air.dims().taxi() / 2, far + air.dims().taxi() / 2);
			}
		}
	}

	/** True when row {@code v} is inside a link's pavement, where the taxiway's edge line must break. */
	private boolean onLink(int v) {
		for (final int[] link : air.links()) {
			if (v >= link[0] && v <= link[1]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A runway holding position across a taxiway link: two solid yellow lines nearest the runway and two dashed lines beyond them,
	 * every line 0.15 m in reality and one block here.
	 */
	private void holdingPositionV(int solidAt, int fromV, int toV) {
		for (int v = fromV; v <= toV; v++) {
			paint(solidAt, v, PAINT_YELLOW);
			paint(solidAt - 1, v, PAINT_YELLOW);
			if ((v - fromV) % 3 < 2) {
				paint(solidAt - 3, v, PAINT_YELLOW);
				paint(solidAt - 4, v, PAINT_YELLOW);
			}
		}
	}

	/** The same holding position across a stub, solid lines nearest the runway in direction {@code toward}. */
	private void holdingPositionH(int solidAt, int toward, int fromU, int toU) {
		for (int u = fromU; u <= toU; u++) {
			paint(u, solidAt, PAINT_YELLOW);
			paint(u, solidAt - toward, PAINT_YELLOW);
			if ((u - fromU) % 3 < 2) {
				paint(u, solidAt - 3 * toward, PAINT_YELLOW);
				paint(u, solidAt - 4 * toward, PAINT_YELLOW);
			}
		}
	}

	/**
	 * The turning pads at the runway ends: a yellow edge line round the widening, broken where the end link joins it, and blue edge
	 * lights every eight cells along its outer edge. The runway's own threshold, edge and centre paint keeps its cells.
	 */
	void turnPadMarks() {
		final int runwayU0 = air.runwayU0().get(0);
		final int runwayU1 = air.runwayU1(0);
		for (final boolean high : new boolean[]{false, true}) {
			final AirportGeometry.Area pad = air.turnPad(high);
			final int edge = high ? pad.v1() : pad.v0();
			for (int u = pad.u0(); u <= pad.u1(); u++) {
				if (u < runwayU0 || u > runwayU1) {
					paint(u, edge, PAINT_YELLOW);
				}
			}
			for (int v = pad.v0(); v <= pad.v1(); v++) {
				paint(pad.u0(), v, PAINT_YELLOW);
				paint(pad.u1(), v, PAINT_YELLOW);
				if (spec.airport().taxiwayLights() && Math.floorMod(v - pad.v0(), air.dims().taxiLightEvery()) == 0) {
					light(pad.u0(), v, TAXI_LIGHT);
					light(pad.u1(), v, TAXI_LIGHT);
				}
			}
		}
	}

	/**
	 * A red runway-holding-position sign beside each hold bar: a three-wide, two-high red panel with a white stripe, on two poles, set
	 * one row outside the link on the grass. The panel reads as a red sign from the air and from the taxiway. A rapid exit's diagonal
	 * strip can cross the first row beside a link, so the sign steps out until it is clear of every taxiway area.
	 */
	void taxiwaySigns() {
		for (final int[] link : air.links()) {
			final int u = air.holdU() + 2;
			final int v = signV(link, u);
			for (int du = 0; du <= 2; du += 2) {
				put(Role.FIXTURE, u + du, StationGeometry.WALK_Y, v, LocalDir.MINUS_U, LAMP_POLE);
			}
			for (int du = 0; du <= 2; du++) {
				put(Role.FIXTURE, u + du, StationGeometry.WALK_Y + 1, v, LocalDir.MINUS_U, PAINT_RED);
				if (du == 1) {
					put(Role.FIXTURE, u + du, StationGeometry.WALK_Y + 2, v, LocalDir.MINUS_U, PAINT_WHITE);
				} else {
					put(Role.FIXTURE, u + du, StationGeometry.WALK_Y + 2, v, LocalDir.MINUS_U, PAINT_RED);
				}
			}
		}
	}

	/** The first row outside a link, on either side, where a three-wide sign at column {@code u} stays off every taxiway area. */
	private int signV(int[] link, int u) {
		for (int step = 1; step <= 4; step++) {
			for (final int v : new int[]{link[1] + step, link[0] - step}) {
				boolean clear = true;
				for (int du = 0; du <= 2; du++) {
					clear &= !onTaxiway(u + du, v);
				}
				if (clear) {
					return v;
				}
			}
		}
		return link[1] + 1;
	}

	private boolean onTaxiway(int u, int v) {
		for (final AirportGeometry.Area area : air.areas()) {
			if (area.kind() == AirportGeometry.Kind.TAXIWAY && area.contains(u, v)) {
				return true;
			}
		}
		return false;
	}

	// ---- apron and stands ---------------------------------------------------------------------------------------------

	/** Where an aircraft's nose stops on stand: past the boarding bridge, or well in from the apron's edge without a terminal. */
	int stopU() {
		return air.stopU();
	}

	/** A yellow lead-in line to the stop bar on every stand, the bar, the stand's number, and white lines between stands. */
	void standMarks() {
		final int stop = stopU();
		final int lane = air.laneU0();
		final int first = air.standV().get(0) - air.dims().standPitch() / 2;
		for (int i = 0; i <= spec.airport().gates(); i++) {
			final int v = first + i * air.dims().standPitch();
			for (int u = stop + 1; u <= lane - 1; u++) {
				paint(u, v, PAINT_WHITE);
			}
		}
		for (int i = 0; i < air.standV().size(); i++) {
			final int c = air.standV().get(i);
			// the lead-in runs from the nose to the taxilane's centreline, so it reaches the lane without crossing a neighbour
			for (int u = stop; u <= air.laneCentre(); u++) {
				paint(u, c, PAINT_YELLOW);
			}
			final int bar = air.dims().bridgeWidth() / 2 + 1;
			for (int v = c - bar; v <= c + bar; v++) {
				paint(stop, v, PAINT_WHITE);
			}
			// the number, upright for someone on the terminal side of the stand facing out over the apron: up is +u, the near edge is the terminal side
			final int scale = numberScale() > 1 ? 2 : 1;
			paintText(List.of(Integer.toString(i + 1)), scale, stop + 4, c + air.dims().standPitch() / 4, 1, 0, PAINT_WHITE);
			// the docking guidance post beside the nose, one per stand
			final int postV = c + air.dims().bridgeWidth() / 2 + 2;
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + 4; y++) {
				put(Role.FIXTURE, stop - 2, y, postV, LocalDir.MINUS_U, LAMP_POLE);
			}
			put(Role.LAMP, stop - 2, StationGeometry.WALK_Y + 4, postV, LocalDir.MINUS_U, TOWER_BEACON);
		}
	}

	/**
	 * The apron's equipment restraint areas: a red and white hatched line along the terminal edge, where tugs and baggage carts
	 * stand clear of the aircraft, and a hatched box between each pair of stands. A cell keeps the first block planned in it, so
	 * this runs after the stand marks and never covers a lead-in line.
	 */
	void equipmentLines() {
		final int at = air.apronU0() + 4;
		for (int v = air.apronV0(); v <= air.apronV1(); v++) {
			paint(at, v, (v - air.apronV0()) % 6 < 3 ? PAINT_RED : PAINT_WHITE);
		}
		final int first = air.standV().get(0) - air.dims().standPitch() / 2;
		for (int i = 0; i <= spec.airport().gates(); i++) {
			final int v = first + i * air.dims().standPitch();
			for (int u = air.apronU0() + 6; u <= air.apronU0() + 10; u++) {
				paint(u, v - 2, (u - air.apronU0()) % 2 == 0 ? PAINT_RED : PAINT_WHITE);
				paint(u, v + 2, (u - air.apronU0()) % 2 == 0 ? PAINT_RED : PAINT_WHITE);
			}
			for (int dv = -1; dv <= 1; dv++) {
				paint(air.apronU0() + 8, v + dv, dv == 0 ? PAINT_RED : PAINT_WHITE);
			}
		}
	}

	/**
	 * The apron's taxilane: a marked one-way lane along the airside edge, from one entrance at the apron's low end to another at its
	 * high end, so the apron is a loop rather than a dead end. Yellow edge lines break at each stand lead-in and at the two entrances;
	 * the centreline has green lights and the edges blue lights, as on the taxiway.
	 */
	void apronLanes() {
		final int u0 = air.laneU0();
		final int u1 = air.laneU1();
		final int centre = air.laneCentre();
		final int greenEvery = Math.max(1, air.dims().taxiLightEvery() / 2);
		for (int v = air.laneV0(); v <= air.laneV1(); v++) {
			final boolean entrance = v <= air.laneV0() + 8 || v >= air.laneV1() - 8;
			boolean stand = false;
			for (final int c : air.standV()) {
				stand |= Math.abs(v - c) <= 1;
			}
			if (!stand) {
				paint(u0, v, PAINT_YELLOW);
				if (spec.airport().taxiwayLights() && Math.floorMod(v - air.laneV0(), air.dims().taxiLightEvery()) == 0) {
					light(u0, v, TAXI_LIGHT);
				}
			}
			if (!entrance && !stand) {
				paint(u1, v, PAINT_YELLOW);
				if (spec.airport().taxiwayLights() && Math.floorMod(v - air.laneV0(), air.dims().taxiLightEvery()) == 0) {
					light(u1, v, TAXI_LIGHT);
				}
			}
			if (!stand) {
				paint(centre, v, PAINT_YELLOW);
				if (spec.airport().taxiwayLights() && Math.floorMod(v - air.laneV0(), greenEvery) == 0) {
					light(centre, v, THRESHOLD_LIGHT);
				}
			}
		}
	}

	/**
	 * The taxilane's stand furniture: a white number board on two poles at each stand's nose, and a white-lined ground-equipment box
	 * with two carts between each pair of stands. Kept clear of the lead-ins and the equipment restraint line, so nothing covers an
	 * aircraft's path.
	 */
	void apronLaneDetails() {
		final int pitch = air.dims().standPitch();
		final int first = air.standV().get(0) - pitch / 2;
		for (int i = 0; i < air.standV().size(); i++) {
			final int c = air.standV().get(i);
			final int v = c - pitch / 4;
			final int u = stopU() - 3;
			for (int du = 0; du <= 1; du++) {
				put(Role.FIXTURE, u + du, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, LAMP_POLE);
				put(Role.FIXTURE, u + du, StationGeometry.WALK_Y + 1, v, LocalDir.PLUS_U, PAINT_WHITE);
				put(Role.FIXTURE, u + du, StationGeometry.WALK_Y + 2, v, LocalDir.PLUS_U, PAINT_WHITE);
			}
		}
		for (int i = 0; i <= spec.airport().gates(); i++) {
			final int v = first + i * pitch + 3;
			final int u = air.apronU0() + 12;
			for (int du = 0; du <= 6; du++) {
				paint(u + du, v, PAINT_WHITE);
				paint(u + du, v + 3, PAINT_WHITE);
			}
			for (int dv = 0; dv <= 3; dv++) {
				paint(u, v + dv, PAINT_WHITE);
				paint(u + 6, v + dv, PAINT_WHITE);
			}
			for (int du = 1; du <= 5; du++) {
				paint(u + du, v + 1, PAINT_YELLOW);
			}
			put(Role.FIXTURE, u + 2, StationGeometry.WALK_Y, v + 2, LocalDir.PLUS_U, TROLLEY);
			put(Role.FIXTURE, u + 4, StationGeometry.WALK_Y, v + 2, LocalDir.MINUS_U, TROLLEY);
		}
	}


	/**
	 * MTR opens a plane's doors only beside a platform block, so both rows beside every stand's platform rail are the
	 * apron's concrete as a platform block. After the stand marks: a cell keeps the first block planned in it, so the
	 * stop bar keeps its paint. Ported from Aurelia Transit Architecture pull request 7.
	 */
	void gatePlatforms() {
		for (final AirportRoute.Gate gate : AirportRoute.of(air).gates()) {
			for (final AirportRoute.Cell cell : gate.platform()) {
				paint(cell.u(), cell.v() - 1, APRON_PLATFORM);
				paint(cell.u(), cell.v() + 1, APRON_PLATFORM);
			}
		}
	}

	/**
	 * The control tower: a white concrete shaft with a dark glass door and window bands, a cab floor, then a glazed cab that steps
	 * outward ring by ring (5, 7, 9 wide) so the glass leans out like a real tower's, a roof and a beacon. Planned before the masts
	 * and the pavement, and its footprint is apron only, so the runway strip stays clear.
	 */
	void controlTower() {
		final AirportGeometry.Area t = air.tower();
		final int cu = (t.u0() + t.u1()) / 2;
		final int cv = (t.v0() + t.v1()) / 2;
		final int top = air.towerTop();
		final int width = t.u1() - t.u0() + 1;
		final int maxHalf = (t.u1() - t.u0()) / 2;
		// Southend's tower (ap07): a stout shaft about half as wide as it is tall, a dark recess up its face and a dark window
		// band under a wide balcony, then a glazed cab narrower than the balcony under an overhanging roof
		final int shaft = width >= 11 ? 4 : width >= 9 ? 3 : 1;
		final int cab = width >= 11 ? 3 : maxHalf;
		final int balconyY = top - 6;
		final int bandY = balconyY - 3;
		final boolean detailed = shaft >= 3;
		for (int y = StationGeometry.WALK_Y; y < balconyY; y++) {
			for (int u = cu - shaft; u <= cu + shaft; u++) {
				for (int v = cv - shaft; v <= cv + shaft; v++) {
					final boolean faceU = u == cu - shaft || u == cu + shaft;
					final boolean faceV = v == cv - shaft || v == cv + shaft;
					if (!faceU && !faceV) {
						continue;
					}
					if (u == cu - shaft && v == cv && y <= StationGeometry.WALK_Y + 2) {
						// the doorway, facing the terminal
						put(Role.WINDOW, u, y, v, LocalDir.MINUS_U, TOWER_GLASS);
						continue;
					}
					final boolean corner = faceU && faceV;
					final int along = faceU ? v - cv : u - cu;
					final boolean band = detailed && y == bandY && !corner && Math.abs(along) < shaft - 1;
					// the recess runs up the face looking at the terminal, off centre as at Southend
					final boolean recess = detailed && u == cu - shaft && (v == cv + 1 || v == cv + 2)
							&& y > StationGeometry.WALK_Y + 2 && y < bandY - 1;
					final LocalDir face = faceU ? (u < cu ? LocalDir.MINUS_U : LocalDir.PLUS_U) : (v < cv ? LocalDir.MINUS_V : LocalDir.PLUS_V);
					if (band || recess) {
						put(Role.WINDOW, u, y, v, face, TOWER_DARK_GLASS);
					} else {
						put(Role.WALL, u, y, v, face, corner ? TOWER_FLOOR : TOWER_SHAFT);
					}
				}
			}
		}
		// the balcony: the full tower area, wider than the shaft and the cab, with a railing round its open edge
		for (int u = t.u0(); u <= t.u1(); u++) {
			for (int v = t.v0(); v <= t.v1(); v++) {
				put(Role.BUILDING_ROOF, u, balconyY, v, LocalDir.PLUS_V, TOWER_FLOOR);
				final boolean edge = u == t.u0() || u == t.u1() || v == t.v0() || v == t.v1();
				if (edge && (Math.abs(u - cu) > cab || Math.abs(v - cv) > cab)) {
					put(Role.FIXTURE, u, balconyY + 1, v, LocalDir.PLUS_V, TOWER_RAILING);
				}
			}
		}
		// the glazed cab, framed at its corners
		for (int y = balconyY + 1; y <= top - 2; y++) {
			for (int u = cu - cab; u <= cu + cab; u++) {
				for (int v = cv - cab; v <= cv + cab; v++) {
					final boolean edgeU = u == cu - cab || u == cu + cab;
					final boolean edgeV = v == cv - cab || v == cv + cab;
					if (edgeU || edgeV) {
						final boolean corner = edgeU && edgeV;
						put(corner ? Role.WALL : Role.WINDOW, u, y, v, LocalDir.PLUS_V, corner ? TOWER_SHAFT : TOWER_GLASS);
					}
				}
			}
		}
		// the cab's roof overhangs it by a block, with a short pole for the beacon
		final int roof = Math.min(cab + 1, maxHalf);
		for (int u = cu - roof; u <= cu + roof; u++) {
			for (int v = cv - roof; v <= cv + roof; v++) {
				if (u == cu && v == cv) {
					put(Role.FIXTURE, u, top - 1, v, LocalDir.PLUS_V, LAMP_POLE);
				} else {
					put(Role.BUILDING_ROOF, u, top - 1, v, LocalDir.PLUS_V, TOWER_FLOOR);
				}
			}
		}
		put(Role.LAMP, cu, top, cv, LocalDir.PLUS_V, TOWER_BEACON);
	}

	/** A windsock on a pole at the apron's south-west corner, clear of the control tower, its cone blowing along +v. */
	void windsock() {
		final int u = air.apronU0() + 1;
		final int v = air.apronV0() + 1;
		for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + 4; y++) {
			put(Role.FIXTURE, u, y, v, LocalDir.PLUS_V, LAMP_POLE);
		}
		put(Role.FIXTURE, u, StationGeometry.WALK_Y + 4, v, LocalDir.PLUS_V, WINDSOCK);
	}

	/**
	 * Floodlight masts on the apron between the stands, on the stand lines at the apron's terminal edge. Out on the apron they stood on
	 * each gate's siding row, so every plane backed through a mast; 2 short of the noses the turn onto the siding still clipped them
	 * (reviews 10-27).
	 */
	void masts() {
		final int u = air.apronU0() + 1;
		for (int i = 0; i <= spec.airport().gates(); i++) {
			final int v = air.standV().get(0) - air.dims().standPitch() / 2 + i * air.dims().standPitch();
			for (int y = StationGeometry.WALK_Y; y < air.dims().mast(); y++) {
				put(Role.FIXTURE, u, y, v, LocalDir.MINUS_U, LAMP_POLE);
			}
			put(Role.LAMP, u, air.dims().mast(), v, LocalDir.MINUS_U, MAST_LAMP);
		}
	}

	// ---- support buildings and the perimeter ---------------------------------------------------------------------------------

	/**
	 * The three support buildings the study asks for: a fire station on the apron's north edge with its doors facing the taxiway and
	 * two tenders ready in the open bays, a hangar with its own apron link, and a bunded fuel farm on the service road. Each stands
	 * on an apron or service-road area, so nothing floats and every one is reached on foot or by road.
	 */
	void supportBuildings() {
		fireStation();
		if (deepApron()) {
			hangar();
			fuelFarm();
		}
	}

	/** A compact apron is too shallow for a hangar and its link beside the fire station; those two wait for a regional apron. */
	private boolean deepApron() {
		return air.dims().standDepth() >= 40;
	}

	private void fireStation() {
		final AirportGeometry.Area a = air.fireStation();
		final int u0 = a.u0();
		final int u1 = a.u1();
		final int v0 = a.v0();
		final int v1 = a.v1();
		final int top = 8;
		final int[][] bays = {{v0 + 1, v0 + 3}, {v0 + 5, v0 + 7}, {v0 + 9, v0 + 11}};
		for (int y = StationGeometry.WALK_Y; y <= top; y++) {
			for (int v = v0; v <= v1; v++) {
				for (int u = u0; u <= u1; u++) {
					if (u != u0 && u != u1 && v != v0 && v != v1) {
						continue;
					}
					if (u == u1 && y <= top - 4 && inBay(bays, v)) {
						// the engine bays: all three are openings; the first gets a red roller door below
						continue;
					}
					final boolean plinth = y == StationGeometry.WALK_Y;
					final boolean band = y == StationGeometry.WALK_Y + 5;
					put(Role.WALL, u, y, v, LocalDir.PLUS_V, plinth ? "minecraft:gray_concrete" : band ? PAINT_RED : "minecraft:white_concrete");
				}
			}
		}
		// red roller door on the first bay, with a white lintel over all three
		for (int v = bays[0][0]; v <= bays[0][1]; v++) {
			for (int y = StationGeometry.WALK_Y; y <= top - 4; y++) {
				put(Role.WALL, u1, y, v, LocalDir.PLUS_U, y == StationGeometry.WALK_Y ? "minecraft:gray_concrete" : PAINT_RED);
			}
		}
		for (final int[] bay : bays) {
			for (int v = bay[0]; v <= bay[1]; v++) {
				put(Role.WALL, u1, top - 3, v, LocalDir.PLUS_U, PAINT_WHITE);
			}
		}
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				put(Role.BUILDING_ROOF, u, top, v, LocalDir.PLUS_V, "minecraft:light_gray_concrete");
			}
		}
		for (int v = v0; v <= v1; v++) {
			put(Role.BUILDING_ROOF, u0, top + 1, v, LocalDir.PLUS_V, "minecraft:gray_concrete");
			put(Role.BUILDING_ROOF, u1, top + 1, v, LocalDir.PLUS_V, "minecraft:gray_concrete");
		}
		for (int u = u0; u <= u1; u++) {
			put(Role.BUILDING_ROOF, u, top + 1, v0, LocalDir.PLUS_V, "minecraft:gray_concrete");
			put(Role.BUILDING_ROOF, u, top + 1, v1, LocalDir.PLUS_V, "minecraft:gray_concrete");
		}
		// the hose tower at the south-west corner, red and white bands, with a beacon over it
		for (int y = StationGeometry.WALK_Y; y <= top + 6; y++) {
			for (int u = u0; u <= u0 + 3; u++) {
				for (int v = v0; v <= v0 + 3; v++) {
					if (u != u0 && u != u0 + 3 && v != v0 && v != v0 + 3) {
						continue;
					}
					put(Role.WALL, u, y, v, LocalDir.PLUS_V, Math.floorMod(y, 6) < 3 ? "minecraft:white_concrete" : PAINT_RED);
				}
			}
		}
		for (int u = u0; u <= u0 + 3; u++) {
			for (int v = v0; v <= v0 + 3; v++) {
				put(Role.BUILDING_ROOF, u, top + 7, v, LocalDir.PLUS_V, "minecraft:gray_concrete");
			}
		}
		put(Role.FIXTURE, u0 + 1, top + 8, v0 + 1, LocalDir.PLUS_V, LAMP_POLE);
		put(Role.LAMP, u0 + 1, top + 9, v0 + 1, LocalDir.PLUS_V, TOWER_BEACON);
		// two tenders in the open bays, one red body each, a white cab and black wheels
		for (int b = 1; b < bays.length; b++) {
			final int v = bays[b][0];
			final int centre = (bays[b][0] + bays[b][1]) / 2;
			for (int u = u1 - 6; u <= u1 - 2; u++) {
				for (int dv = 0; dv <= 1; dv++) {
					final int vv = Math.min(centre + dv, bays[b][1]);
					put(Role.FIXTURE, u, StationGeometry.WALK_Y, vv, LocalDir.PLUS_U, "minecraft:black_concrete");
					put(Role.FIXTURE, u, StationGeometry.WALK_Y + 1, vv, LocalDir.PLUS_U, PAINT_RED);
					put(Role.FIXTURE, u, StationGeometry.WALK_Y + 2, vv, LocalDir.PLUS_U, PAINT_RED);
				}
			}
			for (int dv = 0; dv <= 1; dv++) {
				final int vv = Math.min(centre + dv, bays[b][1]);
				put(Role.FIXTURE, u1 - 7, StationGeometry.WALK_Y + 1, vv, LocalDir.MINUS_U, PAINT_WHITE);
				put(Role.FIXTURE, u1 - 7, StationGeometry.WALK_Y + 2, vv, LocalDir.MINUS_U, PAINT_WHITE);
			}
			put(Role.FIXTURE, u1 - 4, StationGeometry.WALK_Y + 3, v, LocalDir.PLUS_U, PAINT_WHITE);
		}
	}

	private static boolean inBay(int[][] bays, int v) {
		for (final int[] bay : bays) {
			if (v >= bay[0] && v <= bay[1]) {
				return true;
			}
		}
		return false;
	}

	private void hangar() {
		final AirportGeometry.Area a = air.hangar();
		final int u0 = a.u0();
		final int u1 = a.u1();
		final int v0 = a.v0();
		final int v1 = a.v1();
		final int top = 10;
		final int doorU0 = u0 + 5;
		final int doorU1 = u0 + 13;
		for (int y = StationGeometry.WALK_Y; y <= top; y++) {
			for (int v = v0; v <= v1; v++) {
				for (int u = u0; u <= u1; u++) {
					if (u != u0 && u != u1 && v != v0 && v != v1) {
						continue;
					}
					if (v == v0 && u >= doorU0 && u <= doorU1 && y <= top - 1) {
						// the big door, closed, with a red frame and a hazard strip at the bottom
						final String id = y == StationGeometry.WALK_Y ? PAINT_YELLOW : y % 3 == 0 ? PAINT_RED : "minecraft:gray_concrete";
						put(Role.WALL, u, y, v, LocalDir.MINUS_V, id);
						continue;
					}
					final boolean plinth = y == StationGeometry.WALK_Y || y == top;
					put(Role.WALL, u, y, v, LocalDir.PLUS_V, plinth ? "minecraft:gray_concrete" : "minecraft:light_gray_concrete");
				}
			}
		}
		// pilasters down the long walls, a shade darker, so the hangar reads as a shed from the ground
		for (int v = v0 + 4; v < v1; v += 6) {
			for (int y = StationGeometry.WALK_Y; y <= top; y++) {
				put(Role.WALL, u0, y, v, LocalDir.MINUS_U, "minecraft:gray_concrete");
				put(Role.WALL, u1, y, v, LocalDir.PLUS_U, "minecraft:gray_concrete");
			}
		}
		// a shallow gable roof and its end walls
		final int centre = (u0 + u1) / 2;
		final int half = (u1 - u0) / 2;
		for (int u = u0; u <= u1; u++) {
			final int rise = Math.max(0, (half - Math.abs(u - centre)) / 5);
			final int roofY = top + 1 + rise;
			for (int v = v0; v <= v1; v++) {
				put(Role.BUILDING_ROOF, u, roofY, v, LocalDir.PLUS_V, rise == 3 ? "minecraft:gray_concrete" : "minecraft:light_gray_concrete");
			}
			for (int y = top + 1; y <= roofY; y++) {
				put(Role.WALL, u, y, v0, LocalDir.MINUS_V, "minecraft:light_gray_concrete");
				put(Role.WALL, u, y, v1, LocalDir.PLUS_V, "minecraft:light_gray_concrete");
			}
		}
		for (int v = v0 + 8; v < v1; v += 10) {
			for (int y = top + 2; y <= top + 3; y++) {
				put(Role.FIXTURE, centre, y, v, LocalDir.PLUS_V, PLANT_VENT);
			}
		}
		// the apron link from the apron to the door, a yellow centreline and its green lead-on lights
		final AirportGeometry.Area link = air.hangarLink();
		final int linkCentre = (link.u0() + link.u1()) / 2;
		for (int v = link.v0(); v <= link.v1(); v++) {
			paint(linkCentre, v, PAINT_YELLOW);
			paint(link.u0(), v, PAINT_YELLOW);
			paint(link.u1(), v, PAINT_YELLOW);
			if (spec.airport().taxiwayLights() && Math.floorMod(v - link.v0(), air.dims().taxiLightEvery()) == 0) {
				light(link.u0(), v, TAXI_LIGHT);
				light(link.u1(), v, TAXI_LIGHT);
			}
			if (spec.airport().taxiwayLights() && Math.floorMod(v - link.v0(), Math.max(1, air.dims().taxiLightEvery() / 2)) == 0) {
				light(linkCentre, v, THRESHOLD_LIGHT);
			}
		}
	}

	private void fuelFarm() {
		final AirportGeometry.Area a = air.fuelFarm();
		final int u0 = a.u0();
		final int u1 = a.u1();
		final int v0 = a.v0();
		final int v1 = a.v1();
		final int gate0 = v0 + 7;
		final int gate1 = gate0 + 3;
		// the bund: a one-block concrete ring with a gate on the service-road side
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				if (u != u0 && u != u1 && v != v0 && v != v1) {
					continue;
				}
				if (u == u0 && v >= gate0 && v <= gate1) {
					continue;
				}
				put(Role.WALL, u, StationGeometry.WALK_Y, v, LocalDir.PLUS_V, "minecraft:light_gray_concrete");
			}
		}
		for (final int v : new int[]{gate0, gate1}) {
			for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 1; y++) {
				put(Role.FIXTURE, u0, y, v, LocalDir.MINUS_U, PAINT_YELLOW);
			}
		}
		final int[][] tanks = {{u0 + 4, v0 + 4}, {u0 + 12, v0 + 4}, {u0 + 4, v0 + 12}};
		for (final int[] tank : tanks) {
			final int tu = tank[0];
			final int tv = tank[1];
			for (int du = 0; du <= 4; du++) {
				for (int dv = 0; dv <= 4; dv++) {
					final boolean corner = (du == 0 || du == 4) && (dv == 0 || dv == 4);
					put(Role.FIXTURE, tu + du, StationGeometry.WALK_Y, tv + dv, LocalDir.PLUS_V, "minecraft:iron_block");
					if (!corner) {
						put(Role.WALL, tu + du, StationGeometry.WALK_Y + 1, tv + dv, LocalDir.PLUS_V, "minecraft:white_concrete");
						put(Role.WALL, tu + du, StationGeometry.WALK_Y + 2, tv + dv, LocalDir.PLUS_V, "minecraft:white_concrete");
						put(Role.WALL, tu + du, StationGeometry.WALK_Y + 3, tv + dv, LocalDir.PLUS_V, PAINT_RED);
						put(Role.WALL, tu + du, StationGeometry.WALK_Y + 4, tv + dv, LocalDir.PLUS_V, "minecraft:white_concrete");
						put(Role.WALL, tu + du, StationGeometry.WALK_Y + 5, tv + dv, LocalDir.PLUS_V, "minecraft:white_concrete");
					}
				}
			}
			for (int du = 1; du <= 3; du++) {
				for (int dv = 1; dv <= 3; dv++) {
					put(Role.BUILDING_ROOF, tu + du, StationGeometry.WALK_Y + 6, tv + dv, LocalDir.PLUS_V, "minecraft:iron_block");
				}
			}
			put(Role.FIXTURE, tu + 2, StationGeometry.WALK_Y + 7, tv + 2, LocalDir.PLUS_V, PLANT_VENT);
		}
	}

	/**
	 * The perimeter: a two-high iron fence on stone posts round the site, one landside gate where the public access road crosses it
	 * and a pedestrian opening on the car park footpath, plus the service road's dashed centre lines and lamps. The road itself is a
	 * SERVICE_ROAD area, so the fence posts stand on paved ground.
	 */
	void perimeter() {
		final int fu0 = air.fenceU0();
		final int fu1 = air.fenceU1();
		final int fv0 = this.air.fenceV0();
		final int fv1 = this.air.fenceV1();
		final int gate0 = this.air.accessV0();
		final int gate1 = this.air.accessV1();
		final int walk = entranceV();
		for (int v = fv0; v <= fv1; v++) {
			if (v < gate0 || v > gate1) {
				fenceCell(fu0, v);
			}
			fenceCell(fu1, v);
		}
		for (int u = fu0; u <= fu1; u++) {
			fenceCell(u, fv0);
			fenceCell(u, fv1);
		}
		// the gateposts, with a lantern on each
		for (final int v : new int[]{gate0 - 1, gate1 + 1}) {
			gatePost(fu0, v, 4);
		}
		// dashed white centre lines on the ring, the spine and the connector
		final int westCentre = fu0 + 3;
		final int eastCentre = fu1 - 3;
		for (int v = fv0 + 2; v <= fv1 - 2; v++) {
			if (Math.floorMod(v - fv0, 12) < 4) {
				paint(westCentre, v, PAINT_WHITE);
				paint(eastCentre, v, PAINT_WHITE);
			}
		}
		for (int u = fu0 + 2; u <= fu1 - 2; u++) {
			if (Math.floorMod(u - fu0, 12) < 4) {
				paint(u, fv0 + 3, PAINT_WHITE);
				paint(u, fv1 - 3, PAINT_WHITE);
			}
		}
		if (spec.airport().supportBuildings() && deepApron()) {
			final AirportGeometry.Area spine = air.serviceSpine();
			final AirportGeometry.Area connector = air.serviceConnector();
			for (int v = spine.v0(); v <= spine.v1(); v++) {
			if (Math.floorMod(v - spine.v0(), 12) < 4) {
				paint(spine.u0() + 3, v, PAINT_WHITE);
			}
		}
			for (int u = connector.u0(); u <= connector.u1(); u++) {
				if (Math.floorMod(u - connector.u0(), 12) < 4) {
					paint(u, connector.v0() + 2, PAINT_WHITE);
				}
			}
		}
		// a lamp every 32 rows on the ring, on its inner edge
		for (int v = fv0 + 16; v <= fv1 - 16; v += 32) {
			streetLamp(fu0 + 1, v, LocalDir.PLUS_U);
			streetLamp(fu1 - 1, v, LocalDir.MINUS_U);
		}
		for (int u = fu0 + 16; u <= fu1 - 16; u += 32) {
			streetLamp(u, fv0 + 1, LocalDir.PLUS_V);
			streetLamp(u, fv1 - 1, LocalDir.MINUS_V);
		}
	}

	/** One cell of the perimeter fence: a stone post every eighth cell, iron bars between. */
	private void fenceCell(int u, int v) {
		final boolean post = Math.floorMod(u + v, 8) == 0;
		if (post) {
			for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 2; y++) {
				put(Role.FIXTURE, u, y, v, LocalDir.PLUS_V, FENCE_POST);
			}
		} else {
			for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 1; y++) {
				put(Role.FIXTURE, u, y, v, LocalDir.PLUS_V, FENCE_BAR);
			}
		}
	}

	private void gatePost(int u, int v, int height) {
		for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + height; y++) {
			put(Role.FIXTURE, u, y, v, LocalDir.PLUS_V, FENCE_POST);
		}
		if (height > 2) {
			put(Role.FIXTURE, u, StationGeometry.WALK_Y + height, v, LocalDir.PLUS_V, LAMP_POLE);
			put(Role.LAMP, u, StationGeometry.WALK_Y + height + 1, v, LocalDir.PLUS_V, TOWER_BEACON);
		}
	}

	// ---- pavement -----------------------------------------------------------------------------------------------------

	/** The pavement under all of it: what the paint above left bare. The apron is laid in 8 m concrete slabs with darker joints. */
	void pavement() {
		for (final AirportGeometry.Area area : air.areas()) {
			switch (area.kind()) {
				case RUNWAY -> rect(area.u0(), area.u1(), area.v0(), area.v1(), RUNWAY_SURFACE);
				case BLAST_PAD -> rect(area.u0(), area.u1(), area.v0(), area.v1(), BLAST_SURFACE);
				case SHOULDER -> rect(area.u0(), area.u1(), area.v0(), area.v1(), APRON_SURFACE);
				case TAXIWAY -> rect(area.u0(), area.u1(), area.v0(), area.v1(), TAXI_SURFACE);
				case APRON -> apronPavement(area);
				case ROAD -> rect(area.u0(), area.u1(), area.v0(), area.v1(), ROAD_SURFACE);
				case SERVICE_ROAD -> rect(area.u0(), area.u1(), area.v0(), area.v1(), SERVICE_SURFACE);
				case GRASS -> rect(area.u0(), area.u1(), area.v0(), area.v1(), GRASS_SURFACE);
				case TERMINAL -> {
				}
			}
		}
	}

	/**
	 * The apron's concrete: light slabs with a darker joint every 12 blocks (8 m), except along the apron's edge, which stays
	 * whole so the taxiing check can walk from it to the taxiway.
	 */
	private void apronPavement(AirportGeometry.Area area) {
		for (int v = area.v0(); v <= area.v1(); v++) {
			for (int u = area.u0(); u <= area.u1(); u++) {
				final boolean inside = u > area.u0() && u < area.u1() && v > area.v0() && v < area.v1();
				final boolean joint = inside && (Math.floorMod(u - area.u0(), 12) == 0 || Math.floorMod(v - area.v0(), 12) == 0);
				paint(u, v, joint ? APRON_JOINT : APRON_SURFACE);
			}
		}
	}

	// ---- terminal -----------------------------------------------------------------------------------------------------

	/** The first cell of the entrance: the landside wall's door, five wide, in the middle of the terminal. */
	int entranceV() {
		return (air.terminalV0() + air.terminalV1()) / 2;
	}

	/**
	 * The terminal: a floor, glass along the airside with pilasters, a landside wall with a five-wide entrance and the name over it,
	 * end walls, columns, a roof with lights, check-in counters with bag drops and queue posts on the landside, kiosks and trolleys
	 * by the entrance, a security line across the hall, a baggage hall with carousels at the landside's far end, and at every gate a
	 * desk, its number over the door and rows of linked seats; and a doorway onto every boarding bridge. Built from the modern
	 * building set; the security and gate blocks are Null's (10-26).
	 */
	void terminal() {
		final StylePalette.Building b = palette.building();
		final AirportGeometry.Dims d = air.dims();
		final int u0 = air.terminalU0();
		final int u1 = air.terminalU1();
		final int v0 = air.terminalV0();
		final int v1 = air.terminalV1();
		final int door = entranceV();
		final int half = d.entrance() / 2;
		final int top = d.wallTop();
		// openings first: the entrance and the gate doors stay air
		for (int v = door - half; v <= door + half; v++) {
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; y++) {
				keepClear(u0, y, v);
			}
		}
		for (final int c : air.standV()) {
			for (int v = c - d.bridgeWidth() / 2; v <= c + d.bridgeWidth() / 2; v++) {
				for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; y++) {
					keepClear(u1, y, v);
				}
			}
		}
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, TERMINAL_FLOOR);
				if (u > u0 && u < u1 && v > v0 && v < v1) {
					// a glazed skylight strip across the roof every 16 rows; the rest is the roof deck
					final boolean lit = u > u0 + 5 && u < u1 - 5 && Math.floorMod(v - v0, 16) < 2;
					put(Role.BUILDING_ROOF, u, terminalRoofY(), v, LocalDir.PLUS_V, lit ? b.window() : b.roof());
				} else {
					put(Role.BUILDING_ROOF, u, terminalRoofY(), v, LocalDir.PLUS_V, b.roof());
				}
			}
		}
		// a parapet round the roof and a few plant boxes on the deck, so it reads as a building from above
		for (int v = v0; v <= v1; v++) {
			put(Role.BUILDING_ROOF, u0, terminalRoofY() + 1, v, LocalDir.PLUS_V, b.trim());
			put(Role.BUILDING_ROOF, u1, terminalRoofY() + 1, v, LocalDir.PLUS_V, b.trim());
		}
		for (int u = u0; u <= u1; u++) {
			put(Role.BUILDING_ROOF, u, terminalRoofY() + 1, v0, LocalDir.PLUS_V, b.trim());
			put(Role.BUILDING_ROOF, u, terminalRoofY() + 1, v1, LocalDir.PLUS_V, b.trim());
		}
		for (int k = 0; k < 3; k++) {
			final int bu = u0 + (k + 1) * (u1 - u0) / 4;
			final int bv = v0 + (k + 1) * (v1 - v0) / 4;
			for (int u = bu - 1; u <= bu + 1; u++) {
				for (int v = bv - 1; v <= bv + 1; v++) {
					put(Role.BUILDING_ROOF, u, terminalRoofY() + 1, v, LocalDir.PLUS_V, b.trim());
				}
			}
		}
		for (int y = StationGeometry.WALK_Y; y <= top; y++) {
			for (int v = v0; v <= v1; v++) {
				// airside: a full-height glass curtain wall on a solid plinth, a metal mullion every six rows (where the roof's struts
				// meet it) and a transom at door height, so it reads as a glazed grid as at Southend (ap03), not a row of pillars
				final boolean plinth = y == StationGeometry.WALK_Y;
				final boolean mullionAir = Math.floorMod(v - v0, AIRSIDE_BAY) == 0 || v == v1
						|| y == StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT;
				put(plinth || mullionAir ? Role.WALL : Role.WINDOW, u1, y, v, LocalDir.PLUS_U,
						plinth ? b.plinth() : mullionAir ? b.trim() : b.window());
				// landside: a plinth, a band of glass with the same rhythm, a fascia
				final boolean band = y >= 2 && y <= top - 2 && Math.abs(v - door) > half;
				final boolean mullionLand = band && Math.floorMod(v - door, 4) == 0;
				final boolean glass = band && !mullionLand;
				final boolean lintel = y == StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT && Math.abs(v - door) <= half;
				put(glass ? Role.WINDOW : Role.WALL, u0, y, v, LocalDir.MINUS_U,
						plinth ? b.plinth() : glass ? b.window() : mullionLand || lintel ? b.trim() : b.wall());
			}
			for (int u = u0 + 1; u < u1; u++) {
				put(Role.WALL, u, y, v0, LocalDir.MINUS_V, y == StationGeometry.WALK_Y ? b.plinth() : b.wall());
				put(Role.WALL, u, y, v1, LocalDir.PLUS_V, y == StationGeometry.WALK_Y ? b.plinth() : b.wall());
			}
		}
		// the roof's first overhang ring: one row over the forecourt, the apron and both gable ends; terminalRoofEdge adds the rest
		for (int v = v0 - 1; v <= v1 + 1; v++) {
			put(Role.BUILDING_ROOF, u0 - 1, terminalRoofY(), v, LocalDir.MINUS_U, b.roof());
			put(Role.BUILDING_ROOF, u1 + 1, terminalRoofY(), v, LocalDir.PLUS_U, b.roof());
		}
		for (int u = u0; u <= u1; u++) {
			put(Role.BUILDING_ROOF, u, terminalRoofY(), v0 - 1, LocalDir.MINUS_V, b.roof());
			put(Role.BUILDING_ROOF, u, terminalRoofY(), v1 + 1, LocalDir.PLUS_V, b.roof());
		}
		// the name, over the entrance, fixed flat to the landside wall; a compact terminal's canopy sits just below it
		final int nameY = Math.max(top - 1, landsideCanopyY() + 1);
		if (!spec.name().isEmpty()) {
			for (int v = door - half - 3; v <= door + half + 3; v++) {
				putText(Role.FIXTURE, u0 - 1, nameY, v, LocalDir.MINUS_U, LETTERING, spec.name());
			}
		}
		// columns in two lines, each under the roof
		final int depth = u1 - u0 + 1;
		for (final int u : new int[]{u0 + depth / 3, u0 + 2 * depth / 3}) {
			for (int v = v0 + 4; v < v1 - 2; v += 8) {
				for (int y = StationGeometry.WALK_Y; y < top; y++) {
					put(Role.COLUMN, u, y, v, LocalDir.PLUS_V);
				}
				put(Role.COLUMN_CAP, u, top, v, LocalDir.PLUS_V);
				// departures boards on both faces of every column along v, above head height
				put(Role.FIXTURE, u, StationGeometry.WALK_Y + 2, v - 1, LocalDir.MINUS_V, DEPARTURES_BOARD);
				put(Role.FIXTURE, u, StationGeometry.WALK_Y + 2, v + 1, LocalDir.PLUS_V, DEPARTURES_BOARD);
			}
		}
		final int security = securityU();
		final int reclaim = reclaimV(security);
		// check-in counters along the landside, facing the entrance: three desks and a bag drop, a belt behind, queue posts in front
		for (int v = v0 + 5; v <= v1 - 5 && v + 2 < reclaim; v += 6) {
			if (Math.abs(v - door) > half + 3) {
				for (int k = 0; k < 3; k++) {
					put(Role.FIXTURE, u0 + 3, StationGeometry.WALK_Y, v + k - 1, LocalDir.MINUS_U, CHECK_IN_DESK);
				}
				put(Role.FIXTURE, u0 + 3, StationGeometry.WALK_Y, v + 2, LocalDir.MINUS_U, BAG_DROP);
				put(Role.FIXTURE, u0 + 4, StationGeometry.WALK_Y, v, LocalDir.MINUS_U, BAGGAGE_BELT);
				for (int u = u0 + 1; u <= u0 + 2; u++) {
					put(Role.FIXTURE, u, StationGeometry.WALK_Y, v - 2, LocalDir.MINUS_U, QUEUE_POST);
				}
			}
		}
		// self check-in kiosks and luggage trolleys either side of the entrance, inside the door
		for (final int side : new int[]{-1, 1}) {
			for (int k = 0; k < 2; k++) {
				put(Role.FIXTURE, u0 + 1, StationGeometry.WALK_Y, door + side * (half + 1 + k), LocalDir.MINUS_U, TROLLEY);
				put(Role.FIXTURE, u0 + 6, StationGeometry.WALK_Y, door + side * (half + 2 + 2 * k), LocalDir.MINUS_U, KIOSK);
			}
		}
		securityLine(security);
		baggageReclaim(security, reclaim);
		// at every gate: a gate desk beside the door, its number over the door, and rows of linked seats facing the glass
		final int off = d.standPitch() / 4;
		final int doorHalf = d.bridgeWidth() / 2;
		for (int i = 0; i < air.standV().size(); i++) {
			final int c = air.standV().get(i);
			put(Role.FIXTURE, u1 - 2, StationGeometry.WALK_Y, c - doorHalf - 2, LocalDir.MINUS_U, GATE_DESK);
			putText(Role.FIXTURE, u1 - 1, StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT, c, LocalDir.MINUS_U, HANGING_SIGN,
					"NONE:Gate " + (i + 1));
			for (final int u : new int[]{u1 - 2, u1 - 4}) {
				for (int k = 0; k < 4; k++) {
					put(Role.BENCH, u, StationGeometry.WALK_Y, c - off - k, LocalDir.PLUS_U, AIRPORT_SEAT);
					put(Role.BENCH, u, StationGeometry.WALK_Y, c + off + k, LocalDir.PLUS_U, AIRPORT_SEAT);
				}
			}
			put(Role.BIN, u1 - 2, StationGeometry.WALK_Y, c + off + 4, LocalDir.PLUS_U);
		}
		terminalRoofEdge();
		terminalAirsideStruts();
		terminalRoofPlant();
		terminalSkylights();
		terminalClerestory();
		terminalLandsideCanopy();
	}

	/**
	 * The terminal roof's edge: the deck reaches two blocks past the landside wall and three past the airside one, two past the
	 * gable ends, with a light metal fascia at the rim, a soffit under the whole overhang and a low parapet above, so the glass
	 * box has a deep roof rather than a lid. It stays over the terminal, the road and the apron, clear of the boarding bridges.
	 */
	void terminalRoofEdge() {
		final StylePalette.Building b = palette.building();
		final int roofY = terminalRoofY();
		final int u0 = air.terminalU0();
		final int u1 = air.terminalU1();
		final int v0 = air.terminalV0();
		final int v1 = air.terminalV1();
		// the deck: landside 2 deep, airside 3, and 2 past each gable end, the outermost ring in the metal fascia
		for (int v = v0 - 2; v <= v1 + 2; v++) {
			for (int u = u0 - 2; u <= u1 + 3; u++) {
				if (u >= u0 && u <= u1 && v >= v0 && v <= v1) {
					continue;
				}
				final boolean rim = u == u0 - 2 || u == u1 + 3 || v == v0 - 2 || v == v1 + 2;
				final LocalDir front = u < u0 ? LocalDir.MINUS_U : u > u1 ? LocalDir.PLUS_U : v < v0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
				put(Role.BUILDING_ROOF, u, roofY, v, front, rim ? b.trim() : b.roof());
			}
		}
		// the soffit under the overhang, the older one-block ring included; a forecourt column keeps its cap
		for (int v = v0 - 2; v <= v1 + 2; v++) {
			for (int u = u0 - 2; u <= u1 + 3; u++) {
				if (u >= u0 && u <= u1 && v >= v0 && v <= v1 || forecourtCap(u, roofY - 1, v)) {
					continue;
				}
				final LocalDir front = u < u0 ? LocalDir.MINUS_U : u > u1 ? LocalDir.PLUS_U : v < v0 ? LocalDir.MINUS_V : LocalDir.PLUS_V;
				put(Role.BUILDING_ROOF, u, roofY - 1, v, front, b.plinth());
			}
		}
		// a low parapet on the outer rim, so the deck reads as a roof from the airside and the forecourt
		for (int v = v0 - 2; v <= v1 + 2; v++) {
			put(Role.BUILDING_ROOF, u0 - 2, roofY + 1, v, LocalDir.MINUS_U, b.trim());
			put(Role.BUILDING_ROOF, u1 + 3, roofY + 1, v, LocalDir.PLUS_U, b.trim());
		}
		for (int u = u0 - 2; u <= u1 + 3; u++) {
			put(Role.BUILDING_ROOF, u, roofY + 1, v0 - 2, LocalDir.MINUS_V, b.trim());
			put(Role.BUILDING_ROOF, u, roofY + 1, v1 + 2, LocalDir.PLUS_V, b.trim());
		}
	}

	/**
	 * Southend's struts (ap03, ap04): from every airside mullion a 45-degree steel brace rises out to the overhang's rim, so the
	 * three-deep roof edge over the apron is visibly carried instead of floating over a flat glass wall.
	 */
	void terminalAirsideStruts() {
		final int roofY = terminalRoofY();
		final int u1 = air.terminalU1();
		// up to three steps, ending under the soffit; a compact terminal's low wall gets a shorter strut that keeps door headroom
		final int steps = Math.min(3, roofY - 1 - (StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT));
		for (int v = air.terminalV0() + AIRSIDE_BAY; v < air.terminalV1(); v += AIRSIDE_BAY) {
			for (int k = 1; k <= steps; k++) {
				put(Role.RIB, u1 + k, roofY - 2 - steps + k, v, LocalDir.PLUS_U, VIADUCT_BRACE, "kind=diagonal");
			}
		}
	}

	/** True at a forecourt canopy column's cap, which the terminal's roof soffit leaves for the column below it. */
	private boolean forecourtCap(int u, int y, int v) {
		return y == StationGeometry.WALK_Y + 5 && forecourtColumn(u, v);
	}

	/** True at one of the forecourt canopy's two columns, from its foot to its cap. */
	private boolean forecourtColumn(int u, int v) {
		if (!spec.airport().forecourt() || u != air.roadU1() - 1) {
			return false;
		}
		final int side = air.dims().entrance() / 2 + 6;
		return v == entranceV() - side || v == entranceV() + side;
	}

	/**
	 * A few plant boxes on the roof, two by three and three high: grey concrete bodies, an iron trapdoor grille on each long
	 * side and a barred vent in the cap. Placed on the free deck either side of the skylights, clear of the older plant boxes.
	 */
	void terminalRoofPlant() {
		final int deck = terminalRoofY() + 1;
		final int u0 = air.terminalU0();
		final int u1 = air.terminalU1();
		final int v0 = air.terminalV0();
		final int v1 = air.terminalV1();
		final int du = u1 - u0 + 1;
		final int dv = v1 - v0 + 1;
		for (final int uu : new int[]{u0 + du / 5, u1 - du / 5 - 1}) {
			for (final int vv : new int[]{v0 + dv / 6, v0 + 5 * dv / 6}) {
				if (!roofFree(uu, uu + 1, deck, deck + 2, vv, vv + 2)) {
					continue;
				}
				for (int u = uu; u <= uu + 1; u++) {
					for (int v = vv; v <= vv + 2; v++) {
						put(Role.WALL, u, deck, v, LocalDir.PLUS_V, TOWER_FLOOR);
						final boolean grille = v == vv + 1;
						final LocalDir front = u == uu ? LocalDir.MINUS_U : LocalDir.PLUS_U;
						put(grille ? Role.FIXTURE : Role.WALL, u, deck + 1, v, front, grille ? PLANT_GRILLE : TOWER_FLOOR,
								grille ? "open=true" : "");
						final boolean vent = u == uu && v == vv + 1;
						put(vent ? Role.FIXTURE : Role.BUILDING_ROOF, u, deck + 2, v, LocalDir.PLUS_V, vent ? PLANT_VENT : TAXI_SURFACE);
					}
				}
			}
		}
	}

	/**
	 * A raised strip along the roof's centre, two blocks up over a band of glass, with a trim edge overhanging it by a block:
	 * the terminal's roof lifts over glass as at Southend (ap03, ap04) instead of reading as one flat-topped box. The skylights
	 * underneath stay inside it.
	 */
	void terminalClerestory() {
		final StylePalette.Building b = palette.building();
		final int deck = terminalRoofY() + 1;
		final int c = (air.terminalU0() + air.terminalU1()) / 2;
		final int half = 3;
		final int v0 = air.terminalV0() + 4;
		final int v1 = air.terminalV1() - 4;
		if (v1 - v0 < 8 || air.terminalU1() - air.terminalU0() < 2 * half + 8) {
			return;
		}
		for (int v = v0; v <= v1; v++) {
			for (int u = c - half; u <= c + half; u++) {
				final boolean side = u == c - half || u == c + half;
				final boolean end = v == v0 || v == v1;
				if (side || end) {
					final LocalDir out = side ? (u < c ? LocalDir.MINUS_U : LocalDir.PLUS_U) : (v == v0 ? LocalDir.MINUS_V : LocalDir.PLUS_V);
					final boolean post = side && (end || Math.floorMod(v - v0, 6) == 0);
					for (int y = deck; y <= deck + 1; y++) {
						put(post ? Role.WALL : Role.WINDOW, u, y, v, out, post ? b.trim() : b.window());
					}
				}
				put(Role.BUILDING_ROOF, u, deck + 2, v, LocalDir.PLUS_V, b.roof());
			}
			put(Role.BUILDING_ROOF, c - half - 1, deck + 2, v, LocalDir.MINUS_U, b.trim());
			put(Role.BUILDING_ROOF, c + half + 1, deck + 2, v, LocalDir.PLUS_U, b.trim());
		}
	}

	/** A row of framed glass skylights on the roof's centre line, every 24 rows, wherever the deck is free. */
	void terminalSkylights() {
		final StylePalette.Building b = palette.building();
		final int deck = terminalRoofY() + 1;
		final int c = (air.terminalU0() + air.terminalU1()) / 2;
		final int v1 = air.terminalV1();
		for (int v = air.terminalV0() + 8; v + 5 <= v1 - 4; v += 24) {
			if (!roofFree(c - 2, c + 2, deck, deck, v, v + 5)) {
				continue;
			}
			for (int u = c - 2; u <= c + 2; u++) {
				for (int w = v; w <= v + 5; w++) {
					final boolean frame = u == c - 2 || u == c + 2 || w == v || w == v + 5;
					put(Role.BUILDING_ROOF, u, deck, w, LocalDir.PLUS_V, frame ? b.trim() : TOWER_GLASS);
				}
			}
		}
	}

	/**
	 * The landside canopy, Southend's deep roof lip: a glazed deck along the whole front, so the terminal is not a flat box under
	 * its roof slab. It reaches five cells over the kerb and footway on the regional and real layouts, three on the compact one,
	 * closed by a two-block metal fascia, with a slim 45-degree steel brace every eight rows carrying it from the glass wall at
	 * door-head height. The braces stay over the footway and kerb, so no support stands in the carriageway.
	 */
	void terminalLandsideCanopy() {
		final StylePalette.Building b = palette.building();
		final int u0 = air.terminalU0();
		final int door = entranceV();
		final int reach = air.dims() == AirportGeometry.COMPACT ? 3 : 5;
		final int deckY = landsideCanopyY();
		final int outer = u0 - reach;
		final int v0 = air.terminalV0() + 2;
		final int v1 = air.terminalV1() - 2;
		for (int v = v0; v <= v1; v++) {
			// on a compact terminal the deck is at
			// the forecourt column's cap level, so leave it the cell it needs to reach its own canopy
			for (int u = u0 - 1; u > outer; u--) {
				if (deckY <= StationGeometry.WALK_Y + 5 && forecourtColumn(u, v)) {
					continue;
				}
				put(Role.BUILDING_ROOF, u, deckY, v, LocalDir.MINUS_U, b.window());
			}
			// a two-block metal fascia closes the outer row, its upper block standing on the deck's edge, not on its corner
			put(Role.BUILDING_ROOF, outer, deckY, v, LocalDir.MINUS_U, b.trim());
			put(Role.BUILDING_ROOF, outer, deckY + 1, v, LocalDir.MINUS_U, b.trim());
		}
		// a 45-degree brace from the wall at door-head height to the deck a third in from its outer edge; the phase keeps the
		// braced rows clear of the forecourt canopy's two columns
		for (int v = v0; v <= v1; v++) {
			if (Math.floorMod(v - door - 4, 8) != 0) {
				continue;
			}
			for (int u = u0 - 1, y = StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT; u > outer; u--, y++) {
				put(Role.RIB, u, y, v, LocalDir.MINUS_U, VIADUCT_BRACE, "kind=diagonal");
			}
		}
	}

	/** True when every cell of the box (u0..u1, y0..y1, v0..v1) is free: a roof unit only goes where the deck is bare. */
	private boolean roofFree(int u0, int u1, int y0, int y1, int v0, int v1) {
		for (int u = u0; u <= u1; u++) {
			for (int v = v0; v <= v1; v++) {
				if (!freeColumn(u, y0, y1, v)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The row (u) of the security line across the terminal: just airside of the railway station's box when there is one, so its
	 * escalators come up landside, else past the second column line; in the middle of a terminal too shallow for either.
	 */
	int securityU() {
		final int u0 = air.terminalU0();
		final int u1 = air.terminalU1();
		final int depth = u1 - u0 + 1;
		final int wanted = Math.max(u0 + 2 * depth / 3 + 3, rail == null ? u0 : rail.du() + AirportStation.WIDTH);
		// the lane (queue four rows before it, the table two after it) and two rows of seats must still fit before the glass
		return wanted + 2 < u1 - 5 ? wanted : u0 + depth / 2;
	}

	/** The first row of the baggage hall at the terminal's far end, or one past the end wall when the landside has no room for one. */
	int reclaimV(int security) {
		final int v1 = air.terminalV1();
		final int room = security - 5 - (air.terminalU0() + 6);
		if (room < 2) {
			return v1 + 1;
		}
		final int rows = (air.terminalV1() - air.terminalV0()) / 3;
		return rows >= CAROUSEL_LENGTH + 4 ? v1 - rows : v1 + 1;
	}

	/**
	 * Security across the terminal at row {@code at}: lanes of a walk-through arch, an X-ray scanner with a belt either side and the
	 * inspection table after it, queue posts before them, and glass between the lanes up to an exit opening by the far end wall. Cells
	 * already planned (columns, the railway station's openings) are left as they are.
	 */
	void securityLine(int at) {
		final StylePalette.Building b = palette.building();
		final int v0 = air.terminalV0();
		final int v1 = air.terminalV1();
		final int exit = v1 - 3;
		final Set<Integer> lanes = new HashSet<>();
		for (int v = v0 + 3; v + 2 < exit; v += LANE_PITCH) {
			lanes.add(v);
			lanes.add(v + 1);
			put(Role.FIXTURE, at, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, SECURITY_ARCH);
			keepClear(at, StationGeometry.WALK_Y + 1, v);
			put(Role.FIXTURE, at - 1, StationGeometry.WALK_Y, v + 1, LocalDir.PLUS_U, BAGGAGE_BELT);
			put(Role.FIXTURE, at, StationGeometry.WALK_Y, v + 1, LocalDir.PLUS_U, XRAY_SCANNER);
			put(Role.FIXTURE, at + 1, StationGeometry.WALK_Y, v + 1, LocalDir.PLUS_U, BAGGAGE_BELT);
			put(Role.FIXTURE, at + 2, StationGeometry.WALK_Y, v + 1, LocalDir.PLUS_U, SECURITY_DESK);
			for (int u = at - 4; u <= at - 1; u++) {
				put(Role.FIXTURE, u, StationGeometry.WALK_Y, v - 1, LocalDir.MINUS_U, QUEUE_POST);
			}
		}
		for (int v = v0 + 1; v < exit; v++) {
			if (!lanes.contains(v)) {
				for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 1; y++) {
					put(Role.WINDOW, at, y, v, LocalDir.PLUS_U, b.window());
				}
			}
		}
	}

	/**
	 * The baggage hall at the landside's far end (from row {@code from}): carousels, each a loop two pieces wide with corner pieces at
	 * its ends and its lip facing out, between the check-in belts and the security queue.
	 */
	void baggageReclaim(int security, int from) {
		final int v1 = air.terminalV1();
		final int a = (air.terminalU0() + 6 + security - 5) / 2;
		for (int v = from + 2; v + CAROUSEL_LENGTH - 1 <= v1 - 3; v += CAROUSEL_PITCH) {
			final int end = v + CAROUSEL_LENGTH - 1;
			// a whole loop or none: an opening of the railway station below, or a column, would break it
			boolean free = true;
			for (int r = v; r <= end; r++) {
				free &= !taken(a, StationGeometry.WALK_Y, r) && !taken(a + 1, StationGeometry.WALK_Y, r);
			}
			if (!free) {
				continue;
			}
			put(Role.FIXTURE, a, StationGeometry.WALK_Y, v, LocalDir.MINUS_U, CAROUSEL_CORNER);
			put(Role.FIXTURE, a + 1, StationGeometry.WALK_Y, v, LocalDir.MINUS_V, CAROUSEL_CORNER);
			put(Role.FIXTURE, a, StationGeometry.WALK_Y, end, LocalDir.PLUS_V, CAROUSEL_CORNER);
			put(Role.FIXTURE, a + 1, StationGeometry.WALK_Y, end, LocalDir.PLUS_U, CAROUSEL_CORNER);
			for (int r = v + 1; r < end; r++) {
				put(Role.FIXTURE, a, StationGeometry.WALK_Y, r, LocalDir.MINUS_U, CAROUSEL);
				put(Role.FIXTURE, a + 1, StationGeometry.WALK_Y, r, LocalDir.PLUS_U, CAROUSEL);
			}
		}
	}

	/**
	 * A boarding bridge from every gate door in the terminal's airside wall out over the apron: a rotunda against the wall, a
	 * telescoping tube raised to aircraft-door height between glass walls and metal bands, a roof, a drive column with wheels by the
	 * docking end, and the jet bridge's cab at the aircraft door. The cab extends its tunnel to the plane while it is stopped and
	 * pulls it back when it leaves.
	 */
	void boardingBridges() {
		final StylePalette.Building b = palette.building();
		final int from = air.terminalU1() + 1;
		final int to = air.terminalU1() + air.dims().bridge();
		final int half = air.dims().bridgeWidth() / 2;
		final int floorY = StationGeometry.WALK_Y + 1;
		for (final int c : air.standV()) {
			for (int u = from; u <= to; u++) {
				final boolean rotunda = u <= from + 2;
				final int wide = rotunda ? half + 2 : half;
				if (rotunda) {
					for (int v = c - wide; v <= c + wide; v++) {
						put(Role.FLOOR, u, StationGeometry.PLATFORM_Y, v, LocalDir.PLUS_V, b.floor());
					}
					// the rotunda's outer glass ring, so the bridge swells where it leaves the terminal
					for (final int side : new int[]{c - half - 2, c + half + 2}) {
						for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + 4; y++) {
							put(Role.WINDOW, u, y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.window());
						}
					}
					for (int v = c - wide - 1; v <= c + wide + 1; v++) {
						put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 4, v, LocalDir.PLUS_V, b.roof());
					}
					continue;
				}
				// the raised tube: a step up from the rotunda, then a floor at aircraft-door height
				for (int v = c - half; v <= c + half; v++) {
					put(Role.FLOOR, u, u == from + 3 ? StationGeometry.WALK_Y : floorY, v, LocalDir.PLUS_V, b.floor());
				}
				if (u == to) {
					// the cab at the aircraft door: a docking light, the jet bridge block, glass either side, a metal roof
					put(Role.LIGHT_PANEL, u, floorY + 2, c, LocalDir.PLUS_U, TOWER_BEACON);
					for (int y = floorY + 1; y <= floorY + 2; y++) {
						for (int v = c - half; v <= c + half; v++) {
							final boolean cab = v == c && y == floorY + 1;
							put(cab ? Role.FIXTURE : Math.abs(v - c) <= 1 ? Role.WINDOW : Role.WALL, u, y, v, LocalDir.PLUS_U,
									cab ? JET_BRIDGE : Math.abs(v - c) <= 1 ? b.window() : b.wall());
						}
						for (final int side : new int[]{c - half - 1, c + half + 1}) {
							put(Role.WALL, u, y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V, b.wall());
						}
					}
				} else {
					// the tube's sides: glass with a metal band every four blocks, and a drive column with wheels near the cab
					for (final int side : new int[]{c - half - 1, c + half + 1}) {
						final boolean drive = u >= to - 4 && u <= to - 2;
						for (int y = floorY + 1; y <= floorY + 2; y++) {
							if (drive) {
								put(Role.FIXTURE, u, y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V, BRIDGE_COLUMN);
							} else {
								final boolean band = u % 4 == 0;
								put(band ? Role.WALL : Role.WINDOW, u, y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V,
										band ? BRIDGE_BAND : b.window());
							}
						}
						if (drive) {
							put(Role.FIXTURE, u, StationGeometry.PLATFORM_Y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V, BRIDGE_WHEEL);
							for (int y = StationGeometry.WALK_Y; y < floorY; y++) {
								put(Role.FIXTURE, u, y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V, BRIDGE_COLUMN);
							}
						}
					}
					// a wider collar every eight blocks: the nested sections of a telescoping tunnel
					if ((u - from) % 8 == 0) {
						for (final int side : new int[]{c - half - 2, c + half + 2}) {
							for (int y = floorY + 1; y <= floorY + 2; y++) {
								put(Role.WALL, u, y, side, side < c ? LocalDir.MINUS_V : LocalDir.PLUS_V, BRIDGE_BAND);
							}
						}
						for (int v = c - half - 3; v <= c + half + 3; v++) {
							put(Role.BUILDING_ROOF, u, floorY + 3, v, LocalDir.PLUS_V, BRIDGE_BAND);
						}
					}
				}
				for (int v = c - wide - 1; v <= c + wide + 1; v++) {
					put(Role.BUILDING_ROOF, u, floorY + 3, v, LocalDir.PLUS_V, b.roof());
				}
			}
		}
	}

	// ---- the landside forecourt -----------------------------------------------------------------------------------------

	/**
	 * The forecourt in front of the terminal: a footway and kerb on each side of the road, the centre lane's dashed line, a zebra
	 * crossing at the entrance, a canopy on columns over the door, and street lamps. The road pavement itself is laid last.
	 */
	void airportForecourt() {
		final StylePalette.Building b = palette.building();
		final int u0 = air.roadU0();
		final int u1 = air.roadU1();
		final int v0 = air.apronV0();
		final int v1 = air.apronV1();
		final int door = entranceV();
		final int half = air.dims().entrance() / 2;
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u0 + 1; u++) {
				paint(u, v, SIDEWALK);
			}
			for (int u = u1 - 1; u <= u1; u++) {
				paint(u, v, SIDEWALK);
			}
			// the kerbs stand one half-block above the road, which is enough to read as an edge
			put(Role.FIXTURE, u0 + 2, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, KERB, "type=bottom");
			put(Role.FIXTURE, u1 - 2, StationGeometry.WALK_Y, v, LocalDir.MINUS_U, KERB, "type=bottom");
		}
		// two drop-off islands between the through lanes, clear of the crossing
		final int centre = (u0 + u1) / 2;
		for (final int side : new int[]{-1, 1}) {
			final int islandFrom = door + side * 12;
			final int islandTo = door + side * 28;
			for (int v = Math.min(islandFrom, islandTo); v <= Math.max(islandFrom, islandTo); v++) {
				for (int u = centre - 1; u <= centre + 1; u++) {
					paint(u, v, SIDEWALK);
				}
				put(Role.FIXTURE, centre - 2, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, KERB, "type=bottom");
				put(Role.FIXTURE, centre + 2, StationGeometry.WALK_Y, v, LocalDir.MINUS_U, KERB, "type=bottom");
			}
		}
		// the centre line: 4 m of paint and 8 m of gap
		for (int v = v0; v <= v1; v++) {
			if (Math.floorMod(v - v0, 12) < 4) {
				paint(centre, v, PAINT_WHITE);
			}
		}
		// a zebra crossing at the entrance
		for (int v = door - half - 1; v <= door + half + 1; v++) {
			if (Math.floorMod(v - door, 2) == 0) {
				for (int u = u0 + 3; u <= u1 - 3; u++) {
					paint(u, v, PAINT_WHITE);
				}
			}
		}
		// the canopy over the door: six blocks deep over the drop-off lane, on two columns at the kerb
		final int from = Math.max(u0, u1 - 6);
		final int to = u1;
		for (final int v : new int[]{door - half - 6, door + half + 6}) {
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + 5; y++) {
				put(Role.COLUMN, u1 - 1, y, v, LocalDir.MINUS_U);
			}
			put(Role.COLUMN_CAP, u1 - 1, StationGeometry.WALK_Y + 5, v, LocalDir.MINUS_U);
		}
		for (int u = from; u <= to; u++) {
			for (int v = door - half - 6; v <= door + half + 6; v++) {
				put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 6, v, LocalDir.PLUS_V, b.window());
			}
		}
		// the canopy's metal fascia: a frame round the glass, heaviest at the kerb edge
		for (int v = door - half - 7; v <= door + half + 7; v++) {
			put(Role.BUILDING_ROOF, from, StationGeometry.WALK_Y + 7, v, LocalDir.MINUS_U, b.trim());
			put(Role.BUILDING_ROOF, to, StationGeometry.WALK_Y + 7, v, LocalDir.PLUS_U, b.trim());
		}
		for (int u = from; u <= to; u++) {
			put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 7, door - half - 7, LocalDir.MINUS_V, b.trim());
			put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 7, door + half + 7, LocalDir.PLUS_V, b.trim());
		}
		for (int u = from; u <= to; u += 4) {
			for (int v = door - half - 6; v <= door + half + 6; v++) {
				put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 7, v, LocalDir.PLUS_V, b.trim());
			}
		}
		// street lamps down the outer footway, clear of the canopy
		for (int v = v0 + 12; v <= v1 - 8; v += 24) {
			if (Math.abs(v - door) < half + 8) {
				continue;
			}
			streetLamp(u0 + 1, v, LocalDir.PLUS_U);
		}
		// low bollard lights along the kerb, eight blocks apart, so the footway reads at night from the airside too
		for (int v = door - 24; v <= door + 24; v += 8) {
			if (v < v0 || v > v1) {
				continue;
			}
			put(Role.LAMP, u0 + 1, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, TOWER_BEACON);
		}
	}

	/**
	 * The forecourt's transit frontage: a taxi rank on the arrivals side and three bus bays on the departures side, both in the outer
	 * lane clear of the drop-off islands, with painted bay lines, the words TAXI and BUS on the ground, a bus shelter on the footway
	 * and a rank sign. The footpath already runs from the car park to the terminal door across the zebra crossing.
	 */
	void landTransit() {
		final int u0 = air.roadU0();
		final int u1 = air.roadU1();
		final int door = entranceV();
		final int half = air.dims().entrance() / 2;
		// a short forecourt has only the drop-off islands; the rank and bus bays need clear rows beyond them
		if (air.apronV1() - air.apronV0() < 100) {
			return;
		}
		// the taxi rank: four bays 5 rows each, south of the drop-off islands
		final int taxiFrom = door + half + 31;
		for (int v = taxiFrom; v <= taxiFrom + 20; v += 5) {
			for (int u = u0 + 2; u <= u1 - 2; u++) {
				paint(u, v, PAINT_WHITE);
			}
		}
		paintText(List.of("TAXI"), 1, u0 + 3, taxiFrom + 5, 0, 1, PAINT_WHITE);
		// three bus bays 6 rows each, north of the islands
		final int busFrom = door - half - 46;
		for (int k = 0; k <= 3; k++) {
			for (int u = u0 + 2; u <= u1 - 2; u++) {
				paint(u, busFrom + k * 6, PAINT_WHITE);
			}
		}
		paintText(List.of("BUS"), 1, u0 + 3, busFrom + 8, 0, 1, PAINT_WHITE);
		// the bus shelter on the outer footway, glazed at the back, with a roof
		final StylePalette.Building b = palette.building();
		for (int v = busFrom + 1; v <= busFrom + 6; v++) {
			for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 2; y++) {
				put(Role.FIXTURE, u0, y, v, LocalDir.PLUS_U, b.window());
			}
			put(Role.BUILDING_ROOF, u0, StationGeometry.WALK_Y + 3, v, LocalDir.PLUS_V, b.roof());
			put(Role.BUILDING_ROOF, u0 + 1, StationGeometry.WALK_Y + 3, v, LocalDir.PLUS_V, b.roof());
		}
		for (final int v : new int[]{busFrom + 1, busFrom + 6}) {
			for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 3; y++) {
				put(Role.FIXTURE, u0 + 1, y, v, LocalDir.MINUS_U, LAMP_POLE);
			}
		}
		// the rank sign by the outer kerb
		for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 2; y++) {
			put(Role.FIXTURE, u0 + 1, y, taxiFrom, LocalDir.PLUS_U, LAMP_POLE);
		}
		put(Role.FIXTURE, u0 + 1, StationGeometry.WALK_Y + 3, taxiFrom, LocalDir.PLUS_U, PAINT_WHITE);
	}

	// ---- the landside car park and access road -----------------------------------------------------------------------------

	/** A street lamp: the station's sign pole for the post, a sea lantern on top. */
	private void streetLamp(int u, int v, LocalDir front) {
		for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + 6; y++) {
			put(Role.FIXTURE, u, y, v, front, LAMP_POLE);
		}
		put(Role.LAMP, u, StationGeometry.WALK_Y + 6, v, front, TOWER_BEACON);
	}

	/**
	 * The access road from the forecourt out to the landside edge of the site (u = 0): two footways and kerbs round a seven-block
	 * carriageway with a dashed white centre line, a lamp every 10 blocks, and a gap in its kerb where each car park aisle meets it.
	 * Planned before the forecourt, so its mouth takes the forecourt's outer footway and kerb and the two join.
	 */
	void airportAccessRoad() {
		final int u0 = 0;
		final int u1 = air.roadU0() - 1;
		final int v0 = air.accessV0();
		final int v1 = air.accessV1();
		final int centre = air.accessCentre();
		// the mouth: the road carries on through the forecourt's outer footway and kerb
		for (int v = v0; v <= v1; v++) {
			for (int u = u1 + 1; u <= air.roadU0() + 2; u++) {
				paint(u, v, ROAD_SURFACE);
			}
		}
		// the centre line: 4 blocks of paint and 8 of gap, as on the forecourt
		for (int u = u0 + 2; u <= u1; u++) {
			if (Math.floorMod(u - u0 - 2, 12) < 4) {
				paint(u, centre, PAINT_WHITE);
			}
		}
		for (int u = u0; u <= u1; u++) {
			// the footway and kerb on the outer side, and the kerb along the car park
			paint(u, v0, SIDEWALK);
			paint(u, v0 + 1, SIDEWALK);
			put(Role.FIXTURE, u, StationGeometry.WALK_Y, v0 + 2, LocalDir.PLUS_V, KERB, "type=bottom");
			if (!carParkEntrance(u)) {
				put(Role.FIXTURE, u, StationGeometry.WALK_Y, v1, LocalDir.MINUS_V, KERB, "type=bottom");
			}
		}
		for (int u = u0 + 5; u <= u1 - 2; u += 10) {
			streetLamp(u, v0 + 1, LocalDir.PLUS_V);
		}
	}

	/** True for a column of one of the car park's aisles, where its kerb gives way to a driveway. */
	private boolean carParkEntrance(int u) {
		for (int row = 0; row < 2; row++) {
			final int aisle = CAR_FIRST_BAY + row * (CAR_BAY + CAR_AISLE) + CAR_BAY;
			if (u >= aisle && u < aisle + CAR_AISLE) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The surface car park behind the forecourt: three rows of 2 x 5 bays off two 6-wide aisles, a kerbed footway along the site
	 * edge and a wider one along the road, a lamp every 10 blocks down each aisle, and a planter with an oak tree at each end of
	 * every bay row. Its asphalt is the geometry's two ROAD areas; the bay lines are painted before the pavement.
	 */
	void airportCarPark() {
		final int v0 = air.carParkV0();
		final int v1 = air.carParkV1();
		final int walk = entranceV();
		final int landsideU0 = AirportGeometry.SITE;
		final int landsideU1 = landsideU0 + AirportGeometry.LANDSIDE - 1;
		// the kerbed footpath from the perimeter road to the terminal doors, across the car park (the fence has one vehicle
		// gate, so the path starts inside it and joins the service road rather than making a second opening)
		final int pathFrom = air.fenceU0() + 1;
		for (int v = walk - 1; v <= walk + 1; v++) {
			for (int u = pathFrom; u <= landsideU1; u++) {
				paint(u, v, SIDEWALK);
			}
		}
		for (int u = landsideU0 + 2; u < landsideU1 - 1; u++) {
			put(Role.FIXTURE, u, StationGeometry.WALK_Y, walk - 2, LocalDir.MINUS_V, KERB, "type=bottom");
			put(Role.FIXTURE, u, StationGeometry.WALK_Y, walk + 2, LocalDir.PLUS_V, KERB, "type=bottom");
		}
		for (int v = v0; v <= v1; v++) {
			paint(landsideU0, v, SIDEWALK);
			paint(landsideU0 + 1, v, SIDEWALK);
			// the kerbs give way where the footpath crosses them
			if (Math.abs(v - walk) > 1) {
				put(Role.FIXTURE, landsideU0 + 2, StationGeometry.WALK_Y, v, LocalDir.PLUS_U, KERB, "type=bottom");
				put(Role.FIXTURE, landsideU1 - 1, StationGeometry.WALK_Y, v, LocalDir.MINUS_U, KERB, "type=bottom");
			}
			paint(landsideU1, v, SIDEWALK);
		}
		for (int row = 0; row < 3; row++) {
			final int bay = CAR_FIRST_BAY + row * (CAR_BAY + CAR_AISLE);
			// one line every third row leaves the bays two blocks wide
			for (int v = v0 + 3; v <= v1 - 3; v++) {
				if (Math.floorMod(v - v0, 3) == 0) {
					for (int u = bay; u < bay + CAR_BAY; u++) {
						paint(u, v, PAINT_WHITE);
					}
				}
			}
			carParkPlanter(bay, bay + CAR_BAY - 1, v0, v0 + 2);
			carParkPlanter(bay, bay + CAR_BAY - 1, v1 - 2, v1);
		}
		// a lamp down each aisle, every 10 rows, clear of the footpath
		final int aisleA = CAR_FIRST_BAY + CAR_BAY;
		final int aisleB = CAR_FIRST_BAY + CAR_BAY + CAR_AISLE + CAR_BAY;
		for (int v = v0 + 6; v <= v1 - 4; v += 10) {
			if (Math.abs(v - walk) <= 2) {
				continue;
			}
			streetLamp(aisleA, v, LocalDir.PLUS_U);
			streetLamp(aisleB, v, LocalDir.PLUS_U);
		}
	}

	/** A row-end planter: a soil bed in a kerb ring, an oak hedge round one tree. */
	private void carParkPlanter(int u0, int u1, int v0, int v1) {
		for (int v = v0; v <= v1; v++) {
			for (int u = u0; u <= u1; u++) {
				if (u == u0 || u == u1 || v == v0 || v == v1) {
					put(Role.FIXTURE, u, StationGeometry.WALK_Y, v, LocalDir.PLUS_V, KERB, "type=bottom");
				} else {
					paint(u, v, PLANTER_SOIL);
				}
			}
		}
		final int cu = (u0 + u1) / 2;
		final int cv = (v0 + v1) / 2;
		for (int v = v0 + 1; v < v1; v++) {
			for (int u = u0 + 1; u < u1; u++) {
				if (u != cu || v != cv) {
					put(Role.FIXTURE, u, StationGeometry.WALK_Y, v, LocalDir.PLUS_V, HEDGE, "persistent=true");
				}
			}
		}
		for (int y = StationGeometry.WALK_Y; y <= StationGeometry.WALK_Y + 3; y++) {
			put(Role.FIXTURE, cu, y, cv, LocalDir.PLUS_V, TRUNK);
		}
		for (int du = -1; du <= 1; du++) {
			for (int dv = -1; dv <= 1; dv++) {
				if (du != 0 || dv != 0) {
					put(Role.FIXTURE, cu + du, StationGeometry.WALK_Y + 4, cv + dv, LocalDir.PLUS_V, HEDGE, "persistent=true");
				}
			}
		}
	}

	// ---- the railway station under the terminal ---------------------------------------------------------------------------

	/**
	 * The railway station under the terminal: the box station planned on its own and moved under the hall. Its blocks are copied as
	 * planned (role, front, state and text kept); its street slab becomes the terminal's floor; above the floor only its escalators,
	 * lift (with the lift's own shaft walls and roof) and the glass barriers round its wells stay, the street pavilions and pylons go,
	 * as the hall is their roof. Its dig is kept clear of the terminal's floor, columns and fit-out. Planned before the terminal.
	 */
	void railStation() {
		final StationPlan station = StationPlanner.plan(rail.spec());
		final int du = rail.du();
		final int dy = rail.dy();
		final int dv = rail.dv();
		int liftU0 = Integer.MAX_VALUE;
		int liftU1 = Integer.MIN_VALUE;
		int liftV0 = Integer.MAX_VALUE;
		int liftV1 = Integer.MIN_VALUE;
		for (final PlannedBlock b : station.blocks()) {
			if (b.role() == Role.LIFT && b.y() + dy >= 1) {
				liftU0 = Math.min(liftU0, b.u() - 1);
				liftU1 = Math.max(liftU1, b.u() + 1);
				liftV0 = Math.min(liftV0, b.v() - 1);
				liftV1 = Math.max(liftV1, b.v() + 1);
			}
		}
		for (final PlannedBlock b : station.blocks()) {
			final int y = b.y() + dy;
			final boolean lift = b.u() >= liftU0 && b.u() <= liftU1 && b.v() >= liftV0 && b.v() <= liftV1;
			if (y >= 1 && b.role() != Role.ESCALATOR && b.role() != Role.LIFT && b.role() != Role.BARRIER && !lift
					|| b.blockId().endsWith(":entrance_pylon")) {
				continue;
			}
			final int u = b.u() + du;
			final int v = b.v() + dv;
			final boolean floor = y == 0 && (b.role() == Role.BUILDING_ROOF || b.role() == Role.FLOOR);
			cells.put(new Cell(u, y, v), floor ? new PlannedBlock(u, y, v, Role.FLOOR, TERMINAL_FLOOR, LocalDir.PLUS_V, "", "")
					: new PlannedBlock(u, y, v, b.role(), b.blockId(), b.front(), b.text(), b.state()));
		}
		for (final Box box : station.headroom()) {
			final Box moved = moved(box);
			railHeadroom.add(moved);
			for (int y = Math.max(0, moved.yMin()); y <= moved.yMax(); y++) {
				for (int v = moved.vMin(); v <= moved.vMax(); v++) {
					for (int u = moved.uMin(); u <= moved.uMax(); u++) {
						keepClear(u, y, v);
					}
				}
			}
		}
		station.trackEnvelopes().forEach(box -> railEnvelopes.add(moved(box)));
		station.supportAreas().forEach(box -> railSupports.add(moved(box)));
		final int length = rail.layout().platformLength();
		for (int v = dv - AirportStation.END; v <= dv + length - 1 + AirportStation.END; v++) {
			for (int u = du; u < du + AirportStation.WIDTH; u++) {
				railHoles.add(new Cell(u, -1, v));
				wellBarriers(u, v);
			}
		}
	}

	/**
	 * Glass barriers round a well in the hall floor at {@code (u, v)}: the street pavilions' walls closed its sides, and they are left
	 * out, so every floor cell beside the opening with nothing standing on it gets a panel on its side over the drop.
	 */
	private void wellBarriers(int u, int v) {
		if (cells.containsKey(new Cell(u, 0, v)) || !super.air.contains(new Cell(u, 0, v))) {
			return;
		}
		for (final LocalDir side : LocalDir.values()) {
			final int su = u - (side == LocalDir.PLUS_U ? 1 : side == LocalDir.MINUS_U ? -1 : 0);
			final int sv = v - (side == LocalDir.PLUS_V ? 1 : side == LocalDir.MINUS_V ? -1 : 0);
			final PlannedBlock floor = cells.get(new Cell(su, 0, sv));
			if (floor != null && floor.role() == Role.FLOOR) {
				put(Role.BARRIER, su, StationGeometry.WALK_Y, sv, side, Planning.GLASS_BARRIER, "edge=true");
			}
		}
	}

	private Box moved(Box box) {
		return new Box(box.uMin() + rail.du(), box.yMin() + rail.dy(), box.vMin() + rail.dv(), box.uMax() + rail.du(), box.yMax() + rail.dy(),
				box.vMax() + rail.dv());
	}

	// ---- the plan -----------------------------------------------------------------------------------------------------

	/** The block rows an airport plan is ordered, surveyed and built in: a band is built whole before the next, so the chunk window moves along the runway once. */
	static final int BAND = 32;

	/** The support layer under every area, in v bands. */
	List<Box> airportSupports() {
		final List<Box> out = new ArrayList<>();
		for (final AirportGeometry.Area area : air.areas()) {
			out.add(new Box(area.u0(), -1, area.v0(), area.u1(), -1, area.v1()));
		}
		return bands(out);
	}

	/** The air above every area that terrain must be cleared from, in v bands. */
	List<Box> airportHeadroom() {
		final List<Box> out = new ArrayList<>();
		for (final AirportGeometry.Area area : air.areas()) {
			out.add(new Box(area.u0(), StationGeometry.WALK_Y, area.v0(), area.u1(), area.clearance(), area.v1()));
		}
		return bands(out);
	}

	/** Boxes cut at every {@link #BAND} rows and ordered band by band, so a survey walks along v once instead of once per box. */
	static List<Box> bands(List<Box> boxes) {
		final List<Box> out = new ArrayList<>();
		for (final Box box : boxes) {
			for (int v = box.vMin(); v <= box.vMax(); ) {
				final int end = Math.min(box.vMax(), Math.floorDiv(v, BAND) * BAND + BAND - 1);
				out.add(new Box(box.uMin(), box.yMin(), v, box.uMax(), box.yMax(), end));
				v = end + 1;
			}
		}
		out.sort(java.util.Comparator.comparingInt((Box x) -> Math.floorDiv(x.vMin(), BAND)).thenComparingInt(Box::uMin).thenComparingInt(Box::yMin));
		return out;
	}
}
