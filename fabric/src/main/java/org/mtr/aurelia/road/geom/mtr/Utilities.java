/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/*
 * Ported subset of org.mtr.core.tool.Utilities from Minecraft Transit Railway's Transport-Simulation-Core: only the members
 * RailMath and Vector use. Source: src/main/java/org/mtr/core/tool/Utilities.java at commit
 * 69c8436bb6fdb61818ea09a1cc6e390b96c2c853 (later commits removed clamp, but MTR 4.0.5's bytecode still calls it).
 * Changed from upstream: this comment, the package, and every Math call made a StrictMath call (ADR-0011).
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

public interface Utilities {

	static double clamp(double value, double min, double max) {
		return StrictMath.min(max, StrictMath.max(min, value));
	}

	static double getAverage(double a, double b) {
		return (a + b) / 2;
	}
}
