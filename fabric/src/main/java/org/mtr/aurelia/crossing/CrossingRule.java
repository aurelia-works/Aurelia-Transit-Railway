package org.mtr.aurelia.crossing;

import java.util.List;

/**
 * When a level crossing closes, from where the trains are. Each train is its points, head first (MTR's head position,
 * then its bogies). A crossing closes for a train on it or near it, and for a train coming towards it: one whose head is
 * its nearest point to the crossing. A train going away, its tail nearest, lets the crossing open as soon as it has
 * cleared it. Any train on a line within {@link #APPROACH} counts, so a fast or busy line is better served by an MTR
 * train sensor further up the line, wired to the crossing with redstone.
 */
public final class CrossingRule {

	/** Blocks across the ground: a train this close closes the crossing whichever way it goes. */
	static final double ON_CROSSING = 12;
	/** Blocks across the ground: a train coming towards the crossing closes it from this far away. */
	static final double APPROACH = 80;
	/** Blocks up or down: trains on a bridge over the crossing or in a tunnel under it don't count. */
	static final double VERTICAL = 8;

	private CrossingRule() {
	}

	/** Whether {@code trains} close a crossing at {@code x, y, z} (the block's centre). */
	public static boolean closes(List<List<double[]>> trains, double x, double y, double z) {
		for (final List<double[]> train : trains) {
			if (closes(train, x, y, z)) {
				return true;
			}
		}
		return false;
	}

	static boolean closes(List<double[]> train, double x, double y, double z) {
		double nearest = Double.MAX_VALUE;
		double head = Double.MAX_VALUE;
		for (int i = 0; i < train.size(); i++) {
			final double[] point = train.get(i);
			if (point == null || point.length < 3 || Math.abs(point[1] - y) > VERTICAL) {
				continue;
			}
			final double distance = Math.hypot(point[0] - x, point[2] - z);
			nearest = Math.min(nearest, distance);
			if (i == 0) {
				head = distance;
			}
		}
		return nearest <= ON_CROSSING || nearest <= APPROACH && head <= nearest + 0.5;
	}
}
