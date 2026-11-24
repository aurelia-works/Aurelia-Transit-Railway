package org.mtr.mod.cargo;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.generated.data.VehicleSchema;
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
 * a moving train is not treated as deleted, and every train is kept for level crossings ({@link CrossingTrain}): its
 * points, speed, acceleration and the path ahead of it. Speed and progress along the path are protected in MTR's
 * {@code VehicleSchema}, so they are read the same way.
 */
public final class CargoVehicles {

	/** How far ahead, in seconds at the train's speed and acceleration, its path is kept: past a crossing's warning time. */
	static final double AHEAD_SECONDS = 12;
	/** Blocks between the points kept along a train's path ahead. */
	static final double AHEAD_STEP = 2;
	private static final double AHEAD_MAX_BLOCKS = 2000;

	@Nullable
	private static final Field VEHICLES = vehiclesField();
	@Nullable
	private static final Field SPEED = schemaField("speed");
	@Nullable
	private static final Field RAIL_PROGRESS = schemaField("railProgress");

	private CargoVehicles() {
	}

	public static TrainSnapshot capture(Simulator simulator) {
		if (VEHICLES == null) {
			return TrainSnapshot.unreliable();
		}
		final List<StoppedTrain> stopped = new ArrayList<>();
		final List<CrossingTrain> everyTrain = new ArrayList<>();
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
					final List<double[]> points = points(vehicle);
					everyTrain.add(crossingTrain(vehicle, points));
					if (vehicle.isMoving()) {
						continue;
					}
					stopped.add(new StoppedTrain(id, routeId(vehicle), cars(vehicle), points));
				}
			}
		} catch (Exception e) {
			Init.LOGGER.error("Cargo could not read trains", e);
			return TrainSnapshot.unreliable();
		}
		return new TrainSnapshot(stopped, everyTrain, allIds, true, System.currentTimeMillis());
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

	/** The train as a level crossing sees it; with no speed or path to read, it counts as moving fast with no path known. */
	private static CrossingTrain crossingTrain(Vehicle vehicle, List<double[]> points) {
		final boolean moving = vehicle.isMoving();
		try {
			if (SPEED == null || RAIL_PROGRESS == null) {
				throw new IllegalStateException("no speed field");
			}
			final double speed = Math.max(0, SPEED.getDouble(vehicle) * 1000); // m/ms to blocks per second
			final double acceleration = Math.max(0, vehicle.vehicleExtraData.getAcceleration() * 1_000_000); // m/ms² to blocks/s²
			final double progress = RAIL_PROGRESS.getDouble(vehicle);
			final double reach = Math.min(AHEAD_MAX_BLOCKS, speed * AHEAD_SECONDS + acceleration * AHEAD_SECONDS * AHEAD_SECONDS / 2 + AHEAD_STEP);
			return new CrossingTrain(points, moving, speed, acceleration, moving ? ahead(vehicle.vehicleExtraData.immutablePath, progress, reach) : List.of());
		} catch (Exception e) {
			return new CrossingTrain(points, moving, Double.POSITIVE_INFINITY, 0, List.of());
		}
	}

	/** Points every {@link #AHEAD_STEP} blocks along {@code path} from {@code progress} for {@code reach} blocks: {@code x, y, z, distance}. */
	static List<double[]> ahead(List<PathData> path, double progress, double reach) {
		final List<double[]> ahead = new ArrayList<>();
		int index = 0;
		for (double distance = 0; distance <= reach; distance += AHEAD_STEP) {
			final double at = progress + distance;
			while (index < path.size() && path.get(index).getEndDistance() < at) {
				index++;
			}
			if (index >= path.size()) {
				break;
			}
			final PathData pathData = path.get(index);
			if (pathData.getStartDistance() > at) {
				continue;
			}
			final Vector position = pathData.getPosition(at - pathData.getStartDistance());
			if (position != null) {
				ahead.add(new double[]{position.x, position.y, position.z, distance});
			}
		}
		return ahead;
	}

	private static void add(List<double[]> points, @Nullable Vector vector) {
		if (vector != null) {
			points.add(new double[]{vector.x, vector.y, vector.z});
		}
	}

	@Nullable
	private static Field schemaField(String name) {
		try {
			final Field field = VehicleSchema.class.getDeclaredField(name);
			field.setAccessible(true);
			return field;
		} catch (Exception e) {
			Init.LOGGER.error("Level crossings cannot read a train's {}; they close for any moving train in reach", name, e);
			return null;
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
