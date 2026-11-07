package com.aureliatransit.architecture.station;

import java.util.Locale;

/**
 * Switches for the visible fittings the generator adds, so a player (and the studio's dev panel) can turn any of them off
 * without losing the station. Everything is on by default; old descriptions and saved wrenches plan exactly as before.
 *
 * @param cctv      platform and concourse cameras under the roof
 * @param edgeLines the white line along every platform edge (painted on the edge block's top); off uses plain paving
 * @param height    the platform height the station is built to (see {@link PlatformHeight}); {@link PlatformHeight#AUTO}
 *                  reads it from the operator and look
 */
public record StationFeatures(boolean cctv, boolean edgeLines, PlatformHeight height) {

	public static final StationFeatures DEFAULTS = new StationFeatures(true, true, PlatformHeight.AUTO);

	public StationFeatures {
		if (height == null) {
			height = PlatformHeight.AUTO;
		}
	}

	public StationFeatures withCctv(boolean value) {
		return new StationFeatures(value, edgeLines, height);
	}

	public StationFeatures withEdgeLines(boolean value) {
		return new StationFeatures(cctv, value, height);
	}

	public StationFeatures withHeight(PlatformHeight value) {
		return new StationFeatures(cctv, edgeLines, value == null ? PlatformHeight.AUTO : value);
	}

	/** What a country's railways board at, from its platform height above the rail. */
	public enum PlatformHeight {
		/** About 0.55 m: most continental light rail and older metro platforms. */
		LOW("low", 0.55),
		/** About 0.76 m: the continental main-line standard (Netherlands, Germany, Switzerland). */
		STANDARD("standard", 0.76),
		/** About 0.92 m: British main line and most Japanese platforms. */
		HIGH("high", 0.92),
		/** Not set: the operator or look decides (see {@link #of(StationSpec)}). */
		AUTO("auto", 0.76);

		private final String key;
		private final double metres;

		PlatformHeight(String key, double metres) {
			this.key = key;
			this.metres = metres;
		}

		public String key() {
			return key;
		}

		public String label() {
			return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
		}

		/** Height of the platform top above the rail, in metres. */
		public double metres() {
			return metres;
		}

		/** The height a request names; anything unknown or null is {@link #AUTO}. */
		public static PlatformHeight of(String text) {
			if (text == null) {
				return AUTO;
			}
			for (final PlatformHeight value : values()) {
				if (value.key.equals(text.strip().toLowerCase(Locale.ROOT))) {
					return value;
				}
			}
			return AUTO;
		}

		/**
		 * The height a station is actually built to: the asked one, else the operator's country (British and Japanese
		 * operators board high, Dutch, German, Swiss, French and Italian ones at the continental standard), else the look.
		 */
		public static PlatformHeight of(StationSpec spec) {
			if (spec.features().height() != AUTO) {
				return spec.features().height();
			}
			return switch (spec.operator()) {
				case UNDERGROUND, NATIONAL_RAIL, JR -> HIGH;
				case NS, DB, SBB, GVB, SBAHN -> STANDARD;
				case NONE -> switch (spec.building()) {
					case VICTORIAN, ART_DECO, TOKYO, HONG_KONG -> HIGH;
					case GOTHIC_BRICK, CLINKER, CONCRETE, MODERNIST, BERLIN, BEIJING -> STANDARD;
					default -> switch (spec.style()) {
						case GERMAN_SBAHN -> STANDARD;
						case METRO -> LOW;
						default -> STANDARD;
					};
				};
			};
		}
	}
}
