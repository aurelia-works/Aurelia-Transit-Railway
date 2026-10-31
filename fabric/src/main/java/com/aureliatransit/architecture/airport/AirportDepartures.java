package com.aureliatransit.architecture.airport;

import com.aureliatransit.architecture.terminal.TerminalDepartures;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Rows for the airport departures board. Gate services (platforms named {@code Gate 1} and so on, which is what an
 * airport build writes) come first. When the station has none, the board shows the same departures a terminal PIDS
 * shows, so the hall board is not blank at an airport whose railway sits under the terminal.
 */
public final class AirportDepartures {

	public static final String NONE_KEY = "text.aurelia_transit_architecture.no_departures";

	private AirportDepartures() {
	}

	public static List<String> lines(StationSnapshot snapshot, long nowMillis, int max) {
		if (snapshot == null || max <= 0) {
			return List.of();
		}
		final List<ServiceSnapshot> upcoming = TerminalDepartures.select(snapshot, nowMillis, Integer.MAX_VALUE);
		final List<ServiceSnapshot> gates = new ArrayList<>();
		for (final ServiceSnapshot service : upcoming) {
			if (isGate(service.platformName())) {
				gates.add(service);
			}
		}
		final List<ServiceSnapshot> shown = gates.isEmpty() ? upcoming : gates;
		final List<String> lines = new ArrayList<>();
		for (final ServiceSnapshot service : shown) {
			if (lines.size() >= max) {
				break;
			}
			lines.add(format(service, nowMillis));
		}
		return List.copyOf(lines);
	}

	static boolean isGate(String platformName) {
		return platformName != null && platformName.startsWith("Gate ");
	}

	static String format(ServiceSnapshot service, long nowMillis) {
		final long minutes = Math.max(0, (service.departureMillis() - nowMillis) / 60_000L);
		final String when;
		if (minutes <= 0) {
			when = "NOW";
		} else if (minutes > 999) {
			when = "999m";
		} else {
			when = minutes + "m";
		}
		return pad(when, 4) + " " + pad(clip(service.platformName(), 8), 8) + " " + clip(service.destination(), 14);
	}

	private static String clip(String value, int max) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	private static String pad(String value, int width) {
		final String text = value == null ? "" : value;
		if (text.length() >= width) {
			return text;
		}
		final StringBuilder builder = new StringBuilder(width);
		builder.append(text);
		while (builder.length() < width) {
			builder.append(' ');
		}
		return builder.toString();
	}
}
