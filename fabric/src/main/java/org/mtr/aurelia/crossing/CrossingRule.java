package org.mtr.aurelia.crossing;

import org.mtr.mod.cargo.CrossingTrain;

import javax.annotation.Nullable;
import java.util.List;

/**
 * When a level crossing closes, from the trains MTR's simulation has ({@link CrossingTrain}).
 * <ul>
 *   <li>A train on the crossing or near it ({@link #ON_CROSSING}) closes it, moving or not.</li>
 *   <li>A moving train closes it when its path comes within {@link #ON_CROSSING} of the crossing and, at its speed and
 *       acceleration, it gets there within {@link #WARNING_SECONDS}: time for the arm to come down and stay down a while
 *       before the train. A train on another line, whose path never comes near, and a train standing at a platform
 *       further off do not close it.</li>
 * </ul>
 */
public final class CrossingRule {

	/** Blocks across the ground: a train this close closes the crossing whichever way it goes. */
	static final double ON_CROSSING = 12;
	/** Blocks up or down: trains on a bridge over the crossing or in a tunnel under it don't count. */
	static final double VERTICAL = 8;
	/** Seconds the arm takes to come down ({@link LevelCrossingBarrierBlockEntity#SWING_MILLIS}). */
	static final double ARM_SECONDS = LevelCrossingBarrierBlockEntity.SWING_MILLIS / 1000.0;
	/** Seconds a crossing can be late seeing a train: the simulation is read every 8 ticks, an open crossing looks every 10. */
	static final double LATE_SECONDS = 1;
	/** Seconds the arm is down before the train arrives. */
	static final double DOWN_SECONDS = 3;
	/** A train that gets to the crossing within this many seconds closes it. */
	static final double WARNING_SECONDS = ARM_SECONDS + LATE_SECONDS + DOWN_SECONDS;
	/** With no speed or path to read, a moving train heading this way closes the crossing from this far (blocks). */
	static final double UNKNOWN_APPROACH = 200;

	private CrossingRule() {
	}

	/**
	 * Whether a crossing at {@code x, y, z}, now {@code closedNow}, should be closed: {@code powered} by redstone, or closed
	 * by {@code trains}. With {@code trains} null (no fresh read of the simulation) it stays as it is: it fails safe.
	 */
	public static boolean closed(boolean powered, @Nullable List<CrossingTrain> trains, double x, double y, double z, boolean closedNow) {
		if (powered) {
			return true;
		}
		return trains == null ? closedNow : closes(trains, x, y, z);
	}

	/** Whether {@code trains} close a crossing at {@code x, y, z} (the block's centre). */
	public static boolean closes(List<CrossingTrain> trains, double x, double y, double z) {
		for (final CrossingTrain train : trains) {
			if (closesFor(train, x, y, z)) {
				return true;
			}
		}
		return false;
	}

	static boolean closesFor(CrossingTrain train, double x, double y, double z) {
		double nearest = Double.MAX_VALUE;
		double head = Double.MAX_VALUE;
		for (int i = 0; i < train.points().size(); i++) {
			final double distance = distance(train.points().get(i), x, y, z);
			nearest = Math.min(nearest, distance);
			if (i == 0) {
				head = distance;
			}
		}
		if (nearest <= ON_CROSSING) {
			return true;
		}
		if (!train.moving()) {
			return false;
		}
		if (Double.isInfinite(train.speed())) {
			// the speed and path could not be read: the old rule, wider, so a crossing still closes in time
			return nearest <= UNKNOWN_APPROACH && head <= nearest + 0.5;
		}
		final List<double[]> ahead = train.ahead();
		if (ahead.isEmpty() || head - ahead.get(ahead.size() - 1)[3] > ON_CROSSING) {
			return false; // the path ahead is shorter than the way there in a straight line
		}
		for (final double[] point : ahead) {
			if (distance(point, x, y, z) <= ON_CROSSING) {
				return seconds(point[3], train.speed(), train.acceleration()) <= WARNING_SECONDS;
			}
		}
		return false;
	}

	/** Seconds to cover {@code distance} blocks from {@code speed} at {@code acceleration} (no top speed: never later than real). */
	static double seconds(double distance, double speed, double acceleration) {
		if (acceleration > 1E-9) {
			return (Math.sqrt(speed * speed + 2 * acceleration * distance) - speed) / acceleration;
		}
		return speed > 1E-9 ? distance / speed : Double.POSITIVE_INFINITY;
	}

	/** Blocks across the ground from {@code point} to the crossing, or infinity when it is too far up or down. */
	private static double distance(double[] point, double x, double y, double z) {
		if (point == null || point.length < 3 || Math.abs(point[1] - y) > VERTICAL) {
			return Double.POSITIVE_INFINITY;
		}
		return Math.hypot(point[0] - x, point[2] - z);
	}
}
