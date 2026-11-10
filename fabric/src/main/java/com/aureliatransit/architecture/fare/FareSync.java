package com.aureliatransit.architecture.fare;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.mtr.mod.config.Config;

import java.util.HashMap;
import java.util.Map;

/**
 * Sends the zone fare a server charges at its card readers to each player on join, so a passenger terminal can quote the
 * real fare. A server that charges nothing here (no zone fare, or barriers that still take MTR's own fare) sends
 * {@link FarePolicy#OFF}.
 */
public final class FareSync {

	public static final Identifier FARE_POLICY = AureliaTransitArchitecture.id("fare_policy");
	private static final int MAX_ZONES = 4096;

	private FareSync() {
	}

	/** The policy {@link CardTaps} charges with, or OFF when tap-outs take nothing from it. */
	public static FarePolicy charged() {
		final FarePolicy policy = FarePolicy.parse(Config.getServer().getZoneFareBase(), Config.getServer().getZoneFareStep(),
				Config.getServer().getDailyFareCap(), Config.getServer().getFareZoneTable());
		return policy.enabled() && Config.getServer().getFreeTicketBarriers() ? policy : FarePolicy.OFF;
	}

	public static void send(ServerPlayerEntity player) {
		if (ServerPlayNetworking.canSend(player, FARE_POLICY)) {
			ServerPlayNetworking.send(player, FARE_POLICY, write(charged()));
		}
	}

	public static PacketByteBuf write(FarePolicy policy) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(policy.base());
		buf.writeVarInt(policy.step());
		buf.writeVarInt(policy.dailyCap());
		final int count = Math.min(policy.zones().size(), MAX_ZONES);
		buf.writeVarInt(count);
		int written = 0;
		for (final Map.Entry<String, Integer> zone : policy.zones().entrySet()) {
			if (written++ >= count) {
				break;
			}
			buf.writeString(zone.getKey(), 256);
			buf.writeVarInt(zone.getValue());
		}
		return buf;
	}

	public static FarePolicy read(PacketByteBuf buf) {
		final int base = buf.readVarInt();
		final int step = buf.readVarInt();
		final int cap = buf.readVarInt();
		final int count = Math.min(buf.readVarInt(), MAX_ZONES);
		final Map<String, Integer> zones = new HashMap<>();
		for (int i = 0; i < count; i++) {
			zones.put(buf.readString(256), buf.readVarInt());
		}
		return new FarePolicy(base, step, cap, zones);
	}
}
