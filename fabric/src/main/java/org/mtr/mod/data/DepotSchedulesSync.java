package org.mtr.mod.data;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.PersistentState;
import org.mtr.core.Main;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Siding;
import org.mtr.core.operation.ArrivalResponse;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.Init;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server side of {@link DepotSchedules}: saves them ({@code atr_depot_schedules.dat}), writes the plan for today into
 * each depot with a weekly timetable when the day type changes, cancels and restores runs on request, and tells clients
 * what each depot has so the panel can show it.
 */
public final class DepotSchedulesSync {

	public static final Identifier SYNC = new Identifier("mtr", "atr_depot_schedules");
	public static final Identifier ACTION = new Identifier("mtr", "atr_depot_schedule_action");
	private static final String FILE = "atr_depot_schedules";
	private static final int CHECK_EVERY_TICKS = 20;
	/** When the last stop of a cancelled run can't be found, it stays cancelled this long after its first stop. */
	private static final long FALLBACK_CANCEL_MILLIS = 2 * 60 * 60 * 1000;

	public enum Action {ENABLE, DISABLE, SAVE_WEEKDAY, SAVE_WEEKEND, CANCEL_NEXT, RESTORE}

	/** What the panel shows for a depot. */
	public record Summary(boolean enabled, boolean hasWeekday, boolean hasWeekend, int cancelled, String today) {
	}

	private static volatile DepotSchedules current = new DepotSchedules();
	private static volatile Map<Long, Summary> client = Map.of();
	/** The day type last written into each depot, so a plan is only written when the day type changes. */
	private static final Map<Long, DepotSchedules.DayType> APPLIED = new HashMap<>();
	private static int ticks;

	private DepotSchedulesSync() {
	}

	public static boolean isCancelled(long sidingId, long departureIndex, long timeMillis) {
		return current.isCancelled(sidingId, departureIndex, timeMillis);
	}

	public static void registerServer() {
		ServerTickEvents.END_SERVER_TICK.register(DepotSchedulesSync::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sendAll(server)));
		ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, response) -> {
			final long depotId = buf.readLong();
			final int action = buf.readVarInt();
			server.execute(() -> act(server, player, depotId, action));
		});
	}

	public static PacketByteBuf request(long depotId, Action action) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(depotId);
		buf.writeVarInt(action.ordinal());
		return buf;
	}

	/** Each dimension sends its own depots, so a packet adds to what the client has. */
	public static void setClient(Map<Long, Summary> value) {
		final Map<Long, Summary> merged = new HashMap<>(client);
		merged.putAll(value);
		client = Map.copyOf(merged);
	}

	@Nullable
	public static Summary client(long depotId) {
		return client.get(depotId);
	}

	public static Map<Long, Summary> read(PacketByteBuf buf) {
		final int count = buf.readVarInt();
		final Map<Long, Summary> read = new HashMap<>();
		for (int i = 0; i < count; i++) {
			read.put(buf.readLong(), new Summary(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readString(16)));
		}
		return read;
	}

	private static void tick(MinecraftServer server) {
		if (++ticks % CHECK_EVERY_TICKS != 0) {
			return;
		}
		final State state = state(server);
		current = state.schedules;
		if (state.schedules.expire(System.currentTimeMillis())) {
			state.markDirty();
			sendAll(server);
		}
		final long worldDay = server.getOverworld().getTimeOfDay() / 24000;
		final LocalDate today = LocalDate.now();
		final Map<Long, DepotSchedules.Schedule> schedules = new HashMap<>(state.schedules.all());
		final ObjectImmutableList<Simulator> simulators = simulators();
		if (simulators == null || schedules.isEmpty()) {
			return;
		}
		for (final Simulator simulator : simulators) {
			simulator.run(() -> schedules.forEach((depotId, schedule) -> {
				final Depot depot = simulator.depotIdMap.get(depotId);
				if (depot == null || !schedule.enabled() || DepotSuspension.isPaused(depotId)) {
					return;
				}
				final DepotSchedules.DayType dayType = DepotSchedules.dayType(depot.getUseRealTime(), today, worldDay);
				final DepotSchedules.Plan plan = schedule.planFor(dayType);
				if (plan != null && APPLIED.put(depotId, dayType) != dayType) {
					write(depot, plan);
				}
			}));
		}
	}

	private static void act(MinecraftServer server, ServerPlayerEntity player, long depotId, int actionIndex) {
		final GameMode gameMode = player.interactionManager.getGameMode();
		if (actionIndex < 0 || actionIndex >= Action.values().length || gameMode != GameMode.CREATIVE && gameMode != GameMode.SURVIVAL) {
			return;
		}
		final Action action = Action.values()[actionIndex];
		final State state = state(server);
		final ObjectImmutableList<Simulator> simulators = simulators();
		if (simulators == null) {
			return;
		}
		for (final Simulator simulator : simulators) {
			simulator.run(() -> {
				final Depot depot = simulator.depotIdMap.get(depotId);
				if (depot == null) {
					return;
				}
				final List<Long> sidingIds = new ArrayList<>();
				depot.savedRails.forEach(siding -> sidingIds.add(siding.getId()));
				switch (action) {
					case ENABLE, DISABLE -> {
						APPLIED.remove(depotId);
						server.execute(() -> state.schedules.setEnabled(depotId, action == Action.ENABLE));
					}
					case SAVE_WEEKDAY, SAVE_WEEKEND -> {
						final DepotSchedules.Plan plan = capture(depot);
						APPLIED.remove(depotId);
						server.execute(() -> state.schedules.savePlan(depotId, action == Action.SAVE_WEEKDAY ? DepotSchedules.DayType.WEEKDAY : DepotSchedules.DayType.WEEKEND, plan));
					}
					case CANCEL_NEXT -> cancelNext(simulator, depot, state.schedules);
					case RESTORE -> state.schedules.restore(sidingIds);
				}
				server.execute(() -> {
					state.markDirty();
					sendAll(server);
				});
			});
		}
	}

	/** Cancels the depot's next run to reach its first stop, until it would have left its last stop. */
	private static void cancelNext(Simulator simulator, Depot depot, DepotSchedules schedules) {
		final Route route = depot.routes.isEmpty() ? null : depot.routes.get(0);
		if (route == null || route.getRoutePlatforms().isEmpty()) {
			return;
		}
		final Platform first = route.getRoutePlatforms().get(0).platform;
		final Route lastRoute = depot.routes.get(depot.routes.size() - 1);
		final Platform last = lastRoute.getRoutePlatforms().isEmpty() ? null : lastRoute.getRoutePlatforms().get(lastRoute.getRoutePlatforms().size() - 1).platform;
		if (first == null) {
			return;
		}
		final long now = simulator.getCurrentMillis();
		Siding nextSiding = null;
		ArrivalResponse next = null;
		for (final Siding siding : depot.savedRails) {
			final ObjectArrayList<ArrivalResponse> arrivals = new ObjectArrayList<>();
			siding.getArrivals(now, first, 16, arrivals);
			for (final ArrivalResponse arrival : arrivals) {
				if (arrival.getArrival() > now && !schedules.isCancelled(siding.getId(), arrival.getDepartureIndex(), arrival.getArrival()) && (next == null || arrival.getArrival() < next.getArrival())) {
					next = arrival;
					nextSiding = siding;
				}
			}
		}
		if (next == null) {
			return;
		}
		long until = next.getArrival() + FALLBACK_CANCEL_MILLIS;
		if (last != null) {
			final ObjectArrayList<ArrivalResponse> arrivals = new ObjectArrayList<>();
			nextSiding.getArrivals(now, last, 64, arrivals);
			long end = Long.MAX_VALUE;
			for (final ArrivalResponse arrival : arrivals) {
				if (arrival.getDepartureIndex() == next.getDepartureIndex() && arrival.getDeparture() >= next.getArrival() && arrival.getDeparture() < end) {
					end = arrival.getDeparture();
				}
			}
			if (end != Long.MAX_VALUE) {
				until = end + 60_000;
			}
		}
		schedules.cancel(nextSiding.getId(), (int) next.getDepartureIndex(), until);
	}

	private static DepotSchedules.Plan capture(Depot depot) {
		final long[] departures = depot.getRealTimeDepartures().toLongArray();
		final int[] frequencies = new int[24];
		for (int hour = 0; hour < 24; hour++) {
			frequencies[hour] = (int) depot.getFrequency(hour);
		}
		return new DepotSchedules.Plan(depot.getUseRealTime(), departures, frequencies);
	}

	private static void write(Depot depot, DepotSchedules.Plan plan) {
		depot.setUseRealTime(plan.realTime());
		depot.getRealTimeDepartures().clear();
		for (final long departure : plan.departures()) {
			depot.getRealTimeDepartures().add(departure);
		}
		for (int hour = 0; hour < 24; hour++) {
			depot.setFrequency(hour, plan.frequencies()[hour]);
		}
		depot.generatePlatformDirectionsAndWriteDeparturesToSidings();
	}

	/** Each simulator (dimension) collects its depots on its own thread; the server thread then sends them. */
	private static void sendAll(MinecraftServer server) {
		final ObjectImmutableList<Simulator> simulators = simulators();
		if (simulators == null) {
			return;
		}
		final long worldDay = server.getOverworld().getTimeOfDay() / 24000;
		for (final Simulator simulator : simulators) {
			simulator.run(() -> {
				final Map<Long, List<Long>> sidingsByDepot = new HashMap<>();
				final Map<Long, Boolean> realTimeByDepot = new HashMap<>();
				simulator.depots.forEach(depot -> {
					final List<Long> ids = new ArrayList<>();
					depot.savedRails.forEach(siding -> ids.add(siding.getId()));
					sidingsByDepot.put(depot.getId(), ids);
					realTimeByDepot.put(depot.getId(), depot.getUseRealTime());
				});
				server.execute(() -> send(server, sidingsByDepot, realTimeByDepot, worldDay));
			});
		}
	}

	private static void send(MinecraftServer server, Map<Long, List<Long>> sidingsByDepot, Map<Long, Boolean> realTimeByDepot, long worldDay) {
		final State state = state(server);
		final long now = System.currentTimeMillis();
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(sidingsByDepot.size());
		sidingsByDepot.forEach((depotId, sidingIds) -> {
			final DepotSchedules.Schedule schedule = state.schedules.get(depotId);
			buf.writeLong(depotId);
			buf.writeBoolean(schedule.enabled());
			buf.writeBoolean(schedule.weekday() != null);
			buf.writeBoolean(schedule.weekend() != null);
			buf.writeVarInt(state.schedules.cancelledCount(sidingIds, now));
			buf.writeString(DepotSchedules.dayType(realTimeByDepot.getOrDefault(depotId, false), LocalDate.now(), worldDay).name(), 16);
		});
		server.getPlayerManager().getPlayerList().forEach(player -> ServerPlayNetworking.send(player, SYNC, PacketByteBufs.copy(buf)));
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
		return server.getOverworld().getPersistentStateManager().getOrCreate(nbt -> new State(DepotSchedules.fromNbt(nbt)), () -> new State(new DepotSchedules()), FILE);
	}

	private static final class State extends PersistentState {

		private final DepotSchedules schedules;

		private State(DepotSchedules schedules) {
			this.schedules = schedules;
			current = schedules;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			return schedules.toNbt(nbt);
		}
	}
}
