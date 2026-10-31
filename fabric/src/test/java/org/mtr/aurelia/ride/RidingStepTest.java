package org.mtr.aurelia.ride;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectBooleanImmutablePair;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.client.RidingStep;

/**
 * MTR #1069 / #943: at low FPS one step is long enough to cross a doorway, and the player was let go where they stood
 * inside the car, with no floor under them.
 */
public final class RidingStepTest {

	/** A car floor 2 wide and 10 long at height 1, and a doorway on its +X side. */
	private static ObjectArrayList<ObjectBooleanImmutablePair<Box>> car() {
		final ObjectArrayList<ObjectBooleanImmutablePair<Box>> boxes = new ObjectArrayList<>();
		boxes.add(new ObjectBooleanImmutablePair<>(new Box(-1, 1, -5, 1, 1, 5), true));
		boxes.add(new ObjectBooleanImmutablePair<>(new Box(1, 1, -1, 1.8, 1, 1), false));
		return boxes;
	}

	@Test
	public void longStepOutOfADoorwayLeavesThePlayerOutside() {
		// About 10 FPS while sprinting: 1.3 blocks in one frame, from inside the car to past the doorway
		final RidingStep.Result result = RidingStep.step(car(), 0.9, 1, 0, 1.3, 0);
		Assertions.assertTrue(result.dismounted());
		Assertions.assertTrue(result.x() > 1.8, "let go inside the car at x " + result.x());
	}

	@Test
	public void stepIntoAWallStaysOnTheFloor() {
		final RidingStep.Result result = RidingStep.step(car(), 0.5, 1, 3, -1.3, 0);
		Assertions.assertFalse(result.dismounted());
		Assertions.assertEquals(-0.7, result.x(), 1E-6);
	}
}
