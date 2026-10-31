package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.transit.ServiceSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A-to-B over a {@link SystemMap}. Stations match on a trimmed lower-case name. Each map line is ridden as one leg;
 * changing line costs one transfer. The fare is {@code baseFare + perTransfer * transfers} when a path exists, else 0.
 * That number is what the terminal shows; it does not read MTR's balance.
 */
public final class JourneyPlanner {

	public record Leg(String line, int color, List<String> stops) {
		public Leg {
			stops = List.copyOf(stops);
		}
	}

	public record Journey(List<Leg> legs, int transfers, int fare) {
		public static final Journey NONE = new Journey(List.of(), 0, 0);

		public Journey {
			legs = List.copyOf(legs);
		}

		public boolean found() {
			return !legs.isEmpty();
		}
	}

	private JourneyPlanner() {
	}

	public static Journey plan(SystemMap map, String from, String to, int baseFare, int perTransfer) {
		if (map == null || map.lines().isEmpty()) {
			return Journey.NONE;
		}
		final String start = key(from);
		final String goal = key(to);
		if (start.isEmpty() || goal.isEmpty()) {
			return Journey.NONE;
		}
		if (start.equals(goal)) {
			return new Journey(List.of(), 0, Math.max(0, baseFare));
		}
		// Dijkstra over (station, line) so a transfer is an explicit cost.
		final Map<String, Integer> best = new HashMap<>();
		final List<Node> queue = new ArrayList<>();
		for (final MapLine line : map.lines()) {
			if (contains(line, start)) {
				queue.add(new Node(0, line, indexOf(line, start)));
				best.put(state(start, line), 0);
			}
		}
		Node chosen = null;
		while (!queue.isEmpty()) {
			int pick = 0;
			for (int i = 1; i < queue.size(); i++) {
				if (queue.get(i).cost < queue.get(pick).cost) {
					pick = i;
				}
			}
			final Node node = queue.remove(pick);
			if (key(node.line.stops().get(node.index).name()).equals(goal)) {
				chosen = node;
				break;
			}
			final MapStop here = node.line.stops().get(node.index);
			for (int step = -1; step <= 1; step += 2) {
				final int next = node.index + step;
				if (next < 0 || next >= node.line.stops().size()) {
					continue;
				}
				offer(queue, best, node, node.line, next, node.cost + 1);
			}
			final String hereKey = key(here.name());
			for (final MapLine other : map.lines()) {
				if (other == node.line) {
					continue;
				}
				final int at = indexOf(other, hereKey);
				if (at >= 0) {
					offer(queue, best, node, other, at, node.cost + 4);
				}
			}
		}
		if (chosen == null) {
			return Journey.NONE;
		}
		final List<Leg> legs = new ArrayList<>();
		Node cursor = chosen;
		List<String> run = new ArrayList<>();
		MapLine line = chosen.line;
		while (cursor != null) {
			run.add(0, cursor.line.stops().get(cursor.index).name());
			if (cursor.previous == null || cursor.previous.line != line) {
				if (run.size() > 1 || cursor.previous == null) {
					legs.add(0, new Leg(label(line), line.badge() == null ? 0 : line.badge().rgb(), run));
				}
				run = new ArrayList<>();
				if (cursor.previous != null) {
					run.add(cursor.line.stops().get(cursor.index).name());
					line = cursor.previous.line;
				}
			}
			cursor = cursor.previous;
		}
		final int transfers = Math.max(0, legs.size() - 1);
		final int fare = legs.isEmpty() ? 0 : Math.max(0, baseFare) + Math.max(0, perTransfer) * transfers;
		return new Journey(legs, transfers, fare);
	}

	/** The soonest departure whose route number or name contains the leg's line label. */
	public static ServiceSnapshot nextDeparture(List<ServiceSnapshot> services, String lineLabel, long now) {
		final List<ServiceSnapshot> upcoming = TerminalDepartures.select(
				new com.aureliatransit.architecture.transit.StationSnapshot(null, List.of(), services == null ? List.of() : services, 0), now, Integer.MAX_VALUE);
		ServiceSnapshot best = null;
		final String want = key(lineLabel);
		for (final ServiceSnapshot service : upcoming) {
			final String number = key(service.routeNumber());
			final String name = key(service.routeName());
			if (want.isEmpty() || number.contains(want) || name.contains(want) || want.contains(number) && !number.isEmpty()) {
				if (best == null || service.departureMillis() < best.departureMillis()) {
					best = service;
				}
			}
		}
		return best;
	}

	private static void offer(List<Node> queue, Map<String, Integer> best, Node from, MapLine line, int index, int cost) {
		final String state = state(key(line.stops().get(index).name()), line);
		final Integer known = best.get(state);
		if (known != null && known <= cost) {
			return;
		}
		best.put(state, cost);
		queue.add(new Node(cost, line, index, from));
	}

	private static String state(String station, MapLine line) {
		return station + "#" + line.id();
	}

	private static boolean contains(MapLine line, String station) {
		return indexOf(line, station) >= 0;
	}

	private static int indexOf(MapLine line, String station) {
		for (int i = 0; i < line.stops().size(); i++) {
			if (key(line.stops().get(i).name()).equals(station)) {
				return i;
			}
		}
		return -1;
	}

	private static String label(MapLine line) {
		if (line.badge() != null && !line.badge().label().isBlank()) {
			return line.badge().label();
		}
		return line.name() == null ? "" : line.name();
	}

	static String key(String name) {
		return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
	}

	private static final class Node {
		final int cost;
		final MapLine line;
		final int index;
		final Node previous;

		Node(int cost, MapLine line, int index) {
			this(cost, line, index, null);
		}

		Node(int cost, MapLine line, int index, Node previous) {
			this.cost = cost;
			this.line = line;
			this.index = index;
			this.previous = previous;
		}
	}
}
