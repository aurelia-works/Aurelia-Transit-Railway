package org.mtr.aurelia.client.map;

import org.mtr.aurelia.client.AureliaClient;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * This player's own pins on the map of one world and dimension: one home, and any number of named landmarks. They are
 * kept on this computer only, in {@code pins.json} beside the explored map's files, so no world or server data changes.
 */
public final class MapPins {

	public static final int MAX_NAME = 48;
	private static final String FILE = "pins.json";

	public record Pin(String name, int x, int z) {
	}

	@Nullable
	private static Path folder;
	@Nullable
	private static Pin home;
	private static final List<Pin> LANDMARKS = new ArrayList<>();

	private MapPins() {
	}

	@Nullable
	public static Pin home() {
		return home;
	}

	/** Read by index, so drawing them allocates nothing. */
	public static List<Pin> landmarks() {
		return LANDMARKS;
	}

	public static void setHome(int x, int z) {
		home = new Pin("", x, z);
		save();
	}

	public static void removeHome() {
		home = null;
		save();
	}

	public static void addLandmark(String name, int x, int z) {
		final String trimmed = name.strip();
		if (!trimmed.isEmpty()) {
			LANDMARKS.add(new Pin(trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed, x, z));
			save();
		}
	}

	public static void removeLandmark(int index) {
		if (index >= 0 && index < LANDMARKS.size()) {
			LANDMARKS.remove(index);
			save();
		}
	}

	/** Loads the pins of the map in {@code mapFolder}; null forgets them (no world). */
	static void use(@Nullable Path mapFolder) {
		folder = mapFolder;
		home = null;
		LANDMARKS.clear();
		if (mapFolder == null) {
			return;
		}
		final Path file = mapFolder.resolve(FILE);
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (root.has("home")) {
				home = read(root.getAsJsonObject("home"), "");
			}
			if (root.has("landmarks")) {
				for (final JsonElement element : root.getAsJsonArray("landmarks")) {
					final JsonObject landmark = element.getAsJsonObject();
					final Pin pin = read(landmark, landmark.has("name") ? landmark.get("name").getAsString() : "");
					if (!pin.name().isBlank()) {
						LANDMARKS.add(pin);
					}
				}
			}
		} catch (Exception e) {
			AureliaClient.LOGGER.warn("[ATR] map pins {} could not be read; the map starts without them", file, e);
		}
	}

	private static Pin read(JsonObject object, String name) {
		return new Pin(name, object.get("x").getAsInt(), object.get("z").getAsInt());
	}

	private static void save() {
		if (folder == null) {
			return;
		}
		final JsonObject root = new JsonObject();
		if (home != null) {
			root.add("home", write(home));
		}
		final JsonArray landmarks = new JsonArray();
		for (final Pin pin : LANDMARKS) {
			final JsonObject landmark = write(pin);
			landmark.addProperty("name", pin.name());
			landmarks.add(landmark);
		}
		root.add("landmarks", landmarks);
		try {
			Files.createDirectories(folder);
			// written beside it and moved over it, so a crash mid-write leaves the old pins rather than none
			final Path file = folder.resolve(FILE);
			final Path temp = folder.resolve(FILE + ".tmp");
			Files.writeString(temp, root.toString());
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception e) {
			AureliaClient.LOGGER.warn("[ATR] map pins could not be saved in {}", folder, e);
		}
	}

	private static JsonObject write(Pin pin) {
		final JsonObject object = new JsonObject();
		object.addProperty("x", pin.x());
		object.addProperty("z", pin.z());
		return object;
	}
}
