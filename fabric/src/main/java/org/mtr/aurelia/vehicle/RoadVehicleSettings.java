package org.mtr.aurelia.vehicle;

import net.minecraft.network.PacketByteBuf;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The road vehicle switches in {@code config/aurelia/road-vehicles.json}, owned by the server: on/off, body colour, size
 * and top speed. A missing file or key keeps the default, so an old config folder loads unchanged.
 */
public record RoadVehicleSettings(boolean enabled, int colour, int sizePercent, int maxSpeedKmh) {

	public static final int SIZE_MIN = 50;
	public static final int SIZE_MAX = 150;
	public static final int SPEED_MIN = 10;
	public static final int SPEED_MAX = 160;
	public static final RoadVehicleSettings DEFAULT = new RoadVehicleSettings(true, 0x2F5DA8, 100, 90);

	public RoadVehicleSettings clamped() {
		return new RoadVehicleSettings(enabled, colour & 0xFFFFFF, clamp(sizePercent, SIZE_MIN, SIZE_MAX), clamp(maxSpeedKmh, SPEED_MIN, SPEED_MAX));
	}

	public double scale() {
		return sizePercent / 100D;
	}

	public RoadVehicleSettings withEnabled(boolean value) {
		return new RoadVehicleSettings(value, colour, sizePercent, maxSpeedKmh);
	}

	public RoadVehicleSettings withColour(int value) {
		return new RoadVehicleSettings(enabled, value, sizePercent, maxSpeedKmh);
	}

	public RoadVehicleSettings withSize(int value) {
		return new RoadVehicleSettings(enabled, colour, value, maxSpeedKmh);
	}

	public RoadVehicleSettings withSpeed(int value) {
		return new RoadVehicleSettings(enabled, colour, sizePercent, value);
	}

	public void write(PacketByteBuf buf) {
		buf.writeBoolean(enabled);
		buf.writeInt(colour);
		buf.writeVarInt(sizePercent);
		buf.writeVarInt(maxSpeedKmh);
	}

	public static RoadVehicleSettings read(PacketByteBuf buf) {
		return new RoadVehicleSettings(buf.readBoolean(), buf.readInt(), buf.readVarInt(), buf.readVarInt()).clamped();
	}

	public static RoadVehicleSettings load(Path configDir) {
		final Path file = file(configDir);
		if (!Files.isRegularFile(file)) {
			return DEFAULT;
		}
		try {
			final JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			return new RoadVehicleSettings(
					json.has("enabled") ? json.get("enabled").getAsBoolean() : DEFAULT.enabled,
					json.has("colour") ? Integer.parseInt(json.get("colour").getAsString().replace("#", ""), 16) : DEFAULT.colour,
					json.has("size") ? json.get("size").getAsInt() : DEFAULT.sizePercent,
					json.has("maxSpeedKmh") ? json.get("maxSpeedKmh").getAsInt() : DEFAULT.maxSpeedKmh
			).clamped();
		} catch (Exception e) {
			RoadVehicles.LOGGER.error("[ATR] road-vehicles.json could not be read; road vehicles keep their defaults", e);
			return DEFAULT;
		}
	}

	public void save(Path configDir) {
		try {
			final Path file = file(configDir);
			Files.createDirectories(file.getParent());
			final JsonObject json = new JsonObject();
			json.addProperty("enabled", enabled);
			json.addProperty("colour", String.format("#%06X", colour));
			json.addProperty("size", sizePercent);
			json.addProperty("maxSpeedKmh", maxSpeedKmh);
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			RoadVehicles.LOGGER.error("[ATR] road-vehicles.json could not be saved", e);
		}
	}

	private static Path file(Path configDir) {
		return configDir.resolve("aurelia").resolve("road-vehicles.json");
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
