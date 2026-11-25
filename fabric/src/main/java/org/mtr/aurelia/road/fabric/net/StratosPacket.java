/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.net;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.road.proto.Protocol;

/**
 * One message on {@code aurelia:roads} (editor-protocol §2): the bytes core encoded, framed as a Fabric packet. Reading
 * only copies the bytes, on the network thread, and never fails; core decodes and checks them on the game thread.
 */
public record StratosPacket(byte[] bytes) implements FabricPacket {
    public static final Identifier ID = new Identifier(Protocol.CHANNEL);
    public static final PacketType<StratosPacket> TYPE = PacketType.create(ID, StratosPacket::read);

    private static StratosPacket read(PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return new StratosPacket(bytes);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBytes(this.bytes);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
