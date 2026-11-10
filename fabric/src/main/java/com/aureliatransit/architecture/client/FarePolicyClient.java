package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.fare.FarePolicy;
import com.aureliatransit.architecture.fare.FareSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The zone fare the server last said it charges at card readers; OFF until it says, and after leaving. */
public final class FarePolicyClient {

	private static FarePolicy policy = FarePolicy.OFF;

	private FarePolicyClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FareSync.FARE_POLICY, (client, handler, buf, response) -> {
			final FarePolicy received = read(buf);
			client.execute(() -> policy = received);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> policy = FarePolicy.OFF));
	}

	public static FarePolicy policy() {
		return policy;
	}

	private static FarePolicy read(net.minecraft.network.PacketByteBuf buf) {
		try {
			return FareSync.read(buf);
		} catch (RuntimeException e) {
			return FarePolicy.OFF;
		}
	}
}
