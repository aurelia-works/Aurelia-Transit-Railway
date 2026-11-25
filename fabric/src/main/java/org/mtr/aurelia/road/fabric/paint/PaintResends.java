package org.mtr.aurelia.road.fabric.paint;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Players owed a full paint resend. Events only note the player; {@link #flush} sends at the end of the server tick.
 * In single player the client's channels arrive while the server is still building the player's connection, and asking
 * Fabric then whether it can send crashes the join, so nothing here touches the connection before the tick ends.
 * A player who still cannot receive at the flush is dropped: their channels' arrival asks again.
 */
final class PaintResends<P> {
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Function<UUID, P> online;
    private final Predicate<P> canReceive;
    private final Consumer<P> send;

    /** {@code online} returns null for a player who has left. */
    PaintResends(Function<UUID, P> online, Predicate<P> canReceive, Consumer<P> send) {
        this.online = online;
        this.canReceive = canReceive;
        this.send = send;
    }

    /** Any thread. */
    void request(UUID player) {
        pending.add(player);
    }

    /** Server thread, at the end of a tick. */
    void flush() {
        for (Iterator<UUID> iterator = pending.iterator(); iterator.hasNext(); ) {
            UUID id = iterator.next();
            iterator.remove();
            P player = online.apply(id);
            if (player != null && canReceive.test(player)) {
                send.accept(player);
            }
        }
    }
}
