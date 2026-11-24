package org.mtr.mod.engine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Guards the frequency arithmetic: restarting the pattern every hour sent a "one per four hours" train every hour. */
class ClockFaceTest {

	private static final long DAY = ClockFace.MILLIS_PER_HOUR * 24;

	@Test
	void sameMinutesEveryHourAndNoExtraTrains() {
		final long[] everyFifteen = new long[24];
		everyFifteen[7] = 16;
		everyFifteen[8] = 16;
		final long minute = 60_000;
		assertArrayEquals(new long[]{420 * minute, 435 * minute, 450 * minute, 465 * minute, 480 * minute, 495 * minute, 510 * minute, 525 * minute}, ClockFace.departures(everyFifteen, 0, DAY));

		final long[] onePerFourHours = new long[24];
		Arrays.fill(onePerFourHours, 1);
		assertEquals(6, ClockFace.departures(onePerFourHours, 0, DAY).length);
	}

	/** A gap longer than an hour used to fall only on whole gaps after midnight, so most hours it served got no train. */
	@Test
	void everyHourWithTrainsGetsOne() {
		final long hour = ClockFace.MILLIS_PER_HOUR;
		final long[] onceAtSix = new long[24];
		onceAtSix[6] = 1;
		assertArrayEquals(new long[]{6 * hour}, ClockFace.departures(onceAtSix, 0, DAY));

		final long[] everyTwoHours = new long[24];
		everyTwoHours[7] = 2;
		everyTwoHours[8] = 2;
		everyTwoHours[9] = 2;
		assertArrayEquals(new long[]{7 * hour, 9 * hour}, ClockFace.departures(everyTwoHours, 0, DAY));
	}
}
