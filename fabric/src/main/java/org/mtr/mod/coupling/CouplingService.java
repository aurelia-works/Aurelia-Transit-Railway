package org.mtr.mod.coupling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtr.core.Main;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.core.simulation.Simulator;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.coupling.Coupling.Mode;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies coupler blocks on the simulation thread and writes {@code mtr/atr-coupling.json}. Missing file means every
 * train keeps the length its siding was saved with.
 */
public final class CouplingService {

	private static final Logger LOGGER = LogManager.getLogger("MinecraftTransitRailway");
	private static final long WATCH_MILLIS = 3_000;
	private static final ConcurrentHashMap<String, Watch> WATCHES = new ConcurrentHashMap<>();
	private static Field simulatorsField;
	private static Main main;
	private static CouplingFile file;
	private static boolean restore = true;

	private CouplingService() {
	}

	public static void attach(Main attached, Path mtrDirectory) {
		main = attached;
		file = new CouplingFile(mtrDirectory.resolve("atr-coupling.json"));
		file.load();
		restore = true;
		CouplingActions.clearVisits();
	}

	public static void stop() {
		if (file != null) {
			file.save();
		}
		main = null;
		file = null;
		WATCHES.clear();
	}

	public static void onAutosave() {
		if (file != null) {
			file.save();
		}
	}

	public static void watch(String dimension, BlockPos pos, Mode mode, int keep) {
		if (dimension == null || mode == null || mode == Mode.NONE) {
			forget(dimension, pos);
			return;
		}
		WATCHES.put(key(dimension, pos), new Watch(dimension, pos.getX(), pos.getY(), pos.getZ(), mode, keep, System.currentTimeMillis()));
	}

	public static void forget(String dimension, BlockPos pos) {
		if (dimension != null && pos != null) {
			WATCHES.remove(key(dimension, pos));
		}
	}

	public static void onWorldEnd(String dimension) {
		final Main current = main;
		final CouplingFile currentFile = file;
		if (current == null || currentFile == null || dimension == null) {
			return;
		}
		final List<Watch> watches = fresh(dimension);
		if (watches.isEmpty() && !restore) {
			return;
		}
		final Simulator simulator = find(current, dimension);
		if (simulator == null) {
			return;
		}
		simulator.run(() -> {
			try {
				if (restore) {
					currentFile.restore(simulator);
					restore = false;
				}
				apply(simulator, watches, currentFile);
				currentFile.prune(simulator);
			} catch (RuntimeException e) {
				LOGGER.error("Coupler failed in {}", dimension, e);
			}
		});
	}

	private static void apply(Simulator simulator, List<Watch> watches, CouplingFile currentFile) {
		final Map<Long, Order> orders = new HashMap<>();
		for (final Watch watch : watches) {
			final Long stationId = stationAt(simulator, watch.x, watch.y, watch.z);
			if (stationId == null) {
				continue;
			}
			final Order existing = orders.get(stationId);
			final Order next = new Order(watch.mode, watch.keep);
			if (existing == null) {
				orders.put(stationId, next);
			} else if (existing.mode != next.mode || existing.keep != next.keep) {
				orders.put(stationId, Order.CONFLICT);
			}
		}
		for (final Map.Entry<Long, Order> entry : orders.entrySet()) {
			final Order order = entry.getValue();
			if (order == Order.CONFLICT) {
				continue;
			}
			final long stationId = entry.getKey();
			simulator.sidings.forEach(siding -> {
				final int changed = order.mode == Mode.SPLIT ? CouplingActions.split(siding, stationId, order.keep) : CouplingActions.couple(siding, stationId);
				if (changed > 0) {
					currentFile.remember(siding);
				}
			});
		}
	}

	private static Long stationAt(Simulator simulator, int x, int y, int z) {
		final Position position = new Position(x, y, z);
		for (final Station station : simulator.stations) {
			if (station.inArea(position)) {
				return station.getId();
			}
		}
		return null;
	}

	private static List<Watch> fresh(String dimension) {
		final long now = System.currentTimeMillis();
		final List<Watch> watches = new ArrayList<>();
		for (final Watch watch : WATCHES.values()) {
			if (now - watch.millis > WATCH_MILLIS) {
				WATCHES.remove(key(watch.dimension, watch.x, watch.y, watch.z), watch);
			} else if (dimension.equals(watch.dimension)) {
				watches.add(watch);
			}
		}
		return watches;
	}

	@SuppressWarnings("unchecked")
	private static Simulator find(Main current, String dimension) {
		try {
			if (simulatorsField == null) {
				simulatorsField = Main.class.getDeclaredField("simulators");
				simulatorsField.setAccessible(true);
			}
			final Iterable<Simulator> simulators = (Iterable<Simulator>) simulatorsField.get(current);
			if (simulators == null) {
				return null;
			}
			for (final Simulator simulator : simulators) {
				if (dimension.equals(simulator.dimension)) {
					return simulator;
				}
			}
		} catch (ReflectiveOperationException e) {
			LOGGER.error("Could not read simulators", e);
		}
		return null;
	}

	private static String key(String dimension, BlockPos pos) {
		return key(dimension, pos.getX(), pos.getY(), pos.getZ());
	}

	private static String key(String dimension, int x, int y, int z) {
		return dimension + "/" + x + "/" + y + "/" + z;
	}

	private static final class Watch {
		private final String dimension;
		private final int x;
		private final int y;
		private final int z;
		private final Mode mode;
		private final int keep;
		private final long millis;

		private Watch(String dimension, int x, int y, int z, Mode mode, int keep, long millis) {
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.mode = mode;
			this.keep = keep;
			this.millis = millis;
		}
	}

	private static final class Order {
		private static final Order CONFLICT = new Order(Mode.NONE, 0);
		private final Mode mode;
		private final int keep;

		private Order(Mode mode, int keep) {
			this.mode = mode;
			this.keep = keep;
		}
	}
}
