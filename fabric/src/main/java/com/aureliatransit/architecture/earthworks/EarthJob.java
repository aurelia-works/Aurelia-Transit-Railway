package com.aureliatransit.architecture.earthworks;

/**
 * One earthworks job: a tool, the two clicked blocks and the sizes. The constructor keeps width and height in range and makes
 * the width odd, so a line tool has a middle row. Pure logic, no Minecraft classes.
 *
 * @param width  across the line: wall thickness, tunnel or deck width (ignored by the area tools)
 * @param height the clear height inside a box tunnel (ignored by the other tools)
 */
public record EarthJob(EarthTool tool, Point a, Point b, int width, int height, EarthLook look) {

	/** A clicked block, in world coordinates. */
	public record Point(int x, int y, int z) {
	}

	/** The longest line or rectangle side, in blocks: a viaduct of 256 is long; a longer one is two jobs. */
	public static final int MAX_SPAN = 256;
	/** The largest rectangle side for Level and Slope: a 96 x 96 site cuts and fills up to about 900,000 cells. */
	public static final int MAX_AREA_SIDE = 96;
	public static final int MAX_WIDTH = 15;
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_HEIGHT = 12;
	/** The most the two points may differ in height. */
	public static final int MAX_RISE = 64;

	public EarthJob {
		if (tool == null) {
			tool = EarthTool.LEVEL;
		}
		if (look == null) {
			look = EarthLook.STONE;
		}
		final int smallest = tool == EarthTool.WALL ? 1 : tool == EarthTool.BORE ? 5 : 3;
		width = Math.max(smallest, Math.min(MAX_WIDTH, width <= 0 ? tool.defaultWidth() : width));
		if (width % 2 == 0) {
			width++;
		}
		height = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, height <= 0 ? Math.max(MIN_HEIGHT, tool.defaultHeight()) : height));
	}

	/** Why the job cannot be built, in plain words, or null when it can. */
	public String problem() {
		final int dx = Math.abs(b.x() - a.x());
		final int dz = Math.abs(b.z() - a.z());
		final int rise = Math.abs(b.y() - a.y());
		if (rise > MAX_RISE) {
			return "A and B differ by " + rise + " blocks in height; at most " + MAX_RISE + ".";
		}
		if (tool.line()) {
			final double length = Math.sqrt((double) dx * dx + (double) dz * dz);
			if (length > MAX_SPAN) {
				return "A and B are " + Math.round(length) + " blocks apart; at most " + MAX_SPAN + ". Build it in parts.";
			}
			if (length < 2 && tool != EarthTool.WALL) {
				return "A and B are the same spot; click the two ends of the line.";
			}
		} else if (dx + 1 > MAX_AREA_SIDE || dz + 1 > MAX_AREA_SIDE) {
			return "The area is " + (dx + 1) + " x " + (dz + 1) + "; at most " + MAX_AREA_SIDE + " x " + MAX_AREA_SIDE + ". Do it in parts.";
		}
		return null;
	}

	/** The horizontal distance from A to B. */
	public double length() {
		final double dx = b.x() - a.x();
		final double dz = b.z() - a.z();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** The grade as "1 in n" (n = length / rise), or 0 when level. */
	public int gradeOneIn() {
		final int rise = Math.abs(b.y() - a.y());
		return rise == 0 ? 0 : (int) Math.round(length() / rise);
	}

	/** The height at fraction {@code t} (0 at A, 1 at B) of the way, rounded to a block. */
	int heightAt(double t) {
		return (int) Math.round(a.y() + (b.y() - a.y()) * t);
	}
}
