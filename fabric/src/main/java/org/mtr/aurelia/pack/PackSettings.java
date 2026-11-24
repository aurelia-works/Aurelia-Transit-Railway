package org.mtr.aurelia.pack;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.config.AureliaSettings;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The private-pack master switch, the {@code packs} section of {@code config/aurelia/settings.json} (formerly
 * {@code packs.json}). Missing file, missing key or a value that is not a boolean keeps packs on, so old configs and
 * worlds load unchanged.
 */
public final class PackSettings {

	private static boolean enabled = true;
	private static boolean loaded;

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

	public static synchronized void load(Path configDir) {
		AureliaSettings.load(configDir);
		loaded = true;
		apply(AureliaSettings.section(AureliaSettings.PACKS));
	}

	/** Reads the old {@code packs.json} into the section body settings.json keeps. */
	public static JsonObject readLegacy(Path file) throws IOException {
		final JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
		enabled = read(root);
		return toJson();
	}

	private static void apply(JsonObject json) {
		enabled = read(json);
	}

	private static boolean read(JsonObject json) {
		final JsonElement value = json.get("enabled");
		return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : true;
	}

	private static JsonObject toJson() {
		final JsonObject root = new JsonObject();
		root.addProperty("enabled", enabled);
		return root;
	}

	private static void load() {
		if (!loaded) {
			load(FabricLoader.getInstance().getConfigDir());
		}
	}

	private static void save() {
		AureliaSettings.put(AureliaSettings.PACKS, toJson());
	}
}
