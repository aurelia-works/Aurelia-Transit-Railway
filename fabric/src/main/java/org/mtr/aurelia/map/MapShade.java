package org.mtr.aurelia.map;

/**
 * Turns a block's map colour into the pixel the map shows, the way a vanilla map does: a column higher than the one
 * north of it is lit, a lower one is shaded, and water darkens with depth. Pure logic.
 */
public final class MapShade {

	public static final int LIT = 255;
	public static final int FLAT = 220;
	public static final int SHADED = 180;

	private MapShade() {
	}

	/** Brightness from the height step to the column north; an unknown neighbour counts as flat. */
	public static int brightness(int height, int northHeight) {
		if (northHeight == MapRegion.UNKNOWN_HEIGHT || height == northHeight) {
			return FLAT;
		}
		return height > northHeight ? LIT : SHADED;
	}

	/** Water: shallow is lit, deep is shaded, with a chequer so the depth bands read. */
	public static int waterBrightness(int depth, int x, int z) {
		final double d = depth * 0.1 + ((x + z) & 1) * 0.2;
		if (d < 0.5) {
			return LIT;
		}
		return d > 0.9 ? SHADED : FLAT;
	}

	/** An opaque pixel of {@code rgb} at that brightness (0-255). */
	public static int shade(int rgb, int brightness) {
		final int r = (rgb >> 16 & 0xFF) * brightness / 255;
		final int g = (rgb >> 8 & 0xFF) * brightness / 255;
		final int b = (rgb & 0xFF) * brightness / 255;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}
}
