package org.mtr.aurelia.driving;

/**
 * Geometry for the driver's desk. Kept apart from the drawing code so the layout can be read in one place: the panel,
 * its separators, the dial sweep and the stop-tape mapping. The HUD shrinks to fit a small window, never below 40%.
 */
public final class DriverHudLayout {

	public static final int BASE_WIDTH = 380;
	public static final int BASE_HEIGHT = 106;
	public static final int BASE_HEIGHT_SCORE = 124;
	public static final int TOP_HEIGHT = 90;
	public static final int STOP_Y = 92;
	public static final int SCORE_Y = 110;
	public static final int DIAL_CENTER_X = 52;
	public static final int DIAL_CENTER_Y = 40;
	public static final int DIAL_RADIUS = 28;
	public static final double DIAL_START_DEGREES = 135;
	public static final double DIAL_SWEEP_DEGREES = 270;
	public static final int POWER_X = 112;
	public static final int POWER_TOP = 14;
	public static final int POWER_BOTTOM = 80;
	public static final int STOP_TAPE_LEFT = 210;
	public static final int STOP_TAPE_RIGHT = 366;
	/** Separators after speed, power, doors, AWS and TPWS. */
	public static final int[] SEPARATORS = {104, 170, 234, 288, 330};

	private DriverHudLayout() {
	}

	public record Fit(int x, int y, double scale, int width, int height) {
	}

	public static Fit fit(int windowWidth, int windowHeight, double configScale, boolean score) {
		final int baseHeight = score ? BASE_HEIGHT_SCORE : BASE_HEIGHT;
		// Keep the panel clear of MTR's own 96 px speedometer in the bottom-right corner.
		final int reservedRight = 96 + 16 + 8;
		final int availableWidth = windowWidth - 8 - reservedRight;
		final double fit = Math.min(1, Math.min(availableWidth / (double) BASE_WIDTH, (windowHeight - 80) / (double) baseHeight));
		final double scale = Math.max(0.4, configScale * fit);
		final int width = (int) (BASE_WIDTH * scale);
		final int height = (int) (baseHeight * scale);
		final int x = Math.max(8, Math.min((windowWidth - width) / 2, windowWidth - width - reservedRight));
		return new Fit(x, windowHeight - height - 24, scale, width, height);
	}

	public static double dialDegrees(double speed, double maxSpeed) {
		return DIAL_START_DEGREES + DIAL_SWEEP_DEGREES * clamp(speed / Math.max(1, maxSpeed));
	}

	public static double stopPointerFraction(double distance) {
		return 1 - clamp(distance / 300);
	}

	public static double notchFraction(int level) {
		return clamp((level + 8) / 15D);
	}

	public static int maxDialSpeed(int limit, double manualKilometresPerHour) {
		final double fastest = Math.max(Math.max(limit, manualKilometresPerHour), 120);
		return (int) Math.min(400, Math.ceil(fastest / 20) * 20);
	}

	private static double clamp(double value) {
		return Math.max(0, Math.min(1, value));
	}
}
