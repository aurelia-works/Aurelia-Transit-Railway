package com.aureliatransit.architecture.station;

/**
 * How grand the station building is. A compact building is the two-storey block beside the entrance; a city station adds
 * a storey and wings along the platform; a grand station adds two storeys, wings along almost the whole platform, a pitched
 * roof on masonry looks, a clock tower over the entrance and a taller train shed. A side building grows deeper and taller
 * ({@link StationGeometry#buildingDepth}, {@link StationGeometry#headDepth}); platforms, tracks and footbridges are the
 * same at every scale.
 */
public enum StationScale {
	/** The two-storey building block only. */
	COMPACT("compact", 0, 0),
	/** Three storeys, with two-storey wings along about 70% of the platform, at most {@link #CITY_SPAN} long in all. */
	CITY("city", 1, 70),
	/** Four storeys, wings along about 92% of the platform up to {@link #GRAND_SPAN}, a clock tower and a taller shed. */
	GRAND("grand", 2, 92);

	/**
	 * Longest a city building and its wings run along the platform: 96 blocks, 64 m. A town's station building is a block
	 * with short wings and the platforms run on past it under their canopies; at 70% of a 250-long platform it was a 117 m
	 * wall (Surbiton, Coventry). A grand station's building runs the platform's share, up to {@link #GRAND_SPAN}.
	 */
	public static final int CITY_SPAN = 96;
	/**
	 * Longest a grand building and its wings run along the platform: 230 blocks, 153 m. A grand station's building fronts the
	 * concourse, not the whole platform: Guildford 144 m on 311 m, Arnhem 145 m on 452 m; a train shed runs on past it.
	 * Amersfoort's 355 m building is the outlier this leaves out.
	 */
	public static final int GRAND_SPAN = 230;
	/** Tracks from which a building is grand: the big interchange stations (Arnhem, Amersfoort, Guildford). */
	public static final int GRAND_BUILDING_TRACKS = 6;
	/** Tracks from which a building is city size; most real stations' buildings are small blocks. */
	public static final int CITY_BUILDING_TRACKS = 4;

	private final String label;
	private final int extraStoreys;
	private final int wingPercent;

	StationScale(String label, int extraStoreys, int wingPercent) {
		this.label = label;
		this.extraStoreys = extraStoreys;
		this.wingPercent = wingPercent;
	}

	/** Plain-words name for the summary. */
	public String label() {
		return label;
	}

	/** Storeys added on top of the two-storey building block. */
	public int extraStoreys() {
		return extraStoreys;
	}

	/** The share of the platform length the building and its wings cover, in percent; 0 for no wings. */
	public int wingPercent() {
		return wingPercent;
	}

	/** Blocks along the platform the building and its wings cover on platforms {@code length} long; 0 for no wings. */
	public int wingSpan(int length) {
		final int span = length * wingPercent / 100;
		if (this == CITY) {
			return Math.min(span, CITY_SPAN);
		}
		return this == GRAND ? Math.min(span, GRAND_SPAN) : span;
	}

	/**
	 * The scale a description gets when it names none, from its tracks as at 63 real UK and Dutch stations, counting their platformed tracks
	 * (OpenStreetMap, 2026-11): a grand building from {@link #GRAND_BUILDING_TRACKS}, a city one from {@link #CITY_BUILDING_TRACKS}, else compact.
	 * Platform length says little: a 250 m two-track platform usually has a 20-50 m building (Havant, Godalming, Royston).
	 */
	public static StationScale forSize(boolean building, int tracks) {
		if (!building) {
			return COMPACT;
		}
		if (tracks >= GRAND_BUILDING_TRACKS) {
			return GRAND;
		}
		return tracks >= CITY_BUILDING_TRACKS ? CITY : COMPACT;
	}
}
