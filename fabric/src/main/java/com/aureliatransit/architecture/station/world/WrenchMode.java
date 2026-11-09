package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.StationDescriptionParser;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * What the one Aurelia wrench builds. Its screen shows one mode at a time and the mode travels on the stack under a new NBT
 * key, so old {@code station_wrench} and {@code airport_wrench} stacks keep loading and open in their own mode.
 *
 * <p>Station and Airport plan through the parser; Bridge is a front end for the {@code /atr_bridge} tools; Earthworks is a
 * placeholder until that phase lands.
 */
public enum WrenchMode {
	STATION("station"),
	AIRPORT("airport"),
	BRIDGE("bridge"),
	EARTHWORKS("earthworks");

	private static final String KEY = "screen.aurelia_transit_architecture.wrench.mode.";

	private final String key;

	WrenchMode(String key) {
		this.key = key;
	}

	public String key() {
		return key;
	}

	public Text label() {
		return Text.translatable(KEY + key);
	}

	public Text hint() {
		return Text.translatable(KEY + key + ".hint");
	}

	/** The next mode, forwards or backwards, wrapping at either end. */
	public WrenchMode next(int step) {
		final WrenchMode[] modes = values();
		return modes[Math.floorMod(ordinal() + step, modes.length)];
	}

	/** The words this mode plans from. Airport makes sure the parser sees an airport even when the words do not say so. */
	public String words(String description) {
		return this == AIRPORT ? StationDescriptionParser.airportWords(description) : description;
	}

	public static WrenchMode byName(String name, WrenchMode fallback) {
		if (name == null || name.isEmpty()) {
			return fallback;
		}
		try {
			return valueOf(name.strip().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
