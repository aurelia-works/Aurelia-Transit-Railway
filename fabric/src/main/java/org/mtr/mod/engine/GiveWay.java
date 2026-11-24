package org.mtr.mod.engine;

/**
 * One slower train giving way to an express at a junction (see {@link JunctionPriority}). The engine asks about the
 * signal ahead about once a second; this decides each time whether the train treats it as red. A train starts giving
 * way only while it can still stop for the signal, and once it has started it keeps waiting there until the express is
 * through: by the engine's next question it has braked to within stopping distance, and letting it go then would send
 * it through the junction ahead of the express.
 */
public final class GiveWay {

	/** A gap longer than this between two give-ways starts a new wait, so the cap counts one junction at a time. */
	public static final long NEW_WAIT_GAP_MILLIS = 5_000;
	/**
	 * A moving train starts giving way only while at least this share of its normal stopping distance remains before the
	 * signal. The engine asks about a clear signal every tick, so the train walks into the window instead of stepping
	 * over it; this share is what keeps the first ask that sees a claim far enough out to still brake in time.
	 */
	public static final double STOP_COMMIT_FRACTION = 0.5;
	/** Creep this small counts as still standing at the signal; the engine nudges a released train 4 micrometers on. */
	public static final double PASSED_TOLERANCE_METERS = 0.1;

	private int signalIndex = -1;
	private long since;
	private long last;

	/**
	 * The progress of the rail the train's front actually occupies. When a train stops at a signal at the start of
	 * segment {@code i}, its {@code railProgress} equals segment {@code i}'s {@code startDistance}, while the train
	 * itself stands on segment {@code i - 1} behind the signal (matching {@code Vehicle.writeVehiclePositions}).
	 */
	public static double occupiedProgress(double railProgress) {
		return Math.max(0.0, railProgress - 0.01);
	}

	/**
	 * @param signalIndex    the path index of the signal the engine asks about
	 * @param metersToSignal how far the train's front is from that signal
	 * @param stoppingMeters how far the train needs to stop from its current speed
	 * @param claimed        whether an express has claimed the block behind that signal
	 * @return whether the train treats that signal as red
	 */
	public boolean hold(int signalIndex, double metersToSignal, double stoppingMeters, boolean claimed, long now) {
		// the engine asks about every signal from the train's front onwards, so only an answer about the signal the
		// train is waiting at ends the wait
		final boolean waiting = signalIndex == this.signalIndex && now - last <= NEW_WAIT_GAP_MILLIS;
		// too close to stop, or already past it: as on a real railway, a signal is never put back in front of a train
		final boolean past = metersToSignal < -PASSED_TOLERANCE_METERS;
		final boolean tooCloseToStop = !waiting && stoppingMeters > 0 && metersToSignal <= STOP_COMMIT_FRACTION * stoppingMeters;
		if (past || tooCloseToStop) {
			if (waiting) {
				this.signalIndex = -1;
			}
			return false;
		}
		if (!claimed) {
			// a claim can lapse for a single ask (a renewal that jittered, a long server tick). Keep the wait, or the
			// released train runs the junction and can never be stopped there again
			if (waiting) {
				last = now;
			}
			return false;
		}
		if (signalIndex != this.signalIndex || now - last > NEW_WAIT_GAP_MILLIS && stoppingMeters > 0) {
			// a different signal, or a train that left and came back: a new wait, so the cap counts one junction at a
			// time. A long stall at the same signal while stopped is not a new wait, or the cap would never bound it
			since = now;
		}
		last = now;
		this.signalIndex = signalIndex;
		return now - since < JunctionPriority.MAX_YIELD_MILLIS;
	}
}
