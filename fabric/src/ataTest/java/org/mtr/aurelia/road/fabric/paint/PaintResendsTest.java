package org.mtr.aurelia.road.fabric.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tour 2026-11-24: in single player the client's channels arrive while the server is still building the player's
 * connection, and asking Fabric whether it can send then crashed every join. A resend asked for then must wait for the
 * end of the tick, and must still reach the player.
 */
final class PaintResendsTest {
    @Test
    void resendAskedForMidJoinWaitsForTheTickAndStillArrives() {
        UUID id = UUID.randomUUID();
        boolean[] connectionReady = {false};
        List<UUID> asked = new ArrayList<>();
        List<UUID> sent = new ArrayList<>();
        PaintResends<UUID> resends = new PaintResends<>(player -> player, player -> {
            asked.add(player);
            return connectionReady[0];
        }, sent::add);

        resends.request(id);
        assertEquals(List.of(), asked, "nothing may touch the connection while the player is still joining");

        connectionReady[0] = true;
        resends.flush();
        assertEquals(List.of(id), sent);

        resends.flush();
        assertEquals(List.of(id), sent, "one resend per request");
    }
}
