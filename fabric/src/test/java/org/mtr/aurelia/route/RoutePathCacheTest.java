package org.mtr.aurelia.route;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoutePathCacheTest {

	private static List<Long> path(long... ids) {
		return java.util.Arrays.stream(ids).boxed().toList();
	}

	@Test
	void repeatedResolvesComputeOnlyOnce() {
		final RoutePathCache cache = new RoutePathCache();
		final AtomicInteger computes = new AtomicInteger();
		for (int i = 0; i < 5; i++) {
			assertEquals(path(11L, 22L), cache.resolve(7L, 1L, () -> {
				computes.incrementAndGet();
				return path(11L, 22L);
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
			return path(11L);
		});
		cache.resolve(2L, 1L, () -> {
			computesB.incrementAndGet();
			return path(22L);
		});
		cache.resolve(1L, 2L, () -> {
			computesA.incrementAndGet();
			return path(11L, 12L);
		});
		assertEquals(2, computesA.get(), "edited route rebuilt");
		assertEquals(1, computesB.get(), "other routes keep their cache");
		assertEquals(path(11L, 12L), cache.resolve(1L, 2L, () -> path(99L)));
	}

	@Test
	void invalidateRouteRebuildsOnNextUse() {
		final RoutePathCache cache = new RoutePathCache();
		cache.resolve(1L, 1L, () -> path(11L));
		cache.invalidateRoute(1L);
		assertEquals(path(12L), cache.resolve(1L, 1L, () -> path(12L)));
	}

	@Test
	void railChangeRebuildsEveryRoute() {
		final RoutePathCache cache = new RoutePathCache();
		final AtomicInteger computes = new AtomicInteger();
		cache.resolve(1L, 1L, () -> {
			computes.incrementAndGet();
			return path(11L);
		});
		cache.resolve(2L, 1L, () -> {
			computes.incrementAndGet();
			return path(22L);
		});
		cache.noteRailChange();
		cache.resolve(1L, 1L, () -> {
			computes.incrementAndGet();
			return path(11L);
		});
		cache.resolve(2L, 1L, () -> {
			computes.incrementAndGet();
			return path(22L);
		});
		assertEquals(4, computes.get(), "rails changed, both routes rebuilt once each");
		assertEquals(2, cache.size());
	}
}
