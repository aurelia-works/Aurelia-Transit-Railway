package org.mtr.aurelia.depot;

import com.aureliatransit.architecture.wayfinding.RouteHold;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteHoldTest {

	@Test
	void expiredHoldsDropAndALiveOneMatchesTheRoute() {
		final RouteHold live = new RouteHold(3, "Red Line", 1_000);
		final RouteHold dead = new RouteHold(4, "Blue", 100);
		assertEquals(List.of(live), RouteHold.active(List.of(live, dead), 500));
		assertTrue(RouteHold.affects(List.of(live), "red line", 500));
		assertFalse(RouteHold.affects(List.of(dead), "Blue", 500));
		assertFalse(RouteHold.affects(List.of(), "Red Line", 500));
	}

	@Test
	void depotHealthNamesTheSuspendedRoute() {
		final DepotHealth.DepotInfo depot = new DepotHealth.DepotInfo(1, "A", DepotHealth.Status.SUCCESSFUL, 0, 0, 0, 1, 1, 0, List.of(), List.of(), false, List.of(), List.of());
		assertTrue(DepotHealth.check(depot).isEmpty());
		assertEquals("route_suspended", DepotHealth.check(depot, List.of("Red Line")).get(0).key());
	}
}
