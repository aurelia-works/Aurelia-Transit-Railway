package com.aureliatransit.architecture.bridge;

/**
 * The shapes a wrench bridge is planned in: a rail the planner samples ({@link Path}, {@link Sample}) and the blocks it
 * places ({@link Placement}, with the quarter turn each block state is rotated by).
 */
public final class BridgeStampPlan {

	/** A single build larger than this is a mistake; it also keeps undo logs bounded. */
	public static final int MAX_PLACEMENTS = 200_000;

	public record Placement(int x, int y, int z, int palette, int quarterTurns) {
	}

	/** One point on the rail, with its horizontal tangent. */
	public record Sample(double x, double y, double z, double dx, double dz) {
	}

	/** A rail the plan can sample; {@code at} is clamped to 0..length. */
	public interface Path {

		double length();

		Sample at(double distance);
	}

	private BridgeStampPlan() {
	}
}
