package org.mtr.aurelia.route;

import org.junit.jupiter.api.Test;
import org.mtr.aurelia.route.RouteControl.StopVisit;
import org.mtr.aurelia.route.RouteControl.Visit;
import org.mtr.aurelia.route.RouteControl.Waypoint;
import org.mtr.aurelia.route.RouteControl.WaypointVisit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouteControlTest {

	@Test
	void waypointOrderIsRespected() {
		final RouteControl control = new RouteControl(List.of(
				new Waypoint(1, 64, 10, 0),
				new Waypoint(2, 64, 20, 0),
				new Waypoint(3, 64, 30, 1),
				new Waypoint(4, 64, 40, 99)
		), Map.of(), false);
		assertEquals(
				List.of(new StopVisit(0, 11L), new WaypointVisit(1, 64, 10), new WaypointVisit(2, 64, 20),
						new StopVisit(1, 22L), new WaypointVisit(3, 64, 30),
						new StopVisit(2, 33L), new WaypointVisit(4, 64, 40)),
				control.visitOrder(List.of(11L, 22L, 33L)),
				"waypoints follow their stop, keep listed order, and a stop past the end lands last");
	}

	@Test
	void noWaypointsKeepsStopOrder() {
		assertEquals(List.of(new StopVisit(0, 11L), new StopVisit(1, 22L)), RouteControl.EMPTY.visitOrder(List.of(11L, 22L)));
		assertTrue(RouteControl.EMPTY.visitOrder(List.of()).isEmpty());
	}

	@Test
	void platformChoiceOverridesPlannedPlatform() {
		final RouteControl control = RouteControl.EMPTY.withPlatformChoice(1, 220L);
		assertEquals(110L, control.platformForStop(0, 110L));
		assertEquals(220L, control.platformForStop(1, 210L));
		final List<Visit> order = control.visitOrder(List.of(110L, 210L));
		assertEquals(List.of(new StopVisit(0, 110L), new StopVisit(1, 220L)), order, "visit order uses the choice");
	}

	@Test
	void fallbackPicksFirstFreePlatform() {
		final RouteControl control = RouteControl.EMPTY.withFallback(true);
		assertEquals(12L, control.resolvePlatform(11L, List.of(11L, 12L, 13L), Set.of(11L)), "planned taken, first free wins");
		assertEquals(11L, control.resolvePlatform(11L, List.of(11L, 12L), Set.of()), "planned free, stays");
		assertEquals(11L, control.resolvePlatform(11L, List.of(11L, 12L), Set.of(11L, 12L)), "all taken, trains wait at the planned one");
		assertEquals(11L, control.resolvePlatform(11L, List.of(), Set.of(11L)), "no candidates, stays");
	}

	@Test
	void fallbackOffKeepsPlannedPlatform() {
		assertEquals(11L, RouteControl.EMPTY.resolvePlatform(11L, List.of(11L, 12L), Set.of(11L)));
	}

	@Test
	void oldRouteLoadsWithNoWaypointsAndNoFallback() {
		final RouteControl control = RouteControl.fromEntries(Map.of());
		assertTrue(control.waypoints().isEmpty(), "old save has no waypoint key");
		assertTrue(control.platformChoices().isEmpty(), "old save has no platform key");
		assertFalse(control.fallback(), "old save has no fallback key");
		assertEquals(RouteControl.EMPTY.visitOrder(List.of(11L, 22L)), control.visitOrder(List.of(11L, 22L)));
	}

	@Test
	void malformedEntriesFallBackToDefaults() {
		final Map<String, String> entries = new HashMap<>();
		entries.put("waypoints", "abc,1;2,0:-1,1;2;3");
		entries.put("platforms", "x:y");
		entries.put("fallback", "yes");
		final RouteControl control = RouteControl.fromEntries(entries);
		assertTrue(control.waypoints().isEmpty());
		assertTrue(control.platformChoices().isEmpty());
		assertFalse(control.fallback());
	}

	@Test
	void entriesRoundTrip() {
		final RouteControl control = new RouteControl(List.of(new Waypoint(1, 64, -5, 2)), Map.of(1, 220L), true);
		final RouteControl read = RouteControl.fromEntries(control.toEntries());
		assertEquals(control, read);
		assertTrue(read.fallback());
	}

	@Test
	void contentVersionChangesOnAnyEdit() {
		final long base = RouteControl.EMPTY.contentVersion();
		assertEquals(base, RouteControl.fromEntries(Map.of()).contentVersion());
		assertNotEquals(base, RouteControl.EMPTY.withFallback(true).contentVersion());
		assertNotEquals(base, RouteControl.EMPTY.withWaypoint(1, 64, 10, 0).contentVersion());
		assertNotEquals(base, RouteControl.EMPTY.withPlatformChoice(0, 110L).contentVersion());
	}
}
