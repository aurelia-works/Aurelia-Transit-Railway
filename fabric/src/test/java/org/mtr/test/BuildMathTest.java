package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.building.BuildMath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuildMathTest {

	@Test
	public void volumeStaysInsideTheCapAndCornersMatchTheIndex() {
		assertEquals(8, BuildMath.volume(0, 0, 0, 1, 1, 1, BuildMath.Mode.FULL));
		assertEquals(4, BuildMath.volume(0, 0, 0, 1, 1, 1, BuildMath.Mode.ABOVE));
		assertEquals(0, BuildMath.volume(0, 4, 0, 3, 4, 3, BuildMath.Mode.ABOVE));
		assertFalse(BuildMath.volumeFits(0, 512));
		assertFalse(BuildMath.volumeFits(4097, 4096));
		assertTrue(BuildMath.volumeFits(512, 512));
		assertFalse(BuildMath.sidesFit(0, 0, 0, BuildMath.MAX_SIDE, 0, 0));
		assertTrue(BuildMath.sidesFit(0, 0, 0, BuildMath.MAX_SIDE - 1, 0, 0));
		final int[] at = new int[3];
		BuildMath.at(0, 1, 2, 3, 0, 1, 2, BuildMath.Mode.FULL, at);
		assertEquals(0, at[0]);
		assertEquals(1, at[1]);
		assertEquals(2, at[2]);
		BuildMath.at(7, 0, 0, 0, 1, 1, 1, BuildMath.Mode.FULL, at);
		assertEquals(1, at[0]);
		assertEquals(1, at[1]);
		assertEquals(1, at[2]);
		BuildMath.at(0, 0, 5, 0, 1, 6, 1, BuildMath.Mode.ABOVE, at);
		assertEquals(6, at[1]);
	}

	@Test
	public void snapAimAndCurve() {
		assertEquals(15, BuildMath.snapYaw(10, 15));
		assertEquals(0, BuildMath.snapYaw(7, 15));
		assertEquals(345, BuildMath.snapYaw(350, 15));
		final int[] aim = new int[2];
		BuildMath.aim(4, 0, aim);
		assertEquals(0, aim[0]);
		assertEquals(4, aim[1]);
		BuildMath.aim(4, 90, aim);
		assertEquals(-4, aim[0]);
		assertEquals(0, aim[1]);
		final double[] point = new double[3];
		BuildMath.curve(0, 0, 4, 0, 0, 4, 10, 0, 0, point);
		assertEquals(0, point[0], 1.0e-6);
		assertEquals(4, point[1], 1.0e-6);
		assertEquals(0, point[2], 1.0e-6);
		BuildMath.curve(1, 0, 4, 0, 0, 4, 10, 0, 0, point);
		assertEquals(10, point[2], 1.0e-6);
		BuildMath.curve(0.5, 0, 4, 0, 0, 4, 10, 0, 0, point);
		assertEquals(0, point[0], 1.0e-6);
		assertEquals(5, point[2], 1.0e-4);
		BuildMath.curve(0.5, 0, 4, 0, 10, 4, 10, 0, 90, point);
		assertTrue(Math.abs(point[0] - 5) > 0.5 || Math.abs(point[2] - 5) > 0.5);
	}
}
