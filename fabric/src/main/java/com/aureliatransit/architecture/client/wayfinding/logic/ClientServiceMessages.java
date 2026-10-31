package com.aureliatransit.architecture.client.wayfinding.logic;

import com.aureliatransit.architecture.wayfinding.RouteHold;
import com.aureliatransit.architecture.wayfinding.ServiceMessage;
import com.aureliatransit.architecture.wayfinding.ServiceMessages;

/**
 * The latest server-synced {@link ServiceMessages} on this client. Written from the network handler, read by display
 * builders (about once a second per visible board), so it is a single volatile reference to an immutable value.
 */
public final class ClientServiceMessages {

	private static volatile ServiceMessages current = ServiceMessages.EMPTY;
	private static volatile java.util.List<String> suspendedRoutes = java.util.List.of();

	private ClientServiceMessages() {
	}

	public static void set(ServiceMessages messages) {
		set(messages, java.util.List.of());
	}

	public static void set(ServiceMessages messages, java.util.List<RouteHold> holds) {
		current = messages == null ? ServiceMessages.EMPTY : messages;
		final java.util.List<String> names = new java.util.ArrayList<>();
		if (holds != null) {
			for (final RouteHold hold : holds) {
				if (hold.route() != null && !hold.route().isBlank()) {
					names.add(hold.route());
				}
			}
		}
		suspendedRoutes = java.util.List.copyOf(names);
	}

	public static java.util.List<String> suspendedRoutes() {
		return suspendedRoutes;
	}

	public static void clear() {
		current = ServiceMessages.EMPTY;
		suspendedRoutes = java.util.List.of();
	}

	public static ServiceMessages get() {
		return current;
	}

	/**
	 * Every message a board shows, in rotation order: the display's own text, then its station's messages, then the
	 * network's ({@link ServiceMessages#applicable}).
	 *
	 * @param local       the display's own message ({@code DisplayConfig.message()})
	 * @param stationName the board's resolved station display name, or empty
	 */
	public static java.util.List<ServiceMessage> applicable(String local, String stationName) {
		return current.applicable(local, stationName);
	}

	/** Every message applicable to a station (no display-local text), for the terminal's service-info page. */
	public static java.util.List<ServiceMessage> allFor(String stationName) {
		return current.applicable("", stationName);
	}
}
