package org.mtr.camera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * File format: {@code {"version":1,"speed":8.0,"hideHud":true,"keyframes":[{x,y,z,yaw,pitch,fov}]}}.
 * {@code speed} is blocks per second; a missing key falls back to its default so older files keep
 * loading. Bad keyframe entries are skipped and counted; a file without a {@code keyframes} array
 * is not a camera path at all. Pure Java (no Minecraft types) so plain unit tests cover it.
 */
public final class CameraPathJson {

	public static final int VERSION = 1;
	public static final double DEFAULT_SPEED = 8;
	public static final boolean DEFAULT_HIDE_HUD = true;

	public record CameraFile(CameraPath path, double speed, boolean hideHud) {
	}

	public record LoadResult(CameraFile file, int loaded, int skipped) {
	}

	private CameraPathJson() {
	}

	public static String write(CameraPath path, double speed, boolean hideHud) {
		final JsonArray keyframes = new JsonArray();
		path.keyframes().forEach(keyframe -> {
			final JsonObject object = new JsonObject();
			object.addProperty("x", keyframe.x());
			object.addProperty("y", keyframe.y());
			object.addProperty("z", keyframe.z());
			object.addProperty("yaw", CameraPath.normalizeAngle(keyframe.yaw()));
			object.addProperty("pitch", keyframe.pitch());
			object.addProperty("fov", keyframe.fov());
			keyframes.add(object);
		});
		final JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		root.addProperty("speed", speed);
		root.addProperty("hideHud", hideHud);
		root.add("keyframes", keyframes);
		return root.toString();
	}

	/** Reads a saved path. Throws {@link IllegalArgumentException} when the text is not a camera path file. */
	public static LoadResult read(String json) {
		final JsonObject root;
		try {
			root = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("Not a JSON object", e);
		}
		if (!root.has("keyframes") || !root.get("keyframes").isJsonArray()) {
			throw new IllegalArgumentException("Missing \"keyframes\" array");
		}
		final double speed = finiteOr(root, "speed", DEFAULT_SPEED);
		final boolean hideHud = root.has("hideHud") && root.get("hideHud").isJsonPrimitive() ? root.get("hideHud").getAsBoolean() : DEFAULT_HIDE_HUD;
		final java.util.List<CameraKeyframe> keyframes = new java.util.ArrayList<>();
		int skipped = 0;
		for (final JsonElement element : root.getAsJsonArray("keyframes")) {
			try {
				final JsonObject object = element.getAsJsonObject();
				keyframes.add(new CameraKeyframe(
						finite(object, "x"), finite(object, "y"), finite(object, "z"),
						finiteOr(object, "yaw", 0), finiteOr(object, "pitch", 0), finiteOr(object, "fov", CameraKeyframe.DEFAULT_FOV)
				));
			} catch (RuntimeException e) {
				skipped++;
			}
		}
		return new LoadResult(new CameraFile(new CameraPath(keyframes), speed, hideHud), keyframes.size(), skipped);
	}

	private static double finite(JsonObject object, String key) {
		final double value = object.get(key).getAsDouble();
		if (!Double.isFinite(value)) {
			throw new IllegalArgumentException(key + " is not finite");
		}
		return value;
	}

	private static double finiteOr(JsonObject object, String key, double fallback) {
		if (!object.has(key)) {
			return fallback;
		}
		return finite(object, key);
	}
}
