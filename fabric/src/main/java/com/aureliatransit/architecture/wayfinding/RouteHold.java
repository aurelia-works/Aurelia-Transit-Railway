package com.aureliatransit.architecture.wayfinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A suspension that holds one route's trains at the depot until {@code untilEpochMillis}. Stored beside service
 * messages, not inside them, so an older message list still loads. A missing hold list is empty.
 */
public record RouteHold(int messageId, String route, long untilEpochMillis) {

	public RouteHold {
		route = route == null ? "" : route.trim();
	}

	public String key() {
		return route.toLowerCase(Locale.ROOT);
	}

	public boolean active(long now) {
		return messageId > 0 && !route.isEmpty() && untilEpochMillis > now;
	}

	public static List<RouteHold> active(List<RouteHold> holds, long now) {
		final List<RouteHold> next = new ArrayList<>();
		if (holds != null) {
			for (final RouteHold hold : holds) {
				if (hold.active(now)) {
					next.add(hold);
				}
			}
		}
		return List.copyOf(next);
	}

	public static boolean affects(List<RouteHold> holds, String routeName, long now) {
		final String key = routeName == null ? "" : routeName.trim().toLowerCase(Locale.ROOT);
		if (key.isEmpty()) {
			return false;
		}
		for (final RouteHold hold : active(holds, now)) {
			if (hold.key().equals(key) || key.contains(hold.key()) || hold.key().contains(key)) {
				return true;
			}
		}
		return false;
	}
}
