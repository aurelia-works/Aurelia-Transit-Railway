package org.mtr.aurelia.driving;

/**
 * TPWS (Train Protection and Warning System) as it behaves in a UK cab.
 *
 * <p>A train stop sensor at a signal at danger, or an overspeed sensor, puts the emergency brake on and lights the
 * TPWS indicator. The brake cannot be released until the train is at a stand, and then only with the TPWS reset
 * button. MTR does not store the physical loop positions, so overspeed is detected against the current speed limit
 * with the same small margin a TPWS overspeed loop allows.</p>
 */
public final class TpwsProtection {

	public enum Trip {
		NONE, OVERSPEED, SPAD
	}

	/** Five per cent over the limit, plus 1 km/h, before an overspeed trip. */
	public static final double OVERSPEED_FRACTION = 0.05;
	public static final double OVERSPEED_MARGIN_METRES_PER_SECOND = 1 / 3.6;
	/** An overspeed sensor is only assumed in the last stretch before a restrictive signal. */
	public static final double OVERSPEED_WINDOW_METRES = 150;
	/** Below this the train counts as at a stand for the reset. */
	public static final double STANDSTILL_METRES_PER_SECOND = 0.1;

	private Trip trip = Trip.NONE;

	/**
	 * @param enabled  whether TPWS is switched on
	 * @param speed    current speed in metres per second
	 * @param limit    current speed limit in metres per second, 0 when unknown
	 * @param spad     the train has just passed a signal at danger
	 */
	public Trip update(boolean enabled, double speed, double limit, boolean spad) {
		if (!enabled) {
			trip = Trip.NONE;
			return trip;
		}

		if (trip == Trip.NONE) {
			if (spad) {
				trip = Trip.SPAD;
			} else if (limit > 0 && speed > limit + Math.max(OVERSPEED_MARGIN_METRES_PER_SECOND, limit * OVERSPEED_FRACTION)) {
				trip = Trip.OVERSPEED;
			}
		}
		return trip;
	}

	/**
	 * The TPWS reset button.
	 *
	 * @return true when the trip is cleared; false while the train is still moving
	 */
	public boolean reset(boolean stopped) {
		if (trip == Trip.NONE) {
			return true;
		}
		if (!stopped) {
			return false;
		}
		trip = Trip.NONE;
		return true;
	}

	public Trip getTrip() {
		return trip;
	}

	public boolean isBraking() {
		return trip != Trip.NONE;
	}
}
