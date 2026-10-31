package com.aureliatransit.architecture.terminal;

import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JourneyPlannerTest {

	private static MapStop stop(String name) {
		return new MapStop(name.hashCode(), name, false, false);
	}

	private static SystemMap map() {
		final MapLine red = new MapLine(1, new LineBadge("R", 0xFF0000, null), "Red", List.of(stop("Alpha"), stop("Beta"), stop("Gamma")));
		final MapLine blue = new MapLine(2, new LineBadge("B", 0x0000FF, null), "Blue", List.of(stop("Gamma"), stop("Delta")));
		return new SystemMap(List.of(red, blue));
	}

	@Test
	void directRideIsOneLegWithNoTransfer() {
		final JourneyPlanner.Journey journey = JourneyPlanner.plan(map(), "Alpha", "Gamma", 2, 3);
		assertTrue(journey.found());
		assertEquals(1, journey.legs().size());
		assertEquals(0, journey.transfers());
		assertEquals(2, journey.fare());
		assertEquals(List.of("Alpha", "Beta", "Gamma"), journey.legs().get(0).stops());
	}

	@Test
	void changeAtGammaAddsATransferAndTheFare() {
		final JourneyPlanner.Journey journey = JourneyPlanner.plan(map(), "alpha", "Delta", 2, 3);
		assertEquals(2, journey.legs().size());
		assertEquals(1, journey.transfers());
		assertEquals(5, journey.fare());
		assertEquals("Delta", journey.legs().get(1).stops().get(journey.legs().get(1).stops().size() - 1));
	}

	@Test
	void unknownStationIsNotAJourney() {
		assertTrue(!JourneyPlanner.plan(map(), "Alpha", "Nowhere", 2, 3).found());
	}

	@Test
	void nextDepartureMatchesTheLine() {
		final ServiceSnapshot red = service("R", "Red", 5_000);
		final ServiceSnapshot blue = service("B", "Blue", 1_000);
		final ServiceSnapshot next = JourneyPlanner.nextDeparture(List.of(blue, red), "R", 0);
		assertEquals("Red", next.routeName());
	}

	private static ServiceSnapshot service(String number, String name, long departure) {
		return new ServiceSnapshot(1, name, number, 0, "Delta", 1, "1", departure, departure, 0, false, false, List.of(), 0, List.of(), "");
	}
}
