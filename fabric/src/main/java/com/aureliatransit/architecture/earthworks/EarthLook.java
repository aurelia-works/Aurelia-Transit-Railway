package com.aureliatransit.architecture.earthworks;

import java.util.Locale;

/**
 * What the built parts of earthworks are made of: walls, piers and tunnel linings, deck and floor surfaces, and parapets.
 * Earth is always dirt under grass. Pure logic, no Minecraft classes.
 */
public enum EarthLook {
	STONE("stone", "minecraft:stone_bricks", "minecraft:smooth_stone", "minecraft:stone_brick_wall", "minecraft:polished_andesite"),
	CONCRETE("concrete", "minecraft:light_gray_concrete", "minecraft:gray_concrete", "minecraft:andesite_wall", "minecraft:light_gray_concrete"),
	BRICK("brick", "minecraft:bricks", "minecraft:smooth_stone", "minecraft:brick_wall", "minecraft:bricks");

	public static final String EARTH = "minecraft:dirt";
	public static final String TURF = "minecraft:grass_block";

	private final String id;
	private final String structure;
	private final String surface;
	private final String parapet;
	private final String lining;

	EarthLook(String id, String structure, String surface, String parapet, String lining) {
		this.id = id;
		this.structure = structure;
		this.surface = surface;
		this.parapet = parapet;
		this.lining = lining;
	}

	public String id() {
		return id;
	}

	/** Retaining walls, piers, girders and box tunnel walls and roofs. */
	public String structure() {
		return structure;
	}

	/** Viaduct decks and tunnel floors: what the track is laid on. */
	public String surface() {
		return surface;
	}

	/** The low wall along a viaduct's edges. */
	public String parapet() {
		return parapet;
	}

	/** The ring of a bored tunnel. */
	public String lining() {
		return lining;
	}

	/** The look named {@code id}, or STONE. */
	public static EarthLook byId(String id) {
		final String wanted = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
		for (final EarthLook look : values()) {
			if (look.id.equals(wanted)) {
				return look;
			}
		}
		return STONE;
	}

	public EarthLook next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
