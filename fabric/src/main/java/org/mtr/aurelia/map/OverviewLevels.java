package org.mtr.aurelia.map;

import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Which overview level a map view draws from. Level 0 has one tile per region; each level up, a tile covers twice as many
 * regions a side, so four times fewer tiles show. A view uses the lowest level whose tiles in view fit {@link #BUDGET},
 * so the slots never run out however much of the world is explored. Pure logic.
 */
public final class OverviewLevels {

	/** Tiles one view may draw: half the overview's slots, so the tiles drawn last frame still have theirs. */
	public static final int BUDGET = 2048;
	/** A level-6 tile covers 64 by 64 regions; that fits a 4K screen at GUI scale 1, zoomed fully out (see {@link #maxTilesInView}). */
	public static final int MAX_LEVEL = 6;
	public static final int LEVELS = MAX_LEVEL + 1;

	private OverviewLevels() {
	}

	/**
	 * The lowest level at which the regions {@code keys[0..count)} fall in at most {@link #BUDGET} tiles. When it is above 0,
	 * {@code tiles} holds the keys of those tiles ({@link MapRegion#key} of the tile coordinates) on return.
	 */
	public static int level(long[] keys, int count, LongOpenHashSet tiles) {
		tiles.clear();
		if (count <= BUDGET) {
			return 0;
		}
		for (int level = 1; level < MAX_LEVEL; level++) {
			if (tilesAt(keys, count, level, tiles)) {
				return level;
			}
		}
		tilesAt(keys, count, MAX_LEVEL, tiles);
		return MAX_LEVEL;
	}

	/** The tile holding a region at a level. */
	public static int tileOf(int region, int level) {
		return region >> level;
	}

	/** The most tiles of a level a view of this size and zoom can show, whatever is explored. */
	public static int maxTilesInView(double viewWidth, double viewHeight, double scale, int level) {
		final double tileWidth = (double) (MapRegion.SIZE << level) * scale;
		return ((int) Math.ceil(viewWidth / tileWidth) + 1) * ((int) Math.ceil(viewHeight / tileWidth) + 1);
	}

	/** Fills {@code tiles} with the level's tile keys; false as soon as they pass the budget. */
	private static boolean tilesAt(long[] keys, int count, int level, LongOpenHashSet tiles) {
		tiles.clear();
		for (int i = 0; i < count; i++) {
			tiles.add(MapRegion.key(tileOf((int) (keys[i] >> 32), level), tileOf((int) keys[i], level)));
			if (tiles.size() > BUDGET) {
				return false;
			}
		}
		return true;
	}
}
