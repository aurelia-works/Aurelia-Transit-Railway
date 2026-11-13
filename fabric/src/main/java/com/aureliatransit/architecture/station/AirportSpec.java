package com.aureliatransit.architecture.station;

/**
 * What the player asked for when the words describe an airport ("airport with 2 runways and 6 gates"): a station of a different
 * kind, planned by the same pipeline. Kept in {@link StationOptions#airport()}; {@link #NONE} means a railway station.
 * <p>
 * Sizes are in blocks. Markings and the words' metres use {@link #BLOCKS_PER_METRE} blocks to the metre, but an airport is planned at
 * game scale, not the size of its real counterpart: every block of the plan is held in memory, so new words are capped at
 * {@link #MAX_PLANNED_RUNWAY_LENGTH} (the parser says so when they ask for more). {@link #MAX_RUNWAY_LENGTH} stays higher so a
 * runway saved by an older version still loads.
 *
 * @param runways       parallel runways, {@code 0} for no airport, otherwise {@link #MAX_RUNWAYS} at most
 * @param runwayLength  paved length of each runway
 * @param runwayWidth   width of each runway
 * @param gates         aircraft stands along the apron, each with its boarding bridge when there is a terminal
 * @param terminal      a terminal building behind the apron, with a road in front of it; without one the stands are remote
 * @param rail          a railway station under the terminal ("airport with a train station"), planned by {@link AirportStation}
 * @param heading       the number painted at the runways' low end, 1..18 ("runway 05/23" is 5); the high end has it plus 18. The
 *                      numbers only: the runway lies the way the player faces when building, whatever its number says
 * @param runwayMarkings paint the runway's white markings (threshold stripes, numbers, centreline, touchdown bars); off keeps the
 *                      pavement and the lights, so a plain runway still works
 * @param taxiwayLights  light the taxiways (blue edge lights and green centreline lights)
 * @param apronMarkings  paint the apron's stand lead-ins, stop bars, numbers and equipment-area lines
 * @param controlTower   build the control tower on the apron
 * @param forecourt      build the landside forecourt: pavement, kerbs, canopy, lane paint and street lamps
 * @param taxiwaySigns   red runway-holding-position signs beside the hold bars
 * @param turnPads       paved turning pads at both runway ends
 * @param apronLanes     the apron's marked taxilane loop: two entrances, edge lights, stand signs and equipment boxes
 * @param supportBuildings the fire station, one hangar with its apron link, and the bunded fuel farm
 * @param perimeter      the perimeter service road, its fence and the landside gate
 * @param approachLights approach light bars, PAPI units and the red runway end lights
 * @param landTransit    the taxi rank and bus bays on the forecourt
 */
public record AirportSpec(int runways, int runwayLength, int runwayWidth, int gates, boolean terminal, boolean rail, int heading,
		boolean runwayMarkings, boolean taxiwayLights, boolean apronMarkings, boolean controlTower, boolean forecourt,
		boolean taxiwaySigns, boolean turnPads, boolean apronLanes, boolean supportBuildings, boolean perimeter,
		boolean approachLights, boolean landTransit) {

	/** The runway numbers when the words give none: 09 at the low end, 27 at the high. */
	public static final int DEFAULT_HEADING = 9;
	/** Every feature on: what old saves and old words mean, and what a newly typed airport gets. */
	public static final boolean FEATURE_ON = true;

	public AirportSpec(int runways, int runwayLength, int runwayWidth, int gates, boolean terminal) {
		this(runways, runwayLength, runwayWidth, gates, terminal, false);
	}

	public AirportSpec(int runways, int runwayLength, int runwayWidth, int gates, boolean terminal, boolean rail) {
		this(runways, runwayLength, runwayWidth, gates, terminal, rail, runways == 0 ? 0 : DEFAULT_HEADING);
	}

	public AirportSpec(int runways, int runwayLength, int runwayWidth, int gates, boolean terminal, boolean rail, int heading) {
		this(runways, runwayLength, runwayWidth, gates, terminal, rail, heading, FEATURE_ON, FEATURE_ON, FEATURE_ON, FEATURE_ON, FEATURE_ON,
				FEATURE_ON, FEATURE_ON, FEATURE_ON, FEATURE_ON, FEATURE_ON, FEATURE_ON, FEATURE_ON);
	}

	/** Real airports are built at this many blocks to the metre. */
	public static final double BLOCKS_PER_METRE = 1.5;
	public static final int MAX_RUNWAYS = 2;
	public static final int MIN_RUNWAY_LENGTH = 120;
	/** The longest runway a saved plan may hold (2,000 m at 1.5 blocks a metre); every block of the plan is held in memory. */
	public static final int MAX_RUNWAY_LENGTH = 3000;
	/** The longest runway the words make for a new plan (800 m at 1.5 blocks a metre): a game-scale airport, not a real one. */
	public static final int MAX_PLANNED_RUNWAY_LENGTH = 1200;
	/** A game-scale airport's runway, 480 by 23 blocks: about 320 m by 15 m at {@link #BLOCKS_PER_METRE}. */
	public static final int DEFAULT_RUNWAY_LENGTH = 480;
	public static final int MIN_RUNWAY_WIDTH = 11;
	/** 45 m at 1.5 blocks a metre: ICAO code E, the widest runway a saved plan holds. */
	public static final int MAX_RUNWAY_WIDTH = 68;
	/** ICAO code E's 45 m runway in blocks. */
	public static final int CODE_E_WIDTH = 68;
	/** 15 m at 1.5 blocks a metre: a regional runway's width at game scale. */
	public static final int DEFAULT_RUNWAY_WIDTH = 23;
	public static final int MIN_GATES = 1;
	public static final int MAX_GATES = 12;
	public static final int DEFAULT_GATES = 5;

	public static final AirportSpec NONE = new AirportSpec(0, 0, 0, 0, false);

	public AirportSpec {
		if (runways == 0) {
			if (runwayLength != 0 || runwayWidth != 0 || gates != 0 || terminal || rail || heading != 0) {
				throw new IllegalArgumentException("no runways, but an airport's other settings");
			}
		} else {
			check(runways, 1, MAX_RUNWAYS, "runways");
			check(runwayWidth, MIN_RUNWAY_WIDTH, MAX_RUNWAY_WIDTH, "runwayWidth");
			check(runwayLength, minLength(runwayWidth), MAX_RUNWAY_LENGTH, "runwayLength");
			check(heading, 1, 18, "heading");
			check(gates, MIN_GATES, Math.min(MAX_GATES, maxGates(runwayLength, runwayWidth)), "gates");
		}
	}

	/** A game-scale airport: one runway 480 by 23 blocks, a terminal and five gates (the five that fit on the apron). */
	public static AirportSpec defaults() {
		return new AirportSpec(1, DEFAULT_RUNWAY_LENGTH, DEFAULT_RUNWAY_WIDTH, DEFAULT_GATES, true);
	}

	/** The same airport with or without a railway station under its terminal. */
	public AirportSpec withRail(boolean value) {
		return new AirportSpec(runways, runwayLength, runwayWidth, gates, terminal, value, heading, runwayMarkings, taxiwayLights,
				apronMarkings, controlTower, forecourt, taxiwaySigns, turnPads, apronLanes, supportBuildings, perimeter, approachLights, landTransit);
	}

	/** The same airport with its runways numbered {@code value} (1..18) at the low end and {@code value + 18} at the high end. */
	public AirportSpec withHeading(int value) {
		return new AirportSpec(runways, runwayLength, runwayWidth, gates, terminal, rail, value, runwayMarkings, taxiwayLights,
				apronMarkings, controlTower, forecourt, taxiwaySigns, turnPads, apronLanes, supportBuildings, perimeter, approachLights, landTransit);
	}

	/** The same airport with the runway's painted markings on or off ({@link AirportFeatures}). */
	public AirportSpec withRunwayMarkings(boolean value) {
		return new AirportSpec(runways, runwayLength, runwayWidth, gates, terminal, rail, heading, value, taxiwayLights, apronMarkings,
				controlTower, forecourt, taxiwaySigns, turnPads, apronLanes, supportBuildings, perimeter, approachLights, landTransit);
	}

	/** The two-digit number painted at the runways' low end ("05"), or with {@code high} at the high end ("23"). */
	public String runwayNumber(boolean high) {
		return String.format(java.util.Locale.ROOT, "%02d", high ? heading + 18 : heading);
	}

	/** True for an airport; false for a railway station. */
	public boolean present() {
		return runways > 0;
	}

	/** The most gates that fit along a runway this long and wide: the apron stays clear of the taxiway links at both ends. */
	public static int maxGates(int runwayLength, int runwayWidth) {
		final AirportGeometry.Dims dims = AirportGeometry.dims(runwayWidth);
		return Math.max(0, (runwayLength - dims.apronClear()) / dims.standPitch());
	}

	/** The shortest runway of this width: long enough for one gate. */
	public static int minLength(int runwayWidth) {
		return AirportGeometry.dims(runwayWidth).minLength();
	}

	/** Blocks for a length in metres at the airport scale, rounded. */
	public static int blocks(double metres) {
		return (int) Math.round(metres * BLOCKS_PER_METRE);
	}

	private static void check(int value, int min, int max, String what) {
		if (value < min || value > max) {
			throw new IllegalArgumentException(what + " " + value + " outside " + min + ".." + max);
		}
	}
}
