package org.mtr.aurelia.route;

import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.mod.client.MinecraftClientData;

import java.util.ArrayList;
import java.util.List;

/**
 * The live view over {@link RoutePathCache}: one cached visit list per route, built from the dashboard route (stop
 * order and platform ids) plus this client's route-control data (choices, waypoints). Reads happen on explicit UI
 * actions only (route screens); the version folds in the loaded rail count, so rails changing rebuilds the path on
 * next read. Nothing here runs per frame or per tick.
 */
public final class RouteControlStore {

	private static final RoutePathCache CACHE = new RoutePathCache();

	private RouteControlStore() {
	}

	/** The cached visits for a route; computes once per route/control/rail version, never per frame. */
	public static List<RouteControl.Visit> visits(Route route, RouteControl control) {
		final List<Long> stops = new ArrayList<>();
		if (route != null) {
			for (final RoutePlatformData data : route.getRoutePlatforms()) {
				stops.add(data == null || data.platform == null ? 0 : data.platform.getId());
			}
		}
		final RouteControl effective = control == null ? RouteControl.EMPTY : control;
		return CACHE.resolve(route == null ? 0 : route.getId(), version(stops, effective), () -> effective.visitOrder(stops));
	}

	public static void invalidateRoute(long routeId) {
		CACHE.invalidateRoute(routeId);
	}

	/** Route-control data arrived from the server: every cached path rebuilds lazily on next read. */
	public static void onControlSync() {
		CACHE.clear();
	}

	private static long version(List<Long> stops, RouteControl control) {
		long version = control.contentVersion();
		for (final long stop : stops) {
			version = version * 31 + stop;
		}
		version = version * 31 + stops.size();
		version = version * 31 + MinecraftClientData.getInstance().positionsToRail.size();
		return version;
	}
}
