package org.mtr.mod.zip;

/**
 * The four Aurelia panel switches. They live in the world's {@code atr_zip} file, not in {@code config/mtr.json}.
 */
public final class ZipSettings {

	public static final ZipSettings DEFAULT = new ZipSettings(true, 12, 0.4, 0.06);
	public static final double SPEED_MIN = 1;
	public static final double SPEED_MAX = 30;
	public static final double BRAKE_MAX = 2;
	public static final double SAG_MAX = 0.25;

	private final boolean enabled;
	private final double speed;
	private final double brake;
	private final double sag;

	public ZipSettings(boolean enabled, double speed, double brake, double sag) {
		this.enabled = enabled;
		this.speed = speed;
		this.brake = brake;
		this.sag = sag;
	}

	public boolean enabled() {
		return enabled;
	}

	public double speed() {
		return speed;
	}

	public double brake() {
		return brake;
	}

	public double sag() {
		return sag;
	}

	public ZipSettings clamped() {
		return new ZipSettings(enabled, clamp(speed, SPEED_MIN, SPEED_MAX), clamp(brake, 0, BRAKE_MAX), clamp(sag, 0, SAG_MAX));
	}

	private static double clamp(double value, double min, double max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}
}
