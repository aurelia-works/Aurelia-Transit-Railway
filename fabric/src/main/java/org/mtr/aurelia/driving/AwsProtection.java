package org.mtr.aurelia.driving;

/**
 * AWS (Automatic Warning System) as it behaves in a UK cab.
 *
 * <p>Passing a signal at caution or danger sounds the AWS horn; the driver has a few seconds to press the AWS
 * cancellation button. If it is not pressed, a full service brake application follows. Passing a clear signal rings
 * the bell instead and needs no action. The brake is released by the acknowledgement, and the driver then takes the
 * brake handle back.</p>
 *
 * <p>This class is only the warning and brake timing. Sounds, the sunflower indicator and the actual brake notches
 * live in the client. It takes explicit milliseconds so the behaviour is testable without waiting.</p>
 */
public final class AwsProtection {

	public static final long DEFAULT_ACK_MILLIS = 3_000;

	private boolean warning;
	private boolean brakeApplied;
	private long warningStartedMillis;

	/** A restrictive signal was passed. Re-triggering while a warning is active keeps the original timer. */
	public void trigger(long nowMillis) {
		if (!warning) {
			warning = true;
			brakeApplied = false;
			warningStartedMillis = nowMillis;
		}
	}

	/** The AWS cancellation button. Clears the warning and any brake it applied. */
	public void acknowledge() {
		warning = false;
		brakeApplied = false;
	}

	/** Call once per tick. Applies the service brake once the acknowledgement window has passed. */
	public void tick(long nowMillis, long acknowledgeMillis) {
		if (warning && !brakeApplied && nowMillis - warningStartedMillis >= acknowledgeMillis) {
			brakeApplied = true;
		}
	}

	/** No driver, train left, or AWS switched off: forget everything without a brake release delay. */
	public void clear() {
		warning = false;
		brakeApplied = false;
	}

	public boolean isWarning() {
		return warning;
	}

	public boolean isBrakeApplied() {
		return brakeApplied;
	}

	public long warningStartedMillis() {
		return warningStartedMillis;
	}
}
