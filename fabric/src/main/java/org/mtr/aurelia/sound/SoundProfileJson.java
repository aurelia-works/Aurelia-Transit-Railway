package org.mtr.aurelia.sound;

import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reads a profile from JSON. A pack puts it at {@code assets/<namespace>/aurelia_sound_profiles/<name>.json}; the
 * profile id is {@code <namespace>:<name>}. Unknown keys are ignored and a bad file is skipped, so one broken profile
 * cannot take the others down.
 */
public final class SoundProfileJson {

	private SoundProfileJson() {
	}

	@Nullable
	public static SoundProfile parse(String id, String json) {
		try {
			final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			final JsonObject motor = object(root, "motor");
			final float motorVolume = motor == null ? 1 : clampFloat(number(motor, "volume", 1), 0, 2);
			final List<double[]> points = curve(motor, "pitch_curve", "pitch", "pitch", false);
			final List<double[]> gains = curve(motor, "volume_curve", null, "volume", true);
			final double[] speeds = speeds(points);
			final float[] pitches = values(points);
			final double[] gainSpeeds = speeds(gains);
			final float[] gainsAtSpeed = values(gains);

			final String name = string(root, "name");
			return new SoundProfile(
					id,
					name == null ? "" : name,
					motorVolume,
					motorSounds(motor),
					motor != null && bool(motor, "always"),
					speeds,
					pitches,
					gainSpeeds,
					gainsAtSpeed,
					motorLayers(root),
					string(root, "door_chime"),
					string(root, "door_open"),
					string(root, "door_close"),
					string(root, "brake_release"),
					firstNonNull(string(root, "compressor_loop"), string(root, "compressor")),
					string(root, "compressor_attack"),
					string(root, "compressor_release"),
					flange(root)
			);
		} catch (Exception e) {
			return null;
		}
	}

	private static List<String> flange(JsonObject root) {
		final JsonElement element = root.get("flange");
		if (element == null || element.isJsonNull()) {
			return Collections.emptyList();
		}
		if (element.isJsonArray()) {
			final List<String> sounds = new ArrayList<>();
			for (final JsonElement item : element.getAsJsonArray()) {
				if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() && !item.getAsString().isBlank()) {
					sounds.add(item.getAsString().trim());
				}
			}
			return SoundProfile.soundList(sounds);
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && !element.getAsString().isBlank()) {
			return List.of(element.getAsString().trim());
		}
		return Collections.emptyList();
	}

	/** The optional {@code motor_layers} array: objects shaped like {@code motor}. A layer without sounds is dropped. */
	private static List<MotorLayer> motorLayers(JsonObject root) {
		final JsonArray array = array(root, "motor_layers");
		if (array == null) {
			return Collections.emptyList();
		}
		final List<MotorLayer> layers = new ArrayList<>();
		for (final JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			final JsonObject layer = element.getAsJsonObject();
			final List<String> sounds = motorSounds(layer);
			if (sounds.isEmpty()) {
				continue;
			}
			final List<double[]> pitches = curve(layer, "pitch_curve", "pitch", "pitch", false);
			final List<double[]> gains = curve(layer, "volume_curve", null, "volume", true);
			layers.add(new MotorLayer(sounds, clampFloat(number(layer, "volume", 1), 0, 2), bool(layer, "always"), speeds(pitches), values(pitches), speeds(gains), values(gains)));
		}
		return Collections.unmodifiableList(layers);
	}

	private static List<String> motorSounds(@Nullable JsonObject motor) {
		final JsonElement element = motor == null ? null : motor.get("sounds");
		if (element == null || element.isJsonNull()) {
			return Collections.emptyList();
		}
		if (element.isJsonArray()) {
			final List<String> sounds = new ArrayList<>();
			for (final JsonElement item : element.getAsJsonArray()) {
				if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString() && !item.getAsString().isBlank()) {
					sounds.add(item.getAsString().trim());
				}
			}
			return SoundProfile.soundList(sounds);
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() && !element.getAsString().isBlank()) {
			return List.of(element.getAsString().trim());
		}
		return Collections.emptyList();
	}

	/** The {@code [{speed, value}]} points of a curve, sorted by speed. */
	private static List<double[]> curve(@Nullable JsonObject motor, String arrayKey, String aliasKey, String valueKey, boolean allowZero) {
		final JsonArray primary = array(motor, arrayKey);
		final JsonArray chosen = primary == null && aliasKey != null ? array(motor, aliasKey) : primary;
		final List<double[]> points = new ArrayList<>();
		if (chosen == null) {
			return points;
		}
		for (final JsonElement element : chosen) {
			if (!element.isJsonObject()) {
				continue;
			}
			final JsonObject point = element.getAsJsonObject();
			final double speed = number(point, "speed", Double.NaN);
			final double value = number(point, valueKey, Double.NaN);
			if (Double.isFinite(speed) && Double.isFinite(value) && (allowZero ? value >= 0 : value > 0)) {
				points.add(new double[]{Math.max(0, speed), value});
			}
		}
		points.sort((left, right) -> Double.compare(left[0], right[0]));
		return points;
	}

	private static double[] speeds(List<double[]> points) {
		final double[] speeds = new double[points.size()];
		for (int i = 0; i < points.size(); i++) {
			speeds[i] = points.get(i)[0];
		}
		return speeds;
	}

	private static float[] values(List<double[]> points) {
		final float[] values = new float[points.size()];
		for (int i = 0; i < points.size(); i++) {
			values[i] = (float) points.get(i)[1];
		}
		return values;
	}

	@Nullable
	private static JsonObject object(@Nullable JsonObject parent, String key) {
		if (parent == null) {
			return null;
		}
		final JsonElement element = parent.get(key);
		return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
	}

	@Nullable
	private static JsonArray array(@Nullable JsonObject parent, String key) {
		if (parent == null) {
			return null;
		}
		final JsonElement element = parent.get(key);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
	}

	@Nullable
	private static String string(@Nullable JsonObject parent, String key) {
		if (parent == null) {
			return null;
		}
		final JsonElement element = parent.get(key);
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			final String value = element.getAsString().trim();
			return value.isEmpty() ? null : value;
		}
		return null;
	}

	@Nullable
	private static String firstNonNull(@Nullable String first, @Nullable String second) {
		return first == null ? second : first;
	}

	private static boolean bool(@Nullable JsonObject parent, String key) {
		if (parent == null) {
			return false;
		}
		final JsonElement element = parent.get(key);
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() && element.getAsBoolean();
	}

	private static double number(@Nullable JsonObject parent, String key, double fallback) {
		if (parent == null) {
			return fallback;
		}
		final JsonElement element = parent.get(key);
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			return element.getAsDouble();
		}
		return fallback;
	}

	private static float clampFloat(double value, float min, float max) {
		return (float) Math.max(min, Math.min(max, value));
	}
}
