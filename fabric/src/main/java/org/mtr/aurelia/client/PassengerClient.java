package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ClientCommandManager.literal("atr_passenger_draw")
				.executes(context -> setDraw(context.getSource(), !PassengerDrawSettings.get().draw()))
				.then(ClientCommandManager.literal("on").executes(context -> setDraw(context.getSource(), true)))
				.then(ClientCommandManager.literal("off").executes(context -> setDraw(context.getSource(), false)))));
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

	private static int setDraw(FabricClientCommandSource source, boolean draw) {
		PassengerDrawSettings.setDraw(draw);
		source.sendFeedback(Text.literal("Passenger drawing " + (draw ? "on" : "off") + "; figures draw within "
				+ PassengerDrawSettings.get().drawDistance() + " blocks.").formatted(Formatting.GRAY));
		return 1;
	}
}
