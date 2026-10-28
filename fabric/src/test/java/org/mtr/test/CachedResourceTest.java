package org.mtr.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mtr.mod.resource.AsyncModels;
import org.mtr.mod.resource.CachedResource;
import org.mtr.mod.resource.ModelLoadingSettings;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CachedResourceTest {

	@AfterEach
	void clearOverride() {
		ModelLoadingSettings.override(null);
	}

	@Test
	void switchOffKeepsEveryCacheInTheStrongList() {
		ModelLoadingSettings.override(new boolean[]{true, true, false});
		final int strongBefore = CachedResource.strongListedCount();
		final int weakBefore = CachedResource.weakListedCount();
		for (int i = 0; i < 50; i++) {
			new CachedResource<>(() -> "x", 1000);
		}
		System.gc();
		CachedResource.tick();
		assertEquals(strongBefore + 50, CachedResource.strongListedCount());
		assertTrue(CachedResource.weakListedCount() <= weakBefore);
	}

	@Test
	void switchOnDropsUnreachableCachesButKeepsReachableOnes() throws InterruptedException {
		ModelLoadingSettings.override(new boolean[]{true, true, true});
		final int strongBefore = CachedResource.strongListedCount();
		final CachedResource<String> kept = new CachedResource<>(() -> "kept", 1000);
		for (int i = 0; i < 50; i++) {
			new CachedResource<>(() -> "dropped", 1000);
		}
		assertEquals(strongBefore, CachedResource.strongListedCount());
		assertTrue(CachedResource.weakListedCount() >= 51);
		// Only the cache this test still holds (and whatever other tests hold) may stay listed.
		for (int i = 0; i < 100 && CachedResource.weakListedCount() > 20; i++) {
			System.gc();
			Thread.sleep(20);
			CachedResource.tick();
		}
		assertTrue(CachedResource.weakListedCount() <= 20, "unreachable caches were not dropped");
		assertTrue(CachedResource.weakListedCount() >= 1);
		assertEquals("kept", kept.getData(true));
	}

	@Test
	void reachableCacheStillExpiresOnTick() throws InterruptedException {
		ModelLoadingSettings.override(new boolean[]{true, true, true});
		final AtomicInteger builds = new AtomicInteger();
		final CachedResource<Integer> cache = new CachedResource<>(builds::incrementAndGet, 5);
		assertEquals(1, cache.getData(true));
		Thread.sleep(30);
		CachedResource.tick();
		// Expired by the tick: the next fetch builds again.
		assertEquals(2, cache.getData(true));
	}

	@Test
	void oneBuildPerTickUnlessTheBuildWasOnlyDeferred() {
		ModelLoadingSettings.override(new boolean[]{true, true, true});
		CachedResource.tick();
		final AtomicInteger notReadyCalls = new AtomicInteger();
		final CachedResource<String> notReady = new CachedResource<>(() -> {
			notReadyCalls.incrementAndGet();
			AsyncModels.deferred();
			return null;
		}, 1000);
		final AtomicInteger readyCalls = new AtomicInteger();
		final CachedResource<String> ready = new CachedResource<>(() -> {
			readyCalls.incrementAndGet();
			return "ready";
		}, 1000);
		final CachedResource<String> second = new CachedResource<>(() -> "second", 1000);

		assertNull(notReady.getData(false));
		// The deferred build cost nothing, so the slot is still free this tick.
		assertEquals("ready", ready.getData(false));
		// That one used the slot.
		assertNull(second.getData(false));
		assertEquals(1, notReadyCalls.get());
		assertEquals(1, readyCalls.get());
	}

	@Test
	void withoutADeferralTheSlotIsUsedUpAsInMtr() {
		CachedResource.tick();
		final CachedResource<String> first = new CachedResource<>(() -> null, 1000);
		final AtomicInteger calls = new AtomicInteger();
		final CachedResource<String> second = new CachedResource<>(() -> {
			calls.incrementAndGet();
			return "second";
		}, 1000);
		assertNull(first.getData(false));
		assertNull(second.getData(false));
		assertEquals(0, calls.get());
	}

	@Test
	void forcedBuildsNeverWait() {
		ModelLoadingSettings.override(new boolean[]{true, true, true});
		final boolean[] mayDeferInside = new boolean[2];
		new CachedResource<>(() -> {
			mayDeferInside[0] = AsyncModels.mayDefer(false);
			return "forced";
		}, 1000).getData(true);
		CachedResource.tick();
		new CachedResource<>(() -> {
			mayDeferInside[1] = AsyncModels.mayDefer(false);
			return "normal";
		}, 1000).getData(false);
		assertFalse(mayDeferInside[0]);
		assertTrue(mayDeferInside[1]);
		assertTrue(AsyncModels.mayDefer(false));
	}
}
