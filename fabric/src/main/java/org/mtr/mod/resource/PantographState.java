package org.mtr.mod.resource;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The eased raise value of each car's pantographs, kept between frames. A car has one value per end choice, so a front and a rear
 * pantograph on the same car can move on their own when the vehicle reverses. Free of Minecraft types so it can be tested alone.
 */
public final class PantographState {

	public static final PantographState INSTANCE = new PantographState();

	private static final long FORGET_AFTER_NANOS = 30_000_000_000L;

	private final Map<Key, Entry> entries = new HashMap<>();
	private long lastCleanupNanos;

	/**
	 * Moves this car's progress toward {@code up} for the time since it was last asked, and returns the eased raise value.
	 * A car seen for the first time starts at its target, so a train that comes into view on a route already has its pantograph up.
	 * Asking twice with the same time (several parts of one car in one frame) returns the same value.
	 */
	public synchronized double update(long vehicleId, int carNumber, PantographEnd end, boolean up, long nowNanos, double raiseSeconds) {
		if (nowNanos - lastCleanupNanos > FORGET_AFTER_NANOS) {
			forgetStale(nowNanos);
			lastCleanupNanos = nowNanos;
		}
		final Key key = new Key(vehicleId, carNumber, end);
		final Entry entry = entries.get(key);
		if (entry == null) {
			entries.put(key, new Entry(up ? 1 : 0, nowNanos));
			return PantographMotion.ease(up ? 1 : 0);
		}
		entry.progress = PantographMotion.advance(entry.progress, up, (nowNanos - entry.lastNanos) / 1E9, raiseSeconds);
		entry.lastNanos = nowNanos;
		return PantographMotion.ease(entry.progress);
	}

	public synchronized int size() {
		return entries.size();
	}

	public synchronized void clear() {
		entries.clear();
	}

	private void forgetStale(long nowNanos) {
		final Iterator<Entry> iterator = entries.values().iterator();
		while (iterator.hasNext()) {
			if (nowNanos - iterator.next().lastNanos > FORGET_AFTER_NANOS) {
				iterator.remove();
			}
		}
	}

	private record Key(long vehicleId, int carNumber, PantographEnd end) {
	}

	private static final class Entry {

		private double progress;
		private long lastNanos;

		private Entry(double progress, long lastNanos) {
			this.progress = progress;
			this.lastNanos = lastNanos;
		}
	}
}
