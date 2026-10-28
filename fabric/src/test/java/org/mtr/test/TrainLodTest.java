package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.render.TrainLod;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mtr.mod.render.TrainLod.Tier.*;

public class TrainLodTest {

	@Test
	public void tiersByDistance() {
		assertEquals(FULL, TrainLod.tier(80, false, 5, 10));
		assertEquals(NO_INTERIOR, TrainLod.tier(81, false, 5, 10));
		assertEquals(NO_INTERIOR, TrainLod.tier(160, false, 5, 10));
		assertEquals(BOX, TrainLod.tier(161, false, 5, 10));
	}

	@Test
	public void ridingIsAlwaysFull() {
		assertEquals(FULL, TrainLod.tier(500, true, 5, 10));
	}

	@Test
	public void zeroTurnsATierOff() {
		assertEquals(NO_INTERIOR, TrainLod.tier(500, false, 5, 0));
		assertEquals(BOX, TrainLod.tier(500, false, 0, 10));
		assertEquals(FULL, TrainLod.tier(500, false, 0, 0));
	}

	@Test
	public void boxCloserThanInteriorSkipsTheMiddleTier() {
		assertEquals(BOX, TrainLod.tier(100, false, 8, 4));
		assertEquals(FULL, TrainLod.tier(60, false, 8, 4));
	}

	@Test
	public void interiorPartsAreLeftOutInOrder() {
		assertEquals(Arrays.asList("body", "doors", "roof"), TrainLod.withoutInterior(Arrays.asList("body", "seats", "doors", "lights", "roof"), part -> part.equals("seats") || part.equals("lights")));
		assertEquals(Collections.emptyList(), TrainLod.withoutInterior(Collections.singletonList("seats"), part -> true));
	}

	@Test
	public void skipInteriorIsOnlySetDuringItsQueue() {
		final boolean[] seen = {false};
		TrainLod.queue(true, () -> seen[0] = TrainLod.skipInterior());
		assertTrue(seen[0]);
		assertFalse(TrainLod.skipInterior());
		assertThrows(IllegalStateException.class, () -> TrainLod.queue(true, () -> {
			throw new IllegalStateException();
		}));
		assertFalse(TrainLod.skipInterior());
	}
}
