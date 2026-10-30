package org.mtr.mod.data;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Corrections for the airplane path shipped in Transport Simulation Core.
 *
 * <p>Two separate mistakes show up at an airport. A repeating route appends an empty sentinel whose
 * coordinates are the world origin, and the vehicle stops exactly on that sentinel, so it is drawn at
 * the origin. Separately, a height lock written when airplane dummy rails were the only rails at
 * 900 km/h now matches the 300 km/h runway as well, and it flattens every bogie onto the nose height.
 * The tail drops below the glideslope. The renderer then pitches the body on the way down even though
 * {@link TransportMode#hasPitchDescending} is false for airplanes.</p>
 */
public final class AirplanePath {

	private static final double LEVEL_EPSILON = 0.05;
	/** Cruise used when a fresh sentinel has not been given a speed yet. A limit of 0 reads as 1 km/h and the airplane brakes for it. */
	private static final long UNSET_SENTINEL_SPEED = 300;

	private AirplanePath() {
	}

	/** The corrections here are for airplanes only. Trains and boats keep the core's own positions. */
	public static boolean appliesTo(TransportMode mode) {
		return mode == TransportMode.AIRPLANE;
	}

	/**
	 * A repeat sentinel is the segment whose end distance is unbounded. The core builds it from an
	 * empty JSON object, so both of its positions are the origin. Anchor it to the end of the previous
	 * segment and give it that segment's speed, so sampling it stays on the arrival platform and the
	 * approach does not treat the repeat boundary as a 1 km/h rail.
	 */
	public static void anchorRepeatSentinel(List<PathData> path) {
		if (path == null || path.size() < 2) {
			return;
		}
		final int last = path.size() - 1;
		final PathData sentinel = path.get(last);
		if (!(sentinel.getEndDistance() > Double.MAX_VALUE / 2)) {
			return;
		}
		final PathData arrival = path.get(last - 1);
		final double span = Math.max(0, arrival.getEndDistance() - arrival.getStartDistance());
		final Vector end = arrival.getPosition(span);
		final Vector sentinelStart = sentinel.getPosition(0);
		if (end == null || sentinelStart == null || Double.isNaN(end.x()) || close(sentinelStart, end)) {
			return;
		}
		final long speed = speedLimit(sentinel) > 0 ? speedLimit(sentinel) : (speedLimit(arrival) > 0 ? speedLimit(arrival) : UNSET_SENTINEL_SPEED);
		final Position position = new Position(Math.round(end.x() - 0.5), Math.round(end.y()), Math.round(end.z() - 0.5));
		final PathData anchored = new PathData(null, 0, 0, arrival.getStopIndex(), arrival.getEndDistance(), Double.MAX_VALUE, position, Angle.E, position, Angle.E);
		setSpeedLimit(anchored, speed);
		path.set(last, anchored);
	}

	/**
	 * Where a bogie should be drawn. This is the path position, with one exception: a repeat sentinel
	 * that still sits at the origin is reported at the end of the previous segment. Callers that used
	 * the flattened airplane height must use this instead, or the tail stays pinned to the nose.
	 */
	public static Vector sampledPosition(List<PathData> path, int index, double distanceOnSegment) {
		if (path == null || index < 0 || index >= path.size()) {
			return null;
		}
		final PathData segment = path.get(index);
		final Vector position = segment.getPosition(distanceOnSegment);
		if (position == null || index == 0 || !(segment.getEndDistance() > Double.MAX_VALUE / 2)) {
			return position;
		}
		final PathData arrival = path.get(index - 1);
		final double span = Math.max(0, arrival.getEndDistance() - arrival.getStartDistance());
		final Vector end = arrival.getPosition(span);
		if (end == null || Double.isNaN(end.x()) || close(position, end)) {
			return position;
		}
		return end;
	}

	/**
	 * Speed to keep for this segment, or {@code 0} when the cached speed should be left alone.
	 * Platforms and sidings cannot accelerate, so the cache copies the neighbouring runway's
	 * 300 km/h onto them and an airplane never brakes for the platform. Runways and the synthetic
	 * flight rails are left at the cached speed. Trains keep the inherited speed on purpose.
	 */
	public static long savedRailSpeed(PathData segment) {
		if (segment == null) {
			return 0;
		}
		final Rail rail = segment.getRail();
		if (!rail.isPlatform() && !rail.isSiding()) {
			return 0;
		}
		return Math.max(rail.getSpeedLimitKilometersPerHour(false), rail.getSpeedLimitKilometersPerHour(true));
	}

	/** Put platform and siding limits back after {@code PathData.writePathCache} has copied the runway speed onto them. */
	public static void restoreSavedRailSpeeds(List<PathData> path) {
		if (path == null) {
			return;
		}
		for (int i = 0; i < path.size(); i++) {
			final long ownSpeed = savedRailSpeed(path.get(i));
			if (ownSpeed > 0) {
				setSpeedLimit(path.get(i), ownSpeed);
			}
		}
	}

	/**
	 * Whether the car body should pitch. Modes that pitch both ways are unchanged. An airplane pitches
	 * while climbing and stays level while descending, which is what {@link TransportMode#hasPitchDescending}
	 * already says. The bogies still follow the rail.
	 *
	 * @param noseY height of the front bogie
	 * @param tailY height of the rear bogie
	 */
	public static boolean allowBodyPitch(TransportMode mode, double noseY, double tailY) {
		if (mode.hasPitchAscending && mode.hasPitchDescending) {
			return true;
		}
		final double rise = noseY - tailY;
		if (rise > LEVEL_EPSILON) {
			return mode.hasPitchAscending;
		}
		if (rise < -LEVEL_EPSILON) {
			return mode.hasPitchDescending;
		}
		return false;
	}

	private static boolean close(Vector a, Vector b) {
		return Math.abs(a.x() - b.x()) < 1 && Math.abs(a.y() - b.y()) < 1 && Math.abs(a.z() - b.z()) < 1;
	}

	private static long speedLimit(PathData pathData) {
		try {
			return speedField(pathData).getLong(pathData);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void setSpeedLimit(PathData pathData, long speed) {
		try {
			speedField(pathData).setLong(pathData, speed);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	/** {@code speedLimit} lives on the generated schema and is not visible from this package. */
	private static Field speedField(PathData pathData) {
		Class<?> type = pathData.getClass();
		while (type != null) {
			try {
				final Field field = type.getDeclaredField("speedLimit");
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException e) {
				type = type.getSuperclass();
			}
		}
		throw new IllegalStateException("PathData.speedLimit");
	}
}
