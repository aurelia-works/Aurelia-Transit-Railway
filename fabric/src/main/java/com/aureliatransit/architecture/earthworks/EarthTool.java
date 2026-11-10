package com.aureliatransit.architecture.earthworks;

import java.util.Locale;

/**
 * The earthworks the wrench builds between two clicked points A and B. The area tools work on the rectangle the two points
 * span; the line tools run from A to B with a width across. Heights come from the clicked blocks: A's y at A, B's y at B, in a
 * straight grade between. Pure logic, no Minecraft classes.
 */
public enum EarthTool {
	/** The rectangle cut and filled flat at A's height: everything above goes, hollows fill with earth. */
	LEVEL("level", false, 0, 0),
	/** The rectangle cut and filled to a straight slope from A's height to B's. */
	SLOPE("slope", false, 0, 0),
	/** A retaining wall from A to B; its top follows the clicked heights, its foot goes down to the lower ground beside it. */
	WALL("wall", true, 1, 0),
	/** A lined box tunnel from A to B (the clicked blocks are its floor); where it comes up to the surface it is an open cutting. */
	CUT_COVER("cut_cover", true, 5, 5),
	/** A round lined tunnel from A to B (the clicked blocks are its floor), built only where ground covers it all round. */
	BORE("bore", true, 7, 0),
	/** A deck from A to B at the clicked heights with parapets and piers down to the ground, clear space above for trains. */
	VIADUCT("viaduct", true, 5, 0);

	private final String id;
	private final boolean line;
	private final int width;
	private final int height;

	EarthTool(String id, boolean line, int width, int height) {
		this.id = id;
		this.line = line;
		this.width = width;
		this.height = height;
	}

	/** The word the command and the wrench use. */
	public String id() {
		return id;
	}

	/** True for the tools that run along a line from A to B; false for the rectangle tools. */
	public boolean line() {
		return line;
	}

	/** The width across the line a new job starts with: wall thickness, tunnel or deck width; 0 when the tool has none. */
	public int defaultWidth() {
		return width;
	}

	/** The clear height inside a box tunnel; 0 for the tools that have none. */
	public int defaultHeight() {
		return height;
	}

	public boolean usesWidth() {
		return width > 0;
	}

	public boolean usesHeight() {
		return height > 0;
	}

	/** The tool named {@code id}, or null. */
	public static EarthTool byId(String id) {
		final String wanted = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
		for (final EarthTool tool : values()) {
			if (tool.id.equals(wanted)) {
				return tool;
			}
		}
		return null;
	}

	/** The next tool in order, wrapping round, for the wrench's cycle button. */
	public EarthTool next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
