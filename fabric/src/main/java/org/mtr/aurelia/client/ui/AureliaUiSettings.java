package org.mtr.aurelia.client.ui;

import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Per-screen Aurelia look, in {@code config/aurelia/ui.json}. Missing file, missing key, or a value that is not
 * true or false keeps that screen on the Aurelia look. {@code config/mtr.json} is never touched.
 */
public final class AureliaUiSettings {

	public enum ScreenId {
		DASHBOARD("dashboard"),
		STATION("station"),
		ROUTE("route"),
		DEPOT("depot"),
		SIDING("siding"),
		OPTIONS("options"),
		PANEL("panel");

		public final String key;

		ScreenId(String key) {
			this.key = key;
		}
	}

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");
	private static Values current = Values.defaults();

	private AureliaUiSettings() {
	}

	public static boolean enabled(ScreenId id) {
		return current.enabled(id);
	}

	public static void toggle(ScreenId id) {
		current = current.with(id, !current.enabled(id));
		save();
	}

	public static void load(Path configDir) {
		final Path file = file(configDir);
		if (!Files.isRegularFile(file)) {
			current = Values.defaults();
			return;
		}
		try {
			current = Values.from(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
		} catch (Exception e) {
			LOGGER.error("[ATR] ui.json could not be read; the Aurelia look stays on", e);
			current = Values.defaults();
		}
	}

	/** Missing keys stay on. A value that is not a boolean is ignored. Extra keys are ignored. */
	public static Values from(JsonObject json) {
		return Values.from(json);
	}

	private static void save() {
		try {
			final Path file = file(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
			Files.createDirectories(file.getParent());
			Files.writeString(file, current.toJson().toString());
		} catch (Exception e) {
			LOGGER.error("[ATR] ui.json could not be written", e);
		}
	}

	private static Path file(Path configDir) {
		return configDir.resolve("aurelia").resolve("ui.json");
	}

	public static final class Values {
		private final boolean[] on;

		private Values(boolean[] on) {
			this.on = on;
		}

		public static Values defaults() {
			final boolean[] on = new boolean[ScreenId.values().length];
			Arrays.fill(on, true);
			return new Values(on);
		}

		public boolean enabled(ScreenId id) {
			return on[id.ordinal()];
		}

		public Values with(ScreenId id, boolean enabled) {
			final boolean[] next = Arrays.copyOf(on, on.length);
			next[id.ordinal()] = enabled;
			return new Values(next);
		}

		public static Values from(JsonObject json) {
			final boolean[] on = Arrays.copyOf(defaults().on, ScreenId.values().length);
			for (final ScreenId id : ScreenId.values()) {
				if (!json.has(id.key)) {
					continue;
				}
				final JsonElement element = json.get(id.key);
				if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
					on[id.ordinal()] = element.getAsBoolean();
				}
			}
			return new Values(on);
		}

		public JsonObject toJson() {
			final JsonObject json = new JsonObject();
			for (final ScreenId id : ScreenId.values()) {
				json.addProperty(id.key, on[id.ordinal()]);
			}
			return json;
		}
	}
}
