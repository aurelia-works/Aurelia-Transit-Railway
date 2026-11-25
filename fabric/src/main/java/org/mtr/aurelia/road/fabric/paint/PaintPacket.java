/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.paint;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.road.fabric.StratosMod;

/**
 * Road paint for the player's dimension ({@link PaintSync}): cells by block position, value 0 where paint went. With
 * {@code reset}, the client first forgets the paint it had (a player arriving, or changing dimension).
 */
public record PaintPacket(boolean reset, long[] positions, int[] values) implements FabricPacket {
    public static final Identifier ID = new Identifier(StratosMod.MOD_ID, "paint");
    public static final PacketType<PaintPacket> TYPE = PacketType.create(ID, PaintPacket::read);
    /** Cells per packet: 12 bytes each keeps a packet well inside the game's 1 MiB limit. */
    public static final int MAX_CELLS = 65_536;

    private static PaintPacket read(PacketByteBuf buf) {
        boolean reset = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_CELLS) {
            throw new IllegalArgumentException("A paint packet holds at most " + MAX_CELLS + " cells, not " + count);
        }
        long[] positions = new long[count];
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            positions[i] = buf.readLong();
            values[i] = buf.readInt();
        }
        return new PaintPacket(reset, positions, values);
    }

    @Override
    public void write(PacketByteBuf buf) {
        buf.writeBoolean(this.reset);
        buf.writeVarInt(this.positions.length);
        for (int i = 0; i < this.positions.length; i++) {
            buf.writeLong(this.positions[i]);
            buf.writeInt(this.values[i]);
        }
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }
}
