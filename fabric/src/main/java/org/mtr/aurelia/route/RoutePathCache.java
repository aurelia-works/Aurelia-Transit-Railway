package org.mtr.aurelia.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * One cached path per route: the resolved platform/waypoint order from {@link RouteControl#visitOrder(List)}.
 * A cached path is reused until the route changes (the caller passes a new {@code routeVersion}, e.g. from
 * {@link RouteControl#contentVersion()} plus the route's stop count) or the rails change
 * ({@link #noteRailChange()}). The cache is only read from explicit actions (editing, regenerating depots); nothing
 * calls it per frame or per tick, so route pathfinding never costs frame time.
 *
 * <p>Pure Java: no Minecraft or MTR classes, so unit tests cover it directly.
 */
public final class RoutePathCache {

	private record Entry(List<Long> path, long routeVersion, long railVersion) {
	}

	private final Map<Long, Entry> entries = new HashMap<>();
	private long railVersion;

	/**
	 * The cached path for {@code routeId}, computing and storing it on a route edit, a rail change, or first use.
	 * The supplier runs at most once per version pair; repeated calls with the same versions never recompute.
	 */
	public synchronized List<Long> resolve(long routeId, long routeVersion, Supplier<List<Long>> compute) {
		final Entry entry = entries.get(routeId);
		if (entry != null && entry.routeVersion() == routeVersion && entry.railVersion() == railVersion) {
			return entry.path();
		}
		final List<Long> path = List.copyOf(compute.get());
		entries.put(routeId, new Entry(path, routeVersion, railVersion));
		return path;
	}

	/** The route was edited: its cached path is rebuilt on next use, other routes keep theirs. */
	public synchronized void invalidateRoute(long routeId) {
		entries.remove(routeId);
	}

	/** Rails changed: every cached path is rebuilt on next use. */
	public synchronized void noteRailChange() {
		railVersion++;
	}

	public synchronized void clear() {
		entries.clear();
	}

	public synchronized int size() {
		return entries.size();
	}
}
