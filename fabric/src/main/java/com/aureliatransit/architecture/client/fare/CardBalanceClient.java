package com.aureliatransit.architecture.client.fare;

import com.aureliatransit.architecture.fare.CardBalanceSync;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** The balance the server last sent when the player looked at their card; unknown until it does, and after leaving. */
public final class CardBalanceClient {

	/** A balance older than this is not shown: it may have changed at a gate since. */
	private static final long FRESH_MILLIS = 60_000;

	private static int balance;
	private static long receivedAt;

	private CardBalanceClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CardBalanceSync.CARD_BALANCE, (client, handler, buf, response) -> {
			final int received = buf.readVarInt();
			client.execute(() -> {
				balance = received;
				receivedAt = System.currentTimeMillis();
			});
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> receivedAt = 0));
	}

	/** The balance, or null when the server has not said it in the last minute. */
	public static Integer recent() {
		return receivedAt > 0 && System.currentTimeMillis() - receivedAt < FRESH_MILLIS ? balance : null;
	}
}
