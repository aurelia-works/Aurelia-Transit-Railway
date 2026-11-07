package org.mtr.aurelia.sound;

import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Which profile each train uses and the volume of each sound part, in {@code config/aurelia/sound-profiles.json}. The
 * file is new, so worlds and {@code config/mtr.json} are untouched, and a train with no entry keeps its own sound.
 */
public final class SoundProfileStore {

	public enum Part {
		MOTOR("motor"),
		DOOR("door"),
		CHIME("chime"),
		BRAKE("brake"),
		COMPRESSOR("compressor"),
		FLANGE("flange");

		public final String key;

		Part(String key) {
			this.key = key;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");
	private static final Map<Long, Settings> SETTINGS = new HashMap<>();
	private static Path file;

	private SoundProfileStore() {
	}

	public static Settings settings(long vehicleId) {
		final Settings settings = SETTINGS.get(vehicleId);
		return settings == null ? Settings.DEFAULT : settings;
	}

	/** The parsed profile for these settings, or null when the train has none or the pack that defined it is gone. */
	@Nullable
	public static SoundProfile resolve(Settings settings) {
		return SoundProfileRegistry.find(settings.profileId);
	}

	/** The next profile id for this train, cycling back to none. */
	public static String cycleProfile(long vehicleId) {
		final String next = SoundProfileRegistry.nextId(settings(vehicleId).profileId);
		setProfile(vehicleId, next);
		return next;
	}

	public static void setProfile(long vehicleId, String profileId) {
		put(vehicleId, settings(vehicleId).withProfile(profileId == null ? "" : profileId));
		save();
	}

	/** Volume sliders move many times a second, so the panel saves them once, on close. */
	public static void setVolume(long vehicleId, Part part, double volume) {
		put(vehicleId, settings(vehicleId).withVolume(part, volume));
	}

	public static void load(Path configDir) {
		file = configDir.resolve("aurelia").resolve("sound-profiles.json");
		SETTINGS.clear();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonElement root = JsonParser.parseString(Files.readString(file));
			if (!root.isJsonObject()) {
				return;
			}
			final JsonElement vehicles = root.getAsJsonObject().get("vehicles");
			if (vehicles == null || !vehicles.isJsonObject()) {
				return;
			}
			for (final Map.Entry<String, JsonElement> entry : vehicles.getAsJsonObject().entrySet()) {
				final Long vehicleId = parseLong(entry.getKey());
				if (vehicleId != null && entry.getValue().isJsonObject()) {
					SETTINGS.put(vehicleId, Settings.from(entry.getValue().getAsJsonObject()));
				}
			}
		} catch (Exception e) {
			LOGGER.error("[ATR] sound-profiles.json could not be read; all trains keep their own sound", e);
		}
	}

	private static void put(long vehicleId, Settings settings) {
		if (settings.equals(Settings.DEFAULT)) {
			SETTINGS.remove(vehicleId);
		} else {
			SETTINGS.put(vehicleId, settings);
		}
	}

	public static void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			final JsonObject vehicles = new JsonObject();
			for (final Map.Entry<Long, Settings> entry : SETTINGS.entrySet()) {
				vehicles.add(String.valueOf(entry.getKey()), entry.getValue().toJson());
			}
			final JsonObject root = new JsonObject();
			root.add("vehicles", vehicles);
			Files.writeString(file, root.toString());
		} catch (Exception e) {
			LOGGER.error("[ATR] sound-profiles.json could not be written", e);
		}
	}

	@Nullable
	private static Long parseLong(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public static final class Settings {

		public static final Settings DEFAULT = new Settings("", null);
		public final String profileId;
		private final double[] volumes;

		private Settings(String profileId, @Nullable double[] volumes) {
			this.profileId = profileId == null ? "" : profileId;
			this.volumes = volumes == null ? fullVolumes() : volumes;
		}

		public double volume(Part part) {
			return volumes[part.ordinal()];
		}

		public Settings withProfile(String nextProfileId) {
			return new Settings(nextProfileId, Arrays.copyOf(volumes, volumes.length));
		}

		public Settings withVolume(Part part, double volume) {
			final double[] next = Arrays.copyOf(volumes, volumes.length);
			next[part.ordinal()] = Math.max(0, Math.min(1, volume));
			return new Settings(profileId, next);
		}

		public static Settings from(JsonObject json) {
			final JsonElement profile = json.get("profile");
			final String profileId = profile != null && profile.isJsonPrimitive() && profile.getAsJsonPrimitive().isString() ? profile.getAsString() : "";
			final double[] volumes = fullVolumes();
			final JsonElement volumeObject = json.get("volumes");
			if (volumeObject != null && volumeObject.isJsonObject()) {
				for (final Part part : Part.values()) {
					final JsonElement value = volumeObject.getAsJsonObject().get(part.key);
					if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
						volumes[part.ordinal()] = Math.max(0, Math.min(1, value.getAsDouble()));
					}
				}
			}
			return new Settings(profileId, volumes);
		}

		public JsonObject toJson() {
			final JsonObject volumeObject = new JsonObject();
			for (final Part part : Part.values()) {
				volumeObject.addProperty(part.key, Math.round(volumes[part.ordinal()] * 100) / 100D);
			}
			final JsonObject json = new JsonObject();
			json.addProperty("profile", profileId);
			json.add("volumes", volumeObject);
			return json;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof Settings settings)) {
				return false;
			}
			return profileId.equals(settings.profileId) && Arrays.equals(volumes, settings.volumes);
		}

		@Override
		public int hashCode() {
			return profileId.hashCode() * 31 + Arrays.hashCode(volumes);
		}

		private static double[] fullVolumes() {
			final double[] volumes = new double[Part.values().length];
			Arrays.fill(volumes, 1);
			return volumes;
		}
	}
}
