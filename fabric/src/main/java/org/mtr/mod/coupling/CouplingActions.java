package org.mtr.mod.coupling;

import org.mtr.core.data.Data;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Couples or splits the trains already in a siding's vehicle set. The siding's saved car list is not touched, so a
 * depot with no coupler still deploys the fixed consist it was given. A split that cannot leave the detached cars as a
 * real train (no free departure slot) does nothing.
 */
public final class CouplingActions {

	private static final Long2LongOpenHashMap VISITED_STATION = new Long2LongOpenHashMap();

	private CouplingActions() {
	}

	/** Tests reset the "already split at this stop" memory. */
	public static void clearVisits() {
		VISITED_STATION.clear();
	}

	public static int split(Siding siding, long stationId, int requestedKeep) {
		final ObjectArraySet<Vehicle> fleet = SidingFleet.vehicles(siding);
		final Data data = SidingFleet.dataOf(siding);
		if (fleet == null || data == null) {
			return 0;
		}
		int changed = 0;
		for (final Vehicle vehicle : new ArrayList<>(fleet)) {
			if (!parked(vehicle, stationId)) {
				VISITED_STATION.remove(vehicle.getId());
				continue;
			}
			if (VISITED_STATION.getOrDefault(vehicle.getId(), Long.MIN_VALUE) == stationId) {
				continue;
			}
			final ObjectImmutableList<VehicleCar> cars = vehicle.vehicleExtraData.immutableVehicleCars;
			final int keep = Coupling.keepCount(cars.size(), requestedKeep);
			if (!Coupling.canSplit(cars.size(), keep)) {
				continue;
			}
			final Long rearIndex = SidingFleet.rearDeparture(siding, vehicle, fleet);
			if (rearIndex == null) {
				continue;
			}
			final double progress = SidingFleet.railProgress(vehicle);
			final ObjectArrayList<VehicleCar> frontCars = new ObjectArrayList<>();
			final ObjectArrayList<VehicleCar> rearCars = new ObjectArrayList<>();
			for (int i = 0; i < cars.size(); i++) {
				(i < keep ? frontCars : rearCars).add(cars.get(i));
			}
			final double rearProgress = progress - org.mtr.core.data.Siding.getTotalVehicleLength(frontCars);
			if (rearProgress <= SidingFleet.defaultPosition(vehicle)) {
				continue;
			}
			final Vehicle front = ConsistEdit.copy(vehicle, siding, data, frontCars, progress, false, vehicle.getDepartureIndex());
			final Vehicle rear = ConsistEdit.copy(vehicle, siding, data, rearCars, rearProgress, true, rearIndex);
			fleet.remove(vehicle);
			fleet.add(front);
			fleet.add(rear);
			VISITED_STATION.put(front.getId(), stationId);
			VISITED_STATION.put(rear.getId(), stationId);
			changed++;
		}
		return changed;
	}

	public static int couple(Siding siding, long stationId) {
		final ObjectArraySet<Vehicle> fleet = SidingFleet.vehicles(siding);
		final Data data = SidingFleet.dataOf(siding);
		if (fleet == null || data == null) {
			return 0;
		}
		final List<Vehicle> parked = new ArrayList<>();
		for (final Vehicle vehicle : fleet) {
			if (parked(vehicle, stationId)) {
				parked.add(vehicle);
			} else {
				VISITED_STATION.remove(vehicle.getId());
			}
		}
		if (parked.size() < 2) {
			return 0;
		}
		parked.sort(Comparator.comparingDouble(SidingFleet::railProgress).reversed());
		final Vehicle lead = parked.get(0);
		if (VISITED_STATION.getOrDefault(lead.getId(), Long.MIN_VALUE) == stationId) {
			return 0;
		}
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		for (final Vehicle vehicle : parked) {
			final int before = cars.size();
			cars.addAll(vehicle.vehicleExtraData.immutableVehicleCars);
			if (vehicle != lead && !Coupling.canCouple(before, vehicle.vehicleExtraData.immutableVehicleCars.size())) {
				return 0;
			}
		}
		final Vehicle joined = ConsistEdit.copy(lead, siding, data, cars, SidingFleet.railProgress(lead), false, lead.getDepartureIndex());
		for (final Vehicle vehicle : parked) {
			fleet.remove(vehicle);
			VISITED_STATION.remove(vehicle.getId());
		}
		fleet.add(joined);
		VISITED_STATION.put(joined.getId(), stationId);
		return 1;
	}

	private static boolean parked(Vehicle vehicle, long stationId) {
		return !vehicle.isMoving() && vehicle.getIsOnRoute() && vehicle.vehicleExtraData.getThisStationId() == stationId;
	}
}
