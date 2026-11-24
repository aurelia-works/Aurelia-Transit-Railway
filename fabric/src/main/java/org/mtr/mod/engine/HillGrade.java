package org.mtr.mod.engine;

/**
 * A gentle slow-down for trains climbing a hill: a lower top speed and weaker pulling away, growing with the grade.
 * No train weight and no wheel slip (Jah 2026-11-23: hills only, gentle).
 */
public final class HillGrade {

	/** The most the top speed drops, as a share. Reached at a 5% climb. */
	static final double MAX_SPEED_LOSS = 0.25;
	/** The most the pulling power drops, as a share. Reached at a 5% climb. */
	static final double MAX_ACCELERATION_LOSS = 0.6;

	private HillGrade() {
	}

	/** Rise over length in the direction of travel; 0 on the level or downhill counts as no climb. */
	public static double grade(double startY, double endY, double length) {
		return length <= 0 ? 0 : Math.max(0, (endY - startY) / length);
	}

	public static double speedFactor(double grade) {
		return 1 - Math.min(MAX_SPEED_LOSS, grade * 5);
	}

	public static double accelerationFactor(double grade) {
		return 1 - Math.min(MAX_ACCELERATION_LOSS, grade * 12);
	}
}
