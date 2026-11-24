package org.mtr.mod.passenger;

import org.mtr.mod.Init;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One dimension's {@link PassengerRoutes}, worked out again only when the routes change. The search for trips with
 * changes grows quickly with the size of the network, so it runs on a background thread; until it is done, passengers
 * use the direct trains, which take no time to work out. Simulation thread only, apart from the background search.
 */
public final class PassengerRouteCache {

	private static final Executor BACKGROUND = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATR passenger routes");
		thread.setDaemon(true);
		return thread;
	});

	private final Executor executor;
	private final AtomicReference<Finished> finished = new AtomicReference<>();
	private Map<Long, Long> stationOfPlatform;
	private Map<Long, long[]> directByPlatform;
	private int maxChanges = -1;
	private int generation;
	private PassengerRoutes routes;

	public PassengerRouteCache() {
		this(BACKGROUND);
	}

	public PassengerRouteCache(Executor executor) {
		this.executor = executor;
	}

	/** The same arguments as {@link PassengerRoutes#build}. */
	public PassengerRoutes get(Map<Long, Long> stationOfPlatform, Map<Long, long[]> directByPlatform, int maxChanges) {
		if (routes == null || maxChanges != this.maxChanges || !stationOfPlatform.equals(this.stationOfPlatform) || !same(directByPlatform, this.directByPlatform)) {
			this.stationOfPlatform = Map.copyOf(stationOfPlatform);
			this.directByPlatform = copy(directByPlatform);
			this.maxChanges = maxChanges;
			final int ticket = ++generation;
			routes = PassengerRoutes.build(this.stationOfPlatform, this.directByPlatform, 0);
			if (maxChanges > 0) {
				final Map<Long, Long> stations = this.stationOfPlatform;
				final Map<Long, long[]> direct = this.directByPlatform;
				executor.execute(() -> {
					try {
						finished.set(new Finished(ticket, PassengerRoutes.build(stations, direct, maxChanges)));
					} catch (Exception e) {
						Init.LOGGER.error("Passengers could not work out trips with changes", e);
					}
				});
			}
		}
		final Finished done = finished.get();
		// a search for routes that have changed since is thrown away
		if (done != null && done.generation == generation) {
			routes = done.routes;
			finished.set(null);
		}
		return routes;
	}

	private static boolean same(Map<Long, long[]> a, Map<Long, long[]> b) {
		if (b == null || a.size() != b.size()) {
			return false;
		}
		for (final Map.Entry<Long, long[]> entry : a.entrySet()) {
			if (!Arrays.equals(entry.getValue(), b.get(entry.getKey()))) {
				return false;
			}
		}
		return true;
	}

	private static Map<Long, long[]> copy(Map<Long, long[]> map) {
		final Map<Long, long[]> copy = new HashMap<>();
		for (final Map.Entry<Long, long[]> entry : map.entrySet()) {
			copy.put(entry.getKey(), entry.getValue().clone());
		}
		return copy;
	}

	private record Finished(int generation, PassengerRoutes routes) {
	}
}
