package org.mtr.mod.cargo;

import java.util.concurrent.ConcurrentHashMap;

/**
 * The cargo snapshot the server last sent this client. The map layer reads the server store directly; this is what an
 * in-world client has for the same numbers.
 */
public final class ClientCargo {

	private static final ConcurrentHashMap<Long, TrainCargo> BY_ID = new ConcurrentHashMap<>();

	private ClientCargo() {
	}

	public static void apply(boolean replaceAll, String json) {
		try {
			TrainCargoStore.readInto(json, BY_ID, replaceAll);
		} catch (Exception ignored) {
		}
	}

	public static int itemCount(long vehicleId) {
		final TrainCargo cargo = BY_ID.get(vehicleId);
		return cargo == null ? 0 : cargo.itemCount();
	}

	public static void clear() {
		BY_ID.clear();
	}
}
