package com.aureliatransit.architecture.fare;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Zone fare read from the server ticket config. A base of 0 and a step of 0 means "do not charge here", so an older
 * config that never had these keys keeps MTR's own fare. The daily cap, when set, limits what this policy charges
 * across one Minecraft day; it does not refund MTR.
 */
public record FarePolicy(int base, int step, int dailyCap, Map<String, Integer> zones) {

	public static final FarePolicy OFF = new FarePolicy(0, 0, 0, Map.of());

	public FarePolicy {
		base = Math.max(0, base);
		step = Math.max(0, step);
		dailyCap = Math.max(0, dailyCap);
		zones = Map.copyOf(zones == null ? Map.of() : zones);
	}

	public boolean enabled() {
		return base > 0 || step > 0;
	}

	public static FarePolicy parse(int base, int step, int dailyCap, String table) {
		final Map<String, Integer> zones = new HashMap<>();
		if (table != null) {
			for (final String part : table.split(",")) {
				final int eq = part.indexOf('=');
				if (eq <= 0) {
					continue;
				}
				final String key = key(part.substring(0, eq));
				if (key.isEmpty()) {
					continue;
				}
				try {
					zones.put(key, Math.max(0, Integer.parseInt(part.substring(eq + 1).trim())));
				} catch (NumberFormatException ignored) {
					// A bad pair is skipped so the rest of the table still loads.
				}
			}
		}
		return new FarePolicy(base, step, dailyCap, zones);
	}

	public static String key(String station) {
		return station == null ? "" : station.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	public int zone(String station) {
		return zones.getOrDefault(key(station), 0);
	}

	/**
	 * What to take for a tap-out. {@code spentToday} is what this policy already took today. A disabled policy quotes 0.
	 * A cap of 0 does not limit the quote.
	 */
	public int quote(String fromStation, String toStation, int spentToday) {
		if (!enabled()) {
			return 0;
		}
		final int crossed = Math.abs(zone(fromStation) - zone(toStation));
		int fare = base + step * crossed;
		if (dailyCap > 0) {
			fare = Math.min(fare, Math.max(0, dailyCap - Math.max(0, spentToday)));
		}
		return fare;
	}
}
