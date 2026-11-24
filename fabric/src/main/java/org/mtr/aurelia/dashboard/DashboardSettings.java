package org.mtr.aurelia.dashboard;

import org.mtr.aurelia.config.AureliaSettings;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Remembers the last selected mode of the primary Transit Dashboard, the {@code dashboard} section of
 * {@code config/aurelia/settings.json} (formerly {@code dashboard.json}).
 * Missing or unreadable sections default cleanly to {@link DashboardMode#TRAIN}.
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

	public static DashboardMode load(Path dir) {
		configDir = dir;
		AureliaSettings.load(dir);
		lastMode = from(AureliaSettings.section(AureliaSettings.DASHBOARD));
		return lastMode;
	}

	public static DashboardMode read(Path dir) {
		AureliaSettings.load(dir);
		return from(AureliaSettings.section(AureliaSettings.DASHBOARD));
	}

	public static void save(Path dir, DashboardMode mode) {
		AureliaSettings.load(dir);
		AureliaSettings.put(AureliaSettings.DASHBOARD, toJson(mode));
	}

	/** Reads the old {@code dashboard.json} into the section body settings.json keeps. */
	public static JsonObject readLegacy(Path file) throws IOException {
		return toJson(from(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()));
	}

	private static DashboardMode from(JsonObject json) {
		try {
			if (json.has("lastMode")) {
				final JsonElement el = json.get("lastMode");
				if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
					return DashboardMode.byId(el.getAsString(), DashboardMode.TRAIN);
				}
			}
			return DashboardMode.TRAIN;
		} catch (Exception e) {
			LOGGER.error("[ATR] the dashboard section of settings.json could not be read; starting on Train mode", e);
			return DashboardMode.TRAIN;
		}
	}

	private static JsonObject toJson(DashboardMode mode) {
		final JsonObject json = new JsonObject();
		json.addProperty("lastMode", (mode == null ? DashboardMode.TRAIN : mode).id);
		return json;
	}
}
