package org.mtr.mod.resource;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class CachedResource<T> {

	@Nullable
	private T data;
	private long expiry;

	private final Supplier<T> dataSupplier;
	private final long lifespan;

	private static boolean canFetchCache;
	private static int buildDepth;
	private static final ObjectArrayList<CachedResource<?>> CACHED_RESOURCES = new ObjectArrayList<>();
	/**
	 * With "free unused models" on, new caches are listed here instead: train models build new nested caches each time
	 * they reload, and each one keeps its geometry suppliers alive, so a strong list grows for the whole session. The
	 * expiry tick still sees every cache somebody can reach; the rest can be collected.
	 */
	private static final List<WeakReference<CachedResource<?>>> WEAK_CACHED_RESOURCES = new ArrayList<>();

	public CachedResource(final Supplier<T> dataSupplier, final long lifespan) {
		this.dataSupplier = dataSupplier;
		this.lifespan = lifespan;
		if (ModelLoadingSettings.freeUnusedModels()) {
			synchronized (WEAK_CACHED_RESOURCES) {
				WEAK_CACHED_RESOURCES.add(new WeakReference<>(this));
			}
		} else {
			CACHED_RESOURCES.add(this);
		}
	}

	@Nullable
	public T getData(boolean force) {
		if (force || canFetchCache) {
			final long currentMillis = System.currentTimeMillis();
			if (data == null || currentMillis > expiry) {
				// Caches nest (a train's cache builds its model caches); only the outermost build ends the slot.
				AsyncModels.enterBuild(force);
				buildDepth++;
				try {
					data = dataSupplier.get();
				} finally {
					buildDepth--;
					AsyncModels.exitBuild(force);
				}
				canFetchCache = false;
				// A model that was only "not ready yet" (still loading in the background) cost nothing, so the next
				// cache may build in the same tick.
				if (buildDepth == 0 && AsyncModels.takeDeferred()) {
					canFetchCache = true;
				}
			}
			expiry = currentMillis + lifespan;
		}
		return data;
	}

	boolean hasData() {
		return data != null;
	}

	public static void tick() {
		canFetchCache = true;
		final long currentMillis = System.currentTimeMillis();
		AsyncModels.tick(currentMillis);
		CACHED_RESOURCES.forEach(cachedResource -> {
			if (currentMillis > cachedResource.expiry) {
				cachedResource.data = null;
			}
		});
		synchronized (WEAK_CACHED_RESOURCES) {
			WEAK_CACHED_RESOURCES.removeIf(reference -> {
				final CachedResource<?> cachedResource = reference.get();
				if (cachedResource == null) {
					return true;
				}
				if (currentMillis > cachedResource.expiry) {
					cachedResource.data = null;
				}
				return false;
			});
		}
	}

	/** Caches kept alive by the list (for tests). */
	public static int strongListedCount() {
		return CACHED_RESOURCES.size();
	}

	/** Caches listed weakly, collected or not (for tests). */
	public static int weakListedCount() {
		synchronized (WEAK_CACHED_RESOURCES) {
			return WEAK_CACHED_RESOURCES.size();
		}
	}
}
