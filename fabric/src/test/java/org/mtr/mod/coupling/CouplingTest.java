package org.mtr.mod.coupling;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArraySet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CouplingTest {

	@Test
	public void anAutomaticSidingWithNoFreeDepartureDoesNotSplit() {
		CouplingActions.clearVisits();
		final Siding siding = siding();
		siding.setMaxVehicles(1);
		final Vehicle vehicle = vehicle(siding, 200, 7, "a", "b", "c", "d");
		SidingFleet.vehicles(siding).add(vehicle);
		Assertions.assertEquals(0, CouplingActions.split(siding, 7, 2));
		Assertions.assertEquals(1, SidingFleet.vehicles(siding).size());
		Assertions.assertEquals(4, vehicle.vehicleExtraData.immutableVehicleCars.size());
	}

	@Test
	public void aSavedConsistComesBackOntoTheTemplateTrain() throws Exception {
		CouplingActions.clearVisits();
		final Path directory = Files.createTempDirectory("atr-coupling");
		final CouplingFile file = new CouplingFile(directory.resolve("atr-coupling.json"));
		file.load();
		final Siding siding = siding();
		siding.setIsManual(true);
		siding.setVehicleCars(cars("a", "b", "c", "d"));
		final Vehicle vehicle = vehicle(siding, 200, 7, "a", "b", "c", "d");
		SidingFleet.vehicles(siding).add(vehicle);
		Assertions.assertEquals(1, CouplingActions.split(siding, 7, 2));
		file.remember(siding);
		file.save();
		Assertions.assertTrue(Files.isRegularFile(directory.resolve("atr-coupling.json")));

		final Siding reloaded = siding();
		reloaded.setIsManual(true);
		reloaded.setVehicleCars(cars("a", "b", "c", "d"));
		final Vehicle template = vehicle(reloaded, 200, 7, "a", "b", "c", "d");
		// the core rebuilds every saved vehicle from the siding template, so both halves come back full length
		final Vehicle duplicate = vehicle(reloaded, 120, 7, "a", "b", "c", "d");
		long frontId = 0;
		long rearId = 0;
		for (final Vehicle train : SidingFleet.vehicles(siding)) {
			if (train.vehicleExtraData.immutableVehicleCars.size() == 2 && train.vehicleExtraData.immutableVehicleCars.get(0).getVehicleId().equals("a")) {
				frontId = train.getId();
			} else {
				rearId = train.getId();
			}
		}
		final Vehicle front = copyId(template, reloaded, frontId);
		final Vehicle rear = copyId(duplicate, reloaded, rearId);
		SidingFleet.vehicles(reloaded).add(front);
		SidingFleet.vehicles(reloaded).add(rear);
		final Data data = SidingFleet.dataOf(reloaded);
		restoreWithoutSimulator(file, reloaded);
		int shortTrains = 0;
		for (final Vehicle train : SidingFleet.vehicles(reloaded)) {
			if (train.vehicleExtraData.immutableVehicleCars.size() == 2) {
				shortTrains++;
			}
		}
		Assertions.assertEquals(2, shortTrains, "both halves keep the cars they split into");
		Assertions.assertNotNull(data);
	}

	@Test
	public void aMissingFileLeavesTheTemplateAlone() throws Exception {
		final Path directory = Files.createTempDirectory("atr-coupling-empty");
		final CouplingFile file = new CouplingFile(directory.resolve("atr-coupling.json"));
		file.load();
		file.save();
		Assertions.assertFalse(Files.isRegularFile(directory.resolve("atr-coupling.json")));
	}

	private static void restoreWithoutSimulator(CouplingFile file, Siding siding) {
		for (final java.util.Map.Entry<Long, JsonArray> entry : file.consists().entrySet()) {
			final ObjectArraySet<Vehicle> fleet = SidingFleet.vehicles(siding);
			final Data data = SidingFleet.dataOf(siding);
			for (final Vehicle vehicle : new ArrayList<>(fleet)) {
				if (vehicle.getId() != entry.getKey()) {
					continue;
				}
				final List<VehicleCar> cars = ConsistEdit.carsFromJson(entry.getValue());
				final Vehicle restored = ConsistEdit.copy(vehicle, siding, data, cars, SidingFleet.railProgress(vehicle), false, vehicle.getDepartureIndex());
				fleet.remove(vehicle);
				fleet.add(restored);
			}
		}
	}

	private static Vehicle copyId(Vehicle source, Siding siding, long id) {
		final JsonObject json = Utilities.getJsonObjectFromData(source);
		json.addProperty("id", id);
		final Data data = SidingFleet.dataOf(siding);
		return new Vehicle(source.vehicleExtraData, siding, new JsonReader(json), data);
	}

	private static Siding siding() {
		final Data data = new Data() {
		};
		return new Siding(new Position(0, 0, 0), new Position(80, 0, 0), 80, TransportMode.TRAIN, data);
	}

	private static Vehicle vehicle(Siding siding, double progress, long stationId, String... ids) {
		final ObjectArrayList<VehicleCar> cars = cars(ids);
		final JsonObject extra = new JsonObject();
		extra.add("vehicleCars", ConsistEdit.carsToJson(cars));
		extra.add("path", new JsonArray());
		extra.addProperty("thisStationId", stationId);
		extra.addProperty("defaultPosition", 0);
		extra.addProperty("totalVehicleLength", Siding.getTotalVehicleLength(cars));
		final VehicleExtraData extraData = new VehicleExtraData(new JsonReader(extra));
		final Data data = SidingFleet.dataOf(siding);
		final Vehicle seeded = new Vehicle(extraData, siding, TransportMode.TRAIN, data);
		final JsonObject seededJson = Utilities.getJsonObjectFromData(seeded);
		seededJson.addProperty("id", java.util.concurrent.ThreadLocalRandom.current().nextLong() | 1L);
		seededJson.addProperty("railProgress", progress);
		seededJson.addProperty("departureIndex", 0);
		if (!seededJson.has("transportMode")) {
			seededJson.addProperty("transportMode", TransportMode.TRAIN.name());
		}
		return new Vehicle(extraData, siding, new JsonReader(seededJson), data);
	}

	private static ObjectArrayList<VehicleCar> cars(String... ids) {
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		for (final String id : ids) {
			cars.add(car(id));
		}
		return cars;
	}

	private static VehicleCar car(String id) {
		return new VehicleCar(id, 8, 2.5, 2, 6, 0, 0);
	}

}
