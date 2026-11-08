package org.mtr.aurelia.signal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalAspectTest {

	@Test
	void fourAspectFollowsTheRuleBook() {
		assertEquals(SignalAspect.RED, SignalAspect.forBlocks(4, true, 0));
		assertEquals(SignalAspect.YELLOW, SignalAspect.forBlocks(4, true, 1));
		assertEquals(SignalAspect.DOUBLE_YELLOW, SignalAspect.forBlocks(4, true, 2));
		assertEquals(SignalAspect.GREEN, SignalAspect.forBlocks(4, true, 3));
		assertEquals(SignalAspect.GREEN, SignalAspect.forBlocks(4, true, 9));
	}

	@Test
	void threeAndTwoAspectSkipTheAspectsTheyDoNotHave() {
		assertEquals(SignalAspect.RED, SignalAspect.forBlocks(3, true, 0));
		assertEquals(SignalAspect.YELLOW, SignalAspect.forBlocks(3, true, 1));
		assertEquals(SignalAspect.GREEN, SignalAspect.forBlocks(3, true, 2));
		assertEquals(SignalAspect.RED, SignalAspect.forBlocks(2, true, 0));
		assertEquals(SignalAspect.GREEN, SignalAspect.forBlocks(2, true, 1));
	}

	@Test
	void noRouteIsAlwaysRed() {
		assertEquals(SignalAspect.RED, SignalAspect.forBlocks(2, false, 5));
		assertEquals(SignalAspect.RED, SignalAspect.forBlocks(3, false, 5));
		assertEquals(SignalAspect.RED, SignalAspect.forBlocks(4, false, 5));
	}

	@Test
	void mtrLampIndexMatchesTheRenderers() {
		assertEquals(1, SignalAspect.RED.mtrAspect());
		assertEquals(2, SignalAspect.YELLOW.mtrAspect());
		assertEquals(3, SignalAspect.DOUBLE_YELLOW.mtrAspect());
		assertEquals(0, SignalAspect.GREEN.mtrAspect());
		assertTrue(SignalAspect.YELLOW.isRestrictive());
		assertTrue(SignalAspect.DOUBLE_YELLOW.isRestrictive());
		assertFalse(SignalAspect.GREEN.isRestrictive());
	}

	@Test
	void walkOnlyCountsBlocksItHasSeenTheEndOf() {
		// One block of two rails, then a second block whose first rail is occupied.
		final SignalWalk.Result occupiedNextBlock = SignalWalk.walk(List.of(
				new SignalWalk.Step(7, false, 30),
				new SignalWalk.Step(7, false, 30),
				new SignalWalk.Step(9, true, 30)
		), true);
		assertEquals(1, occupiedNextBlock.clearBlocks(), "the first block is complete, the occupied one is not");
		assertEquals(60, occupiedNextBlock.metresToRestriction());

		// Occupied inside the first block: red even though its first rail is clear.
		final SignalWalk.Result occupiedFirstBlock = SignalWalk.walk(List.of(
				new SignalWalk.Step(7, false, 30),
				new SignalWalk.Step(7, true, 30)
		), true);
		assertEquals(0, occupiedFirstBlock.clearBlocks());
	}

	@Test
	void walkCountsTheLastBlockAtARouteEnd() {
		final SignalWalk.Result oneBlock = SignalWalk.walk(List.of(new SignalWalk.Step(7, false, 45)), true);
		assertEquals(1, oneBlock.clearBlocks());
		assertEquals(SignalAspect.YELLOW, SignalWalk.aspect(4, true, oneBlock));

		final SignalWalk.Result cutShort = SignalWalk.walk(List.of(new SignalWalk.Step(7, false, 45)), false);
		assertEquals(0, cutShort.clearBlocks(), "a rail cap is not a block end");
	}

	@Test
	void unsignalledTrackStaysAtProceed() {
		final SignalWalk.Result noColours = SignalWalk.walk(List.of(new SignalWalk.Step(0, false, 45)), true);
		assertEquals(SignalAspect.GREEN, SignalWalk.aspect(4, true, noColours));
	}
}
