package org.mtr.aurelia;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;

import java.util.function.Predicate;

/**
 * Who may use ATR's shared tools (building, zip lines, the dashboard, the panel's Tools tab): operators at permission
 * level 2, and the host of a single-player world, so a solo world with cheats off keeps them. A dedicated server has
 * no host, so it stays op-only. LAN guests are not the host.
 */
public final class Operators {

	/** Set by the client so item code on the client side knows its player hosts the world; false on a server. */
	private static Predicate<PlayerEntity> clientHost = player -> false;

	private Operators() {
	}

	public static boolean isOperator(PlayerEntity player) {
		if (player.hasPermissionLevel(2)) {
			return true;
		}
		if (player.getWorld().isClient()) {
			return clientHost.test(player);
		}
		final MinecraftServer server = player.getServer();
		return server != null && server.isHost(player.getGameProfile());
	}

	public static void setClientHost(Predicate<PlayerEntity> check) {
		clientHost = check;
	}
}
