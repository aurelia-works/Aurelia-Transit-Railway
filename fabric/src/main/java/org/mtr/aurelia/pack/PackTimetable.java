package org.mtr.aurelia.pack;

import java.util.List;

/**
 * A depot timetable in MTR's terms: real departure times or one frequency per hour, with a weekday and an optional
 * weekend plan (the same shapes as {@code org.mtr.mod.data.DepotSchedules}). Times are milliseconds after midnight.
 */
public record PackTimetable(String id, String name, String depot, Plan weekday, Plan weekend) {

	public record Plan(boolean realTime, List<Long> departures, List<Integer> frequencies) {

		public Plan {
			departures = List.copyOf(departures);
			frequencies = List.copyOf(frequencies);
		}

		public boolean usesFrequencies() {
			return !frequencies.isEmpty();
		}
	}
}
