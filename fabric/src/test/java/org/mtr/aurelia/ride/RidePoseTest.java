package org.mtr.aurelia.ride;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class RidePoseTest {

	@Test
	public void standsWhenNotRiding() {
		final RidePose pose = new RidePose(true);
		Assertions.assertFalse(pose.tick(false), "nobody sits outside a vehicle");
	}

	@Test
	public void boardingFollowsTheDefault() {
		Assertions.assertTrue(new RidePose(true).tick(true));
		Assertions.assertFalse(new RidePose(false).tick(true));
	}

	@Test
	public void theKeyTogglesWhileRiding() {
		final RidePose pose = new RidePose(false);
		pose.tick(true);
		pose.toggle();
		Assertions.assertTrue(pose.tick(true));
		pose.toggle();
		Assertions.assertFalse(pose.tick(true));
	}

	@Test
	public void theKeyDoesNothingOffAVehicle() {
		final RidePose pose = new RidePose(false);
		pose.toggle();
		Assertions.assertFalse(pose.tick(false));
		Assertions.assertFalse(pose.tick(true), "boarding later still follows the default");
	}

	@Test
	public void leavingResetsToTheDefault() {
		final RidePose pose = new RidePose(false);
		pose.tick(true);
		pose.toggle();
		pose.tick(false);
		Assertions.assertFalse(pose.tick(true), "the next ride starts from the default again");
	}

	@Test
	public void changesAreReportedOnce() {
		final RidePose pose = new RidePose(true);
		pose.tick(true);
		Assertions.assertTrue(pose.changed());
		Assertions.assertFalse(pose.changed());
		pose.tick(true);
		Assertions.assertFalse(pose.changed());
		pose.tick(false);
		Assertions.assertTrue(pose.changed());
	}

	@Test
	public void otherPlayersAreTrackedByUuid() {
		final RidePose.Others others = new RidePose.Others();
		final UUID a = UUID.randomUUID();
		final UUID b = UUID.randomUUID();
		others.set(a, true);
		Assertions.assertTrue(others.isSitting(a));
		Assertions.assertFalse(others.isSitting(b));
		others.set(a, false);
		Assertions.assertFalse(others.isSitting(a));
	}
}
