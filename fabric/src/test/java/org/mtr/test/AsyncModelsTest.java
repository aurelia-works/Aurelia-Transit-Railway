package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.resource.AsyncModels;
import org.mtr.mod.resource.FastObjLoader;

import static org.junit.jupiter.api.Assertions.*;

class AsyncModelsTest {

	private static final String TRIANGLE = "v 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl white\nf 1 2 3\n";

	private static void awaitDone(AsyncModels.Job job) throws InterruptedException {
		final long end = System.currentTimeMillis() + 10000;
		while (!job.isDone() && System.currentTimeMillis() < end) {
			Thread.sleep(5);
		}
		assertTrue(job.isDone());
	}

	/** Other tests may leave finished jobs behind: age them out. */
	private static void dropLeftovers() {
		AsyncModels.tick(1L << 50);
		AsyncModels.tick(1L << 51);
	}

	@Test
	void mayDeferNeedsBothSwitchesAndNoReload() {
		assertTrue(AsyncModels.mayDefer(true, false));
		assertFalse(AsyncModels.mayDefer(false, false));
		assertFalse(AsyncModels.mayDefer(true, true));
	}

	@Test
	void forcedBuildsNeverDefer() {
		AsyncModels.enterBuild(true);
		assertFalse(AsyncModels.mayDefer(true, false));
		AsyncModels.exitBuild(true);
		assertTrue(AsyncModels.mayDefer(true, false));
		// Unforced builds do not change anything.
		AsyncModels.enterBuild(false);
		assertTrue(AsyncModels.mayDefer(true, false));
		AsyncModels.exitBuild(false);
	}

	@Test
	void deferralIsReportedOnce() {
		AsyncModels.takeDeferred();
		assertFalse(AsyncModels.takeDeferred());
		AsyncModels.deferred();
		assertTrue(AsyncModels.takeDeferred());
		assertFalse(AsyncModels.takeDeferred());
	}

	@Test
	void parsedFileIsClaimedOnceAndOfferedToTheSameTextOnly() throws InterruptedException {
		dropLeftovers();
		final AsyncModels.Job job = AsyncModels.submit(TRIANGLE);
		assertNotNull(job);
		awaitDone(job);
		final FastObjLoader.Prepared prepared = AsyncModels.claim(job);
		assertNotNull(prepared);
		assertNull(AsyncModels.claim(job));
		assertEquals(0, AsyncModels.outstanding());

		AsyncModels.offer(prepared);
		assertNull(AsyncModels.take(TRIANGLE + "# other", true));
		// A different split mode is a different parse; the offer survives until it is cleared or taken.
		assertNull(AsyncModels.take(TRIANGLE, false));
		assertSame(prepared, AsyncModels.take(new String(TRIANGLE), true));
		assertNull(AsyncModels.take(TRIANGLE, true));
		AsyncModels.offer(prepared);
		AsyncModels.clearOffer();
		assertNull(AsyncModels.take(TRIANGLE, true));
	}

	@Test
	void atMostSixFilesWaitAndUnclaimedResultsAreDropped() throws InterruptedException {
		dropLeftovers();
		final AsyncModels.Job[] jobs = new AsyncModels.Job[AsyncModels.MAX_OUTSTANDING];
		for (int i = 0; i < jobs.length; i++) {
			jobs[i] = AsyncModels.submit(TRIANGLE);
			assertNotNull(jobs[i]);
		}
		assertNull(AsyncModels.submit(TRIANGLE));
		assertEquals(AsyncModels.MAX_OUTSTANDING, AsyncModels.outstanding());
		for (final AsyncModels.Job job : jobs) {
			awaitDone(job);
		}
		final long now = 1_000_000;
		// First tick only notes when each result was seen; they are kept for the grace period.
		AsyncModels.tick(now);
		AsyncModels.tick(now + AsyncModels.UNCLAIMED_MILLIS);
		assertEquals(AsyncModels.MAX_OUTSTANDING, AsyncModels.outstanding());
		assertFalse(jobs[0].isDropped());
		AsyncModels.tick(now + AsyncModels.UNCLAIMED_MILLIS + 1);
		assertEquals(0, AsyncModels.outstanding());
		assertTrue(jobs[0].isDropped());
		assertNull(AsyncModels.claim(jobs[0]));
		assertNotNull(AsyncModels.submit(TRIANGLE));
	}
}
