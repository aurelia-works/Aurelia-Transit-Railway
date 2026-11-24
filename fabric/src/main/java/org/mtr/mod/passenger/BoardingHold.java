package org.mtr.mod.passenger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trains that still have people getting on or off, per dimension, as of the passenger network's last second. The
 * engine keeps their doors open a little longer (see {@code BoardingDwellMixin}).
 */
public final class BoardingHold {

	/** The most a stop is stretched for boarding, in milliseconds. */
	public static final long MAX_HOLD_MILLIS = 30_000;

	private static final Map<String, Set<Long>> STILL_BOARDING = new ConcurrentHashMap<>();

	private BoardingHold() {
	}

	static void set(String dimension, Set<Long> vehicleIds) {
		STILL_BOARDING.put(dimension, Set.copyOf(vehicleIds));
	}

	public static boolean stillBoarding(long vehicleId) {
		for (final Set<Long> vehicleIds : STILL_BOARDING.values()) {
			if (vehicleIds.contains(vehicleId)) {
				return true;
			}
		}
		return false;
	}

	public static void clear() {
		STILL_BOARDING.clear();
	}
}
