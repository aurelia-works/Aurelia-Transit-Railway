package org.mtr.aurelia.route;

import org.junit.jupiter.api.Test;
import org.mtr.aurelia.route.RouteControl.StopVisit;
import org.mtr.aurelia.route.RouteControl.Visit;
import org.mtr.aurelia.route.RouteControl.WaypointVisit;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoutePathCacheTest {

	private static List<Visit> path(Visit... visits) {
		return List.of(visits);
	}

	@Test
	void repeatedResolvesComputeOnlyOnce() {
		final RoutePathCache cache = new RoutePathCache();
		final AtomicInteger computes = new AtomicInteger();
		final List<Visit> expected = path(new StopVisit(0, 11L), new WaypointVisit(1, 64, 10), new StopVisit(1, 22L));
		for (int i = 0; i < 5; i++) {
			assertEquals(expected, cache.resolve(7L, 1L, () -> {
				computes.incrementAndGet();
				return expected;
			}));
		}
		assertEquals(1, computes.get(), "no per-use pathfinding: the cached path is reused");
	}

	@Test
	void routeEditInvalidatesOnlyThatRoute() {
		final RoutePathCache cache = new RoutePathCache();
		final AtomicInteger computesA = new AtomicInteger();
		final AtomicInteger computesB = new AtomicInteger();
		cache.resolve(1L, 1L, () -> {
			computesA.incrementAndGet();
			return path(new StopVisit(0, 11L));
		});
		cache.resolve(2L, 1L, () -> {
			computesB.incrementAndGet();
			return path(new StopVisit(0, 22L));
		});
		cache.resolve(1L, 2L, () -> {
			computesA.incrementAndGet();
			return path(new StopVisit(0, 11L), new StopVisit(1, 12L));
		});
		assertEquals(2, computesA.get(), "edited route rebuilt");
		assertEquals(1, computesB.get(), "other routes keep their cache");
		assertEquals(path(new StopVisit(0, 11L), new StopVisit(1, 12L)), cache.resolve(1L, 2L, () -> path(new StopVisit(0, 99L))));
	}

	@Test
	void invalidateRouteRebuildsOnNextUse() {
		final RoutePathCache cache = new RoutePathCache();
		cache.resolve(1L, 1L, () -> path(new StopVisit(0, 11L)));
		cache.invalidateRoute(1L);
		assertEquals(path(new StopVisit(0, 12L)), cache.resolve(1L, 1L, () -> path(new StopVisit(0, 12L))));
	}

	@Test
	void railChangeRebuildsEveryRoute() {
		final RoutePathCache cache = new RoutePathCache();
		final AtomicInteger computes = new AtomicInteger();
		cache.resolve(1L, 1L, () -> {
			computes.incrementAndGet();
			return path(new StopVisit(0, 11L));
		});
		cache.resolve(2L, 1L, () -> {
			computes.incrementAndGet();
			return path(new StopVisit(0, 22L));
		});
		cache.noteRailChange();
		cache.resolve(1L, 1L, () -> {
			computes.incrementAndGet();
			return path(new StopVisit(0, 11L));
		});
		cache.resolve(2L, 1L, () -> {
			computes.incrementAndGet();
			return path(new StopVisit(0, 22L));
		});
		assertEquals(4, computes.get(), "rails changed, both routes rebuilt once each");
		assertEquals(2, cache.size());
	}
}
