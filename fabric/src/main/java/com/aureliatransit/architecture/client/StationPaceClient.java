package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.world.StationBuildSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;

/**
 * The station build pace the server last sent, and the slider's way to ask for another. The server owns the value
 * ({@link StationBuildSettings}); this only mirrors it, so the wrench screen shows what the running builds actually use.
 */
public final class StationPaceClient {

	private static int blocksPerTick = StationBuildSettings.DEFAULT;

	private StationPaceClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ModPackets.STATION_PACE, (client, handler, buf, response) -> {
			final int pace = read(buf);
			client.execute(() -> blocksPerTick = StationBuildSettings.clamp(pace));
		});
	}

	public static int blocksPerTick() {
		return blocksPerTick;
	}

	/** Asks the server to change the pace; the server clamps it and sends the value back. */
	public static void set(int pace) {
		final int clamped = StationBuildSettings.clamp(pace);
		blocksPerTick = clamped;
		ClientPlayNetworking.send(ModPackets.STATION_PACE, ModPackets.writeStationPace(clamped));
	}

	private static int read(PacketByteBuf buf) {
		try {
			return buf.readVarInt();
		} catch (RuntimeException e) {
			return StationBuildSettings.DEFAULT;
		}
	}
}
