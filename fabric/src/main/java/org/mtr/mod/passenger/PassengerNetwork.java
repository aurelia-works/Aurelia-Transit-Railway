package org.mtr.mod.passenger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Passengers as plain records, not mobs: they appear on platforms, wait, board a stopped train going to their station,
 * ride, and leave at that station. One network per dimension, touched only on MTR's simulation thread.
 */
public final class PassengerNetwork {

	/**
	 * Server caps, set from the Aurelia panel.
	 *
	 * @param perPlatformPerMinute  new passengers per platform per minute
	 * @param maxWaitingPerPlatform a platform never holds more than this
	 * @param maxTotal              the whole dimension never holds more than this, waiting and riding together
	 * @param seatsPerCar           how many fit in one car
	 */
	public record Settings(boolean enabled, int perPlatformPerMinute, int maxWaitingPerPlatform, int maxTotal, int seatsPerCar) {

		/** Off until an operator switches it on, so an old world runs exactly as before. */
		public static final Settings DEFAULT = new Settings(false, 6, 40, 4000, 40);
		public static final int MAX_PER_MINUTE = 60;
		public static final int MAX_WAITING = 200;
		public static final int MAX_TOTAL = 20000;
		public static final int MAX_SEATS = 200;

		public Settings clamped() {
			return new Settings(enabled, clamp(perPlatformPerMinute, MAX_PER_MINUTE), clamp(maxWaitingPerPlatform, MAX_WAITING), clamp(maxTotal, MAX_TOTAL), Math.max(1, clamp(seatsPerCar, MAX_SEATS)));
		}

		private static int clamp(int value, int max) {
			return Math.max(0, Math.min(max, value));
		}
	}

	public record Passenger(long destinationStationId, long spawnMillis) {
	}

	public record Exchange(int alighted, int boarded) {
	}

	/** A passenger who waits this long gives up and goes home. */
	public static final long GIVE_UP_MILLIS = 10 * 60_000;
	/** Passengers through the doors per car per exchange (one exchange a second), so boarding takes a while. */
	public static final int MOVES_PER_CAR = 3;

	private final Map<Long, ArrayDeque<Passenger>> waiting = new HashMap<>();
	private final Map<Long, List<Passenger>> riding = new HashMap<>();
	private final Map<Long, Double> spawnCarry = new HashMap<>();
	private int total;
	private long boardedTotal;
	private long alightedTotal;

	/**
	 * Adds the passengers that have arrived at a platform over {@code minutes}, each heading for a station a train from
	 * here can reach. A platform no route serves gets nobody.
	 */
	public void spawn(long platformId, long[] destinations, double minutes, Settings settings, long now, Random random) {
		if (destinations.length == 0 || settings.perPlatformPerMinute() <= 0) {
			return;
		}
		double carry = spawnCarry.getOrDefault(platformId, 0D) + settings.perPlatformPerMinute() * minutes;
		final ArrayDeque<Passenger> queue = waiting.computeIfAbsent(platformId, id -> new ArrayDeque<>());
		while (carry >= 1) {
			carry--;
			if (queue.size() >= settings.maxWaitingPerPlatform() || total >= settings.maxTotal()) {
				carry = 0;
				break;
			}
			queue.add(new Passenger(destinations[random.nextInt(destinations.length)], now));
			total++;
		}
		spawnCarry.put(platformId, carry);
	}

	/**
	 * A train stopped at a platform: riders for this station (or for a station the train no longer goes to) get off,
	 * then waiting passengers whose station is ahead get on, until the train is full. A few per car each call.
	 *
	 * @param upcomingStations the stations this train still stops at after this one
	 */
	public Exchange exchange(long vehicleId, long platformId, long stationId, Set<Long> upcomingStations, int cars, Settings settings) {
		final List<Passenger> onboard = riding.computeIfAbsent(vehicleId, id -> new ArrayList<>());
		int moves = Math.max(1, cars) * MOVES_PER_CAR;
		int alighted = 0;
		final Iterator<Passenger> riders = onboard.iterator();
		while (riders.hasNext() && moves > 0) {
			final Passenger passenger = riders.next();
			if (passenger.destinationStationId() == stationId || !upcomingStations.contains(passenger.destinationStationId())) {
				riders.remove();
				alighted++;
				moves--;
			}
		}
		total -= alighted;
		alightedTotal += alighted;

		int boarded = 0;
		final ArrayDeque<Passenger> queue = waiting.get(platformId);
		final int capacity = Math.max(1, cars) * settings.seatsPerCar();
		if (queue != null) {
			final Iterator<Passenger> waiters = queue.iterator();
			while (waiters.hasNext() && moves > 0 && onboard.size() < capacity) {
				final Passenger passenger = waiters.next();
				if (passenger.destinationStationId() != stationId && upcomingStations.contains(passenger.destinationStationId())) {
					waiters.remove();
					onboard.add(passenger);
					boarded++;
					moves--;
				}
			}
		}
		boardedTotal += boarded;
		return new Exchange(alighted, boarded);
	}

	/** Waiting passengers who have given up go home. */
	public void expire(long now) {
		for (final ArrayDeque<Passenger> queue : waiting.values()) {
			while (!queue.isEmpty() && now - queue.peekFirst().spawnMillis() > GIVE_UP_MILLIS) {
				queue.pollFirst();
				total--;
			}
		}
	}

	/** Trains and platforms that no longer exist take their passengers with them. */
	public void retain(Set<Long> vehicleIds, Set<Long> platformIds) {
		riding.entrySet().removeIf(entry -> {
			if (vehicleIds.contains(entry.getKey())) {
				return false;
			}
			total -= entry.getValue().size();
			return true;
		});
		waiting.entrySet().removeIf(entry -> {
			if (platformIds.contains(entry.getKey())) {
				return false;
			}
			total -= entry.getValue().size();
			spawnCarry.remove(entry.getKey());
			return true;
		});
	}

	public void clear() {
		waiting.clear();
		riding.clear();
		spawnCarry.clear();
		total = 0;
	}

	public int waitingAt(long platformId) {
		final ArrayDeque<Passenger> queue = waiting.get(platformId);
		return queue == null ? 0 : queue.size();
	}

	public int ridingOn(long vehicleId) {
		final List<Passenger> onboard = riding.get(vehicleId);
		return onboard == null ? 0 : onboard.size();
	}

	public int total() {
		return total;
	}

	public int waitingTotal() {
		int count = 0;
		for (final ArrayDeque<Passenger> queue : waiting.values()) {
			count += queue.size();
		}
		return count;
	}

	public long boardedTotal() {
		return boardedTotal;
	}

	public long alightedTotal() {
		return alightedTotal;
	}
}
