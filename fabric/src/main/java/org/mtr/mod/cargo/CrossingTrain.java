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
 */
public record CrossingTrain(List<double[]> points, boolean moving, double speed, double acceleration, List<double[]> ahead) {

	public CrossingTrain {
		points = List.copyOf(points);
		ahead = List.copyOf(ahead);
	}
}
