package org.mtr.mod.resource;

/**
 * The maths behind moving pantographs, kept free of Minecraft types so it can be tested alone.
 * A pantograph part is modelled in its lowered pose; a raise value from 0 (down) to 1 (up) moves it from there.
 * Model space has y pointing down and the arm lying along +z, so a positive angle about the x axis lifts the arm.
 */
public final class PantographMotion {

	private PantographMotion() {
	}

	/**
	 * Smooth start and stop: 0 at 0, 1 at 1, slowest at both ends.
	 */
	public static double ease(double progress) {
		final double clamped = Math.max(0, Math.min(1, progress));
		return clamped * clamped * (3 - 2 * clamped);
	}

	/**
	 * Moves the linear progress toward up (1) or down (0) by {@code deltaSeconds}, taking {@code raiseSeconds} for the whole way.
	 * A raise time of 0 or less jumps straight to the target.
	 */
	public static double advance(double progress, boolean up, double deltaSeconds, double raiseSeconds) {
		final double target = up ? 1 : 0;
		if (raiseSeconds <= 0) {
			return target;
		}
		final double step = Math.max(0, deltaSeconds) / raiseSeconds;
		return up ? Math.min(1, progress + step) : Math.max(0, progress - step);
	}

	/**
	 * Whether a part with this end choice should be up, given which end of the car leads.
	 */
	public static boolean endActive(PantographEnd partEnd, PantographEnd defaultEnd, boolean reversed) {
		final PantographEnd end = partEnd == PantographEnd.DEFAULT ? defaultEnd : partEnd;
		switch (end) {
			case FRONT:
				return !reversed;
			case REAR:
				return reversed;
			default:
				return true;
		}
	}

	/**
	 * Up while on a route, and also whenever the vehicle moves at or above {@code stayUpKilometersPerHour} (0 turns this off).
	 */
	public static boolean shouldBeUp(boolean onRoute, double speedKilometersPerHour, double stayUpKilometersPerHour) {
		return onRoute || stayUpKilometersPerHour > 0 && speedKilometersPerHour >= stayUpKilometersPerHour;
	}

	public static double armAngleDegrees(double raise, double maxAngleDegrees) {
		return raise * maxAngleDegrees;
	}

	/**
	 * How far a head part has risen, in the same units as {@code maxRise} (model pixels).
	 */
	public static double headRise(double raise, double maxRise) {
		return raise * maxRise;
	}

	/**
	 * Rotates the point (y, z) about the x axis through (pivotY, pivotZ) and returns the new {y, z}.
	 */
	public static double[] rotateAboutX(double y, double z, double pivotY, double pivotZ, double angleDegrees) {
		final double radians = Math.toRadians(angleDegrees);
		final double cos = Math.cos(radians);
		final double sin = Math.sin(radians);
		final double dy = y - pivotY;
		final double dz = z - pivotZ;
		return new double[]{pivotY + dy * cos - dz * sin, pivotZ + dy * sin + dz * cos};
	}
}
