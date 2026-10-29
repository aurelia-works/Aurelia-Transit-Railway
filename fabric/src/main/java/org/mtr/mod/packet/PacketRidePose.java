package org.mtr.mod.packet;

import org.mtr.aurelia.client.RideClient;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.mapper.MinecraftServerHelper;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Init;

import java.util.UUID;

/**
 * A player sits or stands while riding. Client to server: the sender's pose (the uuid sent is ignored; the server uses
 * the sender's own). Server to client: one player's pose, for everyone in that world.
 */
public final class PacketRidePose extends PacketHandler {

	private final UUID uuid;
	private final boolean sitting;

	public PacketRidePose(PacketBufferReceiver packetBufferReceiver) {
		uuid = new UUID(packetBufferReceiver.readLong(), packetBufferReceiver.readLong());
		sitting = packetBufferReceiver.readBoolean();
	}

	public PacketRidePose(UUID uuid, boolean sitting) {
		this.uuid = uuid;
		this.sitting = sitting;
	}

	@Override
	public void write(PacketBufferSender packetBufferSender) {
		packetBufferSender.writeLong(uuid.getMostSignificantBits());
		packetBufferSender.writeLong(uuid.getLeastSignificantBits());
		packetBufferSender.writeBoolean(sitting);
	}

	@Override
	public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
		final PacketRidePose packet = new PacketRidePose(serverPlayerEntity.getUuid(), sitting);
		MinecraftServerHelper.iteratePlayers(serverPlayerEntity.getServerWorld(), otherPlayer -> Init.REGISTRY.sendPacketToClient(otherPlayer, packet));
	}

	@Override
	public void runClient() {
		RideClient.setOther(uuid, sitting);
	}
}
