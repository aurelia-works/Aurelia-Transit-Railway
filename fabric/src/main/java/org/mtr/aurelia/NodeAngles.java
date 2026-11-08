package org.mtr.aurelia;

import org.mtr.core.tool.Angle;

/**
 * Rail node placement angles in 11.25 degree steps. A node angle only matters modulo 180 degrees, so there are 16 steps.
 * Step s is stored as FACING (s &lt; 8, base 0 degrees, else 90), IS_45 (s % 8 &gt;= 4), IS_22_5 (s % 4 &gt;= 2) and
 * IS_11_25 (s is odd). Kept apart from BlockNode so it can be tested without Minecraft.
 */
public final class NodeAngles {

	private NodeAngles() {
	}

	/** The node step for a player yaw. Every yaw on MTR's 22.5 degree grid gives the same node angle as MTR 4.0.5. */
	public static int step(float yaw) {
		// Doubling the yaw turns MTR's 16 steps over 360 degrees into 16 steps of 11.25 degrees over 180. The node angle
		// sits 90 degrees from the yaw, hence the 8 step offset.
		return (Angle.getQuadrant(yaw * 2, true) + 8) % 16;
	}
}
