package org.mtr.aurelia.road.road.raster;

/**
 * A pedestrian crossing over a road (ATR P11; built from Stratos's research 13 and roadmap notes, as upstream has no
 * crossing code yet): across the road at {@code at} blocks along it from its start (measured as dashes are, along the
 * lanes' average), {@code width} blocks wide. Where the road has kerbs, each kerb column in that stretch becomes a dropped
 * kerb: flush with the road, with buff tactile paving behind it (UK: flush kerb, at least 1.2 m wide; tactile paving
 * 800–1,200 mm deep, buff at crossings without signals).
 *
 * <p>Two things place them (#193; owner, 2026-11-24):
 * <ul>
 *   <li>the road tools' Crossing option: one across the middle of the road, kept in its parameters
 *       ({@code RoadPlanParams.crossings}), so undo, redo and rebuilds plan it again;</li>
 *   <li>a junction, by itself, across each side road's mouth: part of its own footprint
 *       ({@code JunctionFootprint.crossings}), not a road's.</li>
 * </ul>
 */
public record Crossing(double at, int width) {
    /** The narrowest crossing: two blocks, the first whole width over the UK's 1.2 m. */
    public static final int MIN_WIDTH = 2;
    /** The widest crossing (a wide zebra is about 5 m; 8 leaves room). */
    public static final int MAX_WIDTH = 8;

    public Crossing {
        if (!Double.isFinite(at) || at < 0) {
            throw new IllegalArgumentException("A crossing is a distance along the road, 0 or more: " + at);
        }
        if (width < MIN_WIDTH || width > MAX_WIDTH) {
            throw new IllegalArgumentException("A crossing is " + MIN_WIDTH + " to " + MAX_WIDTH + " blocks wide: " + width);
        }
    }

    /** Whether {@code along} (blocks along the road) is in this crossing: half-open, so a crossing is exactly {@link #width} long. */
    public boolean covers(double along) {
        return along >= this.at - this.width / 2.0 && along < this.at + this.width / 2.0;
    }
}
