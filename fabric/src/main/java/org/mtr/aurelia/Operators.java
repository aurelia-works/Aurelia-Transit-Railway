package org.mtr.aurelia;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Predicate;

/**
 * Who may use ATR's tools. Two levels:
 * <ul>
 * <li>{@link #isOperator}: operators at permission level 2, and the host of a single-player world, so a solo world with
 * cheats off keeps them. A dedicated server has no host; LAN guests are not the host. The panel's Tools tab and
 * {@code /atr_jump} need this.</li>
 * <li>{@link #isOperatorOrCreative}: the above, or any player in creative mode. Everything else needs this: building
 * tools, zip lines, the dashboard shortcut, the ATR wrench, the panel's server settings and ATR's commands.</li>
 * </ul>
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

	public static boolean isOperatorOrCreative(PlayerEntity player) {
		return player.isCreative() || isOperator(player);
	}

	/** For the operator-only commands: level 2 (console, command blocks), or a player who counts by {@link #isOperator(PlayerEntity)}. */
	public static boolean isOperatorSource(ServerCommandSource source) {
		if (source.hasPermissionLevel(2)) {
			return true;
		}
		final ServerPlayerEntity player = source.getPlayer();
		return player != null && isOperator(player);
	}

	/** For ATR's other commands: level 2, or a player who counts by {@link #isOperatorOrCreative(PlayerEntity)}. */
	public static boolean isOperatorOrCreativeSource(ServerCommandSource source) {
		if (source.hasPermissionLevel(2)) {
			return true;
		}
		final ServerPlayerEntity player = source.getPlayer();
		return player != null && isOperatorOrCreative(player);
	}

	public static void setClientHost(Predicate<PlayerEntity> check) {
		clientHost = check;
	}
}
