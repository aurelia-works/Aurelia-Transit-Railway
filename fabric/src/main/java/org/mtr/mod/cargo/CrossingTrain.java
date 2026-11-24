package org.mtr.mod.cargo;

import java.util.List;

/**
 * One train as a level crossing sees it.
 *
 * @param points       where it is now: its head, then its bogies, each {@code x, y, z}
 * @param moving       MTR says it is moving (a train standing at a platform is not)
 * @param speed        blocks per second
 * @param acceleration its acceleration, blocks per second squared
 * @param ahead        where it will go: points along its path from the head, each {@code x, y, z, distance along the path}
 * @param departsIn    for a standing train, seconds until it leaves its stop; {@link Double#POSITIVE_INFINITY} when it is
 *                     not on a route (parked), {@link Double#NaN} when it stands on its route but not at a stop (a red
 *                     signal: no telling when it goes); 0 for a moving train
 */
public record CrossingTrain(List<double[]> points, boolean moving, double speed, double acceleration, List<double[]> ahead, double departsIn) {

	public CrossingTrain {
		points = List.copyOf(points);
		ahead = List.copyOf(ahead);
	}
}
