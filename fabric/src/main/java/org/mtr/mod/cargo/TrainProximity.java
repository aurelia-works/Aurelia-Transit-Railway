package org.mtr.mod.cargo;

import java.util.List;

public final class TrainProximity {

	/**
	 * Block-centre to the nearest car point. 2.5 covers the block the train is in and one block to the side, which is
	 * where a loader sits next to the rail.
	 */
	public static final double REACH = 2.5;

	private TrainProximity() {
	}

	/**
	 * The stopped train within {@code reach} of the block centre. Equal distances take the smaller vehicle id so two
	 * trains in the same block do not swap which one is served from tick to tick.
	 */
	public static StoppedTrain closest(List<StoppedTrain> trains, double x, double y, double z, double reach) {
		StoppedTrain best = null;
		double bestDistance = reach;
		if (trains == null) {
			return null;
		}
		for (final StoppedTrain train : trains) {
			final double distance = train.distanceTo(x, y, z);
			if (distance > reach) {
				continue;
			}
			if (best == null || distance < bestDistance - 1.0E-6 || (Math.abs(distance - bestDistance) <= 1.0E-6 && train.id() < best.id())) {
				best = train;
				bestDistance = distance;
			}
		}
		return best;
	}
}
