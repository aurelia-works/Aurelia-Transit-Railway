package org.mtr.aurelia.passenger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.client.PassengerDoors;

/** The drawing only lets people through an open door, and gives up on a count whose doors never open. */
public final class PassengerDoorsTest {

	@Test
	public void nobodyCrossesAClosedDoor() {
		Assertions.assertFalse(PassengerDoors.canCross(0));
		Assertions.assertFalse(PassengerDoors.canCross(PassengerDoors.OPEN_ENOUGH - 0.01));
		Assertions.assertTrue(PassengerDoors.canCross(PassengerDoors.OPEN_ENOUGH));
		Assertions.assertTrue(PassengerDoors.canCross(1));

		Assertions.assertFalse(PassengerDoors.tooLate(0, PassengerDoors.WAIT_MILLIS));
		Assertions.assertTrue(PassengerDoors.tooLate(0, PassengerDoors.WAIT_MILLIS + 1));
	}

	@Test
	public void theCrowdStepsAsideForTheWholeExchange() {
		Assertions.assertEquals(0, PassengerDoors.stepAsideStrength(0), 0.001);
		Assertions.assertEquals(0, PassengerDoors.stepAsideStrength(1), 0.001);
		Assertions.assertEquals(1, PassengerDoors.stepAsideStrength(0.5), 0.001);
		Assertions.assertTrue(PassengerDoors.stepAsideStrength(0.1) > 0);
		Assertions.assertTrue(PassengerDoors.stepAsideStrength(0.9) > 0);
	}
}
