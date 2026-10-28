package org.mtr.mod.servlet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtr.core.data.*;
import org.mtr.core.generated.data.SavedRailBaseSchema;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;

/**
 * The data the AureliaMap layer draws: stations with their centres, routes with their shape along the rails, and live vehicles.
 * Everything here reads simulation data, so it must run on the simulation thread (see {@link MapLayerServlet}).
 */
public final class MapLayerJson {

	private static final Logger LOGGER = LogManager.getLogger("MinecraftTransitRailway");
	/**
	 * Blocks between shape points along a rail.
	 */
	private static final double SAMPLE_STEP = 8;
	/**
	 * Rail nodes one platform-to-platform search may settle before it gives up and draws a straight line.
	 */
	private static final int MAX_VISITS = 20000;

	@Nullable
	private static final Field POSITION_1 = getField(SavedRailBaseSchema.class, "position1");
	@Nullable
	private static final Field POSITION_2 = getField(SavedRailBaseSchema.class, "position2");
	@Nullable
	private static final Field SIDING_VEHICLES = getField(Siding.class, "vehicles");

	private MapLayerJson() {
	}

	public static JsonObject network(Data data, ShapeCache shapeCache) {
		shapeCache.checkRails(data);
		final Map<Position, Map<Position, Double>> graph = new HashMap<>();
		data.positionsToRail.forEach((position1, railMap) -> {
			final Map<Position, Double> costs = new HashMap<>();
			railMap.forEach((position2, rail) -> costs.put(position2, rail.railMath.getLength()));
			graph.put(position1, costs);
		});

		final Map<Station, Set<String>> stationRoutes = new HashMap<>();
		final JsonArray routesArray = new JsonArray();
		data.routes.forEach(route -> {
			final List<Platform> platforms = new ArrayList<>();
			route.getRoutePlatforms().forEach(routePlatformData -> {
				final Platform platform = routePlatformData.getPlatform();
				if (platform != null) {
					platforms.add(platform);
				}
			});

			final JsonArray platformsArray = new JsonArray();
			platforms.forEach(platform -> {
				final Position position = platform.getMidPosition();
				final JsonObject platformObject = new JsonObject();
				platformObject.addProperty("id", platform.getHexId());
				platformObject.addProperty("name", platform.getName());
				platformObject.addProperty("stationId", platform.area == null ? "" : platform.area.getHexId());
				platformObject.addProperty("x", position.getX());
				platformObject.addProperty("y", position.getY());
				platformObject.addProperty("z", position.getZ());
				platformsArray.add(platformObject);
				if (platform.area != null) {
					stationRoutes.computeIfAbsent(platform.area, key -> new LinkedHashSet<>()).add(route.getHexId());
				}
			});

			final JsonArray shape = new JsonArray();
			final boolean circular = route.getCircularState() != Route.CircularState.NONE;
			boolean railShape = true;
			final JsonArray straightRanges = new JsonArray();
			for (int i = 0; i < platforms.size() - (circular ? 0 : 1); i++) {
				final Platform from = platforms.get(i);
				final Platform to = platforms.get((i + 1) % platforms.size());
				final String key = from.getHexId() + ">" + to.getHexId();
				List<double[]> segment = shapeCache.segments.get(key);
				if (segment == null) {
					segment = segment(data, graph, from, to);
					if (segment == null) {
						shapeCache.straight.add(key);
						segment = straightLine(from, to);
					}
					shapeCache.segments.put(key, segment);
				}
				final int startIndex = Math.max(0, shape.size() - 1);
				for (int j = shape.size() == 0 ? 0 : 1; j < segment.size(); j++) {
					shape.add(point(segment.get(j)));
				}
				if (shapeCache.straight.contains(key)) {
					railShape = false;
					final JsonArray range = new JsonArray();
					range.add(startIndex);
					range.add(shape.size() - 1);
					straightRanges.add(range);
				}
			}

			final JsonObject routeObject = new JsonObject();
			routeObject.addProperty("id", route.getHexId());
			routeObject.addProperty("name", route.getName());
			routeObject.addProperty("number", route.getRouteNumber());
			routeObject.addProperty("color", route.getColorHex());
			routeObject.addProperty("type", route.getRouteTypeKey());
			routeObject.addProperty("hidden", route.getHidden());
			routeObject.addProperty("circular", circular);
			routeObject.add("platforms", platformsArray);
			routeObject.add("shape", shape);
			routeObject.addProperty("railShape", railShape);
			routeObject.add("straight", straightRanges);
			routesArray.add(routeObject);
		});

		final JsonArray stationsArray = new JsonArray();
		data.stations.forEach(station -> {
			final Position center = station.getCenter();
			final JsonObject stationObject = new JsonObject();
			stationObject.addProperty("id", station.getHexId());
			stationObject.addProperty("name", station.getName());
			stationObject.addProperty("color", station.getColorHex());
			stationObject.addProperty("x", center.getX());
			stationObject.addProperty("z", center.getZ());
			stationObject.addProperty("minX", station.getMinX());
			stationObject.addProperty("minZ", station.getMinZ());
			stationObject.addProperty("maxX", station.getMaxX());
			stationObject.addProperty("maxZ", station.getMaxZ());
			final JsonArray routeIds = new JsonArray();
			stationRoutes.getOrDefault(station, Collections.emptySet()).forEach(routeIds::add);
			stationObject.add("routes", routeIds);
			stationsArray.add(stationObject);
		});

		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("stations", stationsArray);
		jsonObject.add("routes", routesArray);
		return jsonObject;
	}

	public static JsonObject vehicles(Data data) {
		final JsonArray vehiclesArray = new JsonArray();
		data.sidings.forEach(siding -> getVehicles(siding).forEach(vehicle -> {
			try {
				final VehicleExtraData extraData = vehicle.vehicleExtraData;
				final Route route = data.routeIdMap.get(extraData.getThisRouteId());
				final Vector head = vehicle.getHeadPosition();
				final JsonObject vehicleObject = new JsonObject();
				vehicleObject.addProperty("id", vehicle.getHexId());
				vehicleObject.addProperty("routeId", route == null ? "" : route.getHexId());
				vehicleObject.addProperty("routeName", extraData.getThisRouteName());
				vehicleObject.addProperty("routeNumber", extraData.getThisRouteNumber());
				vehicleObject.addProperty("color", String.format("%06X", extraData.getThisRouteColor() & 0xFFFFFF));
				vehicleObject.addProperty("destination", extraData.getThisRouteDestination());
				vehicleObject.addProperty("station", extraData.getThisStationName());
				vehicleObject.addProperty("nextStation", extraData.getNextStationName());
				vehicleObject.addProperty("onRoute", vehicle.getIsOnRoute());
				vehicleObject.addProperty("moving", vehicle.isMoving());
				vehicleObject.addProperty("cars", extraData.immutableVehicleCars.size());
				vehicleObject.add("head", point(new double[]{head.x, head.y, head.z}));
				vehiclesArray.add(vehicleObject);
			} catch (Exception e) {
				LOGGER.debug("Skipping a vehicle on the map layer", e);
			}
		}));

		final JsonObject jsonObject = new JsonObject();
		jsonObject.add("vehicles", vehiclesArray);
		return jsonObject;
	}

	/**
	 * The shape from one platform to the next: the first platform's rail, then the cheapest way along the rails to the next platform.
	 *
	 * @return {@code null} when no way along the rails is found
	 */
	@Nullable
	private static List<double[]> segment(Data data, Map<Position, Map<Position, Double>> graph, Platform from, Platform to) {
		final Position[] fromEnds = getEnds(from);
		final Position[] toEnds = getEnds(to);
		if (fromEnds != null && toEnds != null) {
			final List<Position> path = MapLayerGeometry.shortestPath(graph, Arrays.asList(fromEnds), Arrays.asList(toEnds), MAX_VISITS);
			if (!path.isEmpty()) {
				final List<double[]> points = new ArrayList<>();
				final Position start = path.get(0);
				final Rail platformRail = getRail(data, fromEnds[0], fromEnds[1]);
				if (platformRail != null) {
					points.addAll(MapLayerGeometry.sampleRail(platformRail, start.equals(fromEnds[0]) ? fromEnds[1] : fromEnds[0], SAMPLE_STEP));
				}
				for (int i = 0; i < path.size() - 1; i++) {
					final Rail rail = getRail(data, path.get(i), path.get(i + 1));
					if (rail != null) {
						final List<double[]> railPoints = MapLayerGeometry.sampleRail(rail, path.get(i), SAMPLE_STEP);
						points.addAll(points.isEmpty() ? railPoints : railPoints.subList(1, railPoints.size()));
					}
				}
				if (!points.isEmpty()) {
					return points;
				}
			}
		}

		return null;
	}

	private static List<double[]> straightLine(Platform from, Platform to) {
		final Position fromMid = from.getMidPosition();
		final Position toMid = to.getMidPosition();
		return Arrays.asList(new double[]{fromMid.getX(), fromMid.getY(), fromMid.getZ()}, new double[]{toMid.getX(), toMid.getY(), toMid.getZ()});
	}

	@Nullable
	private static Rail getRail(Data data, Position position1, Position position2) {
		final Map<Position, Rail> railMap = data.positionsToRail.get(position1);
		return railMap == null ? null : railMap.get(position2);
	}

	@Nullable
	private static Position[] getEnds(Platform platform) {
		try {
			return POSITION_1 == null || POSITION_2 == null ? null : new Position[]{(Position) POSITION_1.get(platform), (Position) POSITION_2.get(platform)};
		} catch (Exception e) {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	private static Collection<Vehicle> getVehicles(Siding siding) {
		try {
			return SIDING_VEHICLES == null ? Collections.emptyList() : (Collection<Vehicle>) SIDING_VEHICLES.get(siding);
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	private static JsonArray point(double[] point) {
		final JsonArray jsonArray = new JsonArray();
		for (final double value : point) {
			jsonArray.add(Math.round(value * 10) / 10D);
		}
		return jsonArray;
	}

	@Nullable
	private static Field getField(Class<?> type, String name) {
		try {
			final Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			LOGGER.error("Map layer cannot read {}.{}; that part of the layer stays empty", type.getSimpleName(), name, e);
			return null;
		}
	}

	/**
	 * Platform-to-platform shapes, kept until a rail is added or removed. The searches are the expensive part.
	 */
	public static final class ShapeCache {

		private final Map<String, List<double[]>> segments = new HashMap<>();
		private final Set<String> straight = new HashSet<>();
		private int railsHash;

		private void checkRails(Data data) {
			final int newRailsHash = data.railIdMap.keySet().hashCode();
			if (newRailsHash != railsHash) {
				railsHash = newRailsHash;
				segments.clear();
				straight.clear();
			}
		}
	}
}
