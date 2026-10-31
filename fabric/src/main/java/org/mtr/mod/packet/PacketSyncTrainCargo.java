package org.mtr.mod.packet;

import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.cargo.ClientCargo;

/**
 * Server to client cargo snapshot. The packet is a new class name on the existing {@code mtr:packet} channel, which
 * vanilla MTR ignores when it does not recognise the name, so an older client stays connected and simply has no cargo.
 */
public final class PacketSyncTrainCargo extends PacketHandler {

	private final boolean replaceAll;
	private final String json;

	public PacketSyncTrainCargo(PacketBufferReceiver packetBufferReceiver) {
		replaceAll = packetBufferReceiver.readBoolean();
		json = packetBufferReceiver.readString();
	}

	public PacketSyncTrainCargo(boolean replaceAll, String json) {
		this.replaceAll = replaceAll;
		this.json = json == null ? "{\"vehicles\":{}}" : json;
	}

	@Override
	public void write(PacketBufferSender packetBufferSender) {
		packetBufferSender.writeBoolean(replaceAll);
		packetBufferSender.writeString(json);
	}

	@Override
	public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
	}

	@Override
	public void runClient() {
		ClientCargo.apply(replaceAll, json);
	}
}
