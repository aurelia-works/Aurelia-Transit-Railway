package org.mtr.aurelia.client;

/**
 * The door decisions the drawing makes: nobody walks through a closed door, a count that arrives before the doors are
 * open waits a moment for them, and people waiting near a doorway step aside while others get off. Plain Java, so it can
 * be tested without a client.
 */
public final class PassengerDoors {

	/** A door this far open is wide enough for a person. */
	public static final double OPEN_ENOUGH = 0.35;
	/** A count that never gets its doors open by then was not a real stop; no walk is drawn. */
	public static final long WAIT_MILLIS = 5000;
	/** How far along the platform a waiting person moves to clear a doorway. */
	public static final double STEP_ASIDE = 0.9;

	private PassengerDoors() {
	}

	public static boolean canCross(double doorValue) {
		return doorValue >= OPEN_ENOUGH;
	}

	public static boolean tooLate(long sinceMillis, long nowMillis) {
		return nowMillis - sinceMillis > WAIT_MILLIS;
	}

	/**
	 * How far aside the waiting crowd is: 0 before anyone steps out, 1 while the exchange is in full swing, and 0 again
	 * once the last person is clear. A short ramp at each end keeps the movement from snapping.
	 */
	public static double stepAsideStrength(double progress) {
		final double in = Math.min(1, Math.max(0, progress) * 4);
		final double out = Math.min(1, Math.max(0, 1 - progress) * 4);
		return Math.min(in, out);
	}
}
