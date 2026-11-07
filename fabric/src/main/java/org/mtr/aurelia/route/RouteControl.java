package org.mtr.aurelia.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One route's route-control data: waypoints its trains must pass (in passing order), a chosen platform per stop
 * (a stop without a choice uses the route's planned platform), and whether a taken planned platform falls back to a
 * free platform of the same station.
 *
 * <p>Pure Java: no Minecraft or MTR classes, so unit tests cover it directly. The world save keeps it in
 * {@code atr_route_control.dat} under new keys only; a route with no entry loads as {@link #empty()} (no waypoints,
 * no fallback), so old worlds behave exactly as before.
 */
public final class RouteControl {

	/** A rail node the route must pass, after the stop at {@code afterStop} (0-based, clamped into the route). */
	public record Waypoint(long nodeId, int afterStop) {
	}

	public static final RouteControl EMPTY = new RouteControl(List.of(), Map.of(), false);

	private final List<Waypoint> waypoints;
	private final Map<Integer, Long> platformChoices;
	private final boolean fallback;

	public RouteControl(List<Waypoint> waypoints, Map<Integer, Long> platformChoices, boolean fallback) {
		final List<Waypoint> kept = new ArrayList<>();
		if (waypoints != null) {
			for (final Waypoint waypoint : waypoints) {
				if (waypoint != null && waypoint.nodeId() != 0) {
					kept.add(new Waypoint(waypoint.nodeId(), Math.max(0, waypoint.afterStop())));
				}
			}
		}
		this.waypoints = List.copyOf(kept);
		final Map<Integer, Long> choices = new HashMap<>();
		if (platformChoices != null) {
			platformChoices.forEach((stop, platformId) -> {
				if (stop != null && stop >= 0 && platformId != null && platformId != 0) {
					choices.put(stop, platformId);
				}
			});
		}
		this.platformChoices = Map.copyOf(choices);
		this.fallback = fallback;
	}

	public List<Waypoint> waypoints() {
		return waypoints;
	}

	public Map<Integer, Long> platformChoices() {
		return platformChoices;
	}

	public boolean fallback() {
		return fallback;
	}

	public boolean isEmpty() {
		return waypoints.isEmpty() && platformChoices.isEmpty() && !fallback;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof RouteControl control)) {
			return false;
		}
		return fallback == control.fallback && waypoints.equals(control.waypoints) && platformChoices.equals(control.platformChoices);
	}

	@Override
	public int hashCode() {
		return waypoints.hashCode() * 31 + platformChoices.hashCode() * 31 + (fallback ? 1 : 0);
	}

	/** The platform a stop uses: its choice, or the route's planned platform when it has none. */
	public long platformForStop(int stop, long plannedPlatformId) {
		return platformChoices.getOrDefault(stop, plannedPlatformId);
	}

	/**
	 * Every platform id a train passes, in order: each stop's platform (choice or planned), with each waypoint's node
	 * right after its stop. Waypoints keep their listed order inside a segment; ones past the last stop end the list.
	 */
	public List<Long> visitOrder(List<Long> stopPlatformIds) {
		final List<Long> order = new ArrayList<>();
		if (stopPlatformIds == null || stopPlatformIds.isEmpty()) {
			waypoints.forEach(waypoint -> order.add(waypoint.nodeId()));
			return order;
		}
		final List<List<Long>> after = new ArrayList<>();
		for (int i = 0; i < stopPlatformIds.size(); i++) {
			after.add(new ArrayList<>());
		}
		for (final Waypoint waypoint : waypoints) {
			after.get(Math.min(waypoint.afterStop(), stopPlatformIds.size() - 1)).add(waypoint.nodeId());
		}
		for (int i = 0; i < stopPlatformIds.size(); i++) {
			order.add(platformForStop(i, stopPlatformIds.get(i)));
			order.addAll(after.get(i));
		}
		return order;
	}

	/**
	 * Which platform a train should use for a stop. Without fallback (or with a free planned platform) it is the
	 * planned one; otherwise the first free candidate. {@code candidates} lists the station's platforms, planned
	 * first. When every candidate is taken, the planned platform wins (a train waits rather than vanishes).
	 */
	public long resolvePlatform(long plannedPlatformId, List<Long> candidates, Set<Long> occupied) {
		if (!fallback || occupied == null || !occupied.contains(plannedPlatformId)) {
			return plannedPlatformId;
		}
		if (candidates != null) {
			for (final long candidate : candidates) {
				if (!occupied.contains(candidate)) {
					return candidate;
				}
			}
		}
		return plannedPlatformId;
	}

	/** Stable content stamp for {@link RoutePathCache}: equal controls share it, any edit changes it. */
	public long contentVersion() {
		long version = fallback ? 1 : 0;
		for (final Waypoint waypoint : waypoints) {
			version = version * 31 + waypoint.nodeId();
			version = version * 31 + waypoint.afterStop();
		}
		final List<Integer> stops = new ArrayList<>(platformChoices.keySet());
		Collections.sort(stops);
		for (final int stop : stops) {
			version = version * 31 + stop;
			version = version * 31 + platformChoices.get(stop);
		}
		return version;
	}

	public RouteControl withWaypoint(long nodeId, int afterStop) {
		if (nodeId == 0) {
			return this;
		}
		final List<Waypoint> next = new ArrayList<>(waypoints);
		next.add(new Waypoint(nodeId, Math.max(0, afterStop)));
		return new RouteControl(next, platformChoices, fallback);
	}

	public RouteControl withoutWaypoint(int index) {
		if (index < 0 || index >= waypoints.size()) {
			return this;
		}
		final List<Waypoint> next = new ArrayList<>(waypoints);
		next.remove(index);
		return new RouteControl(next, platformChoices, fallback);
	}

	public RouteControl moveWaypoint(int from, int to) {
		if (from < 0 || from >= waypoints.size() || to < 0 || to >= waypoints.size() || from == to) {
			return this;
		}
		final List<Waypoint> next = new ArrayList<>(waypoints);
		next.add(to, next.remove(from));
		return new RouteControl(next, platformChoices, fallback);
	}

	public RouteControl withPlatformChoice(int stop, long platformId) {
		final Map<Integer, Long> next = new HashMap<>(platformChoices);
		if (stop >= 0 && platformId != 0) {
			next.put(stop, platformId);
		} else if (stop >= 0) {
			next.remove(stop);
		}
		return new RouteControl(waypoints, next, fallback);
	}

	public RouteControl withFallback(boolean fallback) {
		return new RouteControl(waypoints, platformChoices, fallback);
	}

	private static final String KEY_WAYPOINTS = "waypoints";
	private static final String KEY_PLATFORMS = "platforms";
	private static final String KEY_FALLBACK = "fallback";

	/** Flat string entries for the world save. New keys only; old saves simply lack them. */
	public Map<String, String> toEntries() {
		final StringBuilder waypointBuilder = new StringBuilder();
		for (final Waypoint waypoint : waypoints) {
			if (!waypointBuilder.isEmpty()) {
				waypointBuilder.append(',');
			}
			waypointBuilder.append(waypoint.nodeId()).append(':').append(waypoint.afterStop());
		}
		final List<Integer> stops = new ArrayList<>(platformChoices.keySet());
		Collections.sort(stops);
		final StringBuilder platformBuilder = new StringBuilder();
		for (final int stop : stops) {
			if (!platformBuilder.isEmpty()) {
				platformBuilder.append(',');
			}
			platformBuilder.append(stop).append(':').append(platformChoices.get(stop));
		}
		return Map.of(KEY_WAYPOINTS, waypointBuilder.toString(), KEY_PLATFORMS, platformBuilder.toString(), KEY_FALLBACK, fallback ? "1" : "0");
	}

	/** Missing or malformed entries fall back to the defaults: no waypoints, planned platforms, no fallback. */
	public static RouteControl fromEntries(Map<String, String> entries) {
		if (entries == null || entries.isEmpty()) {
			return EMPTY;
		}
		final List<Waypoint> waypoints = new ArrayList<>();
		for (final String part : entries.getOrDefault(KEY_WAYPOINTS, "").split(",")) {
			final String[] pair = part.strip().split(":");
			if (pair.length == 2) {
				try {
					final long nodeId = Long.parseLong(pair[0].strip());
					final int afterStop = Integer.parseInt(pair[1].strip());
					if (nodeId != 0 && afterStop >= 0) {
						waypoints.add(new Waypoint(nodeId, afterStop));
					}
				} catch (NumberFormatException ignored) {
				}
			}
		}
		final Map<Integer, Long> choices = new HashMap<>();
		for (final String part : entries.getOrDefault(KEY_PLATFORMS, "").split(",")) {
			final String[] pair = part.strip().split(":");
			if (pair.length == 2) {
				try {
					final int stop = Integer.parseInt(pair[0].strip());
					final long platformId = Long.parseLong(pair[1].strip());
					if (stop >= 0 && platformId != 0) {
						choices.put(stop, platformId);
					}
				} catch (NumberFormatException ignored) {
				}
			}
		}
		return new RouteControl(waypoints, choices, "1".equals(entries.getOrDefault(KEY_FALLBACK, "").strip()));
	}
}
