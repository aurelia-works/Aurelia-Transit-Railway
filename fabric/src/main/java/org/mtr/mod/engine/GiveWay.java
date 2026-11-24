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

	private int signalIndex = -1;
	private long since;
	private long last;

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
		if (!claimed || !waiting && metersToSignal <= stoppingMeters + 1) {
			// too close to stop: as on a real railway, a signal is never put back in front of a train
			if (waiting) {
				this.signalIndex = -1;
			}
			return false;
		}
		if (now - last > NEW_WAIT_GAP_MILLIS) {
			since = now;
		}
		last = now;
		this.signalIndex = signalIndex;
		return now - since < JunctionPriority.MAX_YIELD_MILLIS;
	}
}
