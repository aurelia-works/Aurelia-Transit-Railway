package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.network.ModPackets;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Common/server init of the station generator: the {@code /ata_station} actions and {@code /ata_guide} commands, the guide
 * book on an operator's first join, one server tick listener (idle unless a build or preview is running), the wrench's
 * left-click and cleanup when players leave or the server stops. The word command is registered only for the scripted visual
 * shots ({@code -Datr.visualshots=...}) and the game tests ({@code -Dfabric-api.gametest}); players plan from the wrench and
 * the ATR menu. The wrench item is registered in
 * {@code ModItems} and its screen packet in {@code ModPackets}.
 */
public final class StationSystems {

	private StationSystems() {
	}

	/** True for the scripted visual shots and the game tests, which plan by words; players plan from the wrench. */
	private static boolean wordPlans() {
		return System.getProperty("atr.visualshots") != null || System.getProperty("fabric-api.gametest") != null;
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			StationCommand.register(dispatcher, wordPlans());
			StationGuide.register(dispatcher);
			com.aureliatransit.architecture.earthworks.world.EarthworksCommand.register(dispatcher);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			StationGuide.welcome(handler.getPlayer());
			ModPackets.sendStationPace(handler.getPlayer());
		});
		ServerTickEvents.END_SERVER_TICK.register(StationSessions::tick);
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> StationWrenchItem.attack(player, world, hand));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			StationSessions.forgetPlayer(handler.getPlayer().getUuid());
			com.aureliatransit.architecture.earthworks.world.EarthworksCommand.forget(handler.getPlayer().getUuid());
		});
		// before the worlds save, so a build running when the server stops keeps its undo
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> StationSessions.flushUndo());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> StationSessions.clear());
	}
}
