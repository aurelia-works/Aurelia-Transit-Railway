package org.mtr.aurelia.dashboard;

import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Remembers the last selected mode of the primary Transit Dashboard in {@code config/aurelia/dashboard.json}.
 * Missing or unreadable files default cleanly to {@link DashboardMode#TRAIN}.
 */
public final class DashboardSettings {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Dashboard");
	private static DashboardMode lastMode = DashboardMode.TRAIN;
	@Nullable
	private static Path configDir;

	private DashboardSettings() {
	}

	public static DashboardMode lastMode() {
		return lastMode;
	}

	public static void setLastMode(DashboardMode mode) {
		if (mode == null || mode == lastMode) {
			return;
		}
		lastMode = mode;
		if (configDir != null) {
			save(configDir, lastMode);
		}
	}

	/** Sets the in-memory mode for tests or session overrides without requiring FabricLoader. */
	public static void setLastModeInMemory(DashboardMode mode) {
		lastMode = mode == null ? DashboardMode.TRAIN : mode;
	}

	public static DashboardMode load(Path dir) {
		configDir = dir;
		lastMode = read(dir);
		return lastMode;
	}

	public static DashboardMode read(Path dir) {
		final Path file = file(dir);
		if (!Files.isRegularFile(file)) {
			return DashboardMode.TRAIN;
		}
		try {
			final JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			if (json.has("lastMode")) {
				final JsonElement el = json.get("lastMode");
				if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
					return DashboardMode.byId(el.getAsString(), DashboardMode.TRAIN);
				}
			}
			return DashboardMode.TRAIN;
		} catch (Exception e) {
			LOGGER.error("[ATR] dashboard.json could not be read; starting on Train mode", e);
			return DashboardMode.TRAIN;
		}
	}

	public static void save(Path dir, DashboardMode mode) {
		try {
			final Path file = file(dir);
			Files.createDirectories(file.getParent());
			final JsonObject json = new JsonObject();
			json.addProperty("lastMode", (mode == null ? DashboardMode.TRAIN : mode).id);
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			LOGGER.error("[ATR] dashboard.json could not be saved", e);
		}
	}

	private static Path file(Path dir) {
		return dir.resolve("aurelia").resolve("dashboard.json");
	}
}
