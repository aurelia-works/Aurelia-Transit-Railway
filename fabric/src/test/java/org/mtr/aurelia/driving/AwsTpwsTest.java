package org.mtr.aurelia.driving;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AwsTpwsTest {

	@Test
	void awsBrakesAfterTheAcknowledgementWindow() {
		final AwsProtection aws = new AwsProtection();
		aws.trigger(1_000);
		aws.tick(3_999, AwsProtection.DEFAULT_ACK_MILLIS);
		assertTrue(aws.isWarning());
		assertFalse(aws.isBrakeApplied(), "three seconds is the window, not the deadline");

		aws.tick(4_000, AwsProtection.DEFAULT_ACK_MILLIS);
		assertTrue(aws.isBrakeApplied());
	}

	@Test
	void awsAcknowledgementCancelsBothWarningAndBrake() {
		final AwsProtection aws = new AwsProtection();
		aws.trigger(0);
		aws.tick(3_000, AwsProtection.DEFAULT_ACK_MILLIS);
		assertTrue(aws.isBrakeApplied());

		aws.acknowledge();
		assertFalse(aws.isWarning());
		assertFalse(aws.isBrakeApplied());
	}

	@Test
	void awsAcknowledgementInsideTheWindowAvoidsTheBrake() {
		final AwsProtection aws = new AwsProtection();
		aws.trigger(0);
		aws.tick(1_000, AwsProtection.DEFAULT_ACK_MILLIS);
		aws.acknowledge();
		aws.tick(10_000, AwsProtection.DEFAULT_ACK_MILLIS);
		assertFalse(aws.isBrakeApplied());
	}

	@Test
	void awsRetriggerKeepsTheRunningTimer() {
		final AwsProtection aws = new AwsProtection();
		aws.trigger(0);
		aws.trigger(2_000);
		aws.tick(3_000, AwsProtection.DEFAULT_ACK_MILLIS);
		assertTrue(aws.isBrakeApplied(), "the second horn must not restart the three seconds");
	}

	@Test
	void tpwsTripsOnOverspeedWithAMargin() {
		final TpwsProtection tpws = new TpwsProtection();
		assertEquals(TpwsProtection.Trip.NONE, tpws.update(true, 20.0, 20.0, false), "on the limit is not a trip");
		assertEquals(TpwsProtection.Trip.OVERSPEED, tpws.update(true, 24.0, 20.0, false));
		assertTrue(tpws.isBraking());
	}

	@Test
	void tpwsTripsOnASpadEvenWhenTheSpeedIsLow() {
		final TpwsProtection tpws = new TpwsProtection();
		assertEquals(TpwsProtection.Trip.SPAD, tpws.update(true, 5.0, 30.0, true));
	}

	@Test
	void tpwsBrakeLatchesUntilStoppedAndReset() {
		final TpwsProtection tpws = new TpwsProtection();
		tpws.update(true, 2.0, 30.0, true);

		assertFalse(tpws.reset(false), "moving trains cannot reset a TPWS trip");
		assertTrue(tpws.isBraking());
		assertEquals(TpwsProtection.Trip.SPAD, tpws.update(true, 0.0, 30.0, false), "a trip does not clear itself");

		assertTrue(tpws.reset(true));
		assertFalse(tpws.isBraking());
		assertEquals(TpwsProtection.Trip.NONE, tpws.update(true, 0.0, 30.0, false));
	}

	@Test
	void switchingTpwsOffReleasesTheBrake() {
		final TpwsProtection tpws = new TpwsProtection();
		tpws.update(true, 2.0, 30.0, true);
		assertEquals(TpwsProtection.Trip.NONE, tpws.update(false, 2.0, 30.0, true));
		assertFalse(tpws.isBraking());
	}
}
