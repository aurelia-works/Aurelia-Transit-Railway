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

	/** Cutting a corner through a pillar is the same bug as walking through a wall. */
	@Test
	public void cornersAreCutButNeverThroughAPillar() {
		final PassengerWalkGrid grid = new PassengerWalkGrid(0, 0, 0, 12, 1, 12);
		grid.scan((x, y, z) -> x != 5 || z != 5, 144);

		final int[] round = grid.path(2, 0, 5, 8, 0, 5, 2000);
		Assertions.assertNotNull(round);
		final double[] turns = grid.turns(round, 2.5, 5.5, 8.5, 5.5);
		Assertions.assertTrue(turns.length < round.length, "kept every cell");
		for (int i = 3; i < turns.length; i += 3) {
			for (double t = 0; t <= 1; t += 0.02) {
				final double x = turns[i - 3] + (turns[i] - turns[i - 3]) * t;
				final double z = turns[i - 1] + (turns[i + 2] - turns[i - 1]) * t;
				Assertions.assertFalse(x >= 5 && x < 6 && z >= 5 && z < 6, "walked through the pillar");
			}
		}
		Assertions.assertEquals(6, grid.turns(grid.path(1, 0, 1, 9, 0, 3, 2000), 1.5, 1.5, 9.5, 3.5).length, "open floor is one straight leg");
	}

	/** Once the grid reaches below the platform, a plain edge must not let people drop to the track; a running escalator goes one way. */
	@Test
	public void levelsChangeOnlyOnStepsAndEscalatorsGoOneWay() {
		// Floor at y 2 for z < 5; a walled pit at y 1 for z 5..6; a stair down along x 0, an up escalator along x 9, a lower hall at y 0 from z 7.
		final PassengerWalkGrid grid = new PassengerWalkGrid(0, 0, 0, 10, 3, 12);
		grid.scan((x, y, z) -> {
			if (x == 0 || x == 9) {
				return y == (z < 5 ? 2 : z == 5 ? 1 : 0);
			}
			if ((x == 1 || x == 8) && (z == 5 || z == 6)) {
				return false;
			}
			return y == (z < 5 ? 2 : z < 7 ? 1 : 0);
		}, (x, y, z) -> x == 0 && (z == 5 || z == 6) ? PassengerWalkGrid.BOTH_WAYS : x == 9 && (z == 5 || z == 6) ? PassengerWalkGrid.UP_ONLY : PassengerWalkGrid.FLAT, 360);

		Assertions.assertNull(grid.path(5, 2, 3, 5, 1, 5, 2000), "dropped off the edge");
		Assertions.assertNotNull(grid.path(0, 2, 3, 0, 0, 8, 2000), "the stair goes down");
		Assertions.assertNotNull(grid.path(9, 0, 8, 9, 2, 3, 2000), "the escalator goes up");
		final int[] down = grid.path(9, 2, 3, 9, 0, 8, 2000);
		Assertions.assertNotNull(down);
		for (int i = 0; i < down.length; i += 3) {
			Assertions.assertFalse(down[i] == 9 && down[i + 2] == 5, "rode the escalator the wrong way");
		}
		Assertions.assertEquals(1, grid.exits(0, 2, 3, 2, 4, false, 2000).size(), "only the stair leads down");
		Assertions.assertEquals(2, grid.exits(0, 2, 3, 2, 4, true, 2000).size(), "stair and escalator both lead up here");
	}

	private static boolean standable(int x, int y, int z) {
		if (x == 5 && z != 0 && z < 6) {
			return false;
		}
		final int floor = z < 6 ? 0 : z >= 9 ? 3 : z - 5;
		return y == floor;
	}
}
