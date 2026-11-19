package com.aureliatransit.architecture.bridge.span;

/**
 * The Bridge Builder's choices, kept on the item stack.
 *
 * @param style        the style for every bridge on the rail, or {@link SpanStyle#AUTO} to pick one per gap
 * @param halfWidth    blocks from the track's centre to the deck's edge, parapet not counted (2 is a single track)
 * @param pierSpacing  blocks between girder piers; arch, truss and the big spans size their own bays from it
 * @param masts        overhead-line masts, arms and wire along the whole rail, tunnels excepted
 * @param mastSpacing  blocks between masts on straight track; curves close them up so the wire stays over the track
 * @param towerScale   height of pylons and towers, 1 is the planner's own
 * @param portals      tunnels get a lining and a portal at each end; off leaves hills as they are
 */
public record SpanSettings(SpanStyle style, int halfWidth, int pierSpacing, boolean masts, int mastSpacing, double towerScale, boolean portals) {

	public static final SpanSettings DEFAULT = new SpanSettings(SpanStyle.AUTO, 2, 20, true, 30, 1, true);

	public SpanSettings {
		halfWidth = Math.max(1, Math.min(6, halfWidth));
		pierSpacing = Math.max(8, Math.min(48, pierSpacing));
		mastSpacing = Math.max(12, Math.min(60, mastSpacing));
		towerScale = Double.isFinite(towerScale) ? Math.max(0.5, Math.min(2, towerScale)) : 1;
	}

	public SpanSettings withStyle(SpanStyle next) {
		return new SpanSettings(next, halfWidth, pierSpacing, masts, mastSpacing, towerScale, portals);
	}
}
