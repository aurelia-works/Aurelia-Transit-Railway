package com.aureliatransit.architecture.transit;

import com.aureliatransit.architecture.text.Tr;

/**
 * Simulated passengers at a station. The count is a function of the station id and the minute of the day, so every
 * client and the map agree, and a tick does not keep a crowd of its own. Two peaks, 08:00 and 18:00 in the epoch day
 * (not the local timezone), and a small per-station offset so neighbouring stations are not busy together.
 */
public final class Footfall {

	/** At or above this, platform displays say the station is busy. */
	public static final int BUSY_AT = 640;
	/** 08:00 in the epoch day. Station Studio shows the count at this minute. */
	public static final int MORNING_PEAK_MINUTE = 8 * 60;
	public static final String BUSY_KEY = "gui.aurelia_transit_architecture.busy_try_next";

	private static final int DAY = 24 * 60;
	private static final int PEAK = 520;
	private static final int HALF = 120;
	private static final int BASE = 60;

	private Footfall() {
	}

	/** A stable id for a station that does not have one yet, such as a design open in Station Studio. */
	public static long key(String name) {
		long hash = 0xcbf29ce484222325L;
		final String text = name == null ? "" : name;
		for (int i = 0; i < text.length(); i++) {
			hash ^= text.charAt(i);
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	public static int passengers(long stationId, long epochMillis) {
		final int minute = (int) Math.floorMod(epochMillis / 60_000L, (long) DAY);
		return passengersAtMinute(stationId, minute);
	}

	public static int passengersAtMinute(long stationId, int minuteOfDay) {
		final int minute = Math.floorMod(minuteOfDay, DAY);
		final int personality = (int) Long.remainderUnsigned(mix(stationId), 160);
		return BASE + personality + peak(minute, MORNING_PEAK_MINUTE) + peak(minute, 18 * 60);
	}

	public static boolean busy(int passengers) {
		return passengers >= BUSY_AT;
	}

	/** The platform-display line, or null when the station is not busy. The text is the translation of {@link #BUSY_KEY}. */
	public static String notice(long stationId, long epochMillis) {
		return busy(passengers(stationId, epochMillis)) ? Tr.raw(BUSY_KEY) : null;
	}

	private static int peak(int minute, int center) {
		int delta = Math.abs(minute - center);
		if (delta > DAY / 2) {
			delta = DAY - delta;
		}
		if (delta >= HALF) {
			return 0;
		}
		return (HALF - delta) * PEAK / HALF;
	}

	private static long mix(long value) {
		long mixed = value;
		mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
		mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
		return mixed ^ (mixed >>> 31);
	}
}
