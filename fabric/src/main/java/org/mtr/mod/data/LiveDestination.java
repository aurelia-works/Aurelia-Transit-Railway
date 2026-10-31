package org.mtr.mod.data;

import org.mtr.core.data.Route;

import javax.annotation.Nullable;

/**
 * MTR copies each stop's destination into the timetable when a depot generates its paths, so a destination edited
 * afterwards only reached the PIDS after the next path refresh (MTR #1010). Arrivals read it from the live route instead.
 */
public final class LiveDestination {

	private LiveDestination() {
	}

	public static String forArrival(String bakedDestination, @Nullable Route route, int stopIndex) {
		if (route == null || route.getRoutePlatforms().isEmpty()) {
			return bakedDestination;
		}
		final String live = route.getDestination(stopIndex);
		return live == null || live.isEmpty() ? bakedDestination : live;
	}
}
