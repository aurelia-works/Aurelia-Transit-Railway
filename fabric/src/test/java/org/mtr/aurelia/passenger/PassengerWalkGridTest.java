package org.mtr.aurelia.passenger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.client.PassengerWalkGrid;

public final class PassengerWalkGridTest {

	/** A straight walk crossed walls and could not change level; this guards both. */
	@Test
	public void aWalkGoesRoundAWallAndUpStairs() {
		// Floor at y 0 for z < 6, stairs rising along z 6..8, upper floor at y 3 from z 9. A wall at x 5 with a gap at z 0.
		final PassengerWalkGrid grid = new PassengerWalkGrid(0, 0, 0, 12, 5, 14);
		final long start = System.nanoTime();
		Assertions.assertFalse(grid.scan(PassengerWalkGridTest::standable, 10));
		while (!grid.scan(PassengerWalkGridTest::standable, 1000)) {
			Thread.yield();
		}
		System.out.println("Scan of 840 cells with a trivial probe: " + (System.nanoTime() - start) / 1000 + " us");

		final int[] round = grid.path(2, 0, 3, 8, 0, 3, 2000);
		Assertions.assertNotNull(round);
		for (int i = 0; i < round.length; i += 3) {
			Assertions.assertFalse(round[i] == 5 && round[i + 2] != 0, "walked through the wall");
		}
		final int[] up = grid.path(2, 0, 3, 2, 3, 12, 2000);
		Assertions.assertNotNull(up);
		Assertions.assertEquals(3, up[up.length - 2]);
		Assertions.assertNull(grid.path(2, 0, 3, 2, 3, 12, 3), "stops at its node limit");
	}

	private static boolean standable(int x, int y, int z) {
		if (x == 5 && z != 0 && z < 6) {
			return false;
		}
		final int floor = z < 6 ? 0 : z >= 9 ? 3 : z - 5;
		return y == floor;
	}
}
