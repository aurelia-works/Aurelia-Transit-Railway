package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.servlet.MapLayerGeometry;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MapLayerGeometryTest {

	@Test
	public void straightRailIsSampledFromTheGivenEnd() {
		final Position west = new Position(0, 64, 0);
		final Position east = new Position(100, 64, 0);
		final Rail rail = Rail.newRail(west, Angle.E, east, Angle.W, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 80, 80, false, false, true, false, false, TransportMode.TRAIN);

		final List<double[]> fromWest = MapLayerGeometry.sampleRail(rail, west, 10);
		Assertions.assertEquals(11, fromWest.size());
		Assertions.assertEquals(0, fromWest.get(0)[0], 0.6);
		Assertions.assertEquals(100, fromWest.get(10)[0], 0.6);
		Assertions.assertEquals(0, fromWest.get(5)[2], 0.6);

		final List<double[]> fromEast = MapLayerGeometry.sampleRail(rail, east, 10);
		Assertions.assertEquals(100, fromEast.get(0)[0], 0.6);
		Assertions.assertEquals(0, fromEast.get(10)[0], 0.6);
	}

	@Test
	public void shortestPathTakesTheCheaperBranch() {
		// a -> b -> d costs 2, a -> c -> d costs 10
		final Map<String, Map<String, Double>> graph = new HashMap<>();
		edge(graph, "a", "b", 1);
		edge(graph, "b", "d", 1);
		edge(graph, "a", "c", 5);
		edge(graph, "c", "d", 5);
		Assertions.assertEquals(Arrays.asList("a", "b", "d"), MapLayerGeometry.shortestPath(graph, Collections.singletonList("a"), Collections.singletonList("d"), 100));
	}

	@Test
	public void shortestPathStartsFromTheNearestOfSeveralStarts() {
		final Map<String, Map<String, Double>> graph = new HashMap<>();
		edge(graph, "p1", "p2", 4);
		edge(graph, "p2", "x", 1);
		edge(graph, "x", "q1", 1);
		edge(graph, "q1", "q2", 4);
		Assertions.assertEquals(Arrays.asList("p2", "x", "q1"), MapLayerGeometry.shortestPath(graph, Arrays.asList("p1", "p2"), Arrays.asList("q1", "q2"), 100));
	}

	@Test
	public void unreachableOrTooFarGivesAnEmptyPath() {
		final Map<String, Map<String, Double>> graph = new HashMap<>();
		edge(graph, "a", "b", 1);
		edge(graph, "b", "c", 1);
		edge(graph, "c", "d", 1);
		edge(graph, "y", "z", 1);
		Assertions.assertTrue(MapLayerGeometry.shortestPath(graph, Collections.singletonList("a"), Collections.singletonList("z"), 100).isEmpty());
		Assertions.assertTrue(MapLayerGeometry.shortestPath(graph, Collections.singletonList("a"), Collections.singletonList("d"), 2).isEmpty());
	}

	private static void edge(Map<String, Map<String, Double>> graph, String from, String to, double cost) {
		graph.computeIfAbsent(from, key -> new HashMap<>()).put(to, cost);
		graph.computeIfAbsent(to, key -> new HashMap<>()).put(from, cost);
	}
}
