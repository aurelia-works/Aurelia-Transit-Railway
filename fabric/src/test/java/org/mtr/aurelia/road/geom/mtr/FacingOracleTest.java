/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom.mtr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * {@link MtrRails#facing}, the curve tool's rule for a player's facing (R-3), against MTR 4.0.5's own
 * {@code Angle.getQuadrant(yaw, true)}, which {@code BlockNode.getStateWithAngle} uses when a player places a node (spike
 * S6a): the same heading modulo 180° at every yaw, ties and unwrapped yaws included.
 */
class FacingOracleTest {
    @Test
    void theFacingIsTheNodeHeadingMtrWouldPlace() {
        Random random = new Random(20261006L);
        int checked = 0;
        for (int i = 0; i < 200_000; i++) {
            float yaw = switch (i % 4) {
                case 0 -> random.nextFloat() * 360 - 180;
                case 1 -> (random.nextInt(64) - 32) * 11.25F; // exactly on a tie between two headings
                case 2 -> random.nextFloat() * 4000 - 2000; // the game doesn't wrap its yaw
                default -> (random.nextInt(64) - 32) * 11.25F + (random.nextBoolean() ? 1e-3F : -1e-3F);
            };
            int quadrant = org.mtr.core.tool.Angle.getQuadrant(yaw, true);
            float nodeAngle = (quadrant * 22.5F + 90) % 180; // BlockNode's angle for that state (S6a)
            assertEquals(nodeAngle, (float) (MtrRails.facing(yaw).index() % 8 * 22.5), "yaw " + yaw);
            checked++;
        }
        assertEquals(200_000, checked);
    }
}
