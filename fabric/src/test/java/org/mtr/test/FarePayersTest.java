package org.mtr.test;

import com.aureliatransit.architecture.block.elevated.FarePayers;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The fare gate remembers who it opened for. Two gates at the same x, y, z in different dimensions or worlds are different gates. */
public final class FarePayersTest {

	private static final UUID ALICE = UUID.randomUUID();
	private static final UUID BOB = UUID.randomUUID();

	@Test
	public void sameBlockPositionInAnotherWorldIsAnotherGate() {
		final FarePayers payers = new FarePayers();
		final Object overworld = new Object();
		final Object nether = new Object();
		payers.remember(overworld, 12345L, ALICE, 100);
		assertNull(payers.payerWithin(nether, 12345L, 10), "the nether gate never opened for anyone");
		assertEquals(ALICE, payers.payerWithin(overworld, 12345L, 10));
	}

	@Test
	public void payerStopsCountingWhenTheWindowEnds() {
		final FarePayers payers = new FarePayers();
		final Object world = new Object();
		payers.remember(world, 1L, ALICE, 100);
		assertEquals(ALICE, payers.payerWithin(world, 1L, 100));
		assertNull(payers.payerWithin(world, 1L, 101), "after the window the gate no longer waits for her");
		assertNull(payers.payerWithin(world, 1L, 50), "an expired payer is dropped, not kept for later");
	}

	@Test
	public void forgetAndLaterPayerReplaceTheOldOne() {
		final FarePayers payers = new FarePayers();
		final Object world = new Object();
		payers.remember(world, 1L, ALICE, 100);
		payers.remember(world, 1L, BOB, 200);
		assertEquals(BOB, payers.payerWithin(world, 1L, 150));
		payers.forget(world, 1L);
		assertNull(payers.payerWithin(world, 1L, 150));
	}

	@Test
	public void aWorldNeverTracksMoreThanTheCap() {
		final FarePayers payers = new FarePayers();
		final Object world = new Object();
		for (long pos = 0; pos < 2000; pos++) {
			payers.remember(world, pos, ALICE, 1000);
		}
		assertTrue(payers.tracked(world) <= FarePayers.MAX_TRACKED_GATES, "tracked " + payers.tracked(world));
		assertEquals(ALICE, payers.payerWithin(world, 1999L, 0), "the newest gate is still remembered");
	}
}
