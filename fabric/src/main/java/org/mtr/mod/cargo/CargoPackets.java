package org.mtr.mod.cargo;

import org.mtr.mod.Init;
import org.mtr.mod.packet.PacketSyncTrainCargo;

/**
 * Registers the cargo packet from its own class so {@link Init} does not gain a lambda. Add-ons hook Init's numbered
 * lambdas, and a method reference written into {@code Init} would renumber them.
 */
public final class CargoPackets {

	private CargoPackets() {
	}

	public static void register() {
		Init.REGISTRY.registerPacket(PacketSyncTrainCargo.class, PacketSyncTrainCargo::new);
	}
}
