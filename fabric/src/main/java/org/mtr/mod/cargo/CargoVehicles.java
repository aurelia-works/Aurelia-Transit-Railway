package org.mtr.mod.cargo;

import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mod.Init;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads trains off a simulator. {@code Siding.vehicles} is private; the map layer already reflects it, and this does
 * the same on the simulation thread. Only trains with speed zero are offered to cargo blocks. Every id is recorded so
 * a moving train is not treated as deleted.
 */
public final class CargoVehicles {

	@Nullable
	private static final Field VEHICLES = vehiclesField();

	private CargoVehicles() {
	}

	public static TrainSnapshot capture(Simulator simulator) {
		if (VEHICLES == null) {
			return TrainSnapshot.unreliable();
		}
		final List<StoppedTrain> stopped = new ArrayList<>();
		final Set<Long> allIds = new HashSet<>();
		try {
			for (final Siding siding : simulator.sidings) {
				final Object raw = VEHICLES.get(siding);
				if (!(raw instanceof Collection)) {
					continue;
				}
				for (final Object vehicleObject : (Collection<?>) raw) {
					if (!(vehicleObject instanceof Vehicle)) {
						continue;
					}
					final Vehicle vehicle = (Vehicle) vehicleObject;
					final long id = vehicle.getId();
					allIds.add(id);
					if (vehicle.isMoving()) {
						continue;
					}
					stopped.add(new StoppedTrain(id, routeId(vehicle), cars(vehicle), points(vehicle)));
				}
			}
		} catch (Exception e) {
			Init.LOGGER.error("Cargo could not read trains", e);
			return TrainSnapshot.unreliable();
		}
		return new TrainSnapshot(stopped, allIds, true, System.currentTimeMillis());
	}

	private static long routeId(Vehicle vehicle) {
		try {
			return vehicle.vehicleExtraData.getThisRouteId();
		} catch (Exception e) {
			return 0;
		}
	}

	private static int cars(Vehicle vehicle) {
		try {
			final int count = vehicle.vehicleExtraData.immutableVehicleCars.size();
			return count <= 0 ? 1 : count;
		} catch (Exception e) {
			return 1;
		}
	}

	private static List<double[]> points(Vehicle vehicle) {
		final List<double[]> points = new ArrayList<>();
		try {
			final Vector head = vehicle.getHeadPosition();
			if (head != null) {
				points.add(new double[]{head.x, head.y, head.z});
			}
		} catch (Exception ignored) {
		}
		try {
			final ObjectArrayList<ObjectObjectImmutablePair<VehicleCar, ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>>>> cars = vehicle.getVehicleCarsAndPositions();
			for (int i = 0; i < cars.size(); i++) {
				final ObjectArrayList<ObjectObjectImmutablePair<Vector, Vector>> bogies = cars.get(i).right();
				for (int j = 0; j < bogies.size(); j++) {
					add(points, bogies.get(j).left());
					add(points, bogies.get(j).right());
				}
			}
		} catch (Exception ignored) {
		}
		return points;
	}

	private static void add(List<double[]> points, @Nullable Vector vector) {
		if (vector != null) {
			points.add(new double[]{vector.x, vector.y, vector.z});
		}
	}

	@Nullable
	private static Field vehiclesField() {
		try {
			final Field field = Siding.class.getDeclaredField("vehicles");
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			Init.LOGGER.error("Cargo cannot read Siding.vehicles; loaders will stay idle", e);
			return null;
		}
	}
}
