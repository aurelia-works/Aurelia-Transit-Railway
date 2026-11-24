/*
 * Ported from Minecraft Transit Railway's Transport-Simulation-Core, MIT licensed (MTR's notice is in LICENSE);
 * this copy came through Stratos. MTR's notice below is unchanged, and MIT governs this file.
 */
/*
 * Literal port of org.mtr.core.data.RailMath from Minecraft Transit Railway's Transport-Simulation-Core,
 * src/main/java/org/mtr/core/data/RailMath.java at commit d408c82b4613dc82f81178460f5194dfcf2070bb, as compiled into MTR 4.0.5
 * (MTR-fabric-4.0.5+1.20.1.jar; spike S5 proved the port identical). getPosition calls Utilities.clamp, as at
 * commit 69c8436bb6fdb61818ea09a1cc6e390b96c2c853, because that is what the 4.0.5 bytecode calls.
 * Changed from upstream: this comment, the package, the imports (everything is in one package here), that one
 * call, and every Math call made a StrictMath call (ADR-0011). Nothing else: never "clean up" MTR's maths,
 * names or formatting. Every curve a Stratos lane, and so a hidden MTR rail, follows comes from this class.
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

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;

public class RailMath {

	public final long minX;
	public final long minY;
	public final long minZ;
	public final long maxY;
	public final long maxX;
	public final long maxZ;

	private final Rail.Shape shape;
	private final double verticalRadius;
	private final double h1;
	private final double k1;
	private final double h2;
	private final double k2;
	private final double r1;
	private final double r2;
	private final double tStart1;
	private final double tEnd1;
	private final double tStart2;
	private final double tEnd2;
	private final long yStart;
	private final long yEnd;
	private final boolean reverseT1;
	private final boolean reverseT2;
	private final boolean isStraight1;
	private final boolean isStraight2;

	private static final double ACCEPT_THRESHOLD = 1E-4;
	private static final int CABLE_CURVATURE_SCALE = 1000;
	private static final int MAX_CABLE_DIP = 8;

	// for curves:
	// x = h + r*cos(T)
	// z = k + r*sin(T)
	// for straight lines (both k and r >= 0.5):
	// x = h*T
	// z = k*T + h*r
	// for straight lines (otherwise):
	// x = h*T + k*r
	// z = k*T + h*r
	public RailMath(Position position1, Angle angle1, Position position2, Angle angle2, Rail.Shape shape, double verticalRadius) {
		final long xStart = position1.getX();
		final long zStart = position1.getZ();
		final long xEnd = position2.getX();
		final long zEnd = position2.getZ();

		// Coordinate system translation and rotation
		final Vector vecDifference = new Vector(position2.getX() - position1.getX(), 0, position2.getZ() - position1.getZ());
		final Vector vecDifferenceRotated = vecDifference.rotateY((float) angle1.angleRadians);

		// First we check the Delta Side > 0
		// 1. If they are same angle
		// 1. a. If aligned -> Use One Segment
		// 1. b. If not aligned -> Use two Circle, r = (dv^2 + dp^2) / (4dv).
		// 2. If they are right angle -> r = min ( dx,dz ), work around, actually equation 3. can be used.
		// 3. Check if one segment and one circle is available
		// 3. a. If available -> (Segment First) r2 = dv / ( sin(diff) * tan(diff/2) ) = dv / ( 1 - cos(diff)
		// 							for case 2, diff = 90 degrees, r = dv
		//					-> (Circle First) r1 = ( dp - dv / tan(diff) ) / tan (diff/2)
		// TODO 3. b. If not -> r = very complex one. In this case, we need two circles to connect.
		final double deltaForward = vecDifferenceRotated.z();
		final double deltaSide = vecDifferenceRotated.x();
		if (angle1.isParallel(angle2)) { // 1
			if (StrictMath.abs(deltaForward) < ACCEPT_THRESHOLD) { // 1. a.
				h1 = angle1.cos;
				k1 = angle1.sin;
				if (StrictMath.abs(h1) >= 0.5 && StrictMath.abs(k1) >= 0.5) {
					r1 = (h1 * zStart - k1 * xStart) / h1 / h1;
					tStart1 = xStart / h1;
					tEnd1 = xEnd / h1;
				} else {
					final double div = angle1.add(angle1).cos;
					r1 = (h1 * zStart - k1 * xStart) / div;
					tStart1 = (h1 * xStart - k1 * zStart) / div;
					tEnd1 = (h1 * xEnd - k1 * zEnd) / div;
				}
				h2 = k2 = r2 = 0;
				reverseT1 = tStart1 > tEnd1;
				reverseT2 = false;
				isStraight1 = isStraight2 = true;
				tStart2 = tEnd2 = 0;
			} else { // 1. b
				if (StrictMath.abs(deltaSide) > ACCEPT_THRESHOLD) {
					final double radius = (deltaForward * deltaForward + deltaSide * deltaSide) / (4 * deltaForward);
					r1 = r2 = StrictMath.abs(radius);
					h1 = xStart - radius * angle1.sin;
					k1 = zStart + radius * angle1.cos;
					h2 = xEnd - radius * angle2.sin;
					k2 = zEnd + radius * angle2.cos;
					reverseT1 = deltaForward < 0 != deltaSide < 0;
					reverseT2 = !reverseT1;
					tStart1 = getTBounds(xStart, h1, zStart, k1, r1);
					tEnd1 = getTBounds(xStart + vecDifference.x() / 2, h1, zStart + vecDifference.z() / 2, k1, r1, tStart1, reverseT1);
					tStart2 = getTBounds(xStart + vecDifference.x() / 2, h2, zStart + vecDifference.z() / 2, k2, r2);
					tEnd2 = getTBounds(xEnd, h2, zEnd, k2, r2, tStart2, reverseT2);
					isStraight1 = isStraight2 = false;
				} else {
					// Banned node perpendicular to the rail nodes direction
					h1 = k1 = h2 = k2 = r1 = r2 = 0;
					tStart1 = tStart2 = tEnd1 = tEnd2 = 0;
					reverseT1 = false;
					reverseT2 = false;
					isStraight1 = isStraight2 = true;
				}
			}
		} else { // 3.
			// Check if it needs invert
			final Angle newAngle1 = vecDifferenceRotated.x() < -ACCEPT_THRESHOLD ? angle1.getOpposite() : angle1;
			final Angle newAngle2 = angle2.cos * vecDifference.x() + angle2.sin * vecDifference.z() < -ACCEPT_THRESHOLD ? angle2.getOpposite() : angle2;
			final double angleForward = StrictMath.atan2(deltaForward, deltaSide);
			final Angle railAngleDifference = newAngle2.sub(newAngle1);
			final double angleDifference = railAngleDifference.angleRadians;

			if (StrictMath.signum(angleForward) == StrictMath.signum(angleDifference)) {
				final double absAngleForward = StrictMath.abs(angleForward);

				if (absAngleForward - StrictMath.abs(angleDifference / 2) < ACCEPT_THRESHOLD) { // Segment First
					final double offsetSide = StrictMath.abs(deltaForward / railAngleDifference.halfTan);
					final double remainingSide = deltaSide - offsetSide;
					final double deltaXEnd = xStart + remainingSide * newAngle1.cos;
					final double deltaZEnd = zStart + remainingSide * newAngle1.sin;
					h1 = newAngle1.cos;
					k1 = newAngle1.sin;
					if (StrictMath.abs(h1) >= 0.5 && StrictMath.abs(k1) >= 0.5) {
						r1 = (h1 * zStart - k1 * xStart) / h1 / h1;
						tStart1 = xStart / h1;
						tEnd1 = deltaXEnd / h1;
					} else {
						final double div = newAngle1.add(newAngle1).cos;
						r1 = (h1 * zStart - k1 * xStart) / div;
						tStart1 = (h1 * xStart - k1 * zStart) / div;
						tEnd1 = (h1 * deltaXEnd - k1 * deltaZEnd) / div;
					}
					isStraight1 = true;
					reverseT1 = tStart1 > tEnd1;
					final double radius = deltaForward / (1 - railAngleDifference.cos);
					r2 = StrictMath.abs(radius);
					h2 = deltaXEnd - radius * newAngle1.sin;
					k2 = deltaZEnd + radius * newAngle1.cos;
					reverseT2 = (deltaForward < 0);
					tStart2 = getTBounds(deltaXEnd, h2, deltaZEnd, k2, r2);
					tEnd2 = getTBounds(xEnd, h2, zEnd, k2, r2, tStart2, reverseT2);
					isStraight2 = false;
				} else if (absAngleForward - StrictMath.abs(angleDifference) < ACCEPT_THRESHOLD) { // Circle First
					final double crossSide = deltaForward / railAngleDifference.tan;
					final double remainingSide = (deltaSide - crossSide) * (1 + railAngleDifference.cos);
					final double remainingForward = (deltaSide - crossSide) * (railAngleDifference.sin);
					final double deltaXEnd = xStart + remainingSide * newAngle1.cos - remainingForward * newAngle1.sin;
					final double deltaZEnd = zStart + remainingSide * newAngle1.sin + remainingForward * newAngle1.cos;
					final double radius = (deltaSide - deltaForward / railAngleDifference.tan) / railAngleDifference.halfTan;
					r1 = StrictMath.abs(radius);
					h1 = xStart - radius * newAngle1.sin;
					k1 = zStart + radius * newAngle1.cos;
					isStraight1 = false;
					reverseT1 = (deltaForward < 0);
					tStart1 = getTBounds(xStart, h1, zStart, k1, r1);
					tEnd1 = getTBounds(deltaXEnd, h1, deltaZEnd, k1, r1, tStart1, reverseT1);
					h2 = newAngle2.cos;
					k2 = newAngle2.sin;
					if (StrictMath.abs(h2) >= 0.5 && StrictMath.abs(k2) >= 0.5) {
						r2 = (h2 * deltaZEnd - k2 * deltaXEnd) / h2 / h2;
						tStart2 = deltaXEnd / h2;
						tEnd2 = xEnd / h2;
					} else {
						final double div = newAngle2.add(newAngle2).cos;
						r2 = (h2 * deltaZEnd - k2 * deltaXEnd) / div;
						tStart2 = (h2 * deltaXEnd - k2 * deltaZEnd) / div;
						tEnd2 = (h2 * xEnd - k2 * zEnd) / div;
					}
					isStraight2 = true;
					reverseT2 = tStart2 > tEnd2;
				} else { // Out of available range
					// TODO complex one. Normally we don't need it.
					h1 = k1 = h2 = k2 = r1 = r2 = 0;
					tStart1 = tStart2 = tEnd1 = tEnd2 = 0;
					reverseT1 = false;
					reverseT2 = false;
					isStraight1 = isStraight2 = true;
				}
			} else {
				// TODO 3. b. If not -> r = very complex one. Normally we don't need it.
				h1 = k1 = h2 = k2 = r1 = r2 = 0;
				tStart1 = tStart2 = tEnd1 = tEnd2 = 0;
				reverseT1 = false;
				reverseT2 = false;
				isStraight1 = isStraight2 = true;
			}
		}

		yStart = position1.getY();
		yEnd = position2.getY();
		this.shape = shape;
		this.verticalRadius = StrictMath.min(verticalRadius, getMaxVerticalRadius());

		// Calculate bounds (for culling)
		final double[] bounds = new double[]{Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		render((x1, z1, x2, z2, x3, z3, x4, z4, y1, y2) -> {
			bounds[0] = StrictMath.min(x1, bounds[0]);
			bounds[0] = StrictMath.min(x2, bounds[0]);
			bounds[0] = StrictMath.min(x3, bounds[0]);
			bounds[0] = StrictMath.min(x4, bounds[0]);
			bounds[1] = StrictMath.min(y1, bounds[1]);
			bounds[1] = StrictMath.min(y2, bounds[1]);
			bounds[2] = StrictMath.min(z1, bounds[2]);
			bounds[2] = StrictMath.min(z2, bounds[2]);
			bounds[2] = StrictMath.min(z3, bounds[2]);
			bounds[2] = StrictMath.min(z4, bounds[2]);
			bounds[3] = StrictMath.max(x1, bounds[3]);
			bounds[3] = StrictMath.max(x2, bounds[3]);
			bounds[3] = StrictMath.max(x3, bounds[3]);
			bounds[3] = StrictMath.max(x4, bounds[3]);
			bounds[4] = StrictMath.max(y1, bounds[4]);
			bounds[4] = StrictMath.max(y2, bounds[4]);
			bounds[5] = StrictMath.max(z1, bounds[5]);
			bounds[5] = StrictMath.max(z2, bounds[5]);
			bounds[5] = StrictMath.max(z3, bounds[5]);
			bounds[5] = StrictMath.max(z4, bounds[5]);
		}, 0.1, 0, 0);
		minX = bounds[0] > bounds[3] ? 0 : (long) StrictMath.floor(bounds[0]);
		minY = bounds[1] > bounds[4] ? 0 : (long) StrictMath.floor(bounds[1]);
		minZ = bounds[2] > bounds[5] ? 0 : (long) StrictMath.floor(bounds[2]);
		maxX = bounds[3] < bounds[0] ? 0 : (long) StrictMath.ceil(bounds[3]);
		maxY = bounds[4] < bounds[1] ? 0 : (long) StrictMath.ceil(bounds[4]);
		maxZ = bounds[5] < bounds[2] ? 0 : (long) StrictMath.ceil(bounds[5]);
	}

	public Vector getPosition(double rawValue, boolean reverse) {
		final double count1 = StrictMath.abs(tEnd1 - tStart1);
		final double count2 = StrictMath.abs(tEnd2 - tStart2);
		final double clampedValue = Utilities.clamp(rawValue, 0, count1 + count2);
		final double value = reverse ? count1 + count2 - clampedValue : clampedValue;
		final double y = getPositionY(value);

		if (value <= count1) {
			return getPositionXZ(h1, k1, r1, (reverseT1 ? -1 : 1) * value + tStart1, 0, isStraight1).add(0, y, 0);
		} else {
			return getPositionXZ(h2, k2, r2, (reverseT2 ? -1 : 1) * (value - count1) + tStart2, 0, isStraight2).add(0, y, 0);
		}
	}

	public double getLength() {
		return StrictMath.abs(tEnd2 - tStart2) + StrictMath.abs(tEnd1 - tStart1);
	}

	public Rail.Shape getShape() {
		return shape;
	}

	public DoubleDoubleImmutablePair getHorizontalRadii() {
		return new DoubleDoubleImmutablePair(isStraight1 ? 0 : StrictMath.abs(r1), isStraight2 ? 0 : StrictMath.abs(r2));
	}

	public double getVerticalRadius() {
		return verticalRadius;
	}

	public double getMaxVerticalRadius() {
		final double length = getLength();
		final double height = yEnd - yStart;
		return StrictMath.floor((length * length + height * height) * 100 / StrictMath.abs(4 * height)) / 100;
	}

	public void render(RenderRail callback, double interval, float offsetRadius1, float offsetRadius2) {
		renderSegment(h1, k1, r1, tStart1, tEnd1, 0, interval, offsetRadius1, offsetRadius2, reverseT1, isStraight1, callback);
		renderSegment(h2, k2, r2, tStart2, tEnd2, StrictMath.abs(tEnd1 - tStart1), interval, offsetRadius1, offsetRadius2, reverseT2, isStraight2, callback);
	}

	boolean isValid() {
		return h1 != 0 || k1 != 0 || h2 != 0 || k2 != 0 || r1 != 0 || r2 != 0 || tStart1 != 0 || tStart2 != 0 || tEnd1 != 0 || tEnd2 != 0;
	}

	private void renderSegment(double h, double k, double r, double tStart, double tEnd, double rawValueOffset, double interval, float offsetRadius1, float offsetRadius2, boolean reverseT, boolean isStraight, RenderRail callback) {
		final double count = StrictMath.abs(tEnd - tStart);
		final double increment = count < 0.5 || interval <= 0 ? 0.5 : count / StrictMath.round(count) * interval;
		Vector previousCorner1 = null;
		Vector previousCorner2 = null;
		double previousY = 0;

		for (double i = 0; i < count + increment - 0.1; i += increment) {
			final double t = (reverseT ? -1 : 1) * i + tStart;
			final Vector corner1 = getPositionXZ(h, k, r, t, offsetRadius2, isStraight);
			final Vector corner2 = offsetRadius2 == offsetRadius1 ? corner1 : getPositionXZ(h, k, r, t, offsetRadius1, isStraight);
			final double y = getPositionY(i + rawValueOffset);

			if (previousCorner1 != null) {
				callback.renderRail(previousCorner1.x(), previousCorner1.z(), previousCorner2.x(), previousCorner2.z(), corner1.x(), corner1.z(), corner2.x(), corner2.z(), previousY, y);
			}

			previousCorner1 = corner2;
			previousCorner2 = corner1;
			previousY = y;
		}
	}

	private double getPositionY(double value) {
		if (yStart == yEnd) {
			return yStart;
		}

		final double length = getLength();

		switch (shape) {
			case TWO_RADII:
				// Copied from NTE
				if (verticalRadius <= 0) {
					return (value / length) * (yEnd - yStart) + yStart;
				} else {
					final double vTheta = getVTheta();
					final double curveLength = StrictMath.sin(vTheta) * verticalRadius;
					final double curveHeight = (1 - StrictMath.cos(vTheta)) * verticalRadius;
					final int sign = yStart < yEnd ? 1 : -1;

					if (value < curveLength) {
						return sign * (verticalRadius - StrictMath.sqrt(verticalRadius * verticalRadius - value * value)) + yStart;
					} else if (value > length - curveLength) {
						final double r = length - value;
						return -sign * (verticalRadius - StrictMath.sqrt(verticalRadius * verticalRadius - r * r)) + yEnd;
					} else {
						return sign * (((value - curveLength) / (length - 2 * curveLength)) * (StrictMath.abs(yEnd - yStart) - 2 * curveHeight) + curveHeight) + yStart;
					}
				}
			case CABLE:
				if (value < 0.5) {
					return yStart;
				} else if (value > length - 0.5) {
					return yEnd;
				}

				final double cableOffsetValue = value - 0.5;
				final double offsetLength = length - 1;
				final double posY = yStart + (yEnd - yStart) * cableOffsetValue / offsetLength;
				final double dip = offsetLength * offsetLength / 4 / CABLE_CURVATURE_SCALE;
				return posY + (dip > MAX_CABLE_DIP ? MAX_CABLE_DIP / dip : 1) * (cableOffsetValue - offsetLength) * cableOffsetValue / CABLE_CURVATURE_SCALE;
			default:
				final double intercept = length / 2;
				final double yChange;
				final double yInitial;
				final double offsetValue;

				if (value < intercept) {
					yChange = (yEnd - yStart) / 2D;
					yInitial = yStart;
					offsetValue = value;
				} else {
					yChange = (yStart - yEnd) / 2D;
					yInitial = yEnd;
					offsetValue = length - value;
				}

				return yChange * offsetValue * offsetValue / (intercept * intercept) + yInitial;
		}
	}

	private double getVTheta() {
		final double height = StrictMath.abs(yEnd - yStart);
		final double length = getLength();
		return 2 * StrictMath.atan2(StrictMath.sqrt(height * height - 4 * verticalRadius * height + length * length) - length, height - 4 * verticalRadius);
	}

	private static Vector getPositionXZ(double h, double k, double r, double t, double radiusOffset, boolean isStraight) {
		if (isStraight) {
			return new Vector(h * t + k * ((StrictMath.abs(h) >= 0.5 && StrictMath.abs(k) >= 0.5 ? 0 : r) + radiusOffset) + 0.5, 0, k * t + h * (r - radiusOffset) + 0.5);
		} else {
			return new Vector(h + (r + radiusOffset) * StrictMath.cos(t / r) + 0.5, 0, k + (r + radiusOffset) * StrictMath.sin(t / r) + 0.5);
		}
	}

	private static double getTBounds(double x, double h, double z, double k, double r) {
		return StrictMath.atan2(z - k, x - h) * r;
	}

	private static double getTBounds(double x, double h, double z, double k, double r, double tStart, boolean reverse) {
		final double t = getTBounds(x, h, z, k, r);
		if (t < tStart && !reverse) {
			return t + 2 * StrictMath.PI * r;
		} else if (t > tStart && reverse) {
			return t - 2 * StrictMath.PI * r;
		} else {
			return t;
		}
	}

	@FunctionalInterface
	public interface RenderRail {
		void renderRail(double x1, double z1, double x2, double z2, double x3, double z3, double x4, double z4, double y1, double y2);
	}
}
