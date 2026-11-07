package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.NodeAngles;
import org.mtr.core.tool.Angle;

/** Rail nodes placed by players and build tools must keep MTR 4.0.5's angle on the 22.5 degree grid (PR #49 review). */
public final class NodeAnglesTest {

	/** MTR 4.0.5's node angle for a yaw, in 11.25 degree steps: FACING = q % 8 &gt;= 4, IS_45 = q % 4 &gt;= 2, IS_22_5 = q odd. */
	private static int oldStep(float yaw) {
		final int q = Angle.getQuadrant(yaw, true);
		return (q % 8 >= 4 ? 0 : 8) + (q % 4 >= 2 ? 4 : 0) + (q % 2 == 1 ? 2 : 0);
	}

	@Test
	public void gridYawsKeepMtrAngles() {
		for (int k = -16; k < 32; k++) {
			final float yaw = k * 22.5F;
			Assertions.assertEquals(oldStep(yaw), NodeAngles.step(yaw), "yaw " + yaw);
		}
	}

	@Test
	public void halfStepYawsLandBetween() {
		for (int k = 0; k < 16; k++) {
			final float yaw = k * 22.5F + 11.25F;
			final int step = NodeAngles.step(yaw);
			Assertions.assertEquals(1, step % 2, "yaw " + yaw + " gave step " + step);
			Assertions.assertTrue(step == (oldStep(k * 22.5F) + 1) % 16 || step == (oldStep(k * 22.5F + 22.5F) + 15) % 16, "yaw " + yaw + " gave step " + step);
		}
	}
}
