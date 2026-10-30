package org.mtr.mod.coupling;

import org.mtr.core.data.Data;
import org.mtr.core.data.Siding;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleCar;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds a new {@link Vehicle} by rewriting the JSON the simulation core already uses. {@code Siding} is final and a
 * vehicle's car list is fixed once it is deployed, so a longer or shorter train is a new vehicle with the same id,
 * path and motion. No messagepack field is added.
 */
public final class ConsistEdit {

	private ConsistEdit() {
	}

	public static VehicleExtraData replaceCars(VehicleExtraData source, List<VehicleCar> cars) {
		final JsonObject json = Utilities.getJsonObjectFromData(source);
		final JsonArray array = new JsonArray();
		final ObjectArrayList<VehicleCar> counted = new ObjectArrayList<>();
		for (final VehicleCar car : cars) {
			array.add(Utilities.getJsonObjectFromData(car));
			counted.add(car);
		}
		json.add("vehicleCars", array);
		json.addProperty("totalVehicleLength", Siding.getTotalVehicleLength(counted));
		return new VehicleExtraData(new JsonReader(json));
	}

	public static Vehicle copy(Vehicle source, Siding siding, Data data, List<VehicleCar> cars, double railProgress, boolean newIdentity, long departureIndex) {
		final VehicleExtraData extra = replaceCars(source.vehicleExtraData, cars);
		final JsonObject json = Utilities.getJsonObjectFromData(source);
		json.addProperty("id", newIdentity ? (ThreadLocalRandom.current().nextLong() | 1L) : source.getId());
		json.addProperty("railProgress", railProgress);
		json.addProperty("departureIndex", departureIndex);
		if (!json.has("transportMode")) {
			json.addProperty("transportMode", source.getTransportMode().name());
		}
		return new Vehicle(extra, siding, new JsonReader(json), data);
	}

	public static List<VehicleCar> carsFromJson(JsonArray array) {
		final ObjectArrayList<VehicleCar> cars = new ObjectArrayList<>();
		if (array == null) {
			return cars;
		}
		for (int i = 0; i < array.size(); i++) {
			cars.add(new VehicleCar(new JsonReader(array.get(i))));
		}
		return cars;
	}

	public static JsonArray carsToJson(List<VehicleCar> cars) {
		final JsonArray array = new JsonArray();
		for (final VehicleCar car : cars) {
			array.add(Utilities.getJsonObjectFromData(car));
		}
		return array;
	}
}
