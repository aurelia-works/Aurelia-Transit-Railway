package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.station.AirportRails.AirRail;
import com.aureliatransit.architecture.station.AirportRails.Pos;
import com.aureliatransit.architecture.station.AirportRoute.Kind;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.operation.DeleteDataRequest;
import org.mtr.core.operation.DepotOperationByIds;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.core.operation.UpdateDataResponse;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.packet.PacketUpdateData;

/**
 * An airport's gate loops in MTR, in four update_data steps as MtrRails does them: the rails; then the gate platforms, named
 * "Gate n", the airplane route through them, the depot (frequencies set, or MTR never sends a plane) and the sidings' cars; then
 * generate_by_depot_ids for the depot. Nothing happens without MTR, or with fewer than two gate platforms. An undo that runs
 * before MTR answers wins: the route and depot are then never sent. A changed MTR API is logged, never thrown into the build's tick.
 */
final class AirportMtr {

	/** A plane's time at the gate: the bridge docks, the door opens and closes, the bridge pulls back (MTR's default is 10 s). */
	static final long GATE_DWELL_MILLIS = 45_000;

	private AirportMtr() {
	}

	static void lay(ServerWorld world, List<AirRail> rails, UndoLog log, Consumer<Text> out) {
		if (!MtrRailContract.available() || rails.isEmpty()) {
			return;
		}
		try {
			send(world, rails, log, out);
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not lay the airport's MTR route", e);
			out.accept(Text.literal("Airport: MTR would not take the plane route (see the log); the airport is built without planes.").formatted(Formatting.YELLOW));
		}
	}

	private static void send(ServerWorld world, List<AirRail> rails, UndoLog log, Consumer<Text> out) {
		final org.mtr.mapping.holder.MinecraftServer server = new org.mtr.mapping.holder.MinecraftServer(world.getServer());
		final org.mtr.mapping.holder.World mtrWorld = new org.mtr.mapping.holder.World(world);
		final org.mtr.mapping.holder.ServerWorld serverWorld = new org.mtr.mapping.holder.ServerWorld(world);
		final UpdateDataRequest request = new UpdateDataRequest(new MinecraftClientData());
		int invalid = 0;
		for (final AirRail rail : rails) {
			final Rail built = rail(rail);
			if (built.isValid()) {
				request.addRail(built);
				log.addRail(block(rail.from()), block(rail.to()));
			} else {
				invalid++;
			}
		}
		if (invalid > 0) {
			out.accept(Text.literal("Airport: " + invalid + " rails MTR would not take; the loop is open there.").formatted(Formatting.YELLOW));
		}
		Init.sendMessageC2S("update_data", server, mtrWorld, request, (UpdateDataResponse response) -> {
			PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, response);
			if (log.airportsTaken()) {
				return;
			}
			try {
				depot(server, mtrWorld, serverWorld, rails, response, log, out);
			} catch (RuntimeException | LinkageError e) {
				AureliaTransitArchitecture.LOGGER.warn("Could not make the airport's MTR route and depot", e);
				out.accept(Text.literal("Airport: MTR would not take the route and depot (see the log).").formatted(Formatting.YELLOW));
			}
		}, UpdateDataResponse.class);
	}

	/** The MTR ids one airport build added: its gate platforms, sidings, route and depot. */
	record Ids(List<Long> platforms, List<Long> sidings, long route, long depot) {
	}

	/** Deletes what the airport builds added to MTR, from the undo log. */
	static void remove(ServerWorld world, List<Ids> airports) {
		if (!MtrRailContract.available() || airports.isEmpty()) {
			return;
		}
		final DeleteDataRequest request = new DeleteDataRequest();
		for (final Ids ids : airports) {
			ids.platforms().forEach(request::addPlatformId);
			ids.sidings().forEach(request::addSidingId);
			request.addRouteId(ids.route());
			request.addDepotId(ids.depot());
		}
		try {
			MtrStations.delete(world, request);
		} catch (RuntimeException | LinkageError e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not delete the MTR route and depot of an undone airport", e);
		}
	}

	private static BlockPos block(Pos p) {
		return new BlockPos(p.x(), p.y(), p.z());
	}

	private static void depot(org.mtr.mapping.holder.MinecraftServer server, org.mtr.mapping.holder.World mtrWorld,
			org.mtr.mapping.holder.ServerWorld serverWorld, List<AirRail> rails, UpdateDataResponse response, UndoLog log, Consumer<Text> out) {
		final List<Platform> platforms = MtrRails.platforms(response);
		final List<Siding> sidings = sidings(response);
		final UpdateDataRequest next = new UpdateDataRequest(new MinecraftClientData());
		final Route route = new Route(TransportMode.AIRPLANE, new MinecraftClientData());
		route.setName("Airport");
		final List<Long> platformIds = new ArrayList<>();
		final List<Long> sidingIds = new ArrayList<>();
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		cars.add(new VehicleCar("a320", 30, 2, -14.25, -2, 0, 0));
		int gates = 0;
		int minX = Integer.MAX_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (final AirRail rail : rails) {
			minX = Math.min(minX, Math.min(rail.from().x(), rail.to().x()));
			minZ = Math.min(minZ, Math.min(rail.from().z(), rail.to().z()));
			maxX = Math.max(maxX, Math.max(rail.from().x(), rail.to().x()));
			maxZ = Math.max(maxZ, Math.max(rail.from().z(), rail.to().z()));
			if (rail.kind() == Kind.GATE) {
				for (final Platform platform : platforms) {
					if (platform.containsPos(position(rail.from())) && platform.containsPos(position(rail.to()))) {
						platform.setName("Gate " + (rail.gate() + 1));
						// long enough for the bridge to dock, the door to open and close and the bridge to pull back
						platform.setDwellTime(GATE_DWELL_MILLIS);
						next.addPlatform(platform);
						platformIds.add(platform.getId());
						route.getRoutePlatforms().add(new RoutePlatformData(platform.getId()));
						gates++;
						break;
					}
				}
			} else if (rail.kind() == Kind.SIDING) {
				for (final Siding siding : sidings) {
					if (siding.containsPos(position(rail.from())) && siding.containsPos(position(rail.to()))) {
						siding.setVehicleCars(cars);
						siding.setMaxVehicles(1);
						next.addSiding(siding);
						sidingIds.add(siding.getId());
						break;
					}
				}
			}
		}
		if (gates < 2) {
			out.accept(Text.literal("Airport: MTR needs two gate platforms for a route; " + gates + " formed.").formatted(Formatting.YELLOW));
			return;
		}
		// a repeating route must end where it starts: MTR jumps a plane from the last platform back to the first, so ending on the
		// last gate made it vanish there and reappear at gate 1; ending on gate 1 it flies there
		route.getRoutePlatforms().add(new RoutePlatformData(platformIds.get(0)));
		final Depot depot = new Depot(TransportMode.AIRPLANE, new MinecraftClientData());
		depot.setName("Airport");
		depot.setCorners(new Position(minX - 20, -64, minZ - 20), new Position(maxX + 20, 320, maxZ + 20));
		depot.getRouteIds().add(route.getId());
		depot.setCruisingAltitude(rails.get(0).from().y() + 150L);
		// frequencies default to 0 in every hour: without these the depot never sends a plane out
		depot.setUseRealTime(false);
		// a plane flies gate to gate for good; without this a depot sends each plane round once and then back to its siding
		depot.setRepeatInfinitely(true);
		for (int hour = 0; hour < 24; hour++) {
			depot.setFrequency(hour, 4);
		}
		next.addRoute(route).addDepot(depot);
		if (!log.addAirport(new Ids(platformIds, sidingIds, route.getId(), depot.getId()))) {
			// undone between the two answers: send nothing, the undo has removed the rails these hang on
			return;
		}
		Init.sendMessageC2S("update_data", server, mtrWorld, next, (UpdateDataResponse named) -> {
			PacketUpdateData.sendDirectlyToClientDepotUpdate(serverWorld, named);
			final DepotOperationByIds generate = new DepotOperationByIds();
			generate.addDepotId(depot.getId());
			Init.sendMessageC2S("generate_by_depot_ids", server, mtrWorld, generate, (UpdateDataResponse generated) -> out.accept(
					Text.literal("Airport: MTR route and depot made; the depot is generating the planes' path.").formatted(Formatting.GREEN)),
					UpdateDataResponse.class);
		}, UpdateDataResponse.class);
	}

	private static Rail rail(AirRail r) {
		final Position from = position(r.from());
		final Position to = position(r.to());
		final Angle a = Angle.valueOf(r.startDir().name());
		final Angle b = Angle.valueOf(r.endDir().name());
		// km/h each way, from -> to then back: a plane rolls into a stand slowly and is pushed back out at walking pace; on the push-back
		// stretch it is pushed tail-first, stops, and pulls forward again; it taxis at about 20 knots and only the runway is fast
		return switch (r.kind()) {
			case GATE -> with(Rail.newPlatformRail(from, a, to, b, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.AIRPLANE), 15, 5, false);
			case SIDING -> with(Rail.newSidingRail(from, a, to, b, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), TransportMode.AIRPLANE), 15, 5, false);
			case LEAD_IN -> plain(from, a, to, b, 20, 5, false);
			case PUSH_BACK -> with(plain(from, a, to, b, 5, 20, false), 5, 20, true);
			case LINK -> plain(from, a, to, b, 50, 50, false);
			case TAKE_OFF, LANDING -> plain(from, a, to, b, 300, 0, true);
			case TAXI -> plain(from, a, to, b, 40, 40, false);
		};
	}

	private static Rail plain(Position from, Angle a, Position to, Angle b, long there, long back, boolean runway) {
		return Rail.newRail(from, a, to, b, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), there, back, false, false, true, runway, false,
				TransportMode.AIRPLANE);
	}

	/**
	 * The rail with its speeds set and, for the push-back, turn-back on (a plane may reverse at either end), through its saved form:
	 * MTR's factories fix platform and siding speeds at 80 km/h and give turn-back rails no acceleration.
	 */
	private static Rail with(Rail rail, long there, long back, boolean turnBack) {
		final JsonObject json = Utilities.getJsonObjectFromData(rail);
		json.addProperty("speedLimit1", there);
		json.addProperty("speedLimit2", back);
		json.addProperty("canAccelerate", true);
		json.addProperty("canTurnBack", turnBack);
		return new Rail(new JsonReader(json));
	}

	private static Position position(Pos p) {
		return new Position(p.x(), p.y(), p.z());
	}

	/** The sidings MTR formed or changed ({@code getSidings()} is package private; its schema field is protected, as the platforms'). */
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
