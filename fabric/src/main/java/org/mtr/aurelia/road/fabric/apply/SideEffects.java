/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.apply;

import net.minecraft.server.MinecraftServer;
import org.mtr.aurelia.road.fabric.track.TrackWriter;
import org.mtr.aurelia.road.spi.SideEffectWriter;

/**
 * Who carries out the operations' side effects on this server: the hidden MTR track. In Stratos another mod supplied
 * the writer through an entrypoint; inside ATR the roads and MTR are one mod, so it is always the {@link TrackWriter},
 * and every server offers track.
 *
 * <p>One writer per server, made on first use and dropped when it stops. Server thread only.
 */
public final class SideEffects {
    private static MinecraftServer server;
    private static SideEffectWriter writer = SideEffectWriter.UNAVAILABLE;

    private SideEffects() {
    }

    /** The writer for {@code minecraft}. */
    public static synchronized SideEffectWriter writer(MinecraftServer minecraft) {
        if (server != minecraft) {
            server = minecraft;
            writer = new TrackWriter(minecraft);
        }
        return writer;
    }

    /** The server stopped: the next one gets a writer of its own. */
    public static synchronized void stopped() {
        server = null;
        writer = SideEffectWriter.UNAVAILABLE;
    }
}
