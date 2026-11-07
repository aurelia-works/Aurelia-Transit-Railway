package com.aureliatransit.architecture.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A captured bridge section in local block coordinates, independent of the world it came from. Local x points to the
 * right of the section's facing, y is up, and local z points forward; a section captured facing north therefore has
 * local +x = world +x and local +z = world -z. The anchor (0, 0, 0) is the rail block the section was captured around.
 *
 * <p>The format is a small JSON file rather than a structure template: a rail-stamped section must rotate with the rail
 * and must never write over MTR rail nodes, and a JSON palette of block-state strings is easy to inspect and diff.
 * Block entities are deliberately not captured; a bridge section is made of blocks, not inventories or sign text.
 */
public final class BridgeTemplate {

	public static final int VERSION = 1;
	/** A captured section is small; this keeps a malformed file from turning into a huge build. */
	public static final int MAX_CELLS = 65536;
	public static final int MAX_EXTENT = 128;

	public record Cell(int x, int y, int z, int palette) {
	}

	public record Placed(int x, int y, int z, int palette) {
	}

	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<String> palette;
	private final List<Cell> cells;

	private BridgeTemplate(int sizeX, int sizeY, int sizeZ, List<String> palette, List<Cell> cells) {
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = List.copyOf(palette);
		this.cells = List.copyOf(cells);
	}

	/**
	 * @param sizeX/sizeY/sizeZ the captured box, for display only
	 * @param palette          one block-state string per palette entry
	 * @param cells            block positions relative to the anchor, and their palette index
	 */
	public static BridgeTemplate of(int sizeX, int sizeY, int sizeZ, List<String> palette, List<Cell> cells) {
		if (sizeX < 0 || sizeY < 0 || sizeZ < 0 || sizeX > MAX_EXTENT || sizeY > MAX_EXTENT || sizeZ > MAX_EXTENT) {
			throw new IllegalArgumentException("Template size must be between 0 and " + MAX_EXTENT + " blocks on each axis.");
		}
		if (palette.isEmpty() && !cells.isEmpty()) {
			throw new IllegalArgumentException("A non-empty template needs a palette.");
		}
		if (cells.size() > MAX_CELLS) {
			throw new IllegalArgumentException("Template has " + cells.size() + " blocks; the limit is " + MAX_CELLS + ".");
		}
		for (final String state : palette) {
			if (state == null || state.isBlank()) {
				throw new IllegalArgumentException("Palette entries must not be blank.");
			}
		}
		for (final Cell cell : cells) {
			Objects.requireNonNull(cell, "cell");
			if (cell.palette() < 0 || cell.palette() >= palette.size()) {
				throw new IllegalArgumentException("Cell " + cell + " has no palette entry.");
			}
			if (Math.abs(cell.x()) > MAX_EXTENT || Math.abs(cell.y()) > MAX_EXTENT || Math.abs(cell.z()) > MAX_EXTENT) {
				throw new IllegalArgumentException("Cell " + cell + " is outside the supported " + MAX_EXTENT + "-block range.");
			}
		}
		return new BridgeTemplate(sizeX, sizeY, sizeZ, palette, cells);
	}

	public int sizeX() {
		return sizeX;
	}

	public int sizeY() {
		return sizeY;
	}

	public int sizeZ() {
		return sizeZ;
	}

	public List<String> palette() {
		return palette;
	}

	public List<Cell> cells() {
		return cells;
	}

	/**
	 * Places the section at {@code anchor} and rotates it clockwise by {@code quarterTurns}. {@code height} moves every
	 * block up or down from the anchor's block level.
	 */
	public List<Placed> stamp(int anchorX, int anchorY, int anchorZ, int quarterTurns, int height) {
		final List<Placed> placed = new ArrayList<>(cells.size());
		for (final Cell cell : cells) {
			final int[] offset = rotate(cell.x(), cell.z(), quarterTurns);
			placed.add(new Placed(anchorX + offset[0], anchorY + height + cell.y(), anchorZ + offset[1], cell.palette()));
		}
		return placed;
	}

	/**
	 * Local (x, z) to world (x, z) for a section facing north, rotated clockwise by {@code quarterTurns}.
	 */
	public static int[] rotate(int x, int z, int quarterTurns) {
		return switch (quarterTurns & 3) {
			case 1 -> new int[]{z, x};
			case 2 -> new int[]{-x, z};
			case 3 -> new int[]{-z, -x};
			default -> new int[]{x, -z};
		};
	}

	/**
	 * World (x, z) to local (x, z); the inverse of {@link #rotate}.
	 */
	public static int[] toLocal(int worldX, int worldZ, int quarterTurns) {
		return switch (quarterTurns & 3) {
			case 1 -> new int[]{worldZ, worldX};
			case 2 -> new int[]{-worldX, worldZ};
			case 3 -> new int[]{-worldZ, -worldX};
			default -> new int[]{worldX, -worldZ};
		};
	}

	public JsonObject toJson() {
		final JsonObject json = new JsonObject();
		json.addProperty("version", VERSION);
		final JsonArray size = new JsonArray();
		size.add(sizeX);
		size.add(sizeY);
		size.add(sizeZ);
		json.add("size", size);
		final JsonArray paletteJson = new JsonArray();
		palette.forEach(paletteJson::add);
		json.add("palette", paletteJson);
		final JsonArray cellsJson = new JsonArray();
		for (final Cell cell : cells) {
			final JsonArray cellJson = new JsonArray();
			cellJson.add(cell.x());
			cellJson.add(cell.y());
			cellJson.add(cell.z());
			cellJson.add(cell.palette());
			cellsJson.add(cellJson);
		}
		json.add("cells", cellsJson);
		return json;
	}

	public static BridgeTemplate fromJson(String text) {
		return fromJson(JsonParser.parseString(text).getAsJsonObject());
	}

	public static BridgeTemplate fromJson(JsonObject json) {
		final int version = integer(json, "version", 0);
		if (version != VERSION) {
			throw new IllegalArgumentException("Unsupported bridge template version " + version + "; this build reads version " + VERSION + ".");
		}
		final JsonArray size = array(json, "size");
		if (size.size() != 3) {
			throw new IllegalArgumentException("A bridge template needs a three-number size.");
		}
		final int sizeX = number(size, 0, "size");
		final int sizeY = number(size, 1, "size");
		final int sizeZ = number(size, 2, "size");
		final JsonArray paletteJson = array(json, "palette");
		final List<String> palette = new ArrayList<>(paletteJson.size());
		for (final JsonElement entry : paletteJson) {
			if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
				throw new IllegalArgumentException("Bridge template palette entries must be strings.");
			}
			palette.add(entry.getAsString());
		}
		final JsonArray cellsJson = array(json, "cells");
		final List<Cell> cells = new ArrayList<>(cellsJson.size());
		for (final JsonElement entry : cellsJson) {
			if (!entry.isJsonArray()) {
				throw new IllegalArgumentException("Bridge template cells must be arrays.");
			}
			final JsonArray cellJson = entry.getAsJsonArray();
			if (cellJson.size() != 4) {
				throw new IllegalArgumentException("A bridge template cell needs x, y, z and a palette index.");
			}
			cells.add(new Cell(number(cellJson, 0, "cell"), number(cellJson, 1, "cell"), number(cellJson, 2, "cell"), number(cellJson, 3, "cell")));
		}
		return of(sizeX, sizeY, sizeZ, palette, cells);
	}

	private static int number(JsonArray array, int index, String name) {
		final JsonElement element = array.get(index);
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("Every bridge template " + name + " value must be a number.");
		}
		return element.getAsInt();
	}

	private static int integer(JsonObject json, String name, int fallback) {
		final JsonElement element = json.get(name);
		return element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber() ? fallback : element.getAsInt();
	}

	private static JsonArray array(JsonObject json, String name) {
		final JsonElement element = json.get(name);
		if (element == null || !element.isJsonArray()) {
			throw new IllegalArgumentException("Bridge template is missing its " + name + " array.");
		}
		return element.getAsJsonArray();
	}
}
