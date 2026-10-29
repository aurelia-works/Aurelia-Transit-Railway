package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.resource.PantographEnd;
import org.mtr.mod.resource.PantographMotion;

public final class PantographMotionTest {

	private static final double EPSILON = 1E-9;

	@Test
	public void easeStartsAtZeroEndsAtOneAndIsSlowAtBothEnds() {
		Assertions.assertEquals(0, PantographMotion.ease(0), EPSILON);
		Assertions.assertEquals(1, PantographMotion.ease(1), EPSILON);
		Assertions.assertEquals(0.5, PantographMotion.ease(0.5), EPSILON);
		Assertions.assertTrue(PantographMotion.ease(0.1) < 0.1, "slower than linear at the start");
		Assertions.assertTrue(PantographMotion.ease(0.9) > 0.9, "slower than linear at the end");
	}

	@Test
	public void easeClampsOutsideZeroToOne() {
		Assertions.assertEquals(0, PantographMotion.ease(-3), EPSILON);
		Assertions.assertEquals(1, PantographMotion.ease(7), EPSILON);
	}

	@Test
	public void advanceTakesTheRaiseTimeToGoAllTheWay() {
		double progress = 0;
		for (int i = 0; i < 10; i++) {
			progress = PantographMotion.advance(progress, true, 0.2, 2);
		}
		Assertions.assertEquals(1, progress, EPSILON, "ten steps of 0.2 s fill a 2 s raise");
		Assertions.assertEquals(0.5, PantographMotion.advance(0, true, 1, 2), EPSILON);
	}

	@Test
	public void advanceLowersAndStopsAtTheEnds() {
		Assertions.assertEquals(0.25, PantographMotion.advance(0.75, false, 1, 2), EPSILON);
		Assertions.assertEquals(0, PantographMotion.advance(0.1, false, 5, 2), EPSILON);
		Assertions.assertEquals(1, PantographMotion.advance(0.9, true, 5, 2), EPSILON);
	}

	@Test
	public void advanceJumpsWhenRaiseTimeIsZeroAndIgnoresNegativeTime() {
		Assertions.assertEquals(1, PantographMotion.advance(0, true, 0.01, 0), EPSILON);
		Assertions.assertEquals(0, PantographMotion.advance(1, false, 0.01, -1), EPSILON);
		Assertions.assertEquals(0.4, PantographMotion.advance(0.4, true, -1, 2), EPSILON, "time never runs backwards");
	}

	@Test
	public void endChoiceFollowsTheLeadingEnd() {
		Assertions.assertTrue(PantographMotion.endActive(PantographEnd.FRONT, PantographEnd.BOTH, false));
		Assertions.assertFalse(PantographMotion.endActive(PantographEnd.FRONT, PantographEnd.BOTH, true));
		Assertions.assertFalse(PantographMotion.endActive(PantographEnd.REAR, PantographEnd.BOTH, false));
		Assertions.assertTrue(PantographMotion.endActive(PantographEnd.REAR, PantographEnd.BOTH, true));
		Assertions.assertTrue(PantographMotion.endActive(PantographEnd.BOTH, PantographEnd.FRONT, false));
		Assertions.assertTrue(PantographMotion.endActive(PantographEnd.BOTH, PantographEnd.FRONT, true));
	}

	@Test
	public void defaultEndUsesThePanelChoice() {
		Assertions.assertTrue(PantographMotion.endActive(PantographEnd.DEFAULT, PantographEnd.REAR, true));
		Assertions.assertFalse(PantographMotion.endActive(PantographEnd.DEFAULT, PantographEnd.REAR, false));
		Assertions.assertTrue(PantographMotion.endActive(PantographEnd.DEFAULT, PantographEnd.DEFAULT, false), "a panel default of DEFAULT means both");
	}

	@Test
	public void upOnRouteOrWhenFastEnough() {
		Assertions.assertTrue(PantographMotion.shouldBeUp(true, 0, 20));
		Assertions.assertFalse(PantographMotion.shouldBeUp(false, 5, 20));
		Assertions.assertTrue(PantographMotion.shouldBeUp(false, 20, 20));
		Assertions.assertFalse(PantographMotion.shouldBeUp(false, 200, 0), "a threshold of 0 turns the speed rule off");
	}

	@Test
	public void armAngleAndHeadRiseScaleWithTheRaise() {
		Assertions.assertEquals(0, PantographMotion.armAngleDegrees(0, 50), EPSILON);
		Assertions.assertEquals(25, PantographMotion.armAngleDegrees(0.5, 50), EPSILON);
		Assertions.assertEquals(-50, PantographMotion.armAngleDegrees(1, -50), EPSILON);
		Assertions.assertEquals(0, PantographMotion.headRise(0, 12), EPSILON);
		Assertions.assertEquals(12, PantographMotion.headRise(1, 12), EPSILON);
	}

	@Test
	public void armRotatesAboutItsPivotAndLiftsItsTip() {
		// Arm lying along +z from a pivot at y=10, z=0; the tip is 20 away. 90 degrees stands it straight up (y decreases: y points down).
		final double[] tip = PantographMotion.rotateAboutX(10, 20, 10, 0, 90);
		Assertions.assertEquals(-10, tip[0], EPSILON);
		Assertions.assertEquals(0, tip[1], EPSILON);
		// The pivot itself never moves.
		final double[] pivot = PantographMotion.rotateAboutX(10, 0, 10, 0, 37);
		Assertions.assertEquals(10, pivot[0], EPSILON);
		Assertions.assertEquals(0, pivot[1], EPSILON);
		// Zero angle is the lowered pose, exactly as modelled.
		final double[] rest = PantographMotion.rotateAboutX(3, 4, 10, 6, 0);
		Assertions.assertEquals(3, rest[0], EPSILON);
		Assertions.assertEquals(4, rest[1], EPSILON);
	}
}
