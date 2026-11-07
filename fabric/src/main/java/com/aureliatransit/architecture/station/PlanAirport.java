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
 * Pavement is {@link Role#PLATFORM_SURFACE} (a flat walkable floor), lights are {@link Role#LIGHT_PANEL}, the terminal and bridges
 * use the building roles; the block ids are chosen here, not by the style palette. Everything on the airfield is flush with the
 * ground (y = 0); only the terminal, the bridges and the floodlight masts stand up.
 * One layer of {@link Planning}: the layers extend one another in order, from {@code PlanState} down to {@code Planning}, and
 * share one set of planned cells.
 */
abstract class PlanAirport extends PlanFitOut {

	static final String RUNWAY_SURFACE = "minecraft:black_concrete";
	static final String BLAST_SURFACE = "minecraft:polished_andesite";
	static final String TAXI_SURFACE = "minecraft:gray_concrete";
	static final String APRON_SURFACE = "minecraft:light_gray_concrete";
	/** A joint between two apron slabs: darker than the slabs, so the apron reads as concrete panels. */
	static final String APRON_JOINT = "minecraft:gray_concrete";
	/** The landside forecourt's footways and kerbs. */
	static final String SIDEWALK = "minecraft:smooth_stone";
	static final String KERB = "minecraft:stone_brick_slab";
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
	/** Taxiway edge lights: blue. */
	static final String TAXI_LIGHT = "aurelia_transit_architecture:taxiway_edge_light";
	/** The control tower: a white shaft, a light grey cab floor and roof, and glass walls round the cab. */
	static final String TOWER_SHAFT = "minecraft:white_concrete";
	static final String TOWER_FLOOR = "minecraft:light_gray_concrete";
	static final String TOWER_GLASS = "minecraft:glass";
	/** The tower's beacon on the cab roof. */
	static final String TOWER_BEACON = "minecraft:sea_lantern";
	static final String MAST_LAMP = "aurelia_transit_architecture:apron_floodlight";
	static final String CHECK_IN_DESK = "aurelia_transit_architecture:check_in_desk";
	static final String DEPARTURES_BOARD = "aurelia_transit_architecture:departures_board";
	static final String JET_BRIDGE = "aurelia_transit_architecture:jet_bridge";
	/** The metal band between a jet bridge's telescoping sections, and its drive column. */
	static final String BRIDGE_BAND = "minecraft:light_gray_concrete";
	static final String BRIDGE_COLUMN = "minecraft:iron_block";
	static final String BRIDGE_WHEEL = "minecraft:black_concrete";
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
		if (spec.airport().apronMarkings()) {
			standMarks();
			equipmentLines();
		}
		gatePlatforms();
		for (int i = 0; i < spec.airport().runways(); i++) {
			runwayMarks(i);
		}
		taxiMarks();
		if (spec.airport().terminal() && spec.airport().forecourt()) {
			airportForecourt();
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
		if (air.dims() != AirportGeometry.REAL) {
			return;
		}
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
				// a link is an ordinary taxiway: yellow centre and edges, blue edge lights
				paint(u, middle, PAINT_YELLOW);
				if (spec.airport().taxiwayLights() && u % air.dims().taxiLightEvery() == 0) {
					light(u, link[0], TAXI_LIGHT);
					light(u, link[1], TAXI_LIGHT);
				}
				paint(u, link[0], PAINT_YELLOW);
				paint(u, link[1], PAINT_YELLOW);
			}
			// the runway holding position: two solid and two dashed yellow lines, solid side to the runway
			holdingPositionV(gapTo - 1, link[0], link[1]);
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

	// ---- apron and stands ---------------------------------------------------------------------------------------------

	/** Where an aircraft's nose stops on stand: past the boarding bridge, or well in from the apron's edge without a terminal. */
	int stopU() {
		return air.stopU();
	}

	/** A yellow lead-in line to the stop bar on every stand, the bar, the stand's number, and white lines between stands. */
	void standMarks() {
		final int stop = stopU();
		final int first = air.standV().get(0) - air.dims().standPitch() / 2;
		for (int i = 0; i <= spec.airport().gates(); i++) {
			final int v = first + i * air.dims().standPitch();
			for (int u = stop + 1; u <= air.apronU1(); u++) {
				paint(u, v, PAINT_WHITE);
			}
		}
		for (int i = 0; i < air.standV().size(); i++) {
			final int c = air.standV().get(i);
			for (int u = stop; u <= air.taxiCentre(); u++) {
				paint(u, c, PAINT_YELLOW);
			}
			final int bar = air.dims().bridgeWidth() / 2 + 1;
			for (int v = c - bar; v <= c + bar; v++) {
				paint(stop, v, PAINT_WHITE);
			}
			// the number, upright for someone on the terminal side of the stand facing out over the apron: up is +u, the near edge is the terminal side
			final int scale = numberScale() > 1 ? 2 : 1;
			paintText(List.of(Integer.toString(i + 1)), scale, stop + 4, c + air.dims().standPitch() / 4, 1, 0, PAINT_WHITE);
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
		final int top = 2 * air.dims().mast();
		final int shaft = (t.u1() - t.u0() + 1) >= 9 ? 2 : 1;
		for (int y = StationGeometry.WALK_Y; y <= top - 7; y++) {
			for (int u = cu - shaft; u <= cu + shaft; u++) {
				for (int v = cv - shaft; v <= cv + shaft; v++) {
					if (u == cu - shaft && v == cv && y <= StationGeometry.WALK_Y + 2) {
						// the doorway, facing the terminal
						put(Role.WINDOW, u, y, v, LocalDir.MINUS_U, TOWER_GLASS);
						continue;
					}
					final boolean corner = (u == cu - shaft || u == cu + shaft) && (v == cv - shaft || v == cv + shaft);
					final boolean band = u == cu - shaft && v == cv && Math.floorMod(y - StationGeometry.WALK_Y, 12) == 0;
					put(Role.WALL, u, y, v, LocalDir.PLUS_V, corner || band ? TOWER_FLOOR : TOWER_SHAFT);
				}
			}
		}
		for (int u = t.u0(); u <= t.u1(); u++) {
			for (int v = t.v0(); v <= t.v1(); v++) {
				put(Role.BUILDING_ROOF, u, top - 6, v, LocalDir.PLUS_V, TOWER_FLOOR);
			}
		}
		// the glazed cab: a wide glass drum, its rings 7, 9 and 11 wide and five blocks tall
		final int maxHalf = (t.u1() - t.u0()) / 2;
		final int base = maxHalf >= 5 ? 3 : 2;
		for (int y = top - 5; y <= top - 1; y++) {
			final int ring = y >= top - 1 ? 2 : y >= top - 3 ? 1 : 0;
			final int half = Math.min(base + ring, maxHalf);
			for (int u = cu - half; u <= cu + half; u++) {
				for (int v = cv - half; v <= cv + half; v++) {
					final boolean edge = u == cu - half || u == cu + half || v == cv - half || v == cv + half;
					final boolean corner = (u == cu - half || u == cu + half) && (v == cv - half || v == cv + half);
					if (edge) {
						put(corner ? Role.WALL : Role.WINDOW, u, y, v, LocalDir.PLUS_V, corner ? TOWER_SHAFT : TOWER_GLASS);
					} else if (y == top - 1) {
						// the cab's ceiling, with a short pole for the beacon
						if (u == cu && v == cv) {
							put(Role.FIXTURE, u, y, v, LocalDir.PLUS_V, LAMP_POLE);
						} else {
							put(Role.BUILDING_ROOF, u, y, v, LocalDir.PLUS_V, TOWER_FLOOR);
						}
					}
				}
			}
		}
		put(Role.LAMP, cu, top, cv, LocalDir.PLUS_V, TOWER_BEACON);
		for (int u = t.u0(); u <= t.u1(); u++) {
			for (int v = t.v0(); v <= t.v1(); v++) {
				if (u != cu || v != cv) {
					put(Role.BUILDING_ROOF, u, top, v, LocalDir.PLUS_V, TOWER_FLOOR);
				}
			}
		}
	}

	/** A windsock on a pole at the apron's far corner, past the control tower, its cone blowing along +v. */
	void windsock() {
		final AirportGeometry.Area t = air.tower();
		final int u = t.u1();
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
				// airside: a full-height glass curtain wall, a mullion every five rows
				final boolean pilaster = Math.floorMod(v - v0, 5) == 0 || v == v1;
				put(pilaster ? Role.WALL : Role.WINDOW, u1, y, v, LocalDir.PLUS_U, pilaster ? b.wall() : b.window());
				// landside: a plinth, a band of glass, a fascia
				final boolean glass = y >= 2 && y <= top - 2 && Math.abs(v - door) > half;
				final boolean lintel = y == StationGeometry.WALK_Y + StationGeometry.DOOR_HEIGHT && Math.abs(v - door) <= half;
				put(glass ? Role.WINDOW : Role.WALL, u0, y, v, LocalDir.MINUS_U, glass ? b.window() : lintel ? b.trim() : b.wall());
			}
			for (int u = u0 + 1; u < u1; u++) {
				put(Role.WALL, u, y, v0, LocalDir.MINUS_V, b.wall());
				put(Role.WALL, u, y, v1, LocalDir.PLUS_V, b.wall());
			}
		}
		// the roof's overhang: one row over the forecourt, the apron and both gable ends, so the glass wall is shaded
		for (int v = v0 - 1; v <= v1 + 1; v++) {
			put(Role.BUILDING_ROOF, u0 - 1, terminalRoofY(), v, LocalDir.MINUS_U, b.roof());
			put(Role.BUILDING_ROOF, u1 + 1, terminalRoofY(), v, LocalDir.PLUS_U, b.roof());
		}
		for (int u = u0; u <= u1; u++) {
			put(Role.BUILDING_ROOF, u, terminalRoofY(), v0 - 1, LocalDir.MINUS_V, b.roof());
			put(Role.BUILDING_ROOF, u, terminalRoofY(), v1 + 1, LocalDir.PLUS_V, b.roof());
		}
		// the name, over the entrance, fixed flat to the landside wall
		if (!spec.name().isEmpty()) {
			for (int v = door - half - 3; v <= door + half + 3; v++) {
				putText(Role.FIXTURE, u0 - 1, top - 1, v, LocalDir.MINUS_U, LETTERING, spec.name());
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
					// the cab at the aircraft door: the jet bridge block, glass either side, a metal roof
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
		// the centre line: 4 m of paint and 8 m of gap
		final int centre = (u0 + u1) / 2;
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
		// the canopy over the door, on two columns at the kerb, with a fascia over the entrance
		final int from = u1 - 3;
		final int to = u1;
		for (final int v : new int[]{door - half - 4, door + half + 4}) {
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + 5; y++) {
				put(Role.COLUMN, u1 - 1, y, v, LocalDir.MINUS_U);
			}
			put(Role.COLUMN_CAP, u1 - 1, StationGeometry.WALK_Y + 5, v, LocalDir.MINUS_U);
		}
		for (int u = from; u <= to; u++) {
			for (int v = door - half - 4; v <= door + half + 4; v++) {
				put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 6, v, LocalDir.PLUS_V, b.window());
			}
		}
		// the canopy's metal fascia: a frame round the glass, heaviest at the kerb edge
		for (int v = door - half - 5; v <= door + half + 5; v++) {
			put(Role.BUILDING_ROOF, from, StationGeometry.WALK_Y + 7, v, LocalDir.MINUS_U, b.trim());
			put(Role.BUILDING_ROOF, to, StationGeometry.WALK_Y + 7, v, LocalDir.PLUS_U, b.trim());
		}
		for (int u = from; u <= to; u++) {
			put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 7, door - half - 5, LocalDir.MINUS_V, b.trim());
			put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 7, door + half + 5, LocalDir.PLUS_V, b.trim());
		}
		for (int u = from; u <= to; u += 4) {
			for (int v = door - half - 4; v <= door + half + 4; v++) {
				put(Role.BUILDING_ROOF, u, StationGeometry.WALK_Y + 7, v, LocalDir.PLUS_V, b.trim());
			}
		}
		// street lamps down the outer footway, clear of the canopy
		for (int v = v0 + 12; v <= v1 - 8; v += 24) {
			if (Math.abs(v - door) < half + 8) {
				continue;
			}
			for (int y = StationGeometry.WALK_Y; y < StationGeometry.WALK_Y + 6; y++) {
				put(Role.FIXTURE, u0 + 1, y, v, LocalDir.PLUS_U, LAMP_POLE);
			}
			put(Role.LAMP, u0 + 1, StationGeometry.WALK_Y + 6, v, LocalDir.PLUS_U, TOWER_BEACON);
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
