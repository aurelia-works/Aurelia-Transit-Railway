package org.mtr.aurelia.vehicle;

import net.minecraft.network.PacketByteBuf;
import org.mtr.aurelia.config.AureliaSettings;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The road vehicle switches in the {@code road_vehicles} section of {@code config/aurelia/settings.json} (formerly
 * {@code road-vehicles.json}), owned by the server: on/off, body colour, size and top speed, and bike share on/off with
 * the hire bikes' colour. A missing file or key keeps the default, so an old config folder loads unchanged.
 */
public record RoadVehicleSettings(boolean enabled, int colour, int sizePercent, int maxSpeedKmh, boolean bikeShare, int bikeColour) {

	public static final int SIZE_MIN = 50;
	public static final int SIZE_MAX = 150;
	public static final int SPEED_MIN = 10;
	public static final int SPEED_MAX = 160;
	public static final RoadVehicleSettings DEFAULT = new RoadVehicleSettings(true, 0x2F5DA8, 100, 90, true, 0xC8312B);

	public RoadVehicleSettings clamped() {
		return new RoadVehicleSettings(enabled, colour & 0xFFFFFF, clamp(sizePercent, SIZE_MIN, SIZE_MAX), clamp(maxSpeedKmh, SPEED_MIN, SPEED_MAX), bikeShare,
				bikeColour & 0xFFFFFF);
	}

	public double scale() {
		return sizePercent / 100D;
	}

	/** The paint for a kind: hire bikes wear the scheme colour, everything else the body colour. */
	public int colour(RoadVehicleKind kind) {
		return kind == RoadVehicleKind.BICYCLE ? bikeColour : colour;
	}

	public RoadVehicleSettings withEnabled(boolean value) {
		return new RoadVehicleSettings(value, colour, sizePercent, maxSpeedKmh, bikeShare, bikeColour);
	}

	public RoadVehicleSettings withColour(int value) {
		return new RoadVehicleSettings(enabled, value, sizePercent, maxSpeedKmh, bikeShare, bikeColour);
	}

	public RoadVehicleSettings withSize(int value) {
		return new RoadVehicleSettings(enabled, colour, value, maxSpeedKmh, bikeShare, bikeColour);
	}

	public RoadVehicleSettings withSpeed(int value) {
		return new RoadVehicleSettings(enabled, colour, sizePercent, value, bikeShare, bikeColour);
	}

	public RoadVehicleSettings withBikeShare(boolean value) {
		return new RoadVehicleSettings(enabled, colour, sizePercent, maxSpeedKmh, value, bikeColour);
	}

	public RoadVehicleSettings withBikeColour(int value) {
		return new RoadVehicleSettings(enabled, colour, sizePercent, maxSpeedKmh, bikeShare, value);
	}

	public void write(PacketByteBuf buf) {
		buf.writeBoolean(enabled);
		buf.writeInt(colour);
		buf.writeVarInt(sizePercent);
		buf.writeVarInt(maxSpeedKmh);
		buf.writeBoolean(bikeShare);
		buf.writeInt(bikeColour);
	}

	public static RoadVehicleSettings read(PacketByteBuf buf) {
		return new RoadVehicleSettings(buf.readBoolean(), buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readInt()).clamped();
	}

	public static RoadVehicleSettings load(Path configDir) {
		AureliaSettings.load(configDir);
		try {
			return from(AureliaSettings.section(AureliaSettings.ROAD_VEHICLES));
		} catch (Exception e) {
			RoadVehicles.LOGGER.error("[ATR] the road_vehicles section of settings.json could not be read; road vehicles keep their defaults", e);
			return DEFAULT;
		}
	}

	/** Reads the old {@code road-vehicles.json} into the section body settings.json keeps. */
	public static JsonObject readLegacy(Path file) throws IOException {
		return from(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()).toJson();
	}

	public void save(Path configDir) {
		AureliaSettings.load(configDir);
		AureliaSettings.put(AureliaSettings.ROAD_VEHICLES, toJson());
	}

	public JsonObject toJson() {
		final JsonObject json = new JsonObject();
		json.addProperty("enabled", enabled);
		json.addProperty("colour", String.format("#%06X", colour));
		json.addProperty("size", sizePercent);
		json.addProperty("maxSpeedKmh", maxSpeedKmh);
		json.addProperty("bikeShare", bikeShare);
		json.addProperty("bikeColour", String.format("#%06X", bikeColour));
		return json;
	}

	private static RoadVehicleSettings from(JsonObject json) {
		return new RoadVehicleSettings(
				json.has("enabled") ? json.get("enabled").getAsBoolean() : DEFAULT.enabled,
				json.has("colour") ? Integer.parseInt(json.get("colour").getAsString().replace("#", ""), 16) : DEFAULT.colour,
				json.has("size") ? json.get("size").getAsInt() : DEFAULT.sizePercent,
				json.has("maxSpeedKmh") ? json.get("maxSpeedKmh").getAsInt() : DEFAULT.maxSpeedKmh,
				json.has("bikeShare") ? json.get("bikeShare").getAsBoolean() : DEFAULT.bikeShare,
				json.has("bikeColour") ? Integer.parseInt(json.get("bikeColour").getAsString().replace("#", ""), 16) : DEFAULT.bikeColour
		).clamped();
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
