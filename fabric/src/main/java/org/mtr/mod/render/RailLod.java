package org.mtr.mod.render;

/**
 * Detail level of one piece of a 3D rail by its distance from the camera.
 * Ported from Aurelia Transit Utilities 1.4.0 (RailLod).
 */
public final class RailLod {

	public enum Tier {FULL, SIMPLE, FAR}

	public static final double TALL_HEIGHT = 0.5;
	public static final double FAR_SPACING = 4;
	public static final double TALL_SPACING = 2;

	private RailLod() {
	}

	public static Tier tier(double distanceBlocks, int simpleChunks, int farChunks) {
		if (farChunks > 0 && distanceBlocks > farChunks * 16.0) {
			return Tier.FAR;
		}
		if (simpleChunks > 0 && distanceBlocks > simpleChunks * 16.0) {
			return Tier.SIMPLE;
		}
		return Tier.FULL;
	}

	public static boolean isTall(double modelHeight) {
		return modelHeight > TALL_HEIGHT;
	}

	/** How many consecutive pieces one far-tier piece stands for; at least 1. */
	public static int farStep(double repeatInterval, boolean tall) {
		if (!(repeatInterval > 0)) {
			return 1;
		}
		return Math.max(1, (int) Math.round((tall ? TALL_SPACING : FAR_SPACING) / repeatInterval));
	}

	/** Index of a piece along its rail; stable from frame to frame so the same pieces stay drawn as the camera turns. */
	public static long pieceIndex(double distanceFromRailStart, double repeatInterval) {
		if (!(repeatInterval > 0)) {
			return 0;
		}
		return (long) Math.floor(distanceFromRailStart / repeatInterval + 1E-3);
	}

	/** In the far tier only every {@code step}-th piece is drawn, covering the next {@code step} pieces. */
	public static boolean drawsFarPiece(long pieceIndex, int step) {
		return step <= 1 || Math.floorMod(pieceIndex, step) == 0;
	}
}
