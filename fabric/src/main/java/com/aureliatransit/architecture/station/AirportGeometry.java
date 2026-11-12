package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;

/**
 * Where everything of an airport stands, in the plan's local frame, before any block is chosen: u across the airport from the
 * road to the runways, v along the runways. No Minecraft classes; the planner ({@link PlanAirport}), the checks and the tests all
 * read the same numbers.
 * <p>
 * Across (low u to high u): the landside car park and its access road, the road in front of the terminal, the terminal, the apron
 * with the aircraft stands, the parallel taxiway, open ground, then the runway (and, with two, open ground and the second runway).
 * Along v: a margin at each end, the runway's blast pads, and the runway between them.
 */
public record AirportGeometry(AirportSpec spec, int width, int length, int roadU0, int roadU1, int terminalU0, int terminalU1, int terminalV0,
		int terminalV1, int apronU0, int apronU1, int apronV0, int apronV1, int taxiU0, int taxiU1, int taxiV0, int taxiV1, int runwayV0,
		int runwayV1, List<Integer> runwayU0, List<Integer> standV, List<int[]> links, Dims dims) {

	/**
	 * The sizes of everything but the runway, in blocks. A runway {@link #REAL_WIDTH} wide or more gets {@link #REAL} (an aircraft
	 * stand big enough for an airliner at 1.5 blocks a metre, a taxiway and terminal to match); a narrower one gets {@link #COMPACT},
	 * small enough to look at whole.
	 *
	 * @param end             open margin past each runway end, with room for the blast pad and the taxiway round it
	 * @param blast           paved blast pad past each runway end
	 * @param road            width of the road in front of the terminal
	 * @param terminalDepth   the terminal's depth, landside to airside wall
	 * @param standDepth      how far an aircraft stand reaches from the terminal's airside wall to the taxiway
	 * @param standPitch      rows of apron one gate takes
	 * @param taxi            taxiway width, and the width of every link to a runway
	 * @param gap             open ground between the parallel taxiway and the first runway
	 * @param runwayGap       open ground between two parallel runways
	 * @param terminalEnd     the terminal runs this far past the first and last stand
	 * @param linkInset       taxiway rows kept clear of the runway ends before the first link
	 * @param bridge          length of a boarding bridge from the terminal wall, its docking end included
	 * @param mast            height of a floodlight mast
	 * @param wallTop         the terminal's walls rise to this y
	 * @param entrance        width of the terminal's street entrance (odd)
	 * @param bridgeWidth     clear width of a boarding bridge (odd)
	 * @param thresholdLength length of the threshold stripes
	 * @param numberOffset    rows from the runway end to the painted number
	 * @param edgeLightEvery  rows between runway edge lights
	 * @param taxiLightEvery  cells between taxiway edge lights
	 * @param dash            a runway centreline's painted length
	 * @param dashGap         and its bare length
	 * @param shoulder        light-grey paved strip along each side of a runway, 0 for none
	 */
	public record Dims(int end, int blast, int road, int terminalDepth, int standDepth, int standPitch, int taxi, int gap, int runwayGap,
			int terminalEnd, int linkInset, int bridge, int mast, int wallTop, int entrance, int bridgeWidth, int thresholdLength,
			int numberOffset, int edgeLightEvery, int taxiLightEvery, int dash, int dashGap, int shoulder) {

		/** Rows of runway that no stand may use: both links, their insets and the apron's margin. */
		public int apronClear() {
			return 2 * (linkInset + taxi + 5) + 2 * terminalEnd;
		}

		/** The runway length with room for one gate. */
		public int minLength() {
			return Math.max(AirportSpec.MIN_RUNWAY_LENGTH, apronClear() + standPitch);
		}
	}

	/** A runway this wide or wider has room for a plane's route and a game-scale terminal at 20 blocks (13 m) and up. */
	public static final int REGIONAL_WIDTH = 20;
	/** A runway this wide or wider (30 m at 1.5 blocks a metre) is a full-size airport's, with the real dimensions. */
	public static final int REAL_WIDTH = 30;
	public static final Dims COMPACT = new Dims(24, 6, 6, 18, 30, 28, 9, 14, 30, 8, 2, 8, 12, 6, 5, 3, 8, 11, 4, 6, 8, 6, 0);
	/**
	 * Game-scale dimensions for a runway 20..29 blocks wide: a terminal 40 deep and long enough for the railway station under it, an
	 * apron 44 deep whose siding holds a plane, a 9-block taxiway and 40 blocks of open ground before the runway. Its stands are
	 * 40 rows apart: MTR's A320 spans 34 blocks, so neighbours keep 3 blocks between wingtips (at 28 their wings overlapped). It
	 * sits between {@link #COMPACT} (too narrow for a plane's route) and ICAO's {@link #REAL}.
	 */
	public static final Dims REGIONAL = new Dims(40, 14, 9, 40, 44, 40, 9, 22, 60, 13, 2, 10, 18, 12, 7, 3, 10, 20, 6, 8, 16, 10, 4);
	/**
	 * Real dimensions at 1.5 blocks a metre (ICAO Annex 14 for a code C/E runway): the parallel taxiway's centreline 182.5 m (274
	 * blocks) or more from the runway's, parallel runways at least 210 m (315 blocks) apart, a 23 m (35-block) taxiway, threshold
	 * stripes 30 m long and the runway number 12 m past them. Until 11-07 the taxiway was 14 m wide, the terminal 6.7 m tall, and
	 * the runway's centreline dashes 9 m/7 m instead of ICAO's 30 m/20 m.
	 */
	public static final Dims REAL = new Dims(120, 60, 18, 60, 84, 66, 35, 235, 285, 16, 30, 34, 36, 18, 9, 7, 45, 65, 90, 90, 45, 30, 11);
	/** A runway longer than this gets a link in the middle as well. */
	public static final int MID_LINK_LENGTH = 480;

	/**
	 * Columns the landside band before the forecourt road takes when there is a forecourt: a surface car park with its footways
	 * (u = {@link #SITE}..{@link #SITE} + {@link #LANDSIDE} - 1). Everything from the road right is moved along by it, and the site
	 * margin stays at the edge for the perimeter road and fence.
	 */
	public static final int LANDSIDE = 32;

	/**
	 * Rows of the access road out to the landside edge: a footway, a kerb, a seven-block carriageway and its far kerb.
	 */
	public static final int ACCESS_ROAD = 11;

	/**
	 * The grass margin between the site's edge and the airfield on every side: the perimeter fence, its six-block service road and
	 * the approach lighting live in it, so the runway, taxiway and apron keep their clearances.
	 */
	public static final int SITE = 12;

	/** The dimensions of an airport whose runways are {@code runwayWidth} wide. */
	public static Dims dims(int runwayWidth) {
		return runwayWidth >= REAL_WIDTH ? REAL : runwayWidth >= REGIONAL_WIDTH ? REGIONAL : COMPACT;
	}

	public static AirportGeometry of(AirportSpec spec) {
		if (!spec.present()) {
			throw new IllegalArgumentException("not an airport");
		}
		final Dims d = dims(spec.runwayWidth());
		// no forecourt, no landside: the road is the first column, as before
		final int landside = spec.terminal() && spec.forecourt() ? LANDSIDE : 0;
		final int roadU0 = spec.terminal() ? SITE + landside : -1;
		final int roadU1 = spec.terminal() ? SITE + landside + d.road() - 1 : -1;
		final int terminalU0 = spec.terminal() ? SITE + landside + d.road() : -1;
		final int terminalU1 = spec.terminal() ? SITE + landside + d.road() + d.terminalDepth() - 1 : -1;
		final int apronU0 = spec.terminal() ? terminalU1 + 1 : SITE;
		final int apronU1 = apronU0 + d.standDepth() - 1;
		final int taxiU0 = apronU1 + 1;
		final int taxiU1 = taxiU0 + d.taxi() - 1;
		final List<Integer> runwayU0 = new ArrayList<>();
		int u = taxiU1 + 1 + d.gap();
		for (int i = 0; i < spec.runways(); i++) {
			runwayU0.add(u);
			u += spec.runwayWidth() + d.runwayGap();
		}
		final int width = runwayU0.get(runwayU0.size() - 1) + spec.runwayWidth() + d.shoulder() + SITE;
		final int length = spec.runwayLength() + 2 * (SITE + d.end());
		final int runwayV0 = SITE + d.end();
		final int runwayV1 = SITE + d.end() + spec.runwayLength() - 1;
		final int stands = spec.gates() * d.standPitch();
		final int firstStand = runwayV0 + spec.runwayLength() / 2 - stands / 2;
		final List<Integer> standV = new ArrayList<>();
		for (int i = 0; i < spec.gates(); i++) {
			standV.add(firstStand + i * d.standPitch() + d.standPitch() / 2);
		}
		final int apronV0 = firstStand - d.terminalEnd();
		final int apronV1 = firstStand + stands - 1 + d.terminalEnd();
		// a second runway is reached round the end of the first, so the taxiway runs on to the margin
		final int taxiV0 = spec.runways() > 1 ? SITE : runwayV0 + d.linkInset();
		final int taxiV1 = spec.runways() > 1 ? length - SITE - 1 : runwayV1 - d.linkInset();
		final List<int[]> links = new ArrayList<>();
		links.add(new int[]{runwayV0 + d.linkInset(), runwayV0 + d.linkInset() + d.taxi() - 1});
		links.add(new int[]{runwayV1 - d.linkInset() - d.taxi() + 1, runwayV1 - d.linkInset()});
		if (spec.runwayLength() >= MID_LINK_LENGTH) {
			final int mid = (runwayV0 + runwayV1) / 2;
			links.add(new int[]{mid - d.taxi() / 2, mid + d.taxi() / 2});
		}
		return new AirportGeometry(spec, width, length, roadU0, roadU1, terminalU0, terminalU1, apronV0, apronV1, apronU0, apronU1, apronV0, apronV1,
				taxiU0, taxiU1, taxiV0, taxiV1, runwayV0, runwayV1, List.copyOf(runwayU0), List.copyOf(standV), List.copyOf(links), d);
	}

	/** A runway this long or longer gets rapid exits from its first runway to the taxiway. */
	public static final int RAPID_EXIT_LENGTH = 400;
	/** Rows a 30 degree rapid exit runs along for each column it crosses in the gap (1 / tan 30 degrees). */
	static final double EXIT_ROWS_PER_COLUMN = Math.sqrt(3);

	/**
	 * The first row of each rapid exit, where it leaves the first runway: at a quarter and three quarters of its length, each only where
	 * it joins the taxiway before the far link and clear of the middle one. None on a runway shorter than {@link #RAPID_EXIT_LENGTH}.
	 */
	public List<Integer> rapidExits() {
		if (spec.runwayLength() < RAPID_EXIT_LENGTH) {
			return List.of();
		}
		// an exit runs sqrt(3) rows along for every column across the gap (367 rows at the real 212), so on a shorter runway the
		// second one would join the taxiway past the far link, or past the airport's end: only exits that join before the far link,
		// and clear of the middle link (a runway of about 1,380 to 1,540 brings the first exit onto it)
		final List<Integer> out = new ArrayList<>();
		for (final int start : List.of(runwayV0 + spec.runwayLength() / 4, runwayV0 + 3 * spec.runwayLength() / 4)) {
			final int end = exitCentre(start, taxiU1 + 1);
			boolean clear = end + exitHalf() < links.get(1)[0];
			for (int i = 2; i < links.size(); i++) {
				clear &= end + exitHalf() < links.get(i)[0] || end - exitHalf() > links.get(i)[1];
			}
			if (clear) {
				out.add(start);
			}
		}
		return List.copyOf(out);
	}

	/** The centreline row of a rapid exit that leaves the runway at row {@code start}, in column {@code u} of the gap to the taxiway. */
	public int exitCentre(int start, int u) {
		return start + (int) Math.round((runwayU0.get(0) - 1 - u) * EXIT_ROWS_PER_COLUMN);
	}

	/** Half the width of a rapid exit across the rows: the taxiway's half width. */
	public int exitHalf() {
		return dims.taxi() / 2;
	}

	/**
	 * The control tower's footprint: an 11 by 11 cab on a 5 by 5 shaft (5 by 5 on a compact airport), on the apron's south margin beside
	 * the terminal and west of the taxilane, so no aircraft taxis through it. It is apron only, so it stays out of the runway strip.
	 */
	/**
	 * The control tower's top (its beacon): twice a floodlight mast on a compact field; on the larger ones ten above a mast, so
	 * the shaft stands about twice as tall as it is wide, as at Southend (ap07), instead of a thin pole.
	 */
	public int towerTop() {
		return dims.mast() >= 16 ? dims.mast() + 10 : 2 * dims.mast();
	}

	public Area tower() {
		final int size = dims.mast() >= 16 ? 11 : 5;
		return new Area(Kind.APRON, apronU1 - 8 - size, apronU1 - 9, apronV0, apronV0 + size - 1, 0);
	}

	/** Last column of runway {@code i}. */
	public int runwayU1(int i) {
		return runwayU0.get(i) + spec.runwayWidth() - 1;
	}

	/** The middle column of runway {@code i} (the left one of two when the width is even). */
	public int runwayCentre(int i) {
		return runwayU0.get(i) + (spec.runwayWidth() - 1) / 2;
	}

	/** The middle column of the parallel taxiway. */
	public int taxiCentre() {
		return (taxiU0 + taxiU1) / 2;
	}

	/** Rows of the end-around taxiway at the low end (the high end mirrors it): it carries a second runway's traffic round the first. */
	public int[] endAround() {
		return new int[]{SITE, SITE + dims.taxi() - 1};
	}

	/** The row range of the stub from the end-around taxiway to a second runway's blast pad, at the low end. */
	public int[] stub() {
		return new int[]{endAround()[1] + 1, runwayV0 - dims.blast() - 1};
	}

	/** Mirrors a row to the same place counted from the high end. */
	public int mirror(int v) {
		return length - 1 - v;
	}

	/** True when the landside car park and access road are planned: a terminal with its forecourt. */
	public boolean landside() {
		return spec.terminal() && spec.forecourt();
	}

	/** First and last row of the surface car park, set just past the terminal's ends. */
	public int carParkV0() {
		return Math.max(0, terminalV0 - 8);
	}

	public int carParkV1() {
		return Math.min(length - 1, terminalV1 + 8);
	}

	/** The access road runs along the car park's low-v edge, from the forecourt out to the landside edge at u = 0. */
	public int accessV0() {
		return Math.max(0, carParkV0() - ACCESS_ROAD);
	}

	public int accessV1() {
		return Math.max(accessV0(), carParkV0() - 1);
	}

	/** The row of the access road's centre line: three blocks of carriageway either side of it. */
	public int accessCentre() {
		return Math.min(accessV1() - 1, accessV0() + 6);
	}

	/** Where an aircraft's nose stops on stand: past the boarding bridge, or well in from the apron's edge without a terminal. */
	public int stopU() {
		return spec.terminal() ? terminalU1 + dims.bridge() + 3 : apronU0 + 10;
	}

	/** The column of a runway holding position: 14 cells outside the runway edge at game scale (about 85 m), further on a real-size field. */
	public int holdU() {
		return Math.max(taxiU1 + 2, runwayU0.get(0) - 1 - (dims == REAL ? 90 : 14));
	}

	/** The apron taxilane: a nine-wide one-way lane inside the apron's airside edge, with an entrance at each end. */
	public int laneU0() {
		return apronU1 - 8;
	}

	public int laneU1() {
		return apronU1;
	}

	public int laneCentre() {
		return apronU1 - 4;
	}

	public int laneV0() {
		return apronV0 + 4;
	}

	public int laneV1() {
		return apronV1 - 4;
	}

	/** The fire station: 20 by 14 on the apron's north edge, its engine doors facing the apron. */
	public Area fireStation() {
		return new Area(Kind.APRON, apronU1 - 19, apronU1, apronV1 + 1, apronV1 + 14, 20);
	}

	/**
	 * The hangar: 30 across and 40 deep (40 by 30 in plan) where the apron is deep enough, narrower on a compact field so it stays
	 * between the service spine and the taxiway; north of the apron, with its door on the apron link.
	 */
	public Area hangar() {
		final int width = Math.min(30, dims.standDepth() - 12);
		return new Area(Kind.APRON, apronU0 + 6, apronU0 + 6 + width - 1, apronV1 + 25, apronV1 + 64, 20);
	}

	/** The nine-wide apron link from the apron's north edge to the hangar door. */
	public Area hangarLink() {
		final Area hangar = hangar();
		return new Area(Kind.APRON, hangar.u0() + 5, hangar.u0() + 13, apronV1 + 1, hangar.v0() - 1, OPEN_CLEARANCE);
	}

	/** The bunded fuel farm: 20 by 20, north of the hangar, its gate on the service road. */
	public Area fuelFarm() {
		return new Area(Kind.APRON, apronU0 + 6, apronU0 + 25, apronV1 + 71, apronV1 + 90, 12);
	}

	/** The six-wide service road up the apron's west edge, from the apron to the fuel farm; the fire station, hangar and farm front it. */
	public Area serviceSpine() {
		return new Area(Kind.SERVICE_ROAD, apronU0, apronU0 + 5, apronV1 + 1, fuelFarm().v1(), OPEN_CLEARANCE);
	}

	/** The service road from the west perimeter road to the spine, north of the apron. */
	public Area serviceConnector() {
		return new Area(Kind.SERVICE_ROAD, fenceU0(), serviceSpine().u1(), fuelFarm().v1() - 5, fuelFarm().v1(), OPEN_CLEARANCE);
	}

	/** The perimeter fence's first column: one cell in from the site's edge, with the service road inside it. */
	public int fenceU0() {
		return SITE - 11;
	}

	public int fenceU1() {
		return width - 2;
	}

	public int fenceV0() {
		return SITE - 11;
	}

	public int fenceV1() {
		return length - 2;
	}

	/** The six-wide service road inside each side of the perimeter fence, the fence line on its outer column. */
	public Area perimeterRoad(boolean eastWest, boolean far) {
		final int six = 6;
		return eastWest
				? new Area(Kind.SERVICE_ROAD, far ? fenceU1() - six : fenceU0(), far ? fenceU1() : fenceU0() + six, fenceV0(), fenceV1(), OPEN_CLEARANCE)
				: new Area(Kind.SERVICE_ROAD, fenceU0(), fenceU1(), far ? fenceV1() - six : fenceV0(), far ? fenceV1() : fenceV0() + six, OPEN_CLEARANCE);
	}

	/** The first and last column of the approach light bars at the low end; the high end mirrors them. */
	public int[] approachZone() {
		final int c = runwayCentre(0);
		final int far = runwayV0 - dims.blast() - 2;
		return new int[]{c - 3, c + 3, far - 20, far};
	}

	/** The turning pads at the runway ends: a 15-row widening eight cells past each edge, paved after the runway so it stays black. */
	public Area turnPad(boolean high) {
		final int u0 = runwayU0.get(0) - 8;
		final int u1 = runwayU1(0) + 8;
		return high ? new Area(Kind.TAXIWAY, u0, u1, runwayV1 - 14, runwayV1, OPEN_CLEARANCE)
				: new Area(Kind.TAXIWAY, u0, u1, runwayV0, runwayV0 + 14, OPEN_CLEARANCE);
	}

	/** True when {@code (u, v)} is inside the terminal building's footprint. */
	public boolean inTerminal(int u, int v) {
		return spec.terminal() && u >= terminalU0 && u <= terminalU1 && v >= terminalV0 && v <= terminalV1;
	}

	/** What a paved or built area is for. */
	public enum Kind {
		ROAD, TERMINAL, APRON, TAXIWAY, RUNWAY, BLAST_PAD, SHOULDER, SERVICE_ROAD, GRASS
	}

	/**
	 * A rectangle of the airport: columns u0..u1, rows v0..v1, of one kind, with the height above ground that must be cleared of
	 * terrain for it (the plan's own blocks are exempt).
	 */
	public record Area(Kind kind, int u0, int u1, int v0, int v1, int clearance) {

		public boolean contains(int u, int v) {
			return u >= u0 && u <= u1 && v >= v0 && v <= v1;
		}
	}

	/** Rows of terrain cleared above a runway, taxiway or road. */
	public static final int OPEN_CLEARANCE = 3;


	/** Every area of the airport, in the order the plan paves them; the same list drives the survey's boxes and the checks. */
	public List<Area> areas() {
		final List<Area> out = new ArrayList<>();
		if (spec.terminal()) {
			out.add(new Area(Kind.ROAD, roadU0, roadU1, Math.max(0, apronV0 - 20), Math.min(length - 1, apronV1 + 20), OPEN_CLEARANCE));
			out.add(new Area(Kind.TERMINAL, terminalU0, terminalU1, terminalV0, terminalV1, dims.wallTop() + 2));
			if (landside()) {
				// the car park and the access road out to the site's landside edge
				out.add(new Area(Kind.ROAD, SITE, SITE + LANDSIDE - 1, carParkV0(), carParkV1(), OPEN_CLEARANCE));
				out.add(new Area(Kind.ROAD, 0, SITE + LANDSIDE - 1, accessV0(), accessV1(), OPEN_CLEARANCE));
			}
		}
		out.add(new Area(Kind.APRON, apronU0, apronU1, apronV0, apronV1, dims.mast() + 1));
		if (spec.supportBuildings()) {
			out.add(fireStation());
			if (dims.standDepth() >= 40) {
				out.add(hangar());
				out.add(hangarLink());
				out.add(fuelFarm());
				out.add(serviceSpine());
				out.add(serviceConnector());
			}
		}
		if (spec.perimeter()) {
			out.add(perimeterRoad(true, false));
			out.add(perimeterRoad(true, true));
			out.add(perimeterRoad(false, false));
			out.add(perimeterRoad(false, true));
		}
		out.add(new Area(Kind.TAXIWAY, taxiU0, taxiU1, taxiV0, taxiV1, OPEN_CLEARANCE));
		for (final int[] link : links) {
			out.add(new Area(Kind.TAXIWAY, taxiU1 + 1, runwayU0.get(0) - 1, link[0], link[1], OPEN_CLEARANCE));
		}
		// rapid exits: one column at a time, each a strip of the taxiway's width whose centre moves 30 degrees off the runway
		for (final int start : rapidExits()) {
			for (int u = taxiU1 + 1; u <= runwayU0.get(0) - 1; u++) {
				final int c = exitCentre(start, u);
				out.add(new Area(Kind.TAXIWAY, u, u, c - exitHalf(), c + exitHalf(), OPEN_CLEARANCE));
			}
		}
		if (spec.runways() > 1) {
			final int far = runwayCentre(1) + dims.taxi() / 2;
			final int[] around = endAround();
			final int[] stub = stub();
			out.add(new Area(Kind.TAXIWAY, taxiU0, far, around[0], around[1], OPEN_CLEARANCE));
			out.add(new Area(Kind.TAXIWAY, taxiU0, far, mirror(around[1]), mirror(around[0]), OPEN_CLEARANCE));
			out.add(new Area(Kind.TAXIWAY, runwayCentre(1) - dims.taxi() / 2, far, stub[0], stub[1], OPEN_CLEARANCE));
			out.add(new Area(Kind.TAXIWAY, runwayCentre(1) - dims.taxi() / 2, far, mirror(stub[1]), mirror(stub[0]), OPEN_CLEARANCE));
		}
		for (int i = 0; i < spec.runways(); i++) {
			out.add(new Area(Kind.RUNWAY, runwayU0.get(i), runwayU1(i), runwayV0, runwayV1, OPEN_CLEARANCE));
			if (dims.shoulder() > 0) {
				out.add(new Area(Kind.SHOULDER, runwayU0.get(i) - dims.shoulder(), runwayU0.get(i) - 1, runwayV0, runwayV1, OPEN_CLEARANCE));
				out.add(new Area(Kind.SHOULDER, runwayU1(i) + 1, runwayU1(i) + dims.shoulder(), runwayV0, runwayV1, OPEN_CLEARANCE));
			}
			out.add(new Area(Kind.BLAST_PAD, runwayU0.get(i), runwayU1(i), runwayV0 - dims.blast(), runwayV0 - 1, OPEN_CLEARANCE));
			out.add(new Area(Kind.BLAST_PAD, runwayU0.get(i), runwayU1(i), runwayV1 + 1, runwayV1 + dims.blast(), OPEN_CLEARANCE));
		}
		// the turning pads come after the runway so its black surface wins where they overlap
		if (spec.turnPads()) {
			out.add(turnPad(false));
			out.add(turnPad(true));
		}
		// the grass the square link left outside the curve; before the grass fill, which would otherwise cover it
		if (TaxiwayCurve.radius(this) >= 2) {
			out.addAll(TaxiwayCurve.pavement(this));
		}
		// mown grass between the taxiway and the runway: the tone that makes the pavement read from the air (game scale only;
		// a real-size gap would add hundreds of thousands of grass blocks for no screenshot a player sees)
		if (dims != REAL && taxiU1 + 1 <= runwayU0.get(0) - 1) {
			out.add(new Area(Kind.GRASS, taxiU1 + 1, runwayU0.get(0) - 1, 0, length - 1, OPEN_CLEARANCE));
		}
		if (spec.approachLights()) {
			final int[] low = approachZone();
			out.add(new Area(Kind.GRASS, low[0], low[1], low[2], low[3], OPEN_CLEARANCE));
			out.add(new Area(Kind.GRASS, low[0], low[1], mirror(low[3]), mirror(low[2]), OPEN_CLEARANCE));
		}
		return out;
	}
}
