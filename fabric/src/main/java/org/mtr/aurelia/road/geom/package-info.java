/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * Geometry: the block-centre frame, the 16 MTR headings, vectors ({@code StrictMath} only), the literal
 * {@code RailMath} port (with MTR's MIT notice and field names) and vertical profiles. Tasks G-1 to G-3.
 *
 * <p><b>The block-centre frame.</b> Core coordinates put each block's centre on whole numbers: block
 * {@code (bx, bz)} has its centre at {@code (bx, bz)} and covers {@code [bx − 0.5, bx + 0.5) × [bz − 0.5, bz + 0.5)}.
 * A world coordinate is the frame coordinate + 0.5, as MTR adds +0.5 (spec roads-v1 §4.1). x is east, z is south;
 * heights are handled separately (vertical profiles). Lane nodes are whole-number points, so they are always
 * block centres.
 *
 * <p><b>Curves come only from the RailMath port.</b> Every curve a lane, and so a hidden MTR rail, follows is
 * computed by the literal port of MTR's {@code RailMath} with MTR's own angle values, in the sub-package
 * {@code geom.mtr} (G-2, with MTR's MIT notice), checked against MTR 4.0.5 on every build.
 * {@code geom.mtr.MtrRails} links the two sides: heading {@code i} is MTR angle {@code i}. {@link org.mtr.aurelia.road.geom.Heading} and the vector types handle frames, offsets, mirroring
 * and whole-block steps; they never draw a curve.
 *
 * <p><b>Vertical profiles</b> (G-3). A lane's height comes from its own rail ({@code getPosition(u).y}).
 * {@link org.mtr.aurelia.road.geom.VerticalProfile} adds the grade facts roads need for QUADRATIC and TWO_RADII:
 * the steepest grade, MTR's radius limit, and the shortest piece for a climb. It also reports MTR's quirks (NaN heights at
 * the radius limit; one constant grade at R = H/4).
 */
package org.mtr.aurelia.road.geom;
