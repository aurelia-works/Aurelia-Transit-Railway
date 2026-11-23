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
}
