/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/*
 * Ported subset of org.mtr.core.data.Rail from Minecraft Transit Railway's Transport-Simulation-Core: the Shape enum
 * RailMath uses and, from task G-8, getAngles (how MTR picks a rail's angles from two nodes). Source:
 * src/main/java/org/mtr/core/data/Rail.java at commit d408c82b4613dc82f81178460f5194dfcf2070bb.
 * Changed from upstream: this comment, the package, the imports (everything else is in this package) and Math -> StrictMath.
 *
 * MIT License
 *
 * Copyright (c) 2023 Jonathan Ho
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.mtr.aurelia.road.geom.mtr;

import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

public class Rail {

	/**
	 * A helper method that can be used for finding appropriate angles when creating rails.
	 *
	 * @return angles that make sense for the two supplied positions
	 */
	public static ObjectObjectImmutablePair<Angle, Angle> getAngles(Position positionStart, float angle1, Position positionEnd, float angle2) {
		final float angleDifference = (float) StrictMath.toDegrees(StrictMath.atan2(positionEnd.getZ() - positionStart.getZ(), positionEnd.getX() - positionStart.getX()));
		return new ObjectObjectImmutablePair<>(
				Angle.fromAngle(angle1 + (Angle.similarFacing(angleDifference, angle1) ? 0 : 180)),
				Angle.fromAngle(angle2 + (Angle.similarFacing(angleDifference, angle2) ? 180 : 0))
		);
	}

	public enum Shape {QUADRATIC, TWO_RADII, CABLE}
}
