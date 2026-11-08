package com.aureliatransit.architecture.bridge.world;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Server init for the bridge generator: the {@code /atr_bridge} command, the tick that drives build/undo tasks and
 * previews, and cleanup when a player leaves or the server stops. The tick listener is idle unless a task is running.
 */
public final class BridgeSystems {

	private BridgeSystems() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BridgeCommand.register(dispatcher));
		ServerTickEvents.END_SERVER_TICK.register(BridgeSessions::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BridgeSessions.forgetPlayer(handler.getPlayer().getUuid()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BridgeSessions.clear());
	}
}
