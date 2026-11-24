package org.mtr.mod.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How long MTR's simulation takes per tick, per dimension. The simulator ticks every 10 ms on its own thread; when a
 * tick takes longer than that, trains fall behind. Kept as a smoothed average and the worst tick of the last few seconds.
 */
public final class EngineLag {

	/** The simulator's tick, in microseconds. */
	public static final long TICK_MICROS = 10_000;
	/** The worst tick is forgotten after this long. */
	private static final long PEAK_MILLIS = 5_000;

	public record Reading(long averageMicros, long worstMicros) {
		public static final Reading NONE = new Reading(0, 0);

		/** Share of the simulator's time budget in use, as a percentage. */
		public int busyPercent() {
			return (int) Math.min(999, Math.round(averageMicros * 100D / TICK_MICROS));
		}
	}

	private static final Map<String, long[]> STATE = new ConcurrentHashMap<>();

	private EngineLag() {
	}

	/** Simulation thread: one tick of this dimension took {@code nanos}. */
	public static void record(String dimension, long nanos) {
		final long micros = nanos / 1000;
		final long now = System.currentTimeMillis();
		// average, worst, when the worst was seen
		final long[] state = STATE.computeIfAbsent(dimension, key -> new long[]{micros, micros, now});
		synchronized (state) {
			state[0] = Math.round(state[0] * 0.95 + micros * 0.05);
			if (micros >= state[1] || now - state[2] > PEAK_MILLIS) {
				state[1] = micros;
				state[2] = now;
			}
		}
	}

	public static Reading reading(String dimension) {
		final long[] state = STATE.get(dimension);
		if (state == null) {
			return Reading.NONE;
		}
		synchronized (state) {
			return new Reading(state[0], state[1]);
		}
	}

	public static void clear() {
		STATE.clear();
	}
}
