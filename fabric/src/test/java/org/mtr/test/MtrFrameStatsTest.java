package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.render.MtrFrameStats;

import static org.junit.jupiter.api.Assertions.*;

class MtrFrameStatsTest {

	private static final long MS = 1_000_000L;

	@Test
	void noResultBeforeOneSecond() {
		final MtrFrameStats stats = new MtrFrameStats();
		stats.frame(0);
		stats.draw();
		stats.frame(500 * MS);
		assertFalse(stats.hasResult());
	}

	@Test
	void averagesPerFrameOverOneSecond() {
		final MtrFrameStats stats = new MtrFrameStats();
		stats.frame(0);
		for (int frame = 1; frame <= 4; frame++) {
			for (int i = 0; i < 100; i++) {
				stats.draw();
			}
			stats.mtrRender(2 * MS);
			stats.frame(frame * 250 * MS);
		}
		assertTrue(stats.hasResult());
		assertEquals(100, stats.drawsPerFrame(), 1e-9);
		assertEquals(2, stats.mtrMillisPerFrame(), 1e-9);
	}

	@Test
	void drawsBeforeTheFirstFrameAreDropped() {
		final MtrFrameStats stats = new MtrFrameStats();
		for (int i = 0; i < 1000; i++) {
			stats.draw();
		}
		stats.frame(0);
		stats.draw();
		stats.frame(1000 * MS);
		assertEquals(1, stats.drawsPerFrame(), 1e-9);
	}
}
