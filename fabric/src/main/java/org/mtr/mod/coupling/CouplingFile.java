package org.mtr.mod.coupling;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Car lists that differ from the siding template, so a reload can put them back. The core save stores a vehicle's
 * motion only and rebuilds its cars from the siding, which is why an old world with no file here is unchanged.
 */
final class CouplingFile {

	private static final Logger LOGGER = LogManager.getLogger("MinecraftTransitRailway");
	private static final int VERSION = 1;

	private final Path path;
	private final Map<Long, JsonArray> consists = new ConcurrentHashMap<>();
	private boolean loadFailed;
	private boolean dirty;

	CouplingFile(Path path) {
		this.path = path;
	}

	void load() {
		consists.clear();
		dirty = false;
		loadFailed = false;
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			final JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
			final JsonObject saved = root.has("consists") ? root.getAsJsonObject("consists") : new JsonObject();
			for (final Map.Entry<String, JsonElement> entry : saved.entrySet()) {
				consists.put(Long.parseUnsignedLong(entry.getKey(), 16), entry.getValue().getAsJsonArray());
			}
		} catch (Exception e) {
			loadFailed = true;
			LOGGER.error("Leaving {} unread; coupled consists will not be restored until it parses", path, e);
		}
	}

	void remember(Siding siding) {
		final ObjectArraySet<Vehicle> fleet = SidingFleet.vehicles(siding);
		if (fleet == null) {
			return;
		}
		final List<String> template = ids(siding.getVehicleCars());
		for (final Vehicle vehicle : fleet) {
			final List<VehicleCar> cars = new ArrayList<>(vehicle.vehicleExtraData.immutableVehicleCars);
			if (ids(cars).equals(template)) {
				if (consists.remove(vehicle.getId()) != null) {
					dirty = true;
				}
			} else {
				consists.put(vehicle.getId(), ConsistEdit.carsToJson(cars));
				dirty = true;
			}
		}
	}

	void prune(Simulator simulator) {
		final List<Long> live = new ArrayList<>();
		simulator.sidings.forEach(siding -> {
			final ObjectArraySet<Vehicle> fleet = SidingFleet.vehicles(siding);
			if (fleet != null) {
				for (final Vehicle vehicle : fleet) {
					live.add(vehicle.getId());
				}
			}
		});
		if (consists.keySet().retainAll(live)) {
			dirty = true;
		}
	}

	void restore(Simulator simulator) {
		if (loadFailed || consists.isEmpty()) {
			return;
		}
		for (final Map.Entry<Long, JsonArray> entry : consists.entrySet()) {
			simulator.sidings.forEach(siding -> restoreOne(siding, entry.getKey(), entry.getValue()));
		}
	}

	void save() {
		if (loadFailed || !dirty) {
			return;
		}
		final JsonObject consistsJson = new JsonObject();
		for (final Map.Entry<Long, JsonArray> entry : consists.entrySet()) {
			consistsJson.add(Utilities.numberToPaddedHexString(entry.getKey()), entry.getValue());
		}
		final JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.add("consists", consistsJson);
		final Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
		try {
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			Files.writeString(temporary, root.toString());
			try {
				Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException e) {
			LOGGER.error("Could not write {}", path, e);
		}
	}

	Map<Long, JsonArray> consists() {
		return consists;
	}

	private static void restoreOne(Siding siding, long vehicleId, JsonArray carsJson) {
		final ObjectArraySet<Vehicle> fleet = SidingFleet.vehicles(siding);
		final org.mtr.core.data.Data data = SidingFleet.dataOf(siding);
		if (fleet == null || data == null) {
			return;
		}
		for (final Vehicle vehicle : new ArrayList<>(fleet)) {
			if (vehicle.getId() != vehicleId) {
				continue;
			}
			final List<VehicleCar> cars = ConsistEdit.carsFromJson(carsJson);
			if (ids(cars).equals(ids(vehicle.vehicleExtraData.immutableVehicleCars))) {
				return;
			}
			final Vehicle restored = ConsistEdit.copy(vehicle, siding, data, cars, SidingFleet.railProgress(vehicle), false, vehicle.getDepartureIndex());
			fleet.remove(vehicle);
			fleet.add(restored);
			return;
		}
	}

	private static List<String> ids(List<VehicleCar> cars) {
		final List<String> ids = new ArrayList<>();
		for (final VehicleCar car : cars) {
			ids.add(car.getVehicleId());
		}
		return ids;
	}
}
