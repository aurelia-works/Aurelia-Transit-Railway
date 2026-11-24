package org.mtr.mod.engine;

/**
 * Departures at the same minutes past every hour. MTR spaces in-game departures from the previous one, carrying the
 * gap over from the hour before, so the minutes drift whenever the frequency changes. Here a gap that divides an hour
 * (every 12, 15, 20, 30 or 60 minutes; MTR allows at most five an hour) puts every departure on a whole number of gaps
 * after midnight, so the minutes are the same every hour. A longer gap is spaced as MTR spaces it, one gap after the
 * last departure or at the start of the hour, so every hour with trains still gets one.
 */
public final class ClockFace {

	public static final long MILLIS_PER_HOUR = 3_600_000;
	/** MTR's frequency is trains per four hours: a gap of this many milliseconds divided by it. */
	public static final long FREQUENCY_SPAN_MILLIS = 14_400_000;

	private ClockFace() {
	}

	/**
	 * @param frequencies     MTR's frequency for each of the 24 hours of the day, 0 for none
	 * @param offsetMillis    real milliseconds at game midnight
	 * @param gameMillisPerDay real milliseconds in one game day
	 * @return the departures in real milliseconds, in order
	 */
	public static long[] departures(long[] frequencies, long offsetMillis, long gameMillisPerDay) {
		final org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList departures = new org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList();
		long last = Long.MIN_VALUE;
		for (int hour = 0; hour < frequencies.length; hour++) {
			final long trains = frequencies[hour];
			if (trains <= 0) {
				continue;
			}
			final long interval = Math.max(1, FREQUENCY_SPAN_MILLIS / trains);
			final long hourStart = MILLIS_PER_HOUR * hour;
			final long first = MILLIS_PER_HOUR % interval == 0 ? (hourStart + interval - 1) / interval * interval : last == Long.MIN_VALUE ? hourStart : Math.max(hourStart, last + interval);
			for (long time = first; time < hourStart + MILLIS_PER_HOUR; time += interval) {
				departures.add(offsetMillis + time * gameMillisPerDay / (MILLIS_PER_HOUR * 24));
				last = time;
			}
		}
		return departures.toLongArray();
	}
}
