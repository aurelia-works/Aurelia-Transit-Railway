package com.aureliatransit.architecture.wayfinding;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Server to client sync of the full {@link ServiceMessages} set (at most 17 short strings). Sent to a player when they
 * join and to everyone when a message changes; never polled.
 */
public final class ServiceMessageSync {

	public static final Identifier SYNC = AureliaTransitArchitecture.id("service_messages");

	private ServiceMessageSync() {
	}

	public static PacketByteBuf write(ServiceMessages messages) {
		return write(messages, java.util.List.of());
	}

	/**
	 * Messages, then the route holds. An older client stops after the messages and ignores the tail, so a mixed
	 * client still shows the disruption text. A current client reads the tail into the depot-health list.
	 */
	public static PacketByteBuf write(ServiceMessages messages, java.util.List<RouteHold> holds) {
		final PacketByteBuf buf = PacketByteBufs.create();
		messages.write(buf);
		final java.util.List<RouteHold> list = holds == null ? java.util.List.of() : holds;
		buf.writeVarInt(list.size());
		for (final RouteHold hold : list) {
			buf.writeVarInt(hold.messageId());
			buf.writeString(hold.route(), 64);
			buf.writeLong(hold.untilEpochMillis());
		}
		return buf;
	}

	public static void send(ServerPlayerEntity player, ServiceMessages messages) {
		send(player, messages, java.util.List.of());
	}

	public static void send(ServerPlayerEntity player, ServiceMessages messages, java.util.List<RouteHold> holds) {
		ServerPlayNetworking.send(player, SYNC, write(messages, holds));
	}

	public static void broadcast(MinecraftServer server, ServiceMessages messages) {
		broadcast(server, messages, ServiceMessageState.get(server).holds());
	}

	public static void broadcast(MinecraftServer server, ServiceMessages messages, java.util.List<RouteHold> holds) {
		for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			send(player, messages, holds);
		}
	}
}
