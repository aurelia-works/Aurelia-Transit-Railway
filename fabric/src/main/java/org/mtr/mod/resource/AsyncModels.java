package org.mtr.mod.resource;

import org.mtr.mod.Init;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background loading for train models. MTR parses a car's OBJ file on the render thread inside
 * {@code VehicleModel.createModel}, a 20-300 ms stall per heavy file. With this on, the first request reads the file
 * (on the render thread, through the resource provider's cache, which is not thread-safe) and hands the parse to a
 * worker; the model cache is told "not ready" (null, which {@link CachedResource} already handles), and a later
 * request finds the parsed meshes and builds the model with MTR's own code from them. Materials, textures and GPU
 * uploads stay on the render thread.
 * <p>
 * Everything here runs on the render thread except {@link Job#run}.
 */
public final class AsyncModels {

	/** Parsed results nobody picked up (the train left view) are dropped after this long. */
	public static final long UNCLAIMED_MILLIS = 20_000;
	/** At most this many parsed files wait to be built at once (each is a whole car's vertices). */
	public static final int MAX_OUTSTANDING = 6;

	private static ExecutorService executor;
	private static final List<Job> JOBS = new ArrayList<>();

	private static boolean deferredThisCall;
	private static int forceDepth;
	private static FastObjLoader.Prepared offered;

	private AsyncModels() {
	}

	/** A file being parsed on a worker, then waiting for its model build. */
	public static final class Job implements Runnable {

		private final String objString;
		private volatile boolean done;
		private volatile FastObjLoader.Prepared result;
		private long doneAt;
		private boolean dropped;

		private Job(String objString) {
			this.objString = objString;
		}

		@Override
		public void run() {
			try {
				result = FastObjLoader.prepare(objString, true);
			} catch (RuntimeException e) {
				Init.LOGGER.warn("Background OBJ reading failed; this file is read on the render thread", e);
			} finally {
				done = true;
			}
		}

		public boolean isDone() {
			return done;
		}

		/** The result was thrown away unused; parse again. */
		public boolean isDropped() {
			return dropped;
		}
	}

	/** True when a model may wait for its file (not a forced build, not during a resource reload, both switches on). */
	public static boolean mayDefer(boolean reloading) {
		return mayDefer(ModelLoadingSettings.backgroundModelLoading() && ModelLoadingSettings.fasterModelLoading(), reloading);
	}

	public static boolean mayDefer(boolean switchesOn, boolean reloading) {
		return switchesOn && forceDepth == 0 && !reloading;
	}

	/** Starts parsing; null when too many results are already waiting (the model is then built right away). */
	public static Job submit(String objString) {
		if (objString == null || JOBS.size() >= MAX_OUTSTANDING) {
			return null;
		}
		final Job job = new Job(objString);
		JOBS.add(job);
		executor().execute(job);
		return job;
	}

	/** The model was not built this time because its file is still being parsed. */
	public static void deferred() {
		deferredThisCall = true;
	}

	/** Whether a deferral happened since the last call (then the one-build-per-tick slot was not really used). */
	public static boolean takeDeferred() {
		final boolean deferred = deferredThisCall;
		deferredThisCall = false;
		return deferred;
	}

	/** The parsed result, once; the job is forgotten. */
	public static FastObjLoader.Prepared claim(Job job) {
		JOBS.remove(job);
		final FastObjLoader.Prepared prepared = job.result;
		job.result = null;
		return prepared;
	}

	/** Offers a parsed file to the next OBJ load of the same text, during one model build. */
	public static void offer(FastObjLoader.Prepared prepared) {
		offered = prepared;
	}

	/** The offered parse, if it is for exactly this text and split mode; taken once. */
	public static FastObjLoader.Prepared take(String objString, boolean splitModel) {
		final FastObjLoader.Prepared prepared = offered;
		if (prepared == null || prepared.splitModel != splitModel || prepared.objString != objString && !prepared.objString.equals(objString)) {
			return null;
		}
		offered = null;
		return prepared;
	}

	public static void clearOffer() {
		offered = null;
	}

	/** Around a cache's supplier call: forced builds never wait. */
	public static void enterBuild(boolean force) {
		if (force) {
			forceDepth++;
		}
	}

	public static void exitBuild(boolean force) {
		if (force) {
			forceDepth--;
		}
	}

	/** Once per client tick: drop results whose train left view before they were used. */
	public static void tick(long now) {
		if (JOBS.isEmpty()) {
			return;
		}
		JOBS.removeIf(job -> {
			if (!job.done) {
				return false;
			}
			if (job.doneAt == 0) {
				job.doneAt = now;
				return false;
			}
			if (now - job.doneAt > UNCLAIMED_MILLIS) {
				job.result = null;
				job.dropped = true;
				return true;
			}
			return false;
		});
	}

	/** Files being parsed or waiting for their build. */
	public static int outstanding() {
		return JOBS.size();
	}

	private static ExecutorService executor() {
		if (executor == null) {
			final int threads = Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() / 3));
			final AtomicInteger counter = new AtomicInteger();
			final ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
				final Thread thread = new Thread(runnable, "MTR model loader " + counter.incrementAndGet());
				thread.setDaemon(true);
				thread.setPriority(Thread.NORM_PRIORITY - 1);
				return thread;
			});
			pool.allowCoreThreadTimeOut(true);
			executor = pool;
		}
		return executor;
	}
}
