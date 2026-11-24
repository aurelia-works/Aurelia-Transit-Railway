package org.mtr.mod.engine;

import org.mtr.core.data.Route;
import org.mtr.core.data.RouteType;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Express trains go first at junctions. Each express train claims the track it will cover in the next
 * {@link #LOOK_AHEAD_MILLIS}; a slower train that wants a signal block on that track gives way until the express is
 * through. Two rules keep trains from locking each other:
 * <ul>
 * <li>a train standing on track the express has claimed is in front of it and never gives way;</li>
 * <li>a train gives way for at most {@link #MAX_YIELD_MILLIS} in one go.</li>
 * </ul>
 * Express means a line of MTR's "High Speed" route type, or a line the signaller picked in the Engine tab. Claims are
 * written and read from MTR's simulation threads, one per dimension, so they sit in a concurrent map.
 */
public final class JunctionPriority {

	/** How far ahead an express train claims track, as travel time at its current speed. */
	public static final long LOOK_AHEAD_MILLIS = 20_000;
	/** The least track an express claims, so one waiting at a red signal still claims the junction past it. */
	public static final double MIN_LOOK_AHEAD_METERS = 150;
	/** An express standing at a platform with longer than this left to wait claims nothing yet. */
	public static final long DWELL_CLAIM_MILLIS = 10_000;
	/** The longest a train gives way in one go. */
	public static final long MAX_YIELD_MILLIS = 60_000;
	/** A claim not renewed for this long belongs to a train that is gone. Long enough to ride out a jittered renewal or
	 * one slow server tick, because a claim that lapses for a single ask would release a waiting train for good. */
	private static final long CLAIM_LIFE_MILLIS = 2_500;

	private static final Map<Long, Claim> CLAIMS = new ConcurrentHashMap<>();
	/** Route ids the signaller lets go first, saved with the world. Replaced, never changed in place. */
	private static volatile Set<Long> signallerRoutes = Set.of();

	private JunctionPriority() {
	}

	public static boolean isPriority(Route route) {
		return route != null && (isExpress(route) || signallerRoutes.contains(route.getId()));
	}

	/** A line of MTR's "High Speed" route type: always goes first. */
	public static boolean isExpress(Route route) {
		return route.getRouteType() == RouteType.HIGH_SPEED;
	}

	public static Set<Long> signallerRoutes() {
		return signallerRoutes;
	}

	public static void setSignallerRoutes(Collection<Long> routeIds) {
		signallerRoutes = Set.copyOf(routeIds);
	}

	public static double lookAheadMeters(double speedMetersPerMillisecond) {
		return Math.max(MIN_LOOK_AHEAD_METERS, speedMetersPerMillisecond * LOOK_AHEAD_MILLIS);
	}

	public static void claim(long vehicleId, Set<?> rails, long now) {
		CLAIMS.put(vehicleId, new Claim(rails, now));
	}

	public static void release(long vehicleId) {
		CLAIMS.remove(vehicleId);
	}

	/**
	 * @param blockRails the rails of the signal block the train wants next
	 * @param ownRail    the rail the train's front is on now
	 * @return whether another train's live claim covers that block while the asking train is not standing on the claim
	 */
	public static boolean claimedByOther(long vehicleId, Collection<?> blockRails, Object ownRail, long now) {
		for (final Map.Entry<Long, Claim> entry : CLAIMS.entrySet()) {
			final Claim claim = entry.getValue();
			if (now - claim.stamp > CLAIM_LIFE_MILLIS) {
				CLAIMS.remove(entry.getKey(), claim);
			} else if (entry.getKey() != vehicleId && !claim.rails.contains(ownRail)) {
				for (final Object rail : blockRails) {
					if (claim.rails.contains(rail)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static void clear() {
		CLAIMS.clear();
		signallerRoutes = Set.of();
	}

	private record Claim(Set<?> rails, long stamp) {
	}
}
