package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.render.ExpiringCache;

import java.util.concurrent.atomic.AtomicInteger;

public final class ExpiringCacheTest {

	private long now;

	@Test
	public void computesOnceWhileFresh() {
		final ExpiringCache<String, Integer> cache = new ExpiringCache<>(1000, 10, () -> now);
		final AtomicInteger computed = new AtomicInteger();
		for (int i = 0; i < 5; i++) {
			now = i * 100;
			Assertions.assertEquals(7, cache.get("a", "world", () -> computed.incrementAndGet() * 7));
		}
		Assertions.assertEquals(1, computed.get());
		Assertions.assertEquals(4, cache.hits());
		Assertions.assertEquals(1, cache.misses());
	}

	@Test
	public void expiresAfterTheMaximumAge() {
		final ExpiringCache<String, Integer> cache = new ExpiringCache<>(1000, 10, () -> now);
		final AtomicInteger computed = new AtomicInteger();
		now = 0;
		cache.get("a", "world", computed::incrementAndGet);
		now = 1001;
		Assertions.assertEquals(2, cache.get("a", "world", computed::incrementAndGet));
	}

	@Test
	public void changingWorldClears() {
		final ExpiringCache<String, Integer> cache = new ExpiringCache<>(1000, 10, () -> now);
		final AtomicInteger computed = new AtomicInteger();
		cache.get("a", "world1", computed::incrementAndGet);
		Assertions.assertEquals(2, cache.get("a", "world2", computed::incrementAndGet));
	}

	@Test
	public void clearsWhenFull() {
		final ExpiringCache<Integer, Integer> cache = new ExpiringCache<>(1000, 3, () -> now);
		for (int i = 0; i < 10; i++) {
			cache.get(i, "world", () -> 0);
			Assertions.assertTrue(cache.size() <= 4);
		}
	}
}
