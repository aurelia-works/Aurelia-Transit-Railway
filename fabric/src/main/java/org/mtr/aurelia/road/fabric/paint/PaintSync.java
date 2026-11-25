/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.paint;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Sends road paint to players: all of a dimension's when a player arrives in it (joining, changing dimension,
 * respawning) at the end of that tick, then each tick's changes to the players there.
 */
public final class PaintSync {
    private static volatile PaintResends<ServerPlayerEntity> resends;

    private PaintSync() {
    }

    /** Called once, from the mod initialiser. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> resends = new PaintResends<>(
            id -> server.getPlayerManager().getPlayer(id),
            player -> !player.isDisconnected() && ServerPlayNetworking.canSend(player, PaintPacket.TYPE),
            PaintSync::sendAll));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> resends = null);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> resend(handler.getPlayer()));
        // A client's channels can arrive after it joins (and off the server thread), so it may hear only now.
        S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
            if (channels.contains(PaintPacket.ID)) {
                resend(handler.getPlayer());
            }
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> resend(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> resend(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (resends != null) {
                resends.flush();
            }
            for (ServerWorld world : server.getWorlds()) {
                Long2IntMap changes = PaintStore.of(world).drain();
                if (!changes.isEmpty()) {
                    List<PaintPacket> packets = packets(false, changes);
                    for (ServerPlayerEntity player : world.getPlayers()) {
                        send(player, packets);
                    }
                }
            }
        });
    }

    /** Queued, never sent from the event itself: see {@link PaintResends}. */
    private static void resend(ServerPlayerEntity player) {
        PaintResends<ServerPlayerEntity> current = resends;
        if (current != null) {
            current.request(player.getUuid());
        }
    }

    /** The player can receive (checked by {@link PaintResends}). */
    private static void sendAll(ServerPlayerEntity player) {
        for (PaintPacket packet : packets(true, PaintStore.of(player.getServerWorld()).cells())) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static void send(ServerPlayerEntity player, List<PaintPacket> packets) {
        if (ServerPlayNetworking.canSend(player, PaintPacket.TYPE)) {
            packets.forEach(packet -> ServerPlayNetworking.send(player, packet));
        }
    }

    /** {@code cells} in packets of at most {@link PaintPacket#MAX_CELLS}; a reset always sends one, even when empty. */
    private static List<PaintPacket> packets(boolean reset, Long2IntMap cells) {
        List<PaintPacket> packets = new ArrayList<>();
        int count = StrictMath.min(cells.size(), PaintPacket.MAX_CELLS);
        long[] positions = new long[count];
        int[] values = new int[count];
        int at = 0;
        for (Long2IntMap.Entry entry : cells.long2IntEntrySet()) {
            positions[at] = entry.getLongKey();
            values[at] = entry.getIntValue();
            at++;
            if (at == count) {
                packets.add(new PaintPacket(reset && packets.isEmpty(), positions, values));
                int left = cells.size() - packets.size() * PaintPacket.MAX_CELLS;
                count = StrictMath.min(StrictMath.max(left, 0), PaintPacket.MAX_CELLS);
                positions = new long[count];
                values = new int[count];
                at = 0;
            }
        }
        if (reset && packets.isEmpty()) {
            packets.add(new PaintPacket(true, new long[0], new int[0]));
        }
        return packets;
    }
}
