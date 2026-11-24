package org.mtr.aurelia.client;

import org.mtr.aurelia.config.AureliaSettings;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This client's passenger drawing switches, the {@code passenger_draw} section of {@code config/aurelia/settings.json}
 * (formerly {@code passenger-draw.json}).
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
	public static final int MAX_WALKERS_MAX = 128;
	public static final int WALK_RANGE_MIN = 4;
	public static final int WALK_RANGE_MAX = 32;
	public static final int MAX_PATH_MIN = 500;
	public static final int MAX_PATH_MAX = 12000;

	/** On (the server decides whether there is anyone to draw), full figures inside 32 blocks, one simple figure out to 64, at most 16 in one place, life size, drawn as people. Boxes, if chosen, are solid, blue waiting and orange riding. */
	public static final Values DEFAULT = new Values(true, 64, 32, 16, true, 100, 210, 30, true, 64, true, 16, 6000);

	private static Values values = DEFAULT;

	private PassengerDrawSettings() {
	}

	public static void load(Path configDir) {
		AureliaSettings.load(configDir);
		try {
			values = from(AureliaSettings.section(AureliaSettings.PASSENGER_DRAW));
		} catch (Exception e) {
			AureliaClient.LOGGER.error("[ATR] the passenger_draw section of settings.json could not be read; people drawing keeps its defaults", e);
			values = DEFAULT;
		}
	}

	/** Reads the old {@code passenger-draw.json} into the section body settings.json keeps. */
	public static JsonObject readLegacy(Path file) throws IOException {
		return from(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()).toJson();
	}

	public static Values get() {
		return values;
	}

	public static void setDraw(boolean draw) {
		values = values.withDraw(draw);
		save();
	}

	public static void setDrawDistance(int drawDistance) {
		values = new Values(values.draw(), clamp(drawDistance, 0, DRAW_DISTANCE_MAX), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people(), values.maxWalkers(), values.routes(), values.walkRange(), values.maxPath()).detailInsideDraw();
		save();
	}

	public static void setDetailDistance(int detailDistance) {
		values = new Values(values.draw(), values.drawDistance(), clamp(detailDistance, 0, Math.min(DETAIL_DISTANCE_MAX, values.drawDistance())), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people(), values.maxWalkers(), values.routes(), values.walkRange(), values.maxPath());
		save();
	}

	public static void setMaxPerPlace(int maxPerPlace) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), clamp(maxPerPlace, 0, MAX_PER_PLACE_MAX), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people(), values.maxWalkers(), values.routes(), values.walkRange(), values.maxPath());
		save();
	}

	/** People, then solid boxes, then outlines, then people again. */
	public static void cycleLook() {
		final boolean people = !values.people() && !values.solid();
		final boolean solid = values.people();
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), solid, values.figureSize(), values.waitingHue(), values.ridingHue(), people, values.maxWalkers(), values.routes(), values.walkRange(), values.maxPath());
		save();
	}

	public static void setMaxWalkers(int maxWalkers) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people(), clamp(maxWalkers, 0, MAX_WALKERS_MAX), values.routes(), values.walkRange(), values.maxPath());
		save();
	}

	public static void setRoutes(boolean routes) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people(), values.maxWalkers(), routes, values.walkRange(), values.maxPath());
		save();
	}

	public static void setWalkRange(int walkRange) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people(), values.maxWalkers(), values.routes(), clamp(walkRange, WALK_RANGE_MIN, WALK_RANGE_MAX), values.maxPath());
		save();
	}

	public static void setMaxPath(int maxPath) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), values.ridingHue(), values.people(), values.maxWalkers(), values.routes(), values.walkRange(), clamp(maxPath, MAX_PATH_MIN, MAX_PATH_MAX));
		save();
	}

	public static void setFigureSize(int figureSize) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), clamp(figureSize, FIGURE_SIZE_MIN, FIGURE_SIZE_MAX), values.waitingHue(), values.ridingHue(), values.people(), values.maxWalkers(), values.routes(), values.walkRange(), values.maxPath());
		save();
	}

	public static void setWaitingHue(int waitingHue) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), clamp(waitingHue, 0, HUE_MAX), values.ridingHue(), values.people(), values.maxWalkers(), values.routes(), values.walkRange(), values.maxPath());
		save();
	}

	public static void setRidingHue(int ridingHue) {
		values = new Values(values.draw(), values.drawDistance(), values.detailDistance(), values.maxPerPlace(), values.solid(), values.figureSize(), values.waitingHue(), clamp(ridingHue, 0, HUE_MAX), values.people(), values.maxWalkers(), values.routes(), values.walkRange(), values.maxPath());
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
				json.has("people") ? json.get("people").getAsBoolean() : DEFAULT.people(),
				json.has("maxWalkers") ? clamp(json.get("maxWalkers").getAsInt(), 0, MAX_WALKERS_MAX) : DEFAULT.maxWalkers(),
				json.has("routes") ? json.get("routes").getAsBoolean() : DEFAULT.routes(),
				json.has("walkRange") ? clamp(json.get("walkRange").getAsInt(), WALK_RANGE_MIN, WALK_RANGE_MAX) : DEFAULT.walkRange(),
				json.has("maxPath") ? clamp(json.get("maxPath").getAsInt(), MAX_PATH_MIN, MAX_PATH_MAX) : DEFAULT.maxPath()
		).detailInsideDraw();
	}

	private static void save() {
		AureliaSettings.put(AureliaSettings.PASSENGER_DRAW, values.toJson());
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	public record Values(boolean draw, int drawDistance, int detailDistance, int maxPerPlace, boolean solid, int figureSize, int waitingHue, int ridingHue, boolean people, int maxWalkers, boolean routes, int walkRange, int maxPath) {
		public Values withDraw(boolean draw) {
			return new Values(draw, drawDistance, detailDistance, maxPerPlace, solid, figureSize, waitingHue, ridingHue, people, maxWalkers, routes, walkRange, maxPath);
		}

		/** Detail never reaches farther than the draw distance. */
		public Values detailInsideDraw() {
			return detailDistance <= drawDistance ? this : new Values(draw, drawDistance, drawDistance, maxPerPlace, solid, figureSize, waitingHue, ridingHue, people, maxWalkers, routes, walkRange, maxPath);
		}

		public JsonObject toJson() {
			final JsonObject json = new JsonObject();
			json.addProperty("draw", draw);
			json.addProperty("drawDistance", drawDistance);
			json.addProperty("detailDistance", detailDistance);
			json.addProperty("maxPerPlatform", maxPerPlace);
			json.addProperty("solid", solid);
			json.addProperty("figureSize", figureSize);
			json.addProperty("waitingHue", waitingHue);
			json.addProperty("ridingHue", ridingHue);
			json.addProperty("people", people);
			json.addProperty("maxWalkers", maxWalkers);
			json.addProperty("routes", routes);
			json.addProperty("walkRange", walkRange);
			json.addProperty("maxPath", maxPath);
			return json;
		}
	}
}
