package org.mtr.mod.coupling;

import org.mtr.core.data.Data;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.lang.reflect.Field;

/**
 * The running trains of a siding. {@code Siding.vehicles} and {@code Siding.departures} are private, and
 * {@code railProgress} is protected on the generated schema, so this is the only door to them.
 */
final class SidingFleet {

	private static final Field VEHICLES = field(Siding.class, "vehicles");
	private static final Field DEPARTURES = field(Siding.class, "departures");
	private static final Field RAIL_PROGRESS = field("org.mtr.core.generated.data.VehicleSchema", "railProgress");

	private SidingFleet() {
	}

	@SuppressWarnings("unchecked")
	static ObjectArraySet<Vehicle> vehicles(Siding siding) {
		try {
			return (ObjectArraySet<Vehicle>) VEHICLES.get(siding);
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	static Data dataOf(Object object) {
		Class<?> type = object.getClass();
		while (type != null) {
			try {
				final Field field = type.getDeclaredField("data");
				field.setAccessible(true);
				final Object value = field.get(object);
				if (value instanceof Data) {
					return (Data) value;
				}
			} catch (NoSuchFieldException ignored) {
				type = type.getSuperclass();
			} catch (IllegalAccessException ignored) {
				return null;
			}
		}
		return null;
	}

	static double railProgress(Vehicle vehicle) {
		try {
			return RAIL_PROGRESS.getDouble(vehicle);
		} catch (IllegalAccessException e) {
			final JsonObject json = Utilities.getJsonObjectFromData(vehicle);
			return json.has("railProgress") ? json.get("railProgress").getAsDouble() : 0;
		}
	}

	static double defaultPosition(Vehicle vehicle) {
		final JsonObject json = Utilities.getJsonObjectFromData(vehicle.vehicleExtraData);
		return json.has("defaultPosition") ? json.get("defaultPosition").getAsDouble() : 0;
	}

	/**
	 * A departure index the core will not delete on the next {@code simulateTrain}, or null when this siding has no room
	 * for another train. Automatic sidings reject a second vehicle that reuses a departure index; manual sidings keep one
	 * vehicle with index {@code -1}; unlimited sidings skip the check.
	 */
	static Long rearDeparture(Siding siding, Vehicle front, Iterable<Vehicle> fleet) {
		if (siding.getIsUnlimited()) {
			return front.getDepartureIndex();
		}
		if (siding.getIsManual()) {
			for (final Vehicle vehicle : fleet) {
				if (vehicle != front && vehicle.getDepartureIndex() < 0) {
					return null;
				}
			}
			return -1L;
		}
		final int size = departureCount(siding);
		final boolean[] used = new boolean[Math.max(0, size)];
		for (final Vehicle vehicle : fleet) {
			final long index = vehicle.getDepartureIndex();
			if (index >= 0 && index < used.length) {
				used[(int) index] = true;
			}
		}
		for (int i = 0; i < used.length; i++) {
			if (!used[i]) {
				return (long) i;
			}
		}
		return null;
	}

	private static int departureCount(Siding siding) {
		try {
			final Object value = DEPARTURES.get(siding);
			if (value instanceof LongArrayList) {
				return ((LongArrayList) value).size();
			}
		} catch (IllegalAccessException ignored) {
		}
		return 0;
	}

	private static Field field(Class<?> type, String name) {
		try {
			final Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (NoSuchFieldException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private static Field field(String className, String name) {
		try {
			return field(Class.forName(className), name);
		} catch (ClassNotFoundException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
