package org.mtr.mod.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the rule that keeps trains from locking each other: a train in front of the express never gives way to it. */
class JunctionPriorityTest {

	@AfterEach
	void clear() {
		JunctionPriority.clear();
	}

	@Test
	void slowTrainGivesWayOnlyAtTheJunction() {
		final long express = 1;
		final long slow = 2;
		JunctionPriority.claim(express, Set.of("approach", "junction", "beyond"), 1_000);

		// coming in from the branch: waits for the express
		assertTrue(JunctionPriority.claimedByOther(slow, List.of("junction"), "branch", 1_500));
		// already standing on the express's track, so in front of it: never waits, or the two would lock
		assertFalse(JunctionPriority.claimedByOther(slow, List.of("junction"), "approach", 1_500));
		// a train never waits for its own claim
		assertFalse(JunctionPriority.claimedByOther(express, List.of("junction"), "elsewhere", 1_500));
		// a claim the express stopped renewing (it left or was removed) lets everyone through
		assertFalse(JunctionPriority.claimedByOther(slow, List.of("junction"), "branch", 3_000));
	}

	/** The engine asks again a second after a train starts braking for the signal; it must not let the train go then. */
	@Test
	void aTrainThatStartedGivingWayStaysHeldAtTheSignal() {
		final GiveWay giveWay = new GiveWay();
		// 22 m/s, 5 m outside its 30 m stopping distance: it can still stop, so it gives way
		assertTrue(giveWay.hold(7, 35, 30, true, 1_000));
		// a second later it has braked to within stopping distance of the signal, and the express is still coming
		assertTrue(giveWay.hold(7, 12, 11.5, true, 2_000));
		// standing at the signal
		assertTrue(giveWay.hold(7, 0, 0, true, 3_000));
		// the express is through: the signal clears
		assertFalse(giveWay.hold(7, 0, 0, false, 4_000));
		// a train that never gave way at this signal and can no longer stop is not stopped in front of it
		assertFalse(new GiveWay().hold(7, 12, 11.5, true, 2_000));
	}
}
