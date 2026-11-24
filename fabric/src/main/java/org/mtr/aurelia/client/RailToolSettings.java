package org.mtr.aurelia.client;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The bridge, tunnel, tunnel wall and rail builder tools' client switches, in a new file {@code config/aurelia/rail_tools.json}.
 * A missing file, a missing key or a value that is not a boolean keeps the default (on), so old configs need no migration.
 */
public final class RailToolSettings {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");
	private static boolean loaded;
	private static boolean multiNode = true;

	private RailToolSettings() {
	}

	/** Whether a second click further along the track applies the tool to every rail in between (MTR 4.1). */
	public static boolean multiNode() {
		load();
		return multiNode;
	}

	public static void toggleMultiNode() {
		load();
		multiNode = !multiNode;
		save();
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		final Path file = file();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonElement element = JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("multiNode");
			if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
				multiNode = element.getAsBoolean();
			}
		} catch (Exception e) {
			LOGGER.error("[ATR] rail_tools.json could not be read; keeps the defaults", e);
		}
	}

	private static void save() {
		try {
			final Path file = file();
			Files.createDirectories(file.getParent());
			final JsonObject json = new JsonObject();
			json.addProperty("multiNode", multiNode);
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			LOGGER.error("[ATR] rail_tools.json could not be written", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurelia").resolve("rail_tools.json");
	}
}
