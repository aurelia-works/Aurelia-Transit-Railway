package org.mtr.mod.render;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Remembers computed values for a short time. The whole cache is dropped when it gets too old, too big, or when the
 * owner (for example the world) it was filled for changes. Render thread only.
 */
public final class ExpiringCache<K, V> {

	private final Map<K, V> map = new HashMap<>();
	private final long maxAgeNanos;
	private final int maxEntries;
	private final LongSupplier clock;
	private Object owner;
	private long since;
	private long hits;
	private long misses;

	public ExpiringCache(long maxAgeNanos, int maxEntries, LongSupplier clock) {
		this.maxAgeNanos = maxAgeNanos;
		this.maxEntries = maxEntries;
		this.clock = clock;
	}

	public V get(K key, Object currentOwner, Supplier<V> compute) {
		final long now = clock.getAsLong();
		if (currentOwner != owner || now - since > maxAgeNanos || map.size() > maxEntries) {
			map.clear();
			owner = currentOwner;
			since = now;
		}
		V value = map.get(key);
		if (value == null) {
			value = compute.get();
			map.put(key, value);
			misses++;
		} else {
			hits++;
		}
		return value;
	}

	public int size() {
		return map.size();
	}

	public long hits() {
		return hits;
	}

	public long misses() {
		return misses;
	}
}
