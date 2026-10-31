package org.mtr.mod.cargo;

import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.mod.Init;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cargo for every dimension, keyed by vehicle id. The file is {@code mtr/atr-cargo.json} inside the world save.
 * Missing file means an old world: cargo starts empty and the simulation files are left alone.
 */
public final class TrainCargoStore {

	static final int VERSION = 1;

	private final ConcurrentHashMap<Long, TrainCargo> byId = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, String> dimensionOf = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, String> dirty = new ConcurrentHashMap<>();
	private boolean fileDirty;
	private boolean loadFailed;

	public TrainCargo getOrCreate(String dimension, long id, int slots) {
		dimensionOf.put(id, dimension);
		final TrainCargo created = byId.computeIfAbsent(id, key -> new TrainCargo(slots));
		created.ensureSlots(slots);
		return created;
	}

	public void remove(long id) {
		byId.remove(id);
		dimensionOf.remove(id);
	}

	public int itemCount(long id) {
		final TrainCargo cargo = byId.get(id);
		return cargo == null ? 0 : cargo.itemCount();
	}

	public void markDirty(String dimension, long id) {
		dirty.put(id, dimension);
		fileDirty = true;
	}

	public boolean isFileDirty() {
		return fileDirty;
	}

	public List<Long> drainDirty(String dimension) {
		final List<Long> ids = new ArrayList<>();
		for (final Map.Entry<Long, String> entry : dirty.entrySet()) {
			if (dimension.equals(entry.getValue())) {
				ids.add(entry.getKey());
			}
		}
		for (final Long id : ids) {
			dirty.remove(id);
		}
		return ids;
	}

	/**
	 * Drops cargo whose train is gone. Callers wait until the simulation has had time to load sidings, and skip the
	 * call when the vehicle read itself failed, so a late load cannot wipe a hold.
	 *
	 * @return how many holds were removed
	 */
	public int prune(String dimension, Set<Long> liveIds) {
		final List<Long> gone = new ArrayList<>();
		for (final Map.Entry<Long, String> entry : dimensionOf.entrySet()) {
			if (dimension.equals(entry.getValue()) && !liveIds.contains(entry.getKey())) {
				gone.add(entry.getKey());
			}
		}
		for (final Long id : gone) {
			dimensionOf.remove(id);
			byId.remove(id);
			markDirty(dimension, id);
		}
		return gone.size();
	}

	public String dimensionJson(String dimension) {
		final List<Long> ids = new ArrayList<>();
		for (final Map.Entry<Long, String> entry : dimensionOf.entrySet()) {
			if (dimension.equals(entry.getValue())) {
				ids.add(entry.getKey());
			}
		}
		return vehiclesJson(ids, false);
	}

	public String vehiclesJson(Collection<Long> ids, boolean includeEmpty) {
		final JsonObject vehicles = new JsonObject();
		for (final Long id : ids) {
			final TrainCargo cargo = byId.get(id);
			if (cargo == null || cargo.isEmpty()) {
				if (!includeEmpty) {
					continue;
				}
				final JsonObject empty = new JsonObject();
				empty.addProperty("slots", 0);
				empty.add("stacks", new org.mtr.libraries.com.google.gson.JsonArray());
				vehicles.add(Utilities.numberToPaddedHexString(id), empty);
			} else {
				vehicles.add(Utilities.numberToPaddedHexString(id), cargo.toJson());
			}
		}
		final JsonObject root = new JsonObject();
		root.add("vehicles", vehicles);
		return root.toString();
	}

	/**
	 * Applies a sync payload. An empty stack list removes that vehicle. {@code replaceAll} clears the map first.
	 */
	public static void readInto(String json, Map<Long, TrainCargo> target, boolean replaceAll) {
		if (replaceAll) {
			target.clear();
		}
		if (json == null || json.isEmpty()) {
			return;
		}
		final JsonElement element = JsonParser.parseString(json);
		if (!element.isJsonObject()) {
			return;
		}
		final JsonObject root = element.getAsJsonObject();
		if (!root.has("vehicles") || !root.get("vehicles").isJsonObject()) {
			return;
		}
		for (final Map.Entry<String, JsonElement> entry : root.getAsJsonObject("vehicles").entrySet()) {
			final long id;
			try {
				id = Long.parseUnsignedLong(entry.getKey(), 16);
			} catch (NumberFormatException ignored) {
				continue;
			}
			if (!entry.getValue().isJsonObject()) {
				continue;
			}
			final TrainCargo cargo = TrainCargo.fromJson(entry.getValue().getAsJsonObject());
			if (cargo.isEmpty()) {
				target.remove(id);
			} else {
				target.put(id, cargo);
			}
		}
	}

	public void load(Path file) {
		byId.clear();
		dimensionOf.clear();
		dirty.clear();
		fileDirty = false;
		loadFailed = false;
		if (file == null || !Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonElement element = JsonParser.parseString(Files.readString(file));
			if (!element.isJsonObject()) {
				loadFailed = true;
				return;
			}
			final JsonObject root = element.getAsJsonObject();
			if (!root.has("dimensions") || !root.get("dimensions").isJsonObject()) {
				return;
			}
			for (final Map.Entry<String, JsonElement> dimensionEntry : root.getAsJsonObject("dimensions").entrySet()) {
				if (!dimensionEntry.getValue().isJsonObject()) {
					continue;
				}
				final String dimension = dimensionEntry.getKey();
				for (final Map.Entry<String, JsonElement> vehicleEntry : dimensionEntry.getValue().getAsJsonObject().entrySet()) {
					final long id;
					try {
						id = Long.parseUnsignedLong(vehicleEntry.getKey(), 16);
					} catch (NumberFormatException ignored) {
						continue;
					}
					if (!vehicleEntry.getValue().isJsonObject()) {
						continue;
					}
					final TrainCargo cargo = TrainCargo.fromJson(vehicleEntry.getValue().getAsJsonObject());
					if (!cargo.isEmpty()) {
						byId.put(id, cargo);
						dimensionOf.put(id, dimension);
					}
				}
			}
		} catch (Exception e) {
			loadFailed = true;
			Init.LOGGER.error("Could not read cargo save {}; keeping it and starting with no cargo in memory", file, e);
		}
	}

	public void save(Path file) {
		if (file == null) {
			return;
		}
		if (loadFailed && !fileDirty) {
			return;
		}
		final JsonObject dimensions = new JsonObject();
		for (final Map.Entry<Long, String> entry : dimensionOf.entrySet()) {
			final TrainCargo cargo = byId.get(entry.getKey());
			if (cargo == null || cargo.isEmpty()) {
				continue;
			}
			JsonObject vehicles = dimensions.getAsJsonObject(entry.getValue());
			if (vehicles == null) {
				vehicles = new JsonObject();
				dimensions.add(entry.getValue(), vehicles);
			}
			vehicles.add(Utilities.numberToPaddedHexString(entry.getKey()), cargo.toJson());
		}
		final JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.add("dimensions", dimensions);
		try {
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			final Path temporary = file.resolveSibling(file.getFileName().toString() + ".tmp");
			Files.writeString(temporary, root.toString(), StandardCharsets.UTF_8);
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
			fileDirty = false;
			loadFailed = false;
		} catch (IOException e) {
			Init.LOGGER.error("Could not write cargo save {}", file, e);
		}
	}
}
