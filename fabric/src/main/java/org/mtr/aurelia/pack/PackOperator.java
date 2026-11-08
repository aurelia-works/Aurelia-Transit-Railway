package org.mtr.aurelia.pack;

/**
 * A railway operator a pack may name. Colours are 0xRRGGBB; {@link #primaryHex()} and friends return the form used in
 * JSON and on signs.
 */
public record PackOperator(String id, String name, String shortCode, int primaryColour, int secondaryColour, int textColour) {

	public String primaryHex() {
		return String.format("#%06X", primaryColour);
	}

	public String secondaryHex() {
		return String.format("#%06X", secondaryColour);
	}

	public String textHex() {
		return String.format("#%06X", textColour);
	}
}
