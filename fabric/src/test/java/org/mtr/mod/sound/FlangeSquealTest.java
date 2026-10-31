package org.mtr.mod.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlangeSquealTest {

	@Test
	void straightAndSlowStaySilent() {
		assertEquals(0f, FlangeSqueal.gain(0, 80));
		assertEquals(0f, FlangeSqueal.gain(1, 2));
		assertEquals(0.0, FlangeSqueal.curvature(0, 0.01, 0.01));
		assertEquals(0.0, FlangeSqueal.curvature(Double.NaN, 1, 1));
	}

	@Test
	void tightFastCurveReachesFullGainAndPicksAHigherSlot() {
		final double tight = FlangeSqueal.CURVE_FULL;
		assertEquals(1f, FlangeSqueal.gain(tight, FlangeSqueal.SPEED_FULL_KMH), 0.001f);
		assertTrue(FlangeSqueal.gain(tight, 20) > 0f && FlangeSqueal.gain(tight, 20) < 1f);
		assertEquals(0, FlangeSqueal.slot(80, 1));
		assertEquals(2, FlangeSqueal.slot(80, 4));
		assertTrue(FlangeSqueal.pitch(0) < FlangeSqueal.pitch(100));
	}

	@Test
	void headingWrapDoesNotLookLikeAFullTurn() {
		final double almost = Math.PI - 0.05;
		final double curve = FlangeSqueal.curvature(almost, -almost, 1);
		assertTrue(curve < 0.2, "a small step across the branch cut is not a U-turn");
	}
}
