package com.aureliatransit.architecture.client.airport;

import com.aureliatransit.architecture.airport.AircraftSample;
import net.minecraft.client.MinecraftClient;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;

import java.util.ArrayList;
import java.util.List;

/**
 * Airplanes the client is already simulating, read once per tick and shared by every jet bridge. Door value is the
 * same signal {@code RenderVehicleHelper} drives from platform blocks.
 */
public final class JetBridgeScanner {

	private static long tick = Long.MIN_VALUE;
	private static List<AircraftSample> cached = List.of();

	private JetBridgeScanner() {
	}

	public static List<AircraftSample> samples() {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return List.of();
		}
		final long now = client.world.getTime();
		if (now == tick) {
			return cached;
		}
		tick = now;
		final List<AircraftSample> found = new ArrayList<>();
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			if (!vehicle.isTransportMode(TransportMode.AIRPLANE)) {
				continue;
			}
			final Vector head = vehicle.getHeadPosition();
			if (head == null) {
				continue;
			}
			final double door = vehicle.persistentVehicleData == null ? 0 : vehicle.persistentVehicleData.getDoorValue();
			found.add(new AircraftSample(head.x, head.y, head.z, vehicle.isMoving(), door, true));
		}
		cached = List.copyOf(found);
		return cached;
	}
}
