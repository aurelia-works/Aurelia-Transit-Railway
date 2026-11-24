package org.mtr.aurelia.client.voice;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.config.AureliaSettings;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * The player's announcer voice choices, the {@code voice} section of {@code config/aurelia/settings.json} (formerly
 * {@code voice.properties}, whose keys and string values are kept as they were): our own voice or the computer's,
 * which voice per language, and how fast it talks. A missing file means our voice, each language's first voice, normal
 * speed.
 */
public final class VoiceSettings {

	public static final double MIN_SPEED = 0.8;
	public static final double MAX_SPEED = 1.3;

	private static final Properties PROPERTIES = new Properties();
	private static boolean loaded;

	private VoiceSettings() {
	}

	/** Our own voice (once downloaded) rather than the computer's. */
	public static synchronized boolean ours() {
		load();
		return !"false".equals(PROPERTIES.getProperty("ours"));
	}

	public static synchronized void setOurs(boolean ours) {
		load();
		PROPERTIES.setProperty("ours", Boolean.toString(ours));
		save();
	}

	/** The chosen voice for a language, or its first voice. */
	public static synchronized VoiceCatalog.Voice voice(String language) {
		load();
		final VoiceCatalog.Voice chosen = VoiceCatalog.byId(PROPERTIES.getProperty("voice." + language, ""));
		if (chosen != null && chosen.language().equals(language)) {
			return chosen;
		}
		final java.util.List<VoiceCatalog.Voice> voices = VoiceCatalog.voices(language);
		return voices.isEmpty() ? null : voices.get(0);
	}

	public static synchronized void setVoice(VoiceCatalog.Voice voice) {
		load();
		PROPERTIES.setProperty("voice." + voice.language(), voice.id());
		save();
	}

	public static synchronized double speed() {
		load();
		try {
			return Math.max(MIN_SPEED, Math.min(MAX_SPEED, Double.parseDouble(PROPERTIES.getProperty("speed", "1"))));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	public static synchronized void setSpeed(double speed) {
		load();
		PROPERTIES.setProperty("speed", String.format(java.util.Locale.ROOT, "%.2f", speed));
		save();
	}

	/** Reads the old {@code voice.properties} into the section body settings.json keeps, keys untouched. */
	public static JsonObject readLegacy(Path file) throws IOException {
		final Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		return toJson(properties);
	}

	public static synchronized void load(Path configDir) {
		AureliaSettings.load(configDir);
		loaded = true;
		apply(AureliaSettings.section(AureliaSettings.VOICE));
	}

	private static void apply(JsonObject json) {
		PROPERTIES.clear();
		for (final Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (entry.getValue().isJsonPrimitive()) {
				PROPERTIES.setProperty(entry.getKey(), entry.getValue().getAsString());
			}
		}
	}

	private static JsonObject toJson(Properties properties) {
		final JsonObject json = new JsonObject();
		for (final String name : properties.stringPropertyNames()) {
			json.addProperty(name, properties.getProperty(name));
		}
		return json;
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		load(FabricLoader.getInstance().getConfigDir());
	}

	private static void save() {
		AureliaSettings.put(AureliaSettings.VOICE, toJson(PROPERTIES));
	}
}
