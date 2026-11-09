package org.mtr.aurelia.route;

import org.mtr.core.data.Data;
import org.mtr.core.data.Depot;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.generated.data.NameColorDataBaseSchema;
import org.mtr.core.generated.data.PathDataSchema;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.data.RouteControlOptions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies the server's route control to MTR's own path data. MTR builds a depot's path once (a
 * {@link SidingPathFinder} per platform pair) and every train copies it; this class feeds those finders the chosen or
 * fallback platform and inserts each waypoint's rail as an extra pair of finders, so the cached path MTR writes already
 * passes the waypoint. Nothing here runs per frame: the work happens on a depot's explicit path generation.
 *
 * <p>MTR keeps the stop list in the private {@code Depot.PlatformRouteDetails} record. The record is rebuilt from the
 * public route data with reflection because the alternative would be editing the core jar. A route with no control data
 * is rebuilt to the same list MTR produced, so old worlds behave exactly as before.
 */
public final class RouteControlPlan {

	/** Waypoints are rail midpoints rounded to blocks; the editor snaps within 8 blocks. */
	private static final double WAYPOINT_RADIUS_SQUARED = 8 * 8;

	private static final Map<Long, Plan> PLANS = new ConcurrentHashMap<>();
	private static final Set<Long> WAYPOINT_IDS = ConcurrentHashMap.newKeySet();
	/** Per depot: route id -> raw route stop index -> the platform the cached path actually stops at. */
	private static final Map<Long, Map<Long, Map<Integer, Platform>>> EFFECTIVE_PLATFORMS = new ConcurrentHashMap<>();
	/** Route platform fields temporarily pointed at those platforms while MTR's stop times are built. */
	private static final ThreadLocal<List<Override>> OVERRIDES = ThreadLocal.withInitial(ArrayList::new);

	private static final Field SAVED_RAIL_BASE_ID = pathDataField("savedRailBaseId");
	private static final Field DWELL_TIME = pathDataField("dwellTime");
	private static final Field STOP_INDEX = pathDataField("stopIndex");
	private static final Field DATA = dataField();

	private static volatile Constructor<?> detailsConstructor;

	private RouteControlPlan() {
	}

	/** One entry of MTR's platform-in-route list, plus the raw route stop index. */
	private record Stop(Platform platform, Route route, int stopIndex, int previousStopIndex) {
	}

	private record Override(RoutePlatformData routePlatform, Platform original) {
	}

	private static final class Plan {

		private final List<Stop> stops = new ArrayList<>();
		private final Map<Long, Integer> fakeStopIndex = new HashMap<>();
	}

	/**
	 * Rebuilds the depot's platform-in-route list with each stop's chosen or fallback platform and remembers the plan
	 * for the waypoint step. Called from {@code Depot.writeRouteCache} and before generation, so a route-control edit
	 * does not need a dashboard data sync to take effect.
	 */
	public static void refresh(Depot depot, ObjectArrayList platformsInRoute, boolean resolveFallback) {
		restoreOverrides();
		final Data data = data(depot);
		if (!(data instanceof Simulator)) {
			// The client's dashboard copy never builds train paths; leaving it alone avoids the shared static plan
			// colliding with the integrated server's depot of the same id.
			return;
		}
		boolean hasControl = false;
		boolean hasFallback = false;
		for (final Route route : depot.routes) {
			final RouteControl control = RouteControlOptions.server(route.getId());
			hasControl |= !control.isEmpty();
			hasFallback |= control.fallback();
		}
		if (!hasControl) {
			// No route control on this depot: leave MTR's own platform list as it built it, so worlds without route-control
			// data take exactly MTR's path.
			final Plan old = PLANS.remove(depot.getId());
			if (old != null) {
				WAYPOINT_IDS.removeAll(old.fakeStopIndex.keySet());
			}
			if (EFFECTIVE_PLATFORMS.remove(depot.getId()) != null || old != null) {
				// The control was cleared after this list was rewritten, and MTR refills it only on its next data sync:
				// put back the planned platforms now, so the regeneration that follows the edit takes MTR's path again.
				fillPlatforms(platformsInRoute, plannedStops(depot));
			}
			return;
		}

		final Set<Long> occupied = resolveFallback && hasFallback && RouteControlOptions.serverFallbackMaster() ? occupied(data) : Set.of();
		final Map<Long, Map<Integer, Platform>> storedForDepot = EFFECTIVE_PLATFORMS.get(depot.getId());
		final Plan plan = new Plan();
		final Map<Long, Map<Integer, Platform>> effectivePlatforms = new HashMap<>();
		long previousPlatformId = 0;

		for (final Route route : depot.routes) {
			final RouteControl control = RouteControlOptions.server(route.getId());
			final ObjectArrayList<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
			for (int i = 0; i < routePlatforms.size(); i++) {
				final RoutePlatformData routePlatform = routePlatforms.get(i);
				final Platform planned = routePlatform == null ? null : routePlatform.platform;
				if (planned == null) {
					continue;
				}

				final Platform platform;
				if (resolveFallback) {
					long platformId = control.platformForStop(i, planned.getId());
					final Platform chosen = data.platformIdMap.get(platformId);
					if (chosen != null && control.fallback() && RouteControlOptions.serverFallbackMaster() && occupied.contains(platformId)) {
						final List<Long> candidates = candidates(data, chosen);
						// A fallback onto the platform the previous stop already uses would make MTR merge the two stops.
						for (int candidate = candidates.size() - 1; candidate >= 0; candidate--) {
							if (candidates.get(candidate) == previousPlatformId) {
								candidates.remove(candidate);
							}
						}
						platformId = control.resolvePlatform(platformId, candidates, occupied);
					}
					final Platform resolved = data.platformIdMap.get(platformId);
					platform = resolved == null ? planned : resolved;
				} else {
					// Between generations the cached path is authoritative: reuse the platforms it was built with
					// instead of resolving fallback again against traffic that has moved since.
					final Map<Integer, Platform> stored = storedForDepot == null ? null : storedForDepot.get(route.getId());
					final Platform storedPlatform = stored == null ? null : stored.get(i);
					final Platform mappedStored = storedPlatform == null ? null : data.platformIdMap.get(storedPlatform.getId());
					if (mappedStored != null) {
						platform = mappedStored;
					} else {
						final Platform chosen = data.platformIdMap.get(control.platformForStop(i, planned.getId()));
						platform = chosen == null ? planned : chosen;
					}
				}

				effectivePlatforms.computeIfAbsent(route.getId(), id -> new HashMap<>()).put(i, platform);
				if (platform.getId() != previousPlatformId) {
					// Same shape as Depot.writeRouteCache: a route's first stop belongs to the previous route's tail.
					plan.stops.add(new Stop(platform, i == 0 ? null : route, i, i == 0 ? Integer.MAX_VALUE : i - 1));
					previousPlatformId = platform.getId();
				}
			}
		}

		final Plan old = PLANS.put(depot.getId(), plan);
		if (old != null) {
			WAYPOINT_IDS.removeAll(old.fakeStopIndex.keySet());
		}
		// Rebuilt from the stored platforms on a plain sync, so the map always points at the current Platform objects.
		EFFECTIVE_PLATFORMS.put(depot.getId(), effectivePlatforms);
		fillPlatforms(platformsInRoute, plan.stops);
	}

	/** Replaces MTR's per-platform finders with the same chain plus one forced hop through each waypoint rail. */
	public static void applyWaypoints(Depot depot, ObjectArrayList sidingPathFinders) {
		final Plan plan = PLANS.get(depot.getId());
		if (plan == null || plan.stops.size() < 2 || depot.getTransportMode() == TransportMode.AIRPLANE) {
			return;
		}
		final Data data = data(depot);
		if (!(data instanceof Simulator)) {
			return;
		}
		boolean hasWaypoints = false;
		for (final Route route : depot.routes) {
			if (!RouteControlOptions.server(route.getId()).waypoints().isEmpty()) {
				hasWaypoints = true;
				break;
			}
		}
		if (!hasWaypoints) {
			return;
		}

		WAYPOINT_IDS.removeAll(plan.fakeStopIndex.keySet());
		plan.fakeStopIndex.clear();
		final Map<Rail, Position[]> endpoints = endpoints(data);
		final ObjectArrayList built = new ObjectArrayList();

		for (int i = 0; i + 1 < plan.stops.size(); i++) {
			final Stop start = plan.stops.get(i);
			final Stop next = plan.stops.get(i + 1);
			final boolean lastPair = i + 2 == plan.stops.size();
			Platform current = start.platform();

			for (final RouteControl.Waypoint waypoint : waypointsFor(start, next, lastPair)) {
				final Rail rail = nearestRail(data, waypoint);
				if (rail == null) {
					continue;
				}
				final Position[] ends = endpoints.get(rail);
				if (ends == null || ends[0] == null || ends[1] == null) {
					continue;
				}

				Position from = ends[0];
				Position to = ends[1];
				if (rail.getSpeedLimitMetersPerMillisecond(from) <= 0) {
					final Position swap = from;
					from = to;
					to = swap;
				}
				if (rail.getSpeedLimitMetersPerMillisecond(from) <= 0) {
					continue;
				}

				// Two throw-away platforms on the waypoint's rail: the second one makes the finder emit exactly this
				// rail as one segment. Their random ids identify the segments that must stop looking like platforms.
				final Platform enter = new Platform(from, to, depot.getTransportMode(), data);
				final Platform exit = new Platform(to, from, depot.getTransportMode(), data);
				plan.fakeStopIndex.put(enter.getId(), i);
				plan.fakeStopIndex.put(exit.getId(), i);
				WAYPOINT_IDS.add(enter.getId());
				WAYPOINT_IDS.add(exit.getId());
				built.add(new SidingPathFinder<>(data, current, enter, i));
				built.add(new SidingPathFinder<>(hopData(data, from, to, rail), enter, exit, i));
				current = exit;
			}

			built.add(new SidingPathFinder<>(data, current, next.platform(), i));
		}

		sidingPathFinders.clear();
		sidingPathFinders.addAll(built);
	}

	/** Whether the id belongs to a throw-away waypoint platform. Used by the path-tick mixin. */
	public static boolean isWaypointPlatform(long savedRailId) {
		return WAYPOINT_IDS.contains(savedRailId);
	}

	/** Generation failed around a waypoint: drop its plan so a later generation starts clean. */
	public static void failed(long savedRailId) {
		if (!WAYPOINT_IDS.remove(savedRailId)) {
			return;
		}
		for (final Map.Entry<Long, Plan> entry : PLANS.entrySet()) {
			if (entry.getValue().fakeStopIndex.containsKey(savedRailId)) {
				PLANS.remove(entry.getKey());
				WAYPOINT_IDS.removeAll(entry.getValue().fakeStopIndex.keySet());
				return;
			}
		}
	}

	/**
	 * Turns the throw-away waypoint segments back into ordinary track before any train copies the path: no dwell, no
	 * platform id, and the stop index of the platform the train last stopped at.
	 */
	public static void fixPath(Depot depot) {
		if (!(data(depot) instanceof Simulator)) {
			return;
		}
		final Plan plan = PLANS.remove(depot.getId());
		if (plan == null) {
			return;
		}
		WAYPOINT_IDS.removeAll(plan.fakeStopIndex.keySet());
		if (plan.fakeStopIndex.isEmpty()) {
			return;
		}

		for (final PathData pathData : depot.getPath()) {
			final Integer stopIndex = plan.fakeStopIndex.get(pathData.getSavedRailBaseId());
			if (stopIndex != null) {
				setLong(pathData, SAVED_RAIL_BASE_ID, 0);
				setLong(pathData, DWELL_TIME, 0);
				setLong(pathData, STOP_INDEX, stopIndex);
			}
		}
	}

	/**
	 * Points each route stop at the platform its cached path actually stops at, for the duration of MTR's stop-time
	 * pass. MTR matches those times by platform id, so this is what keeps arrivals, departures and durations on the
	 * chosen or fallback platform. Restored immediately afterwards.
	 */
	public static void applyOverrides(Depot depot) {
		restoreOverrides();
		final Data data = data(depot);
		if (!(data instanceof Simulator)) {
			return;
		}
		final Map<Long, Map<Integer, Platform>> byRoute = EFFECTIVE_PLATFORMS.get(depot.getId());
		if (byRoute == null) {
			return;
		}
		final List<Override> overrides = OVERRIDES.get();

		for (final Route route : depot.routes) {
			final Map<Integer, Platform> platforms = byRoute.get(route.getId());
			if (platforms == null) {
				continue;
			}
			final ObjectArrayList<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
			for (int i = 0; i < routePlatforms.size(); i++) {
				final RoutePlatformData routePlatform = routePlatforms.get(i);
				final Platform stored = platforms.get(i);
				final Platform current = stored == null ? null : data.platformIdMap.get(stored.getId());
				final Platform platform = current == null ? stored : current;
				if (routePlatform != null && platform != null && routePlatform.platform != platform) {
					overrides.add(new Override(routePlatform, routePlatform.platform));
					routePlatform.platform = platform;
				}
			}
		}
	}

	public static void restoreOverrides() {
		final List<Override> overrides = OVERRIDES.get();
		for (final Override override : overrides) {
			override.routePlatform().platform = override.original();
		}
		overrides.clear();
	}

	/** Waypoints that belong between these two stops, in listed order. */
	private static List<RouteControl.Waypoint> waypointsFor(Stop start, Stop next, boolean lastPair) {
		final List<RouteControl.Waypoint> waypoints = new ArrayList<>();

		if (next.route() == null) {
			// The next entry is the first stop of another route: the tail of this route runs before it.
			if (start.route() == null) {
				return List.of();
			}
			addWaypoints(waypoints, start.route(), start.stopIndex(), Integer.MAX_VALUE);
			return waypoints;
		}

		if (start.route() != null && start.route() != next.route()) {
			// A skipped shared platform: the tail of the old route and the leading waypoints of the new one both run
			// on this segment.
			addWaypoints(waypoints, start.route(), start.stopIndex(), Integer.MAX_VALUE);
			addWaypoints(waypoints, next.route(), 0, next.stopIndex());
			return waypoints;
		}

		// Consecutive stops of one route (next.route() is set for every stop except index 0).
		addWaypoints(waypoints, next.route(), start.stopIndex(), next.stopIndex());
		// There is no track after the last platform of a linear route, so its "after end" waypoints go on the final
		// approach. They are still passed in listed order; they are only placed before the last stop, not after it.
		if (lastPair) {
			addWaypoints(waypoints, next.route(), next.stopIndex(), Integer.MAX_VALUE);
		}
		return waypoints;
	}

	private static void addWaypoints(List<RouteControl.Waypoint> waypoints, Route route, int min, int max) {
		final RouteControl control = RouteControlOptions.server(route.getId());
		for (final RouteControl.Waypoint waypoint : control.waypoints()) {
			if (waypoint.afterStop() >= min && waypoint.afterStop() < max) {
				waypoints.add(waypoint);
			}
		}
	}

	/** The rail whose rounded midpoint is closest to the waypoint, or null when nothing is within snapping range. */
	private static Rail nearestRail(Data data, RouteControl.Waypoint waypoint) {
		Rail best = null;
		double bestDistance = WAYPOINT_RADIUS_SQUARED;
		for (final Rail rail : data.rails) {
			final Vector middle = rail.railMath.getPosition(rail.railMath.getLength() / 2, false);
			final double dx = middle.x - waypoint.x();
			final double dy = middle.y - waypoint.y();
			final double dz = middle.z - waypoint.z();
			final double distance = dx * dx + dy * dy + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = rail;
			}
		}
		return best;
	}

	private static Map<Rail, Position[]> endpoints(Data data) {
		final Map<Rail, Position[]> endpoints = new HashMap<>();
		data.positionsToRail.forEach((start, ends) -> ends.forEach((end, rail) -> endpoints.putIfAbsent(rail, new Position[]{start, end})));
		return endpoints;
	}

	/**
	 * A view of the rail graph where the waypoint's start node can only leave along the waypoint rail. The finder
	 * captures this map, so the hop cannot take a detour that skips the chosen rail.
	 */
	private static Data hopData(Data data, Position from, Position to, Rail rail) {
		final WaypointData hop = new WaypointData();
		hop.positionsToRail.putAll(data.positionsToRail);
		final Object2ObjectOpenHashMap<Position, Rail> onlyWaypoint = new Object2ObjectOpenHashMap<>();
		onlyWaypoint.put(to, rail);
		hop.positionsToRail.put(from, onlyWaypoint);
		return hop;
	}

	/** Only {@code positionsToRail} is read by a non-airplane {@link SidingPathFinder}. */
	private static final class WaypointData extends Data {
	}

	private static Set<Long> occupied(Data data) {
		final Set<Long> occupied = new HashSet<>();
		for (final Siding siding : data.sidings) {
			siding.iterateVehiclesAndRidingEntities((extra, entity) -> {
				if (extra != null) {
					if (extra.getThisPlatformId() != 0) {
						occupied.add(extra.getThisPlatformId());
					}
					if (extra.getNextPlatformId() != 0) {
						occupied.add(extra.getNextPlatformId());
					}
				}
			});
		}
		return occupied;
	}

	/** Same station, same transport mode; the planned platform first, then nearest first. */
	private static List<Long> candidates(Data data, Platform platform) {
		final List<Long> options = new ArrayList<>();
		options.add(platform.getId());
		if (platform.area == null) {
			return options;
		}

		final long stationId = platform.area.getId();
		final Position middle = platform.getMidPosition();
		final List<Platform> others = new ArrayList<>();
		for (final Platform other : data.platforms) {
			if (other.getId() != platform.getId() && other.area != null && other.area.getId() == stationId && other.isTransportMode(platform.getTransportMode())) {
				others.add(other);
			}
		}
		others.sort(Comparator.comparingDouble((Platform other) -> {
			final Position otherMiddle = other.getMidPosition();
			final double dx = middle.getX() - otherMiddle.getX();
			final double dz = middle.getZ() - otherMiddle.getZ();
			return dx * dx + dz * dz;
		}).thenComparingLong(Platform::getId));
		for (final Platform other : others) {
			options.add(other.getId());
		}
		return options;
	}

	/** Each route's own planned platforms, in the list shape {@code Depot.writeRouteCache} builds. */
	private static List<Stop> plannedStops(Depot depot) {
		final List<Stop> stops = new ArrayList<>();
		long previousPlatformId = 0;
		for (final Route route : depot.routes) {
			final ObjectArrayList<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
			for (int i = 0; i < routePlatforms.size(); i++) {
				final RoutePlatformData routePlatform = routePlatforms.get(i);
				final Platform platform = routePlatform == null ? null : routePlatform.platform;
				if (platform != null && platform.getId() != previousPlatformId) {
					stops.add(new Stop(platform, i == 0 ? null : route, i, i == 0 ? Integer.MAX_VALUE : i - 1));
					previousPlatformId = platform.getId();
				}
			}
		}
		return stops;
	}

	private static void fillPlatforms(ObjectArrayList platformsInRoute, List<Stop> stops) {
		final Constructor<?> constructor = detailsConstructor();
		if (constructor == null) {
			return;
		}

		final ObjectArrayList built = new ObjectArrayList();
		try {
			for (final Stop stop : stops) {
				built.add(constructor.newInstance(stop.platform(), stop.route(), stop.previousStopIndex()));
			}
		} catch (Exception e) {
			Init.LOGGER.error("[ATR] Could not rebuild a depot's platform list; MTR path generation keeps its own", e);
			return;
		}
		platformsInRoute.clear();
		platformsInRoute.addAll(built);
	}

	private static Constructor<?> detailsConstructor() {
		if (detailsConstructor == null) {
			try {
				detailsConstructor = Class.forName("org.mtr.core.data.Depot$PlatformRouteDetails").getDeclaredConstructor(Platform.class, Route.class, int.class);
				detailsConstructor.setAccessible(true);
			} catch (Exception e) {
				Init.LOGGER.error("[ATR] Depot.PlatformRouteDetails is not accessible; route control platform choices are idle", e);
				return null;
			}
		}
		return detailsConstructor;
	}

	private static Field dataField() {
		try {
			final Field field = NameColorDataBaseSchema.class.getDeclaredField("data");
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			Init.LOGGER.error("[ATR] Depot data is not accessible; route control is idle", e);
			return null;
		}
	}

	private static Data data(Depot depot) {
		if (DATA == null) {
			return null;
		}
		try {
			return (Data) DATA.get(depot);
		} catch (Exception e) {
			Init.LOGGER.error("[ATR] Could not read a depot's data", e);
			return null;
		}
	}

	private static Field pathDataField(String name) {
		try {
			final Field field = PathDataSchema.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			Init.LOGGER.error("[ATR] PathData field {} is not accessible; waypoint segments keep their stop data", name, e);
			return null;
		}
	}

	private static void setLong(PathData pathData, Field field, long value) {
		if (field == null) {
			return;
		}
		try {
			field.setLong(pathData, value);
		} catch (Exception e) {
			Init.LOGGER.error("[ATR] Could not rewrite a waypoint path segment", e);
		}
	}
}
