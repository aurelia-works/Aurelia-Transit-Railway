package com.aureliatransit.architecture.bridge.world;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Server init for the bridge generator: the {@code /atr_bridge undo} command, the tick that drives build/undo tasks, and
 * cleanup when the server stops. The tick listener is idle unless a task is running.
 */
public final class BridgeSystems {

	private BridgeSystems() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> BridgeCommand.register(dispatcher));
		RailBridges.registerPackets();
		ServerTickEvents.END_SERVER_TICK.register(BridgeSessions::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BridgeSessions.clear());
	}
}
