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
	/** Spacing of the flat quads drawn past Minecraft's render distance. */
	public static final double FAR_VIEW_INTERVAL = 8;
	/** How far the far pass reaches back into the normal render distance, so detail levels do not pop or gap. */
	public static final double FAR_VIEW_OVERLAP = 16;

	private RailLod() {
	}

	public static Tier tier(double distanceBlocks, int simpleChunks, int farChunks) {
		return tier(distanceBlocks, simpleChunks, farChunks, false);
	}

	/**
	 * @param cheapShadowPass a shader pack's shadow pass with cheap shadows on: rails are drawn in the far tier at any
	 *                        distance (a shadow needs no rail detail)
	 */
	public static Tier tier(double distanceBlocks, int simpleChunks, int farChunks, boolean cheapShadowPass) {
		if (cheapShadowPass) {
			return Tier.FAR;
		}
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

	/**
	 * A piece past Minecraft's render distance but still inside the player's rail view distance. These are drawn as flat
	 * quads instead of full rail models. 0 or a setting at or below Minecraft's render distance turns this off.
	 */
	public static boolean inFarView(double distanceBlocks, int minecraftChunks, int railViewChunks, boolean farDetail) {
		return inFarView(distanceBlocks, minecraftChunks, railViewChunks, farDetail, 0);
	}

	/**
	 * @param overlapBlocks how far the far pass reaches back into Minecraft's render distance. The far quads then sit
	 *                      under the normal rail for a short stretch, so the switch to the cheap shape does not pop and
	 *                      the boundary has no gap.
	 */
	public static boolean inFarView(double distanceBlocks, int minecraftChunks, int railViewChunks, boolean farDetail, double overlapBlocks) {
		return farDetail && railViewChunks > minecraftChunks && distanceBlocks > Math.max(0, minecraftChunks * 16.0 - overlapBlocks) && distanceBlocks <= railViewChunks * 16.0;
	}

	/**
	 * Horizontal distance from a point to an axis-aligned box, 0 when the point is inside it. Used to skip rails whose
	 * whole bounding box is outside the far view without walking their pieces.
	 */
	public static double boxDistance(double x, double z, double minX, double minZ, double maxX, double maxZ) {
		final double dx = Math.max(0, Math.max(minX - x, x - maxX));
		final double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
		return Math.hypot(dx, dz);
	}

	/** Horizontal distance from a point to the farthest corner of an axis-aligned box. */
	public static double farthestBoxDistance(double x, double z, double minX, double minZ, double maxX, double maxZ) {
		final double dx = Math.max(Math.abs(x - minX), Math.abs(x - maxX));
		final double dz = Math.max(Math.abs(z - minZ), Math.abs(z - maxZ));
		return Math.hypot(dx, dz);
	}
}
