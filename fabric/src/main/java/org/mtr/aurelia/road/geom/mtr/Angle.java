/*
 * Ported from Minecraft Transit Railway's Transport-Simulation-Core, MIT licensed (see LICENSE-MTR.txt);
 * this copy came through Stratos. MTR's notice below is unchanged, and MIT governs this file.
 */
/*
 * Literal port of org.mtr.core.tool.Angle from Minecraft Transit Railway's Transport-Simulation-Core,
 * src/main/java/org/mtr/core/tool/Angle.java at commit d408c82b4613dc82f81178460f5194dfcf2070bb, the maths MTR 4.0.5 runs
 * (spike S5 proved the port identical to MTR 4.0.5). Changed from upstream: this comment, the package, and every Math call made a StrictMath call
 * (ADR-0011). Nothing else: never "clean up" MTR's maths, names or formatting.
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

public enum Angle {
	E(0),
	SEE(22.5F),
	SE(45),
	SSE(67.5F),
	S(90),
	SSW(112.5F),
	SW(135),
	SWW(157.5F),
	W(180),
	NWW(202.5F),
	NW(225),
	NNW(247.5F),
	N(270),
	NNE(292.5F),
	NE(315),
	NEE(337.5F);

	public final float angleDegrees;
	public final double angleRadians;
	public final double sin;
	public final double cos;
	public final double tan;
	public final double halfTan;

	private static final int DEGREES_IN_CIRCLE = 360;
	private static final int QUADRANTS = values().length;
	private static final float ANGLE_INCREMENT = (float) DEGREES_IN_CIRCLE / QUADRANTS;

	Angle(float angleDegrees) {
		this.angleDegrees = normalizeAngle(angleDegrees);
		angleRadians = StrictMath.toRadians(this.angleDegrees);
		sin = StrictMath.sin(angleRadians);
		cos = StrictMath.cos(angleRadians);
		tan = StrictMath.tan(angleRadians);
		halfTan = StrictMath.tan(angleRadians / 2);
	}

	public Angle getOpposite() {
		switch (this) {
			default:
				return W;
			case SEE:
				return NWW;
			case SE:
				return NW;
			case SSE:
				return NNW;
			case S:
				return N;
			case SSW:
				return NNE;
			case SW:
				return NE;
			case SWW:
				return NEE;
			case W:
				return E;
			case NWW:
				return SEE;
			case NW:
				return SE;
			case NNW:
				return SSE;
			case N:
				return S;
			case NNE:
				return SSW;
			case NE:
				return SW;
			case NEE:
				return SWW;
		}
	}

	public Angle getClosest45() {
		switch (this) {
			default:
				return this;
			case NNW:
			case NNE:
				return N;
			case NEE:
			case SEE:
				return E;
			case SSE:
			case SSW:
				return S;
			case SWW:
			case NWW:
				return W;
		}
	}

	public Angle add(Angle angle) {
		return fromAngle(angleDegrees + angle.angleDegrees);
	}

	public Angle sub(Angle angle) {
		return fromAngle(angleDegrees - angle.angleDegrees);
	}

	public boolean isParallel(Angle angle) {
		return this == angle || this == angle.getOpposite();
	}

	public boolean similarFacing(float newAngleDegrees) {
		return similarFacing(angleDegrees, newAngleDegrees);
	}

	public static boolean similarFacing(float angleDegrees1, float angleDegrees2) {
		return StrictMath.abs(normalizeAngle(angleDegrees1 - angleDegrees2)) < DEGREES_IN_CIRCLE / 4F;
	}

	public static int getQuadrant(float angleDegrees, boolean include225) {
		final int factor = include225 ? 1 : 2;
		return (StrictMath.round((normalizeAngle(angleDegrees) + DEGREES_IN_CIRCLE) / ANGLE_INCREMENT / factor) % (QUADRANTS / factor));
	}

	public static Angle fromAngle(float angleDegrees) {
		return Angle.values()[getQuadrant(angleDegrees, true)];
	}

	private static float normalizeAngle(float angleDegrees) {
		int additional = 0;
		while (angleDegrees + additional < -DEGREES_IN_CIRCLE / 2F) {
			additional += DEGREES_IN_CIRCLE;
		}
		while (angleDegrees + additional >= DEGREES_IN_CIRCLE / 2F) {
			additional -= DEGREES_IN_CIRCLE;
		}
		return angleDegrees + additional;
	}
}
