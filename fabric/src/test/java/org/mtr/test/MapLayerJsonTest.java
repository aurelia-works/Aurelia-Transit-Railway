package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.*;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.servlet.MapLayerJson;

public final class MapLayerJsonTest {

	/**
	 * Platform A (x 0 to 20) and platform B (x 120 to 140) joined by a plain rail (x 20 to 120), all at z 0.
	 */
	@Test
	public void routeShapeFollowsTheRailsFromPlatformToPlatform() {
		final Data data = new Data() {
		};
		final Position a1 = new Position(0, 64, 0);
		final Position a2 = new Position(20, 64, 0);
		final Position b1 = new Position(120, 64, 0);
		final Position b2 = new Position(140, 64, 0);
		data.rails.add(Rail.newPlatformRail(a1, Angle.E, a2, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.TRAIN));
		data.rails.add(Rail.newRail(a2, Angle.E, b1, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 80, 80, false, false, true, false, false, TransportMode.TRAIN));
		data.rails.add(Rail.newPlatformRail(b1, Angle.E, b2, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.TRAIN));

		final Station station = new Station(data);
		station.setName("Alpha");
		station.setCorners(new Position(-10, 0, -10), new Position(30, 128, 10));
		data.stations.add(station);

		final Platform platformA = new Platform(a1, a2, TransportMode.TRAIN, data);
		final Platform platformB = new Platform(b1, b2, TransportMode.TRAIN, data);
		data.platforms.add(platformA);
		data.platforms.add(platformB);

		final Route route = new Route(TransportMode.TRAIN, data);
		route.setName("Red Line");
		route.getRoutePlatforms().add(new RoutePlatformData(platformA.getId()));
		route.getRoutePlatforms().add(new RoutePlatformData(platformB.getId()));
		data.routes.add(route);
		data.sync();

		final JsonObject network = MapLayerJson.network(data, new MapLayerJson.ShapeCache());

		final JsonObject stationJson = network.getAsJsonArray("stations").get(0).getAsJsonObject();
		Assertions.assertEquals("Alpha", stationJson.get("name").getAsString());
		Assertions.assertEquals(10, stationJson.get("x").getAsDouble(), 1);
		Assertions.assertEquals(0, stationJson.get("z").getAsDouble(), 1);
		Assertions.assertEquals(route.getHexId(), stationJson.getAsJsonArray("routes").get(0).getAsString());

		final JsonObject routeJson = network.getAsJsonArray("routes").get(0).getAsJsonObject();
		Assertions.assertEquals("Red Line", routeJson.get("name").getAsString());
		Assertions.assertEquals(2, routeJson.getAsJsonArray("platforms").size());
		Assertions.assertEquals(station.getHexId(), routeJson.getAsJsonArray("platforms").get(0).getAsJsonObject().get("stationId").getAsString());

		Assertions.assertTrue(routeJson.get("railShape").getAsBoolean());
		Assertions.assertEquals(0, routeJson.getAsJsonArray("straight").size());
		final JsonArray shape = routeJson.getAsJsonArray("shape");
		Assertions.assertTrue(shape.size() > 10, "shape has " + shape.size() + " points");
		final JsonArray first = shape.get(0).getAsJsonArray();
		final JsonArray last = shape.get(shape.size() - 1).getAsJsonArray();
		Assertions.assertEquals(0, first.get(0).getAsDouble(), 1);
		Assertions.assertEquals(120, last.get(0).getAsDouble(), 1);
		for (int i = 0; i < shape.size(); i++) {
			Assertions.assertEquals(0, shape.get(i).getAsJsonArray().get(2).getAsDouble(), 1);
		}
	}

	@Test
	public void platformsWithoutRailsBetweenThemGiveAStraightLine() {
		final Data data = new Data() {
		};
		final Position a1 = new Position(0, 64, 0);
		final Position a2 = new Position(20, 64, 0);
		final Position b1 = new Position(500, 64, 300);
		final Position b2 = new Position(520, 64, 300);
		data.rails.add(Rail.newPlatformRail(a1, Angle.E, a2, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.TRAIN));
		data.rails.add(Rail.newPlatformRail(b1, Angle.E, b2, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.TRAIN));
		final Platform platformA = new Platform(a1, a2, TransportMode.TRAIN, data);
		final Platform platformB = new Platform(b1, b2, TransportMode.TRAIN, data);
		data.platforms.add(platformA);
		data.platforms.add(platformB);
		final Route route = new Route(TransportMode.TRAIN, data);
		route.getRoutePlatforms().add(new RoutePlatformData(platformA.getId()));
		route.getRoutePlatforms().add(new RoutePlatformData(platformB.getId()));
		data.routes.add(route);
		data.sync();

		final JsonObject routeJson = MapLayerJson.network(data, new MapLayerJson.ShapeCache()).getAsJsonArray("routes").get(0).getAsJsonObject();
		Assertions.assertFalse(routeJson.get("railShape").getAsBoolean());
		Assertions.assertEquals(2, routeJson.getAsJsonArray("shape").size());
		Assertions.assertEquals("[[0,1]]", routeJson.getAsJsonArray("straight").toString());
	}

	@Test
	public void noSidingsMeansNoVehicles() {
		final Data data = new Data() {
		};
		data.sync();
		Assertions.assertEquals(0, MapLayerJson.vehicles(data).getAsJsonArray("vehicles").size());
	}
}
