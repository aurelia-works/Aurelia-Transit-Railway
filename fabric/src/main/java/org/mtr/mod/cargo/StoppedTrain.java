package org.mtr.mod.cargo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A train whose speed is zero, plus the points along it a cargo block can reach. Distance is Chebyshev (the sensor
 * cube), so a block beside the track matches the same way a redstone sensor matches the head.
 */
public final class StoppedTrain {

	private final long id;
	private final long routeId;
	private final int cars;
	private final List<double[]> points;

	public StoppedTrain(long id, long routeId, int cars, List<double[]> points) {
		this.id = id;
		this.routeId = routeId;
		this.cars = cars;
		final List<double[]> copy = new ArrayList<>();
		if (points != null) {
			for (final double[] point : points) {
				if (point != null && point.length >= 3) {
					copy.add(new double[]{point[0], point[1], point[2]});
				}
			}
		}
		this.points = Collections.unmodifiableList(copy);
	}

	public long id() {
		return id;
	}

	public long routeId() {
		return routeId;
	}

	public int cars() {
		return cars;
	}

	public double distanceTo(double x, double y, double z) {
		double best = Double.POSITIVE_INFINITY;
		for (final double[] point : points) {
			final double distance = Math.max(Math.abs(point[0] - x), Math.max(Math.abs(point[1] - y), Math.abs(point[2] - z)));
			if (distance < best) {
				best = distance;
			}
		}
		return best;
	}
}
