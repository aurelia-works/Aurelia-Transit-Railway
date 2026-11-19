package org.mtr.perfbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.operation.DepotOperationByIds;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.operation.UpdateDataResponse;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Film only (perfbench, never shipped): lays a train line for the trailer from a JSON spec, as AirportMtr does for planes.
 * Rails first (plain, siding, platform or turn-back), then the siding's cars, the route through the given platform ids and the
 * depot, then generate_by_depot_ids. Spec: {"rails":[{"from":[x,y,z],"a":"E","to":[x,y,z],"b":"W","speed":160,"type":"plain"}],
 * "route":{"name":..,"color":..,"platforms":[id,..]},"depot":{"name":..,"corners":[x1,z1,x2,z2],"freq":6,"accel":1.2e-5,
 * "cars":[["class_802_gwr_mini_cab_1",19,2,-5.5,5.5,0,0],..]}}.
 */
final class FilmLine {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR film line");

	private FilmLine() {
	}

	static void run(MinecraftServer server, String specPath) {
		final JsonObject spec;
		try {
			spec = JsonParser.parseString(Files.readString(Path.of(specPath), StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not read {}", specPath, e);
			return;
		}
		server.execute(() -> {
			try {
				send(server.getOverworld(), spec);
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("Film line failed", e);
			}
		});
	}

	private static void send(ServerWorld world, JsonObject spec) {
		final org.mtr.mapping.holder.MinecraftServer server = new org.mtr.mapping.holder.MinecraftServer(world.getServer());
		final org.mtr.mapping.holder.World mtrWorld = new org.mtr.mapping.holder.World(world);
		final org.mtr.mapping.holder.ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld(world);
		final UpdateDataRequest rails = new UpdateDataRequest(new MinecraftClientData());
		int count = 0;
		if (spec.has("rails")) {
			for (final JsonElement element : spec.getAsJsonArray("rails")) {
				final Rail rail = rail(element.getAsJsonObject());
				if (rail.isValid()) {
					rails.addRail(rail);
					count++;
				} else {
					LOGGER.warn("Invalid rail {}", element);
				}
			}
		}
		LOGGER.info("Film line: sending {} rails", count);
		Init.sendMessageC2S("update_data", server, mtrWorld, rails, (UpdateDataResponse response) -> {
			PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, response);
			final List<Siding> sidings = sidings(response);
			LOGGER.info("Film line: rails in, {} sidings formed", sidings.size());
			if (!spec.has("depot")) {
				return;
			}
			final JsonObject depotSpec = spec.getAsJsonObject("depot");
			final JsonObject routeSpec = spec.getAsJsonObject("route");
			final UpdateDataRequest next = new UpdateDataRequest(new MinecraftClientData());
			final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
			for (final JsonElement car : depotSpec.getAsJsonArray("cars")) {
				final JsonArray c = car.getAsJsonArray();
				cars.add(new VehicleCar(c.get(0).getAsString(), c.get(1).getAsDouble(), c.get(2).getAsDouble(), c.get(3).getAsDouble(),
						c.get(4).getAsDouble(), c.get(5).getAsDouble(), c.get(6).getAsDouble()));
			}
			for (final Siding siding : sidings) {
				siding.setVehicleCars(cars);
				siding.setMaxVehicles(depotSpec.has("max") ? depotSpec.get("max").getAsInt() : 1);
				final double accel = depotSpec.has("accel") ? depotSpec.get("accel").getAsDouble() : Siding.ACCELERATION_DEFAULT;
				siding.setAcceleration(accel);
				siding.setDeceleration(accel);
				next.addSiding(siding);
			}
			final Route route = new Route(TransportMode.TRAIN, new MinecraftClientData());
			route.setName(routeSpec.get("name").getAsString());
			route.setColor(Integer.decode(routeSpec.get("color").getAsString()));
			for (final JsonElement id : routeSpec.getAsJsonArray("platforms")) {
				route.getRoutePlatforms().add(new RoutePlatformData(id.getAsLong()));
			}
			final Depot depot = new Depot(TransportMode.TRAIN, new MinecraftClientData());
			depot.setName(depotSpec.get("name").getAsString());
			final JsonArray k = depotSpec.getAsJsonArray("corners");
			depot.setCorners(new Position(k.get(0).getAsLong(), -64, k.get(1).getAsLong()), new Position(k.get(2).getAsLong(), 320, k.get(3).getAsLong()));
			depot.getRouteIds().add(route.getId());
			depot.setUseRealTime(false);
			depot.setRepeatInfinitely(true);
			final int freq = depotSpec.has("freq") ? depotSpec.get("freq").getAsInt() : 6;
			for (int hour = 0; hour < 24; hour++) {
				depot.setFrequency(hour, freq);
			}
			next.addRoute(route).addDepot(depot);
			Init.sendMessageC2S("update_data", server, mtrWorld, next, (UpdateDataResponse named) -> {
				PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, named);
				final DepotOperationByIds generate = new DepotOperationByIds();
				generate.addDepotId(depot.getId());
				Init.sendMessageC2S("generate_by_depot_ids", server, mtrWorld, generate, (UpdateDataResponse generated) -> {
					PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, generated);
					LOGGER.info("Film line: route {} and depot {} made, path generating", route.getId(), depot.getId());
				}, UpdateDataResponse.class);
			}, UpdateDataResponse.class);
		}, UpdateDataResponse.class);
	}

	private static Rail rail(JsonObject r) {
		final Position from = position(r.getAsJsonArray("from"));
		final Position to = position(r.getAsJsonArray("to"));
		final Angle a = Angle.valueOf(r.get("a").getAsString());
		final Angle b = Angle.valueOf(r.get("b").getAsString());
		final long speed = r.has("speed") ? r.get("speed").getAsLong() : 80;
		final String type = r.has("type") ? r.get("type").getAsString() : "plain";
		final ObjectArrayList<String> styles = ObjectArrayList.of(r.has("style") ? r.get("style").getAsString() : "default");
		return switch (type) {
			case "siding" -> speeds(Rail.newSidingRail(from, a, to, b, Rail.Shape.QUADRATIC, 0, styles, TransportMode.TRAIN), speed, false);
			case "platform" -> speeds(Rail.newPlatformRail(from, a, to, b, Rail.Shape.QUADRATIC, 0, styles, TransportMode.TRAIN), speed, false);
			case "turnback" -> speeds(Rail.newTurnBackRail(from, a, to, b, Rail.Shape.QUADRATIC, 0, styles, TransportMode.TRAIN), speed, true);
			default -> Rail.newRail(from, a, to, b, Rail.Shape.QUADRATIC, 0, styles, speed, speed, false, false, true, false, true, TransportMode.TRAIN);
		};
	}

	/** Speeds and turn-back set through the saved form: MTR's factories fix siding and platform speeds at 80 km/h. */
	private static Rail speeds(Rail rail, long speed, boolean turnBack) {
		final org.mtr.libraries.com.google.gson.JsonObject json = Utilities.getJsonObjectFromData(rail);
		json.addProperty("speedLimit1", speed);
		json.addProperty("speedLimit2", speed);
		json.addProperty("canAccelerate", true);
		json.addProperty("canTurnBack", turnBack);
		return new Rail(new JsonReader(json));
	}

	private static Position position(JsonArray p) {
		return new Position(p.get(0).getAsLong(), p.get(1).getAsLong(), p.get(2).getAsLong());
	}

	@SuppressWarnings("unchecked")
	private static List<Siding> sidings(UpdateDataResponse response) {
		try {
			final Field field = org.mtr.core.generated.operation.UpdateDataResponseSchema.class.getDeclaredField("sidings");
			field.setAccessible(true);
			return (List<Siding>) field.get(response);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return List.of();
		}
	}
}
