package org.mtr.mod.passenger;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Station;
import org.mtr.core.data.Vehicle;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.Init;
import org.mtr.core.Main;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs {@link PassengerNetwork} once a second on each dimension's simulation thread, keeps the caps in the overworld's
 * data folder ({@code atr_passengers.dat}), and tells every player the caps and the measured cost.
 * Only operators may change the caps: they decide how much work the server does.
 */
public final class PassengerService {

	public static final Identifier SYNC = new Identifier("mtr", "atr_passengers");
	public static final Identifier SET = new Identifier("mtr", "atr_passengers_set");
	private static final String FILE = "atr_passengers";
	private static final int INTERVAL_TICKS = 20;

	/** What every player sees on the panel. Cost is the average simulation-thread time per update, in microseconds. */
	public record Stats(int waiting, int riding, long boarded, long alighted, long costMicros) {
		public static final Stats NONE = new Stats(0, 0, 0, 0, 0);
	}

	private static final Map<String, PassengerNetwork> NETWORKS = new ConcurrentHashMap<>();
	private static final Map<String, Stats> STATS = new ConcurrentHashMap<>();
	private static final Map<String, Long> LAST_MILLIS = new ConcurrentHashMap<>();
	private static final Map<String, Double> COST = new ConcurrentHashMap<>();
	@Nullable
	private static Field vehiclesField;
	private static volatile PassengerNetwork.Settings settings = PassengerNetwork.Settings.DEFAULT;
	private static int ticks;

	private PassengerService() {
	}

	public static void registerServer() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> settings = state(server).settings);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NETWORKS.clear();
			STATS.clear();
			LAST_MILLIS.clear();
			COST.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(PassengerService::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final PassengerNetwork.Settings requested = read(buf);
			server.execute(() -> set(server, player, requested));
		});
	}

	/** Client: asks the server for new caps. */
	public static PacketByteBuf request(PassengerNetwork.Settings requested) {
		final PacketByteBuf buf = PacketByteBufs.create();
		write(buf, requested);
		return buf;
	}

	public static void write(PacketByteBuf buf, PassengerNetwork.Settings value) {
		buf.writeBoolean(value.enabled());
		buf.writeVarInt(value.perPlatformPerMinute());
		buf.writeVarInt(value.maxWaitingPerPlatform());
		buf.writeVarInt(value.maxTotal());
		buf.writeVarInt(value.seatsPerCar());
	}

	public static PassengerNetwork.Settings read(PacketByteBuf buf) {
		return new PassengerNetwork.Settings(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()).clamped();
	}

	private static void set(MinecraftServer server, ServerPlayerEntity player, PassengerNetwork.Settings requested) {
		if (!player.hasPermissionLevel(2)) {
			return;
		}
		final State state = state(server);
		state.settings = requested;
		state.markDirty();
		settings = requested;
		if (!requested.enabled()) {
			NETWORKS.values().forEach(PassengerNetwork::clear);
		}
		server.getPlayerManager().getPlayerList().forEach(PassengerService::send);
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % INTERVAL_TICKS != 0) {
			return;
		}
		final ObjectImmutableList<Simulator> simulators = simulators();
		if (simulators != null && settings.enabled()) {
			for (final Simulator simulator : simulators) {
				simulator.run(() -> update(simulator));
			}
		}
		server.getPlayerManager().getPlayerList().forEach(PassengerService::send);
	}

	/** Simulation thread. */
	private static void update(Simulator simulator) {
		final long start = System.nanoTime();
		try {
			final String dimension = simulator.dimension;
			final PassengerNetwork network = NETWORKS.computeIfAbsent(dimension, key -> new PassengerNetwork());
			final PassengerNetwork.Settings current = settings;
			final long now = System.currentTimeMillis();
			final long last = LAST_MILLIS.getOrDefault(dimension, now);
			LAST_MILLIS.put(dimension, now);
			final double minutes = Math.min(1, (now - last) / 60_000D);

			final Map<Long, long[]> destinations = destinationsByPlatform(simulator);
			final Random random = new Random();
			destinations.forEach((platformId, stations) -> network.spawn(platformId, stations, minutes, current, now, random));
			network.expire(now);

			final Set<Long> vehicleIds = new HashSet<>();
			int riding = 0;
			for (final Vehicle vehicle : vehicles(simulator)) {
				vehicleIds.add(vehicle.getId());
				if (!vehicle.isMoving() && vehicle.getIsOnRoute()) {
					final long platformId = vehicle.vehicleExtraData.getThisPlatformId();
					final Platform platform = simulator.platformIdMap.get(platformId);
					final Route route = simulator.routeIdMap.get(vehicle.vehicleExtraData.getThisRouteId());
					if (platform != null && platform.area != null && route != null) {
						final int cars = Math.max(1, vehicle.vehicleExtraData.immutableVehicleCars.size());
						network.exchange(vehicle.getId(), platformId, platform.area.getId(), upcomingStations(route, platformId, vehicle.vehicleExtraData.getStopIndex()), cars, current);
					}
				}
				riding += network.ridingOn(vehicle.getId());
			}
			network.retain(vehicleIds, destinations.keySet());

			final double micros = (System.nanoTime() - start) / 1000D;
			final double cost = COST.getOrDefault(dimension, micros) * 0.9 + micros * 0.1;
			COST.put(dimension, cost);
			STATS.put(dimension, new Stats(network.waitingTotal(), riding, network.boardedTotal(), network.alightedTotal(), Math.round(cost)));
		} catch (Exception e) {
			Init.LOGGER.error("Passengers could not update", e);
		}
	}

	/** Every platform a route stops at, with the stations reachable from it by staying on that route. */
	static Map<Long, long[]> destinationsByPlatform(Simulator simulator) {
		final Map<Long, Set<Long>> sets = new HashMap<>();
		for (final Route route : simulator.routes) {
			final List<RoutePlatformData> stops = route.getRoutePlatforms();
			for (int i = 0; i < stops.size(); i++) {
				final Platform platform = stops.get(i).platform;
				if (platform == null) {
					continue;
				}
				final Set<Long> reachable = sets.computeIfAbsent(platform.getId(), id -> new HashSet<>());
				final long here = platform.area == null ? 0 : platform.area.getId();
				for (int j = i + 1; j < stops.size(); j++) {
					final Platform later = stops.get(j).platform;
					if (later != null && later.area != null && later.area.getId() != here) {
						reachable.add(later.area.getId());
					}
				}
			}
		}
		final Map<Long, long[]> result = new HashMap<>();
		sets.forEach((platformId, stations) -> {
			if (!stations.isEmpty()) {
				result.put(platformId, stations.stream().mapToLong(Long::longValue).toArray());
			}
		});
		return result;
	}

	/** Stations after this platform on the route. The stop index picks the right visit when a route calls twice. */
	static Set<Long> upcomingStations(Route route, long platformId, int stopIndex) {
		final List<RoutePlatformData> stops = route.getRoutePlatforms();
		int index = -1;
		for (int i = Math.max(0, stopIndex - 1); i < stops.size() && index < 0; i++) {
			if (stops.get(i).platform != null && stops.get(i).platform.getId() == platformId) {
				index = i;
			}
		}
		for (int i = 0; i < stops.size() && index < 0; i++) {
			if (stops.get(i).platform != null && stops.get(i).platform.getId() == platformId) {
				index = i;
			}
		}
		final Set<Long> upcoming = new HashSet<>();
		for (int i = index + 1; index >= 0 && i < stops.size(); i++) {
			final Platform later = stops.get(i).platform;
			final Station station = later == null ? null : later.area;
			if (station != null) {
				upcoming.add(station.getId());
			}
		}
		return upcoming;
	}

	private static List<Vehicle> vehicles(Simulator simulator) {
		final List<Vehicle> result = new java.util.ArrayList<>();
		try {
			if (vehiclesField == null) {
				vehiclesField = Siding.class.getDeclaredField("vehicles");
				vehiclesField.setAccessible(true);
			}
			for (final Siding siding : simulator.sidings) {
				if (vehiclesField.get(siding) instanceof Collection<?> collection) {
					for (final Object object : collection) {
						if (object instanceof Vehicle vehicle) {
							result.add(vehicle);
						}
					}
				}
			}
		} catch (ReflectiveOperationException e) {
			Init.LOGGER.error("Passengers cannot read trains", e);
		}
		return result;
	}

	private static void send(ServerPlayerEntity player) {
		final PacketByteBuf buf = PacketByteBufs.create();
		write(buf, settings);
		final Stats stats = STATS.getOrDefault(Init.getWorldId(new org.mtr.mapping.holder.World(player.getServerWorld())), Stats.NONE);
		buf.writeVarInt(stats.waiting());
		buf.writeVarInt(stats.riding());
		buf.writeVarLong(stats.boarded());
		buf.writeVarLong(stats.alighted());
		buf.writeVarLong(stats.costMicros());
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private static ObjectImmutableList<Simulator> simulators() {
		try {
			final Field mainField = Init.class.getDeclaredField("main");
			mainField.setAccessible(true);
			final Main main = (Main) mainField.get(null);
			if (main == null) {
				return null;
			}
			final Field field = Main.class.getDeclaredField("simulators");
			field.setAccessible(true);
			return (ObjectImmutableList<Simulator>) field.get(main);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(State::fromNbt, () -> new State(PassengerNetwork.Settings.DEFAULT), FILE);
	}

	private static final class State extends PersistentState {

		private PassengerNetwork.Settings settings;

		private State(PassengerNetwork.Settings settings) {
			this.settings = settings;
		}

		private static State fromNbt(NbtCompound nbt) {
			final PassengerNetwork.Settings fallback = PassengerNetwork.Settings.DEFAULT;
			return new State(new PassengerNetwork.Settings(
					nbt.getBoolean("enabled"),
					nbt.contains("per_minute") ? nbt.getInt("per_minute") : fallback.perPlatformPerMinute(),
					nbt.contains("max_waiting") ? nbt.getInt("max_waiting") : fallback.maxWaitingPerPlatform(),
					nbt.contains("max_total") ? nbt.getInt("max_total") : fallback.maxTotal(),
					nbt.contains("seats") ? nbt.getInt("seats") : fallback.seatsPerCar()
			).clamped());
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			nbt.putBoolean("enabled", settings.enabled());
			nbt.putInt("per_minute", settings.perPlatformPerMinute());
			nbt.putInt("max_waiting", settings.maxWaitingPerPlatform());
			nbt.putInt("max_total", settings.maxTotal());
			nbt.putInt("seats", settings.seatsPerCar());
			return nbt;
		}
	}
}
