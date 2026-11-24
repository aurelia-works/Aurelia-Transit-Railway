/*
 * Ported from Minecraft Transit Railway's Transport-Simulation-Core, MIT licensed (see LICENSE-MTR.txt);
 * this copy came through Stratos. MTR's notice below is unchanged, and MIT governs this file.
 */
/*
 * Ported subset of org.mtr.core.data.Position from Minecraft Transit Railway's Transport-Simulation-Core: only the members
 * RailMath uses. Source: src/main/java/org/mtr/core/data/Position.java at commit d408c82b4613dc82f81178460f5194dfcf2070bb.
 * In MTR, x, y and z are fields of the generated superclass PositionSchema.
 * Changed from upstream: this comment and the package.
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

public class Position {

	protected final long x;
	protected final long y;
	protected final long z;

	public Position(long x, long y, long z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public long getX() {
		return x;
	}

	public long getY() {
		return y;
	}

	public long getZ() {
		return z;
	}
}
