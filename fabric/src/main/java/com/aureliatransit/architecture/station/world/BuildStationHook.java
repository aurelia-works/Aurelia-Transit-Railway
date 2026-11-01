package com.aureliatransit.architecture.station.world;

import net.minecraft.server.world.ServerWorld;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Guided station placement calls the same station create the dashboard uses. The hook stays next to {@link MtrStations}
 * because that class is package-private.
 */
public final class BuildStationHook {

	private BuildStationHook() {
	}

	public static void create(ServerWorld world, int minX, int minZ, int maxX, int maxZ, int y, String name, int colour, BiConsumer<Long, String> done) {
		final StationZone zone = new StationZone(name, colour & 0xFFFFFF, minX, minZ, maxX, maxZ, y, List.of(), List.of());
		MtrStations.create(world, zone, result -> done.accept(result.id(), result.existing()));
	}
}
