package org.mtr.mod.passenger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where passengers can get to from each station, changing trains up to a few times, and which train they take first.
 * Built from the routes: each platform reaches the stations after it on its route. A trip with fewer changes always
 * wins. With no changes allowed, every passenger rides one train, as before.
 */
public final class PassengerRoutes {

	/** The first train of a trip: wait at this platform and get off at this station. */
	public record Leg(long platformId, long stationId) {
	}

	/** The most changes a trip may have when changing is on. */
	public static final int MAX_CHANGES = 2;
	public static final PassengerRoutes NONE = new PassengerRoutes(Map.of(), Map.of());

	private final Map<Long, Map<Long, Leg>> legsByStation;
	private final Map<Long, long[]> destinationsByPlatform;

	private PassengerRoutes(Map<Long, Map<Long, Leg>> legsByStation, Map<Long, long[]> destinationsByPlatform) {
		this.legsByStation = legsByStation;
		this.destinationsByPlatform = destinationsByPlatform;
	}

	/**
	 * @param stationOfPlatform platform id to its station id
	 * @param directByPlatform  platform id to the stations after it on its route
	 * @param maxChanges        0 for one train only
	 */
	public static PassengerRoutes build(Map<Long, Long> stationOfPlatform, Map<Long, long[]> directByPlatform, int maxChanges) {
		final Map<Long, List<Long>> platformsAt = new HashMap<>();
		directByPlatform.keySet().forEach(platformId -> {
			final Long stationId = stationOfPlatform.get(platformId);
			if (stationId != null) {
				platformsAt.computeIfAbsent(stationId, id -> new ArrayList<>()).add(platformId);
			}
		});

		final Map<Long, Map<Long, Leg>> legsByStation = new HashMap<>();
		final Map<Long, List<Long>> destinations = new HashMap<>();
		platformsAt.forEach((start, startPlatforms) -> {
			final Map<Long, Leg> legs = new HashMap<>();
			// breadth first, so the trip with the fewest changes is found first
			final ArrayDeque<Object[]> queue = new ArrayDeque<>();
			for (final long platformId : startPlatforms) {
				for (final long stationId : directByPlatform.get(platformId)) {
					if (stationId != start && !legs.containsKey(stationId)) {
						final Leg leg = new Leg(platformId, stationId);
						legs.put(stationId, leg);
						queue.add(new Object[]{stationId, leg, 0});
					}
				}
			}
			while (!queue.isEmpty()) {
				final Object[] entry = queue.poll();
				final int changes = (int) entry[2];
				if (changes >= maxChanges) {
					continue;
				}
				for (final long platformId : platformsAt.getOrDefault((Long) entry[0], List.of())) {
					for (final long stationId : directByPlatform.get(platformId)) {
						if (stationId != start && !legs.containsKey(stationId)) {
							legs.put(stationId, (Leg) entry[1]);
							queue.add(new Object[]{stationId, entry[1], changes + 1});
						}
					}
				}
			}
			legsByStation.put(start, legs);
			legs.forEach((destination, leg) -> destinations.computeIfAbsent(leg.platformId(), id -> new ArrayList<>()).add(destination));
		});

		final Map<Long, long[]> destinationsByPlatform = new HashMap<>();
		destinations.forEach((platformId, list) -> destinationsByPlatform.put(platformId, list.stream().mapToLong(Long::longValue).toArray()));
		return new PassengerRoutes(legsByStation, destinationsByPlatform);
	}

	/** Every station a passenger starting at this platform can get to; their first train leaves from here. */
	public long[] destinations(long platformId) {
		return destinationsByPlatform.getOrDefault(platformId, new long[0]);
	}

	/** The next train from this station towards the destination, or null when there is no way there. */
	public Leg leg(long stationId, long destinationStationId) {
		final Map<Long, Leg> legs = legsByStation.get(stationId);
		return legs == null ? null : legs.get(destinationStationId);
	}

	public Map<Long, long[]> destinationsByPlatform() {
		return destinationsByPlatform;
	}
}
