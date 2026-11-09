package com.aureliatransit.architecture.station;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The airport features the dev panel can switch, and the plain words that carry the choice. Words are the airport's saved
 * form (the wrench keeps the description, not a separate options record), so a save from before these switches existed simply has
 * none of the "no ..." phrases and opens with every feature on.
 */
public final class AirportFeatures {

	/** One switch: its stable key (the panel's button and the lang file), and the noun the words use. */
	public enum Feature {
		RUNWAY_MARKINGS("runway_markings", "runway markings"),
		TAXIWAY_LIGHTS("taxiway_lights", "taxiway lights"),
		APRON_MARKINGS("apron_markings", "apron markings"),
		CONTROL_TOWER("control_tower", "control tower"),
		FORECOURT("forecourt", "forecourt"),
		TAXIWAY_SIGNS("taxiway_signs", "taxiway signs"),
		TURN_PADS("turn_pads", "turn pads"),
		APRON_LANES("apron_lanes", "apron lanes"),
		SUPPORT_BUILDINGS("support_buildings", "support buildings"),
		PERIMETER("perimeter_fence", "perimeter fence"),
		APPROACH_LIGHTS("approach_lights", "approach lights"),
		LAND_TRANSIT("landside_transit", "landside transit");

		private final String key;
		private final String noun;

		Feature(String key, String noun) {
			this.key = key;
			this.noun = noun;
		}

		/** The stable id used by the lang file and the wrench's buttons. */
		public String key() {
			return key;
		}

		/** The words that name it: "no runway markings", "without the control tower". */
		public String noun() {
			return noun;
		}

		/** The phrase that turns it off. */
		public String offPhrase() {
			return "no " + noun;
		}

		private Pattern offPattern() {
			return Pattern.compile("(?i)(?:,\\s*)?\\b(?:no|without)\\s+(?:a\\s+|an\\s+|the\\s+|any\\s+)?" + Pattern.quote(noun) + "\\b");
		}
	}

	private AirportFeatures() {
	}

	/** True when the words leave {@code feature} on; words from before the switches existed leave every feature on. */
	public static boolean enabled(String words, Feature feature) {
		return !feature.offPattern().matcher(words == null ? "" : words).find();
	}

	/**
	 * {@code words} with {@code feature} switched on or off. Off appends its "no ..." clause; on removes every such clause the
	 * player wrote, including "without the ...", then tidies the commas. Empty words become "airport", which is what the wrench
	 * plans anyway.
	 */
	public static String set(String words, Feature feature, boolean on) {
		String text = feature.offPattern().matcher(words == null ? "" : words).replaceAll("");
		text = text.replaceAll("\\s{2,}", " ").replaceAll("\\s+,", ",").replaceAll("(,\\s*){2,}", ", ").replaceAll("^\\s*,+|,\\s*$", "").trim();
		if (!on) {
			text = text.isEmpty() ? feature.offPhrase() : text + ", " + feature.offPhrase();
		}
		return text.isEmpty() ? "airport" : text;
	}

	/** The lower-case key of a feature, for the wrench's translation key. */
	public static String langKey(Feature feature) {
		return "screen.aurelia_transit_architecture.airport_wrench.feature." + feature.key().toLowerCase(Locale.ROOT);
	}
}
