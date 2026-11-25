/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom.mtr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The ported {@code Rail.getAngles} (task G-8) against MTR 4.0.5's own: how MTR picks a rail's two angles from two
 * nodes and their facings. 100,000 random inputs, including facings exactly at right angles to the line between the
 * nodes (where {@code similarFacing}'s "within 90°" is decided by its strict comparison).
 */
class RailGetAnglesOracleTest {
    @Test
    void portMatchesMtr405() {
        Random random = new Random(20261006L);
        int perpendicular = 0;
        for (int i = 0; i < 100_000; i++) {
            long x1 = random.nextInt(601) - 300;
            long z1 = random.nextInt(601) - 300;
            long x2 = x1 + random.nextInt(201) - 100;
            long z2 = z1 + random.nextInt(201) - 100;
            float facing1;
            float facing2;
            if (i % 4 == 0 && (x2 != x1 || z2 != z1)) {
                // A facing exactly 90° from the line between the nodes, when the line is on a 22.5° step.
                long dx = x2 - x1;
                long dz = z2 - z1;
                float line = (float) StrictMath.toDegrees(StrictMath.atan2(dz, dx));
                facing1 = line + (random.nextBoolean() ? 90 : -90);
                facing2 = line + (random.nextBoolean() ? 90 : -90);
                perpendicular++;
            } else {
                facing1 = 22.5F * (random.nextInt(32) - 8);
                facing2 = random.nextBoolean() ? 22.5F * (random.nextInt(32) - 8) : random.nextFloat() * 720 - 360;
            }
            var oracle = org.mtr.core.data.Rail.getAngles(new org.mtr.core.data.Position(x1, 64, z1), facing1, new org.mtr.core.data.Position(x2, 70, z2),
                    facing2);
            var port = Rail.getAngles(new Position(x1, 64, z1), facing1, new Position(x2, 70, z2), facing2);
            String input = String.format(Locale.ROOT, "(%d, %d) %s -> (%d, %d) %s", x1, z1, facing1, x2, z2, facing2);
            assertEquals(oracle.left().name(), port.left().name(), input);
            assertEquals(oracle.right().name(), port.right().name(), input);
        }
        System.out.printf(Locale.ROOT, "G-8: Rail.getAngles port = MTR 4.0.5 on 100,000 inputs (%,d with a facing at right angles)%n", perpendicular);
    }
}
