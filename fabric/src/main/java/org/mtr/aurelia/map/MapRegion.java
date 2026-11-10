package org.mtr.aurelia.map;

import java.util.Arrays;

/**
 * One square of the explored map, {@link #SIZE} blocks a side at one pixel per block: the colour of each column as last
 * seen from above, and its surface height for the shading of the column south of it. A pixel nobody has seen is 0
 * (fully transparent). Pure data; the client fills it and draws it.
 */
public final class MapRegion {

	public static final int SIZE = 256;
	public static final short UNKNOWN_HEIGHT = Short.MIN_VALUE;

	public final int regionX;
	public final int regionZ;
	private final int[] colors;
	private final short[] heights;
	/** Goes up on every change, so a texture knows when to upload again. */
	private int version;
	private boolean unsaved;

	public MapRegion(int regionX, int regionZ) {
		this(regionX, regionZ, new int[SIZE * SIZE], blankHeights());
	}

	public MapRegion(int regionX, int regionZ, int[] colors, short[] heights) {
		if (colors.length != SIZE * SIZE || heights.length != SIZE * SIZE) {
			throw new IllegalArgumentException("region arrays must hold " + SIZE * SIZE + " columns");
		}
		this.regionX = regionX;
		this.regionZ = regionZ;
		this.colors = colors;
		this.heights = heights;
	}

	public static int regionOf(int block) {
		return Math.floorDiv(block, SIZE);
	}

	public static int localOf(int block) {
		return Math.floorMod(block, SIZE);
	}

	public static long key(int regionX, int regionZ) {
		return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
	}

	public static int keyX(long key) {
		return (int) (key >> 32);
	}

	public static int keyZ(long key) {
		return (int) key;
	}

	public long key() {
		return key(regionX, regionZ);
	}

	public int color(int localX, int localZ) {
		return colors[localZ * SIZE + localX];
	}

	public short height(int localX, int localZ) {
		return heights[localZ * SIZE + localX];
	}

	/** Stores one column. Returns true when anything changed. */
	public boolean set(int localX, int localZ, int argb, int height) {
		final int index = localZ * SIZE + localX;
		final short clamped = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, height));
		if (colors[index] == argb && heights[index] == clamped) {
			return false;
		}
		colors[index] = argb;
		heights[index] = clamped;
		version++;
		unsaved = true;
		return true;
	}

	public int version() {
		return version;
	}

	public boolean unsaved() {
		return unsaved;
	}

	public void markSaved() {
		unsaved = false;
	}

	/** Copies for saving off the render thread. */
	public int[] copyColors() {
		return colors.clone();
	}

	public short[] copyHeights() {
		return heights.clone();
	}

	private static short[] blankHeights() {
		final short[] heights = new short[SIZE * SIZE];
		Arrays.fill(heights, UNKNOWN_HEIGHT);
		return heights;
	}
}
