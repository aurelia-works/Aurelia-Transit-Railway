/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.net;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.mtr.aurelia.road.proto.EditorServer;

/**
 * Who holds a Stratos permission node (editor-protocol §7), by operator level alone: {@link #ADMIN_LEVEL} for
 * {@code stratos.limits.bypass} and {@code stratos.admin}, so that operators still have the limits, and
 * {@link #EDIT_LEVEL} for the rest. The singleplayer world's owner holds every node, so cheats needn't be on. Stratos
 * asked a permissions mod first; ATR checks op levels only, so it needs no permissions library.
 */
public final class StratosPermissions {
    /** The operator level each node needs. */
    public static final int EDIT_LEVEL = 2;
    /** The operator level {@code stratos.limits.bypass} and {@code stratos.admin} need. */
    public static final int ADMIN_LEVEL = 4;
    /** Baking the world to vanilla and redrawing every road's markings; later journal repair and data commands. */
    public static final String NODE_ADMIN = EditorServer.NODE_ADMIN;

    private StratosPermissions() {
    }

    /** Whether {@code player} holds {@code node}. */
    public static boolean has(ServerPlayerEntity player, String node) {
        MinecraftServer server = player.getServer();
        if (server != null && server.isHost(player.getGameProfile())) {
            return true;
        }
        return player.hasPermissionLevel(level(node));
    }

    /** The operator level {@code node} needs. */
    public static int level(String node) {
        return node.equals(EditorServer.NODE_LIMITS_BYPASS) || node.equals(NODE_ADMIN) ? ADMIN_LEVEL : EDIT_LEVEL;
    }
}
