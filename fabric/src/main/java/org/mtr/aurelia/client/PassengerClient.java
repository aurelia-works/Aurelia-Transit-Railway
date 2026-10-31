package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.mtr.mod.passenger.PassengerNetwork;
import org.mtr.mod.passenger.PassengerService;

/**
 * The passenger caps and counts the server last sent, for the Aurelia panel's Passengers tab.
 */
public final class PassengerClient {

	private static volatile PassengerNetwork.Settings settings = PassengerNetwork.Settings.DEFAULT;
	private static volatile PassengerService.Stats stats = PassengerService.Stats.NONE;
	private static volatile PassengerService.Counts counts = PassengerService.Counts.NONE;

	private PassengerClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PassengerService.SYNC, (client, handler, buf, response) -> {
			final PassengerNetwork.Settings readSettings = PassengerService.read(buf);
			final PassengerService.Stats readStats = new PassengerService.Stats(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong());
			final PassengerService.Counts readCounts = new PassengerService.Counts(PassengerService.readCounts(buf), PassengerService.readCounts(buf));
			client.execute(() -> {
				settings = readSettings;
				stats = readStats;
				counts = readCounts;
			});
		});
	}

	public static PassengerNetwork.Settings settings() {
		return settings;
	}

	public static PassengerService.Counts counts() {
		return counts;
	}

	public static PassengerService.Stats stats() {
		return stats;
	}

	/** Only operators' requests take effect; the server ignores the rest. */
	public static void request(PassengerNetwork.Settings requested) {
		settings = requested.clamped();
		ClientPlayNetworking.send(PassengerService.SET, PassengerService.request(settings));
	}
}
