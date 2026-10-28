package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.render.RailLod;

import static org.junit.jupiter.api.Assertions.*;

public final class RailLodTest {

	@Test
	public void tiersByDistance() {
		assertEquals(RailLod.Tier.FULL, RailLod.tier(40, 3, 8));
		assertEquals(RailLod.Tier.SIMPLE, RailLod.tier(49, 3, 8));
		assertEquals(RailLod.Tier.SIMPLE, RailLod.tier(128, 3, 8));
		assertEquals(RailLod.Tier.FAR, RailLod.tier(129, 3, 8));
	}

	@Test
	public void zeroTurnsATierOff() {
		assertEquals(RailLod.Tier.FULL, RailLod.tier(500, 0, 0));
		assertEquals(RailLod.Tier.FAR, RailLod.tier(500, 0, 8));
		assertEquals(RailLod.Tier.SIMPLE, RailLod.tier(500, 3, 0));
	}

	@Test
	public void cheapShadowPassIsAlwaysFar() {
		assertEquals(RailLod.Tier.FAR, RailLod.tier(1, 3, 8, true));
		assertEquals(RailLod.Tier.FAR, RailLod.tier(1, 0, 0, true));
		assertEquals(RailLod.Tier.FULL, RailLod.tier(1, 3, 8, false));
	}

	@Test
	public void tallModels() {
		assertFalse(RailLod.isTall(0.2));
		assertFalse(RailLod.isTall(0.5));
		assertTrue(RailLod.isTall(0.51));
		assertTrue(RailLod.isTall(6));
	}

	@Test
	public void farStepFromInterval() {
		// MTR's default 3D rail: a piece every 0.5 blocks.
		assertEquals(8, RailLod.farStep(0.5, false));
		assertEquals(4, RailLod.farStep(0.5, true));
		// A catenary style with masts every 30 blocks is never thinned.
		assertEquals(1, RailLod.farStep(30, true));
		assertEquals(1, RailLod.farStep(0, false));
		assertEquals(1, RailLod.farStep(Double.NaN, false));
	}

	@Test
	public void pieceIndexIsStableAndDrawsEveryStep() {
		assertEquals(0, RailLod.pieceIndex(0, 0.5));
		assertEquals(3, RailLod.pieceIndex(1.5, 0.5));
		// Rounding noise from the rail maths must not drop a piece to the previous index.
		assertEquals(3, RailLod.pieceIndex(1.4999999, 0.5));
		int drawn = 0;
		for (int i = 0; i < 64; i++) {
			if (RailLod.drawsFarPiece(i, 8)) {
				drawn++;
			}
		}
		assertEquals(8, drawn);
		assertTrue(RailLod.drawsFarPiece(5, 1));
	}
}
