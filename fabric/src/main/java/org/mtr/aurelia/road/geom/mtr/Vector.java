/*
 * Ported from Minecraft Transit Railway's Transport-Simulation-Core, MIT licensed (see LICENSE-MTR.txt);
 * this copy came through Stratos. MTR's notice below is unchanged, and MIT governs this file.
 */
/*
 * Literal port of org.mtr.core.tool.Vector from Minecraft Transit Railway's Transport-Simulation-Core,
 * src/main/java/org/mtr/core/tool/Vector.java at commit d408c82b4613dc82f81178460f5194dfcf2070bb, the maths MTR 4.0.5 runs
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

public record Vector(double x, double y, double z) {

	public Vector add(double x, double y, double z) {
		return new Vector(this.x + x, this.y + y, this.z + z);
	}

	public Vector add(Vector vector) {
		return add(vector.x, vector.y, vector.z);
	}

	public Vector multiply(double x, double y, double z) {
		return new Vector(this.x * x, this.y * y, this.z * z);
	}

	public Vector multiply(Vector vector) {
		return multiply(vector.x, vector.y, vector.z);
	}

	public Vector rotateX(double angle) {
		final double cos = StrictMath.cos(angle);
		final double sin = StrictMath.sin(angle);
		return new Vector(x, y * cos + z * sin, z * cos - y * sin);
	}

	public Vector rotateY(double angle) {
		final double cos = StrictMath.cos(angle);
		final double sin = StrictMath.sin(angle);
		return new Vector(x * cos + z * sin, y, z * cos - x * sin);
	}

	public Vector rotateZ(double angle) {
		final double cos = StrictMath.cos(angle);
		final double sin = StrictMath.sin(angle);
		return new Vector(x * cos + y * sin, y * cos - x * sin, z);
	}

	public double distanceTo(Vector vec) {
		final double differenceX = vec.x - x;
		final double differenceY = vec.y - y;
		final double differenceZ = vec.z - z;
		return StrictMath.sqrt(differenceX * differenceX + differenceY * differenceY + differenceZ * differenceZ);
	}

	public Vector normalize() {
		final double length = StrictMath.sqrt(x * x + y * y + z * z);
		return length < 1E-4 ? new Vector(0, 0, 0) : new Vector(x / length, y / length, z / length);
	}

	public static Vector getAverage(Vector position1, Vector position2) {
		return new Vector(Utilities.getAverage(position1.x, position2.x), Utilities.getAverage(position1.y, position2.y), Utilities.getAverage(position1.z, position2.z));
	}
}
