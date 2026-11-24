package org.mtr.mod.engine;

import org.mtr.core.Main;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mod.Init;

import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * MTR's simulators, one per dimension, for the running server. MTR keeps them in private fields, so they are read by
 * reflection. The fields are looked up once: several ATR services ask every second, and one asked every tick.
 */
public final class MtrSimulators {

	@Nullable
	private static final Field MAIN = field(Init.class, "main");
	@Nullable
	private static final Field SIMULATORS = field(Main.class, "simulators");

	private MtrSimulators() {
	}

	/** The simulators, or null when no server is running or MTR's fields could not be reached. */
	@SuppressWarnings("unchecked")
	@Nullable
	public static ObjectImmutableList<Simulator> get() {
		if (MAIN == null || SIMULATORS == null) {
			return null;
		}
		try {
			final Main main = (Main) MAIN.get(null);
			return main == null ? null : (ObjectImmutableList<Simulator>) SIMULATORS.get(main);
		} catch (IllegalAccessException | RuntimeException e) {
			return null;
		}
	}

	@Nullable
	private static Field field(Class<?> type, String name) {
		try {
			final Field field = type.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (ReflectiveOperationException | RuntimeException e) {
			Init.LOGGER.error("[ATR] Cannot reach MTR's {}.{}; passengers, depot schedules and route holds are off", type.getSimpleName(), name, e);
			return null;
		}
	}
}
