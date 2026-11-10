package org.mtr.aurelia.client.map;

import org.mtr.aurelia.client.AureliaClient;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.mod.client.MinecraftClientData;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The MTR network this client has seen in one world and dimension: rails as lines of points and stations as areas,
 * kept in {@code network.json} beside the map regions so the map still shows them far away. MTR only sends the client
 * what is near the player, so a rail or station is forgotten only when the player is near where it was and MTR no
 * longer has it.
 */
public final class SeenNetwork {

	/** Points along a rail are this many blocks apart. */
	private static final double STEP = 2;
	private static final int MAX_POINTS = 512;
	/** A missing rail or station within this many blocks of the player has been removed. */
	private static final double FORGET_RANGE = 64;
	private static final int MAX_RAILS = 20000;

	public enum Kind {TRACK, PLATFORM, SIDING, BOAT, RUNWAY}

	/** A rail as {@code x0, z0, x1, z1, ...} block coordinates, with its bounding box for culling. */
	public record Line(Kind kind, float[] points, float minX, float minZ, float maxX, float maxZ) {

		public static Line of(Kind kind, float[] points) {
			float minX = Float.MAX_VALUE;
			float minZ = Float.MAX_VALUE;
			float maxX = -Float.MAX_VALUE;
			float maxZ = -Float.MAX_VALUE;
			for (int i = 0; i + 1 < points.length; i += 2) {
				minX = Math.min(minX, points[i]);
				maxX = Math.max(maxX, points[i]);
				minZ = Math.min(minZ, points[i + 1]);
				maxZ = Math.max(maxZ, points[i + 1]);
			}
			return new Line(kind, points, minX, minZ, maxX, maxZ);
		}
	}

	public record Area(String name, int color, long minX, long minZ, long maxX, long maxZ) {
	}

	private final Path file;
	private final Map<String, Line> rails = new HashMap<>();
	private final Map<Long, Area> stations = new HashMap<>();
	private boolean unsaved;

	public SeenNetwork(Path folder) {
		file = folder.resolve("network.json");
		load();
	}

	public Collection<Line> rails() {
		return rails.values();
	}

	public Collection<Area> stations() {
		return stations.values();
	}

	/** Takes in what MTR has now near {@code playerX, playerZ}. */
	public void refresh(double playerX, double playerZ) {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		final Set<String> present = new HashSet<>();
		for (final Rail rail : data.rails) {
			final Kind kind = kind(rail);
			if (kind == null) {
				continue;
			}
			final String id = rail.getHexId();
			present.add(id);
			if (!rails.containsKey(id) && rails.size() < MAX_RAILS) {
				rails.put(id, Line.of(kind, points(rail)));
				unsaved = true;
			}
		}
		// MTR sends nothing for a moment after joining: forget only once it has sent something
		if (!present.isEmpty()) {
			unsaved |= rails.entrySet().removeIf(entry -> !present.contains(entry.getKey()) && near(entry.getValue().points(), playerX, playerZ));
		}

		final Set<Long> presentStations = new HashSet<>();
		addStations(data.stations, presentStations);
		addStations(MinecraftClientData.getDashboardInstance().stations, presentStations);
		if (!data.stations.isEmpty()) {
			unsaved |= stations.entrySet().removeIf(entry -> !presentStations.contains(entry.getKey()) && inside(entry.getValue(), playerX, playerZ));
		}
	}

	public void saveIfChanged() {
		if (!unsaved) {
			return;
		}
		unsaved = false;
		final JsonObject json = new JsonObject();
		final JsonArray railArray = new JsonArray();
		rails.forEach((id, line) -> {
			final JsonObject rail = new JsonObject();
			rail.addProperty("id", id);
			rail.addProperty("kind", line.kind().name());
			final JsonArray points = new JsonArray();
			for (final float point : line.points()) {
				points.add(Math.round(point * 2) / 2F);
			}
			rail.add("points", points);
			railArray.add(rail);
		});
		json.add("rails", railArray);
		final JsonArray stationArray = new JsonArray();
		stations.forEach((id, area) -> {
			final JsonObject station = new JsonObject();
			station.addProperty("id", id);
			station.addProperty("name", area.name());
			station.addProperty("color", area.color());
			station.addProperty("minX", area.minX());
			station.addProperty("minZ", area.minZ());
			station.addProperty("maxX", area.maxX());
			station.addProperty("maxZ", area.maxZ());
			stationArray.add(station);
		});
		json.add("stations", stationArray);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			AureliaClient.LOGGER.warn("[ATR] map network {} could not be saved", file, e);
		}
	}

	private void addStations(Iterable<Station> from, Set<Long> present) {
		for (final Station station : from) {
			present.add(station.getId());
			final Area area = new Area(station.getName(), station.getColor(), station.getMinX(), station.getMinZ(), station.getMaxX(), station.getMaxZ());
			if (!area.equals(stations.put(station.getId(), area))) {
				unsaved = true;
			}
		}
	}

	private void load() {
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			for (final JsonElement element : json.getAsJsonArray("rails")) {
				final JsonObject rail = element.getAsJsonObject();
				final JsonArray array = rail.getAsJsonArray("points");
				final float[] points = new float[array.size() & ~1];
				for (int i = 0; i < points.length; i++) {
					points[i] = array.get(i).getAsFloat();
				}
				rails.put(rail.get("id").getAsString(), Line.of(Kind.valueOf(rail.get("kind").getAsString()), points));
			}
			for (final JsonElement element : json.getAsJsonArray("stations")) {
				final JsonObject station = element.getAsJsonObject();
				stations.put(station.get("id").getAsLong(), new Area(station.get("name").getAsString(), station.get("color").getAsInt(),
						station.get("minX").getAsLong(), station.get("minZ").getAsLong(), station.get("maxX").getAsLong(), station.get("maxZ").getAsLong()));
			}
		} catch (Exception e) {
			AureliaClient.LOGGER.warn("[ATR] map network {} could not be read; it starts empty", file, e);
			rails.clear();
			stations.clear();
		}
	}

	private static Kind kind(Rail rail) {
		if (rail.railMath.getShape() == Rail.Shape.CABLE) {
			return null;
		}
		if (rail.getTransportMode() == TransportMode.AIRPLANE) {
			return Kind.RUNWAY;
		}
		if (rail.getTransportMode() == TransportMode.BOAT) {
			return Kind.BOAT;
		}
		if (rail.isPlatform()) {
			return Kind.PLATFORM;
		}
		return rail.isSiding() ? Kind.SIDING : Kind.TRACK;
	}

	private static float[] points(Rail rail) {
		final double length = Math.max(0, rail.railMath.getLength());
		final int count = Math.max(2, Math.min(MAX_POINTS, (int) Math.ceil(length / STEP) + 1));
		final float[] points = new float[count * 2];
		for (int i = 0; i < count; i++) {
			final Vector point = rail.railMath.getPosition(length * i / (count - 1), false);
			points[i * 2] = (float) point.x;
			points[i * 2 + 1] = (float) point.z;
		}
		return points;
	}

	private static boolean near(float[] points, double x, double z) {
		for (int i = 0; i + 1 < points.length; i += 2) {
			final double dx = points[i] - x;
			final double dz = points[i + 1] - z;
			if (dx * dx + dz * dz < FORGET_RANGE * FORGET_RANGE) {
				return true;
			}
		}
		return false;
	}

	private static boolean inside(Area area, double x, double z) {
		return x >= area.minX() - FORGET_RANGE && x <= area.maxX() + FORGET_RANGE && z >= area.minZ() - FORGET_RANGE && z <= area.maxZ() + FORGET_RANGE;
	}
}
