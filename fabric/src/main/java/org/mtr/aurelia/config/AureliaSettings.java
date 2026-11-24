package org.mtr.aurelia.config;

import com.aureliatransit.architecture.client.WrenchSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.mtr.aurelia.client.map.MapSettings;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.aurelia.client.voice.VoiceSettings;
import org.mtr.aurelia.dashboard.DashboardSettings;
import org.mtr.aurelia.pack.PackSettings;
import org.mtr.aurelia.route.RouteControlSettings;
import org.mtr.aurelia.sound.SoundProfileStore;
import org.mtr.aurelia.vehicle.RoadVehicleSettings;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one client settings file, {@code config/aurelia/settings.json}: {@code version} plus one section per feature. The
 * ten files it replaces are read once with their own parsers, copied into their sections, and renamed to
 * {@code <name>.migrated-v1}. A file that cannot be read stays where it is and keeps the version at 0, so the next start
 * retries; the values that did read are used for this session either way. A file at version 1 or later is the whole
 * truth: old files left next to it are ignored. Nothing is ever deleted, and {@code config/mtr.json} is never touched.
 */
public final class AureliaSettings {

	public static final int VERSION = 1;

	public static final String PASSENGER_DRAW = "passenger_draw";
	public static final String MAP = "map";
	public static final String UI = "ui";
	public static final String VOICE = "voice";
	public static final String DASHBOARD = "dashboard";
	public static final String PACKS = "packs";
	public static final String ROUTE_CONTROL = "route_control";
	public static final String ROAD_VEHICLES = "road_vehicles";
	public static final String WRENCH = "wrench";
	public static final String SOUND_PROFILES = "sound_profiles";

	private static final String FILE = "settings.json";
	private static final String VERSION_KEY = "version";
	private static final String MIGRATED_SUFFIX = ".migrated-v1";

	/** Reads one old file into the section body it becomes, throwing when the file is not usable. */
	@FunctionalInterface
	private interface LegacyReader {
		JsonObject read(Path file) throws IOException;
	}

	private record LegacyFile(String fileName, String section, LegacyReader reader) {
	}

	/** The old files, in the order they are read. */
	private static final List<LegacyFile> LEGACY_FILES = List.of(
			new LegacyFile("passenger-draw.json", PASSENGER_DRAW, PassengerDrawSettings::readLegacy),
			new LegacyFile("map.json", MAP, MapSettings::readLegacy),
			new LegacyFile("ui.json", UI, AureliaUiSettings::readLegacy),
			new LegacyFile("voice.properties", VOICE, VoiceSettings::readLegacy),
			new LegacyFile("dashboard.json", DASHBOARD, DashboardSettings::readLegacy),
			new LegacyFile("packs.json", PACKS, PackSettings::readLegacy),
			new LegacyFile("route_control.json", ROUTE_CONTROL, RouteControlSettings::readLegacy),
			new LegacyFile("road-vehicles.json", ROAD_VEHICLES, RoadVehicleSettings::readLegacy),
			new LegacyFile("wrench.json", WRENCH, WrenchSettings::readLegacy),
			new LegacyFile("sound-profiles.json", SOUND_PROFILES, SoundProfileStore::readLegacy)
	);

	private static final Map<String, JsonObject> SECTIONS = new LinkedHashMap<>();
	private static Path file;
	/** A damaged file that could not be moved aside: never write over it. */
	private static boolean locked;
	private static int version;

	private AureliaSettings() {
	}

	/** Reads the one file, migrating the old ones on the first start. Called once at client init. */
	public static synchronized void load(Path configDir) {
		final Path nextFile = configDir.resolve(AureliaClient.MOD_ID).resolve(FILE);
		if (nextFile.equals(file)) {
			return;
		}
		file = nextFile;
		version = 0;
		locked = false;
		SECTIONS.clear();
		final JsonObject existing = readObject(file);
		if (existing == null && Files.isRegularFile(file)) {
			// unreadable: keep the bytes for the player, never overwrite them, and start that one setting set from defaults
			setAsideUnreadable();
		}
		if (existing != null) {
			final JsonElement stored = existing.get(VERSION_KEY);
			if (stored != null && stored.isJsonPrimitive() && stored.getAsJsonPrimitive().isNumber()) {
				version = stored.getAsInt();
			}
			for (final Map.Entry<String, JsonElement> entry : existing.entrySet()) {
				if (entry.getValue().isJsonObject()) {
					SECTIONS.put(entry.getKey(), entry.getValue().getAsJsonObject());
				}
			}
		}
		if (version < VERSION) {
			migrate();
		}
	}

	/** A copy of one section, or an empty object when it was never written. Change it and hand it back through {@link #put}. */
	public static synchronized JsonObject section(String name) {
		ensureLoaded();
		final JsonObject section = SECTIONS.get(name);
		return section == null ? new JsonObject() : section.deepCopy();
	}

	/** Replaces one section and writes the whole file. */
	public static synchronized void put(String name, JsonObject section) {
		ensureLoaded();
		SECTIONS.put(name, section);
		save();
	}

	public static synchronized int version() {
		return version;
	}

	/** Writes {@code settings.json} through a temp file and an atomic move. A failure leaves the old file untouched. */
	public static synchronized boolean save() {
		if (file == null || locked) {
			return false;
		}
		try {
			Files.createDirectories(file.getParent());
			final JsonObject root = new JsonObject();
			root.addProperty(VERSION_KEY, version);
			for (final Map.Entry<String, JsonObject> entry : SECTIONS.entrySet()) {
				root.add(entry.getKey(), entry.getValue());
			}
			final Path temp = file.resolveSibling(FILE + ".tmp");
			Files.writeString(temp, root.toString(), StandardCharsets.UTF_8);
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return true;
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] settings.json could not be written; the old files are left as they are", e);
			return false;
		}
	}

	/** Reads the old files on top of what a version 1 file already holds, then names the ones it used. */
	private static void migrate() {
		boolean complete = true;
		final List<Path> used = new ArrayList<>();
		for (final LegacyFile legacy : LEGACY_FILES) {
			final Path source = file.resolveSibling(legacy.fileName);
			if (!Files.isRegularFile(source)) {
				continue;
			}
			try {
				SECTIONS.put(legacy.section, legacy.reader.read(source));
				used.add(source);
			} catch (Exception e) {
				complete = false;
				AureliaClient.LOGGER.error("[ATR] {} could not be read; it is left alone and settings.json stays on version 0", legacy.fileName, e);
			}
		}
		version = complete ? VERSION : 0;
		if (!save()) {
			version = 0;
			return;
		}
		for (final Path source : used) {
			rename(source);
		}
	}

	private static void rename(Path source) {
		final Path target = source.resolveSibling(source.getFileName() + MIGRATED_SUFFIX);
		if (Files.exists(target)) {
			return;
		}
		try {
			Files.move(source, target);
		} catch (IOException e) {
			AureliaClient.LOGGER.error("[ATR] {} could not be renamed to {}", source.getFileName(), target.getFileName(), e);
		}
	}

	private static void ensureLoaded() {
		if (file == null) {
			load(FabricLoader.getInstance().getConfigDir());
		}
	}

	@Nullable
	private static JsonObject readObject(Path path) {
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try {
			final JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
			return root.isJsonObject() ? root.getAsJsonObject() : null;
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] {} could not be read", path.getFileName(), e);
			return null;
		}
	}

	/** Moves a damaged {@code settings.json} out of the way so the bytes survive and the next start can write a fresh one. */
	private static void setAsideUnreadable() {
		final Path target = file.resolveSibling(FILE + ".unreadable");
		try {
			Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
			AureliaClient.LOGGER.error("[ATR] settings.json could not be read; it is kept as {} and the settings start from their defaults", target.getFileName());
		} catch (IOException e) {
			// could not move it: leave the file alone and refuse to write over it this session
			locked = true;
			AureliaClient.LOGGER.error("[ATR] settings.json could not be read or moved aside; it is left alone and will not be overwritten", e);
		}
	}
}
