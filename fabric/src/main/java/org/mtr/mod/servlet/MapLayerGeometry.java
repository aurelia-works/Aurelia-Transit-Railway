package org.mtr.mod.servlet;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;

import java.util.*;

/**
 * Shapes for the map layer: rails sampled into points, and the cheapest way along the rail graph between two platforms.
 */
public final class MapLayerGeometry {

	private MapLayerGeometry() {
	}

	/**
	 * Points along a rail every {@code step} blocks (both ends included), starting at the end nearest to {@code from}.
	 * Each point is {@code {x, y, z}}.
	 */
	public static List<double[]> sampleRail(Rail rail, Position from, double step) {
		final double length = rail.railMath.getLength();
		final Vector start = rail.railMath.getPosition(0, false);
		final Vector end = rail.railMath.getPosition(length, false);
		final boolean reversed = distanceSquared(end, from) < distanceSquared(start, from);
		final int segments = Math.max(1, (int) Math.ceil(length / step));
		final List<double[]> points = new ArrayList<>(segments + 1);
		for (int i = 0; i <= segments; i++) {
			final double distance = length * i / segments;
			final Vector vector = rail.railMath.getPosition(reversed ? length - distance : distance, false);
			points.add(new double[]{vector.x, vector.y, vector.z});
		}
		return points;
	}

	/**
	 * Dijkstra from any of {@code starts} to the nearest of {@code goals}.
	 *
	 * @param maxVisits stop after settling this many nodes, so a disconnected network costs a bounded amount
	 * @return the nodes from a start to a goal, or an empty list when none is reachable within {@code maxVisits}
	 */
	public static <T> List<T> shortestPath(Map<T, ? extends Map<T, Double>> graph, Collection<T> starts, Collection<T> goals, int maxVisits) {
		final Map<T, Double> distances = new HashMap<>();
		final Map<T, T> previous = new HashMap<>();
		final PriorityQueue<Map.Entry<T, Double>> queue = new PriorityQueue<>(Map.Entry.comparingByValue());
		final Set<T> goalSet = new HashSet<>(goals);
		final Set<T> settled = new HashSet<>();

		for (final T start : starts) {
			distances.put(start, 0D);
			queue.add(new AbstractMap.SimpleImmutableEntry<>(start, 0D));
		}

		while (!queue.isEmpty() && settled.size() < maxVisits) {
			final Map.Entry<T, Double> entry = queue.poll();
			final T node = entry.getKey();
			if (!settled.add(node)) {
				continue;
			}

			if (goalSet.contains(node)) {
				final LinkedList<T> path = new LinkedList<>();
				for (T step = node; step != null; step = previous.get(step)) {
					path.addFirst(step);
				}
				return path;
			}

			final Map<T, Double> neighbours = graph.get(node);
			if (neighbours != null) {
				neighbours.forEach((neighbour, cost) -> {
					final double distance = entry.getValue() + cost;
					if (!settled.contains(neighbour) && distance < distances.getOrDefault(neighbour, Double.MAX_VALUE)) {
						distances.put(neighbour, distance);
						previous.put(neighbour, node);
						queue.add(new AbstractMap.SimpleImmutableEntry<>(neighbour, distance));
					}
				});
			}
		}

		return Collections.emptyList();
	}

	private static double distanceSquared(Vector vector, Position position) {
		final double x = vector.x - position.getX();
		final double y = vector.y - position.getY();
		final double z = vector.z - position.getZ();
		return x * x + y * y + z * z;
	}
}
