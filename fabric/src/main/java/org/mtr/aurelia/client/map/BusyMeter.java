package org.mtr.aurelia.client.map;

import org.mtr.aurelia.client.PassengerClient;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Station;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.passenger.PassengerNetwork;
import org.mtr.mod.passenger.PassengerService;

import java.util.HashMap;
import java.util.Map;

/**
 * How busy each station is right now, from the people waiting on its platforms: quiet, busy or packed. Measured against
 * how many a platform can hold (the server's cap), so a small station fills up as fast as a big one.
 */
public final class BusyMeter {

	public enum Level {
		QUIET("gui.aurelia.map.busy.quiet", 0xFF34C77B, 1),
		BUSY("gui.aurelia.map.busy.busy", 0xFFE8B04A, 2),
		PACKED("gui.aurelia.map.busy.packed", 0xFFFF6B5E, 3);

		public final String key;
		public final int color;
		/** Bars lit on the three-bar meter. */
		public final int bars;

		Level(String key, int color, int bars) {
			this.key = key;
			this.color = color;
			this.bars = bars;
		}
	}

	/** From this share of the station's room, it counts as busy. */
	static final double BUSY_SHARE = 0.25;
	/** From this share, packed. */
	static final double PACKED_SHARE = 0.6;
	/** The map draws every frame, but the waiting counts arrive from the server only every few seconds. */
	private static final long REFRESH_MILLIS = 1000;

	private static Map<Long, Level> cached = Map.of();
	private static long cachedAt = Long.MIN_VALUE;

	private BusyMeter() {
	}

	public static Level level(int waiting, int platforms, int maxPerPlatform) {
		if (waiting <= 0 || platforms <= 0) {
			return Level.QUIET;
		}
		final double share = waiting / ((double) platforms * Math.max(1, maxPerPlatform));
		return share >= PACKED_SHARE ? Level.PACKED : share >= BUSY_SHARE ? Level.BUSY : Level.QUIET;
	}

	/** Station id to level, for stations with platforms; empty while passengers are off. Worked out at most once a second. */
	public static Map<Long, Level> levels() {
		final long now = System.currentTimeMillis();
		if (now - cachedAt >= REFRESH_MILLIS || now < cachedAt) {
			cached = compute();
			cachedAt = now;
		}
		return cached;
	}

	private static Map<Long, Level> compute() {
		final PassengerNetwork.Settings settings = PassengerClient.settings();
		final PassengerService.Counts counts = PassengerClient.counts();
		final Map<Long, Level> levels = new HashMap<>();
		if (!settings.enabled()) {
			return Map.of();
		}
		for (final Station station : MinecraftClientData.getInstance().stations) {
			int waiting = 0;
			int platforms = 0;
			for (final Platform platform : station.savedRails) {
				platforms++;
				waiting += counts.waiting().getOrDefault(platform.getId(), 0);
			}
			if (platforms > 0) {
				levels.put(station.getId(), level(waiting, platforms, settings.maxWaitingPerPlatform()));
			}
		}
		return Map.copyOf(levels);
	}
}
