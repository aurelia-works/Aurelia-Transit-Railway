/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import org.mtr.aurelia.road.geom.Vec2;

/**
 * A lane's curve, or one piece of it, in the block-centre frame: the line or arc a lane, and the MTR rail on it,
 * follows (spec roads-v1 §5). The rasteriser measures every column against these curves (§7.1). G-6 has lines; G-6+
 * adds arcs ({@link ArcSegment}) and whole lanes made of pieces ({@link LanePath}).
 */
public sealed interface LaneCurve permits LineSegment, ArcSegment, LanePath {
    /** A point's position relative to a curve: {@code s} along it from its start, {@code d} sideways (positive right). */
    record Local(double s, double d) {
    }

    /** {@code point} measured along and across this curve. */
    Local local(Vec2 point);

    /** The curve's length in blocks, measured horizontally. */
    double length();

    /** The point {@code s} blocks along the curve from its start ({@code 0 ≤ s ≤ length()}). */
    Vec2 point(double s);
}
