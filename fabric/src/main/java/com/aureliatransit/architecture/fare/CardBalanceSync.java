package com.aureliatransit.architecture.fare;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Tells a player their MTR balance when they look at their transit card, so the card screen can show it. The balance
 * lives on the server (MTR keeps it on the scoreboard), so the client cannot read it on its own.
 */
public final class CardBalanceSync {

	public static final Identifier CARD_BALANCE = AureliaTransitArchitecture.id("card_balance");

	private CardBalanceSync() {
	}

	public static void send(ServerPlayerEntity player, int balance) {
		if (ServerPlayNetworking.canSend(player, CARD_BALANCE)) {
			final PacketByteBuf buf = PacketByteBufs.create();
			buf.writeVarInt(balance);
			ServerPlayNetworking.send(player, CARD_BALANCE, buf);
		}
	}
}
