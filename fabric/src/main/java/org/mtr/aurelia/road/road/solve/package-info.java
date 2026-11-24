/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The per-lane solver (spec roads-v1 §5; ADR-0011; task G-5): one MTR rail per lane, between the lane's own nodes.
 * <ul>
 *   <li>{@link org.mtr.aurelia.road.road.solve.LaneOffsets}: where each lane's node sits relative to the
 *       centre-line node (the exact offset rounded to the nearest block, symmetric; owner, 2026-10-06).</li>
 *   <li>{@link org.mtr.aurelia.road.road.solve.PieceSolver}: solves a piece with MTR's heading convention (the end
 *       angle points back toward the start, spike S5), checks every rail really runs between its nodes, and refuses with
 *       roads-v1 §12's codes in a fixed order.</li>
 *   <li>{@link org.mtr.aurelia.road.road.solve.PieceSolution}: {@code Solved} (the centre-line rail and a
 *       {@link org.mtr.aurelia.road.road.solve.LaneRail} per lane) or {@code Refused}.</li>
 * </ul>
 * Rails are built relative to the piece's start node, so a piece solves the same anywhere. The rasteriser (G-6+), the
 * lane nodes (G-8), spike S6 and the curve tool (R-3) build on it.
 */
package org.mtr.aurelia.road.road.solve;
