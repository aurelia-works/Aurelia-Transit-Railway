package org.mtr.aurelia.vehicle.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.network.PacketByteBuf;
import org.mtr.aurelia.vehicle.RoadVehicleEntity;
import org.mtr.aurelia.vehicle.RoadVehicleSettings;
import org.mtr.aurelia.vehicle.RoadVehicles;

/** The road vehicle renderer, the settings from the server, and the driver's keys (W, S, A, D) sent each tick. */
public final class RoadVehicleClient {

	/** Resend unchanged keys this often so the server's input timeout does not cut a held throttle. */
	private static final int RESEND_TICKS = 5;
	private static int lastSent = -1;
	private static int sinceSent;

	private RoadVehicleClient() {
	}

	public static void register() {
		EntityRendererRegistry.register(RoadVehicles.ENTITY, RoadVehicleRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(RoadVehicles.SYNC, (client, handler, buf, response) -> {
			final RoadVehicleSettings read = RoadVehicleSettings.read(buf);
			client.execute(() -> RoadVehicles.setClientSettings(read));
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RoadVehicles.setClientSettings(RoadVehicleSettings.DEFAULT));
		ClientTickEvents.END_CLIENT_TICK.register(RoadVehicleClient::sendKeys);
	}

	/** The panel's change: shown at once, then confirmed (or put back) by the server's answer. */
	public static void request(RoadVehicleSettings next) {
		RoadVehicles.setClientSettings(next);
		if (ClientPlayNetworking.canSend(RoadVehicles.SET)) {
			final PacketByteBuf buf = PacketByteBufs.create();
			next.clamped().write(buf);
			ClientPlayNetworking.send(RoadVehicles.SET, buf);
		}
	}

	/** Operators and the single player host; the server checks again. */
	public static boolean mayEdit(MinecraftClient client) {
		return client.player != null && (client.player.hasPermissionLevel(2) || client.isInSingleplayer());
	}

	private static void sendKeys(MinecraftClient client) {
		if (client.player == null || !(client.player.getVehicle() instanceof RoadVehicleEntity vehicle) || vehicle.driver() != client.player) {
			lastSent = -1;
			return;
		}
		final Input input = client.player.input;
		final int flags = (input.pressingForward ? RoadVehicles.THROTTLE : 0) | (input.pressingBack ? RoadVehicles.BRAKE : 0)
				| (input.pressingLeft ? RoadVehicles.LEFT : 0) | (input.pressingRight ? RoadVehicles.RIGHT : 0);
		if (flags != lastSent || ++sinceSent >= RESEND_TICKS) {
			final PacketByteBuf buf = PacketByteBufs.create();
			buf.writeByte(flags);
			ClientPlayNetworking.send(RoadVehicles.INPUT, buf);
			lastSent = flags;
			sinceSent = 0;
		}
	}
}
