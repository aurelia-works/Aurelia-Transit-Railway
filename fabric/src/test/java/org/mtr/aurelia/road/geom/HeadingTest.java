/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The 16 MTR headings and the conventions of spec roads-v1 §1. */
class HeadingTest {
    @Test
    void cardinalsAreExact() {
        assertEquals(new Vec2(1, 0), Heading.E.tangent());
        assertEquals(new Vec2(0, 1), Heading.E.normal(), "facing east, right is south");
        assertEquals(new Vec2(0, 1), Heading.S.tangent());
        assertEquals(new Vec2(-1, 0), Heading.S.normal(), "facing south, right is west");
        assertEquals(new Vec2(-1, 0), Heading.W.tangent());
        assertEquals(new Vec2(0, -1), Heading.W.normal());
        assertEquals(new Vec2(0, -1), Heading.N.tangent());
        assertEquals(new Vec2(1, 0), Heading.N.normal());
    }

    @Test
    void everyHeadingIsAUnitVectorWithItsNormalToTheRight() {
        for (Heading heading : Heading.values()) {
            assertEquals(22.5 * heading.index(), heading.degrees(), heading.name());
            assertEquals(StrictMath.toRadians(heading.degrees()), heading.radians(), heading.name());
            assertEquals(1, heading.tangent().length(), 1e-15, heading.name());
            assertEquals(1, heading.normal().length(), 1e-15, heading.name());
            assertEquals(0, heading.tangent().dot(heading.normal()), 1e-15, heading.name());
            assertEquals(1, heading.tangent().cross(heading.normal()), 1e-15, heading.name() + ": the normal is clockwise of the tangent");
            assertEquals(heading.tangent().rotateQuarterClockwise(), heading.normal(), heading.name());
            if (!heading.isCardinal()) {
                // Away from the cardinals the values are MTR's: StrictMath of the angle normalised into
                // [-180, 180), as MTR's Angle does (MtrRailsTest checks them against MTR itself).
                double degrees = heading.degrees() >= 180 ? heading.degrees() - 360 : heading.degrees();
                assertEquals(StrictMath.cos(StrictMath.toRadians(degrees)), heading.cos(), heading.name());
                assertEquals(StrictMath.sin(StrictMath.toRadians(degrees)), heading.sin(), heading.name());
                assertEquals(StrictMath.cos(heading.radians()), heading.cos(), 1e-15, heading.name());
            }
        }
    }

    @Test
    void indicesWrapRound() {
        for (int index = -32; index <= 32; index++) {
            assertEquals(StrictMath.floorMod(index, 16), Heading.of(index).index(), "of(" + index + ")");
        }
        assertSame(Heading.E, Heading.of(16));
        assertSame(Heading.ENE, Heading.of(-1));
    }

    @Test
    void rotateOppositeAndMirrorRoundTrip() {
        for (Heading heading : Heading.values()) {
            for (int steps = -32; steps <= 32; steps++) {
                assertSame(heading, heading.rotate(steps).rotate(-steps), heading + " by " + steps);
                assertSame(Heading.of(heading.index() + steps), heading.rotate(steps));
            }
            assertSame(heading, heading.opposite().opposite());
            assertTrue(heading.isParallel(heading.opposite()));
            assertTrue(heading.isParallel(heading));
            assertFalse(heading.isParallel(heading.rotate(1)));
            assertSame(heading, heading.mirrored().mirrored());
            assertEquals(StrictMath.floorMod(16 - heading.index(), 16), heading.mirrored().index());
            assertEquals(0, heading.tangent().mirrored().distanceTo(heading.mirrored().tangent()), 1e-15, heading.name());
        }
        assertSame(Heading.E, Heading.E.mirrored());
        assertSame(Heading.W, Heading.W.mirrored());
        assertSame(Heading.N, Heading.S.mirrored());
        assertSame(Heading.ENE, Heading.ESE.mirrored());
    }

    @Test
    void stepsToIsTheSignedChangeFromMinus7ToPlus8() {
        for (Heading from : Heading.values()) {
            for (Heading to : Heading.values()) {
                int steps = from.stepsTo(to);
                assertTrue(steps >= -7 && steps <= 8, from + " -> " + to + " = " + steps);
                assertSame(to, from.rotate(steps), from + " -> " + to);
            }
            assertEquals(8, from.stepsTo(from.opposite()), "a U-turn is always +8");
            assertEquals(0, from.stepsTo(from));
        }
        // Spec roads-v1 §11.5: 315° -> 45° is a signed change of +90°.
        assertEquals(4, Heading.NE.stepsTo(Heading.SE));
        assertEquals(-4, Heading.SE.stepsTo(Heading.NE));
    }

    @Test
    void familiesSplitTheSixteenHeadings() {
        int cardinal = 0;
        int diagonal = 0;
        int intermediate = 0;
        for (Heading heading : Heading.values()) {
            int kinds = (heading.isCardinal() ? 1 : 0) + (heading.isDiagonal() ? 1 : 0) + (heading.isIntermediate() ? 1 : 0);
            assertEquals(1, kinds, heading.name());
            cardinal += heading.isCardinal() ? 1 : 0;
            diagonal += heading.isDiagonal() ? 1 : 0;
            intermediate += heading.isIntermediate() ? 1 : 0;
        }
        assertEquals(4, cardinal);
        assertEquals(4, diagonal);
        assertEquals(8, intermediate);
        assertTrue(Heading.SE.isDiagonal() && Heading.SW.isDiagonal() && Heading.NW.isDiagonal() && Heading.NE.isDiagonal());
    }

    @Test
    void minecraftYawIsThetaMinus90() {
        assertEquals(-90, Heading.E.minecraftYaw());
        assertEquals(0, Heading.S.minecraftYaw());
        assertEquals(90, Heading.W.minecraftYaw());
        assertEquals(-180, Heading.N.minecraftYaw());
        assertEquals(-67.5, Heading.ESE.minecraftYaw());
        assertEquals(135, Heading.NW.minecraftYaw());
        assertEquals(-112.5, Heading.ENE.minecraftYaw());
        for (Heading heading : Heading.values()) {
            double yaw = heading.minecraftYaw();
            assertTrue(yaw >= -180 && yaw < 180, heading + ": " + yaw);
            assertEquals(0, StrictMath.floorMod((long) ((heading.degrees() - 90 - yaw) * 2), 720L), heading.name());
        }
    }
}
