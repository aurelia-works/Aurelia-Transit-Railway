package org.mtr.aurelia.map;

import org.junit.jupiter.api.Test;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the map's over-cap bug: past 4096 explored regions in view the overview ran out of slots, so most of the map
 * never drew and the same regions were read again and again.
 */
public final class OverviewLevelsTest {

	@Test
	public void aViewNeverNeedsMoreTilesThanTheBudget() {
		// a 4K screen at GUI scale 1, zoomed fully out, every region explored
		assertTrue(OverviewLevels.maxTilesInView(3840, 2160, MapCamera.MIN_SCALE, OverviewLevels.MAX_LEVEL) <= OverviewLevels.BUDGET);
	}

	@Test
	public void pastTheBudgetTheViewMovesUpALevel() {
		final LongOpenHashSet tiles = new LongOpenHashSet();
		final long[] small = grid(40, 40);
		assertEquals(0, OverviewLevels.level(small, small.length, tiles));

		// 200 by 200 regions: 2500 tiles at level 2 is over, 625 at level 3 fits
		final long[] large = grid(200, 200);
		assertEquals(3, OverviewLevels.level(large, large.length, tiles));
		assertEquals(625, tiles.size());
	}

	private static long[] grid(int width, int height) {
		final long[] keys = new long[width * height];
		for (int z = 0; z < height; z++) {
			for (int x = 0; x < width; x++) {
				keys[z * width + x] = MapRegion.key(x, z);
			}
		}
		return keys;
	}
}
