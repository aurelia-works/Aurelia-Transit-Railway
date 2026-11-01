package org.mtr.aurelia.client;

import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This client's passenger drawing switches, in {@code config/aurelia/passenger-draw.json}.
 * Missing file and missing keys keep the defaults, so an old config folder loads unchanged.
 * Defaults stay cheap: drawing is off, and the distances are short, for low-end PCs and shader packs.
 */
public final class PassengerDrawSettings {

	public static final int DRAW_DISTANCE_MAX = 128;
	public static final int DETAIL_DISTANCE_MAX = 64;
	public static final int MAX_PER_PLACE_MAX = 40;

	public static final int FIGURE_SIZE_MIN = 50;
	public static final int FIGURE_SIZE_MAX = 200;
	public static final int HUE_MAX = 360;

	/** Off, figures out to 64 blocks, a single figure past 32, at most 16 in one place, life size, drawn as people. Boxes, if chosen, are solid, blue waiting and orange riding. */
	public static final Values DEFAULT = new Values(false, 64, 32, 16, true, 100, 210, 30, true);

	private static Values values = DEFAULT;

	private PassengerDrawSettings() {
	}

	public static void load(Path configDir) {
		final Path file = file(configDir);
		if (!Files.isRegularFile(file)) {
			values = DEFAULT;
			return;
		}
		try {
			values = from(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] passenger-draw.json could not be read; drawing stays off", e);
			values = DEFAULT;
		}
	}

	public static Values get() {
		return values;
	}

	public static void setDraw(boolean draw) {
		values = values.withDraw(draw);
		save();
	}

	public static void setDrawDistance(int drawDistance) {
		values = new Values(values.draw(), clamp(drawDistance, 0, DRAW_DISTANCE_MAX), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people()).detailInsideDraw();
		save();
	}

	public static void setDetailDistance(int detailDistance) {
		values = new Values(values.draw(), values.drawDistance(), clamp(detailDistance, 0, Math.min(DETAIL_DISTANCE_MAX, values.drawDistance())), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people());
		save();
	}

	public static void setMaxPerPlace(int maxPerPlace) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), clamp(maxPerPlace, 0, MAX_PER_PLACE_MAX), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people());
		save();
	}

	/** People, then solid boxes, then outlines, then people again. */
	public static void cycleLook() {
		final boolean people = !values.people() && !values.solid();
		final boolean solid = values.people();
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), solid, values.figureSize(), values.waitingHue(), values.ridingHue(), people);
		save();
	}

	public static void setFigureSize(int figureSize) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), clamp(figureSize, FIGURE_SIZE_MIN, FIGURE_SIZE_MAX), values.waitingHue(), values.ridingHue(), values.people());
		save();
	}

	public static void setWaitingHue(int waitingHue) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), clamp(waitingHue, 0, HUE_MAX), values.ridingHue(), values.people());
		save();
	}

	public static void setRidingHue(int ridingHue) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), clamp(ridingHue, 0, HUE_MAX), values.people());
		save();
	}

	/** Missing keys stay at the defaults. Extra keys are ignored. */
	public static Values from(JsonObject json) {
		return new Values(
				json.has("draw") ? json.get("draw").getAsBoolean() : DEFAULT.draw(),
				json.has("drawDistance") ? clamp(json.get("drawDistance").getAsInt(), 0, DRAW_DISTANCE_MAX) : DEFAULT.drawDistance(),
				json.has("detailDistance") ? clamp(json.get("detailDistance").getAsInt(), 0, DETAIL_DISTANCE_MAX) : DEFAULT.detailDistance(),
				json.has("maxPerPlatform") ? clamp(json.get("maxPerPlatform").getAsInt(), 0, MAX_PER_PLACE_MAX) : DEFAULT.maxPerPlace(),
				json.has("solid") ? json.get("solid").getAsBoolean() : DEFAULT.solid(),
				json.has("figureSize") ? clamp(json.get("figureSize").getAsInt(), FIGURE_SIZE_MIN, FIGURE_SIZE_MAX) : DEFAULT.figureSize(),
				json.has("waitingHue") ? clamp(json.get("waitingHue").getAsInt(), 0, HUE_MAX) : DEFAULT.waitingHue(),
				json.has("ridingHue") ? clamp(json.get("ridingHue").getAsInt(), 0, HUE_MAX) : DEFAULT.ridingHue(),
				json.has("people") ? json.get("people").getAsBoolean() : DEFAULT.people()
		).detailInsideDraw();
	}

	private static void save() {
		try {
			final Path file = file(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
			Files.createDirectories(file.getParent());
			final JsonObject json = new JsonObject();
			json.addProperty("draw", values.draw());
			json.addProperty("drawDistance", values.drawDistance());
			json.addProperty("detailDistance", values.detailDistance());
			json.addProperty("maxPerPlatform", values.maxPerPlace());
			json.addProperty("solid", values.solid());
			json.addProperty("figureSize", values.figureSize());
			json.addProperty("waitingHue", values.waitingHue());
			json.addProperty("ridingHue", values.ridingHue());
			json.addProperty("people", values.people());
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] passenger-draw.json could not be written", e);
		}
	}

	private static Path file(Path configDir) {
		return configDir.resolve("aurelia").resolve("passenger-draw.json");
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public record Values(boolean draw, int drawDistance, int detailDistance, int maxPerPlace, boolean solid, int figureSize, int waitingHue, int ridingHue, boolean people) {
		public Values withDraw(boolean draw) {
			return new Values(draw, drawDistance, detailDistance, maxPerPlace, solid, figureSize, waitingHue, ridingHue, people);
		}

		/** Detail never reaches farther than the draw distance. */
		public Values detailInsideDraw() {
			return detailDistance <= drawDistance ? this : new Values(draw, drawDistance, drawDistance, maxPerPlace, solid, figureSize, waitingHue, ridingHue, people);
		}
	}
}
