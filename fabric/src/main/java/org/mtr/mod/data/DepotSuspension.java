package org.mtr.mod.data;

import com.aureliatransit.architecture.wayfinding.RouteHold;
import com.aureliatransit.architecture.wayfinding.ServiceMessageState;
import com.aureliatransit.architecture.wayfinding.ServiceMessageSync;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Route;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.engine.MtrSimulators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * While a {@link RouteHold} is active, depots that run that route have their departures cleared and their hourly
 * frequencies set to 0, which is how MTR keeps trains from leaving the depot. The previous departures are remembered
 * here and written back when the hold expires or the message is removed. A restart keeps the hold (it is saved) and
 * re-captures whatever the depot currently has, so a restart during a suspension does not invent departures.
 */
public final class DepotSuspension {

	private static final Map<Long, Snapshot> PAUSED = new ConcurrentHashMap<>();
	/** The holds the map layer draws as suspended. Updated on the server tick when the saved holds change. */
	private static volatile List<RouteHold> suspended = List.of();
	/** The saved holds last seen. ServiceMessageState replaces its list on every change, so a new list is a change. */
	private static List<RouteHold> lastHolds = List.of();

	private DepotSuspension() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DepotSuspension::tick);
	}

	/** Whether a route hold has this depot's departures cleared right now. */
	public static boolean isPaused(long depotId) {
		return PAUSED.containsKey(depotId);
	}

	public static boolean suspended(String routeName) {
		return RouteHold.affects(suspended, routeName, 0);
	}

	private static void tick(MinecraftServer server) {
		final ServiceMessageState state = ServiceMessageState.get(server);
		if (state.expire(System.currentTimeMillis())) {
			ServiceMessageSync.broadcast(server, state.messages());
		}
		final List<RouteHold> holds = state.holds();
		if (holds != lastHolds) {
			lastHolds = holds;
			final List<RouteHold> forMap = new ArrayList<>(holds.size());
			holds.forEach(hold -> forMap.add(new RouteHold(1, hold.route(), Long.MAX_VALUE)));
			suspended = List.copyOf(forMap);
		}
		// nothing held and nothing to restore: no work for the simulation threads, which this used to queue every tick
		if (holds.isEmpty() && PAUSED.isEmpty()) {
			return;
		}
		final ObjectImmutableList<Simulator> simulators = MtrSimulators.get();
		if (simulators == null) {
			return;
		}
		for (final Simulator simulator : simulators) {
			simulator.run(() -> apply(simulator, holds));
		}
	}

	private static void apply(Simulator simulator, List<RouteHold> holds) {
		final long now = System.currentTimeMillis();
		simulator.depots.forEach(depot -> {
			boolean hit = false;
			for (final Route route : depot.routes) {
				if (RouteHold.affects(holds, route.getName(), now)) {
					hit = true;
					break;
				}
			}
			if (hit) {
				PAUSED.computeIfAbsent(depot.getId(), id -> Snapshot.capture(depot));
				depot.getRealTimeDepartures().clear();
				for (int hour = 0; hour < 24; hour++) {
					depot.setFrequency(hour, 0);
				}
			} else {
				final Snapshot snapshot = PAUSED.remove(depot.getId());
				if (snapshot != null) {
					snapshot.restore(depot);
				}
			}
		});
	}

	private record Snapshot(boolean realtime, List<Long> departures, int[] frequencies) {
		static Snapshot capture(Depot depot) {
			final List<Long> departures = new ArrayList<>();
			depot.getRealTimeDepartures().forEach(departures::add);
			final int[] frequencies = new int[24];
			for (int hour = 0; hour < 24; hour++) {
				frequencies[hour] = (int) depot.getFrequency(hour);
			}
			return new Snapshot(depot.getUseRealTime(), List.copyOf(departures), frequencies);
		}

		void restore(Depot depot) {
			depot.getRealTimeDepartures().clear();
			departures.forEach(depot.getRealTimeDepartures()::add);
			for (int hour = 0; hour < 24; hour++) {
				depot.setFrequency(hour, frequencies[hour]);
			}
			depot.setUseRealTime(realtime);
		}
	}
}
