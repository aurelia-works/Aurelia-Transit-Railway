package com.aureliatransit.architecture.station;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Block letters for runway numbers, gate numbers, kerb words and the terminal's name: a 3 x 5 pixel font of the digits, the
 * capital letters and a space, drawn as cells of a grid. No Minecraft classes.
 */
public final class AirportGlyphs {

	public static final int GLYPH_WIDTH = 3;
	public static final int GLYPH_HEIGHT = 5;

	private static final Map<Character, String> FONT = Map.ofEntries(
			Map.entry('0', "111101101101111"), Map.entry('1', "010110010010111"), Map.entry('2', "111001111100111"),
			Map.entry('3', "111001111001111"), Map.entry('4', "101101111001001"), Map.entry('5', "111100111001111"),
			Map.entry('6', "111100111101111"), Map.entry('7', "111001001001001"), Map.entry('8', "111101111101111"),
			Map.entry('9', "111101111001111"), Map.entry('L', "100100100100111"), Map.entry('R', "110101110110101"),
			Map.entry('C', "111100100100111"),
			Map.entry('A', "111101111101101"), Map.entry('B', "110101110101110"), Map.entry('D', "110101101101110"), Map.entry('E', "111100110100111"), Map.entry('F', "111100110100100"), Map.entry('G', "011100101101011"), Map.entry('H', "101101111101101"), Map.entry('I', "111010010010111"), Map.entry('J', "001001001101010"), Map.entry('K', "101101110101101"), Map.entry('M', "101111111101101"), Map.entry('N', "110101101101101"), Map.entry('O', "010101101101010"), Map.entry('P', "110101110100100"), Map.entry('Q', "010101101110011"), Map.entry('S', "011100010001110"), Map.entry('T', "111010010010010"), Map.entry('U', "101101101101111"), Map.entry('V', "101101101101010"), Map.entry('W', "101101111111101"), Map.entry('X', "101101010101101"), Map.entry('Y', "101101010010010"), Map.entry('Z', "111001010100111"), Map.entry(' ', "000000000000000"));

	private AirportGlyphs() {
	}

	/** True when the font has this character. */
	public static boolean has(char c) {
		return FONT.containsKey(c);
	}

	/**
	 * The lit pixels of {@code text} in one line, as {@code {x, r}} with x from the left and r from the top row, scaled up by
	 * {@code scale} and one pixel apart. Characters the font lacks are left out.
	 */
	public static List<int[]> pixels(String text, int scale) {
		final List<int[]> out = new ArrayList<>();
		int x0 = 0;
		for (final char c : text.toCharArray()) {
			final String rows = FONT.get(c);
			if (rows == null) {
				continue;
			}
			for (int r = 0; r < GLYPH_HEIGHT; r++) {
				for (int x = 0; x < GLYPH_WIDTH; x++) {
					if (rows.charAt(r * GLYPH_WIDTH + x) == '1') {
						for (int dy = 0; dy < scale; dy++) {
							for (int dx = 0; dx < scale; dx++) {
								out.add(new int[]{x0 + x * scale + dx, r * scale + dy});
							}
						}
					}
				}
			}
			x0 += (GLYPH_WIDTH + 1) * scale;
		}
		return out;
	}

	/** Width in cells of {@code text} at {@code scale}, without the gap after the last letter. */
	public static int width(String text, int scale) {
		int n = 0;
		for (final char c : text.toCharArray()) {
			if (FONT.containsKey(c)) {
				n++;
			}
		}
		return n == 0 ? 0 : n * (GLYPH_WIDTH + 1) * scale - scale;
	}

	public static int height(int scale) {
		return GLYPH_HEIGHT * scale;
	}
}
