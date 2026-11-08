package org.mtr.aurelia.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The private-pack master switch, in {@code config/aurelia/packs.json}. Missing file, missing key or a value that is not
 * a boolean keeps packs on, so old configs and worlds load unchanged.
 */
public final class PackSettings {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");
	private static Boolean enabled;

	private PackSettings() {
	}

	public static synchronized boolean enabled() {
		load();
		return enabled;
	}

	public static synchronized void setEnabled(boolean value) {
		load();
		enabled = value;
		save();
	}

	private static void load() {
		if (enabled != null) {
			return;
		}
		enabled = true;
		final Path file = file();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonElement root = JsonParser.parseString(Files.readString(file));
			if (root.isJsonObject()) {
				final JsonElement value = root.getAsJsonObject().get("enabled");
				if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
					enabled = value.getAsBoolean();
				}
			}
		} catch (Exception e) {
			LOGGER.error("[ATR] packs.json could not be read; private packs stay on", e);
		}
	}

	private static void save() {
		try {
			final Path file = file();
			Files.createDirectories(file.getParent());
			final JsonObject root = new JsonObject();
			root.addProperty("enabled", enabled);
			Files.writeString(file, root.toString());
		} catch (Exception e) {
			LOGGER.error("[ATR] packs.json could not be written", e);
		}
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("aurelia").resolve("packs.json");
	}
}
