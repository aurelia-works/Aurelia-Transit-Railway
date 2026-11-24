package org.mtr.aurelia.client.voice;

import net.fabricmc.loader.api.FabricLoader;
import org.mtr.aurelia.client.AureliaClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The player's announcer voice choices, in {@code config/aurelia/voice.properties}: our own voice or the computer's,
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

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(AureliaClient.MOD_ID).resolve("voice.properties");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;
		final Path file = file();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				PROPERTIES.load(reader);
			} catch (IOException e) {
				AureliaClient.LOGGER.error("[ATR] Could not read {}; using the default voice", file, e);
			}
		}
	}

	private static void save() {
		final Path file = file();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				PROPERTIES.store(writer, "Aurelia Transit Railway announcer voice");
			}
		} catch (IOException e) {
			AureliaClient.LOGGER.error("[ATR] Could not save {}", file, e);
		}
	}
}
