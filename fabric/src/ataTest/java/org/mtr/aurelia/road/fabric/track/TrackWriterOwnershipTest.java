package org.mtr.aurelia.road.fabric.track;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.undo.SideEffect.TrackRail;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.RailType;

/**
 * Council 2026-11-24 (#180): player rails have no styles either, so "no styles" can't mean "the road's". A road's
 * delete or undo must remove only the rail it made, and a lane speed off MTR's connector speeds must still get track.
 */
final class TrackWriterOwnershipTest {
    private static final TrackRail LANE = new TrackRail(0, 64, 0, 0, 0, 64, 32, 0, 30, 30, TrackRail.Shape.QUADRATIC, 0);

    private static Rail rail(long speedA, long speedB, boolean isPlatform) {
        Position a = new Position(0, 64, 0);
        Position b = new Position(0, 64, 32);
        var angles = Rail.getAngles(a, 0, b, 0);
        return Rail.newRail(a, angles.left(), b, angles.right(), Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), speedA, speedB,
                isPlatform, false, true, false, true, TransportMode.TRAIN);
    }

    @Test
    void theRoadsOwnRailIsOurs() {
        assertTrue(TrackWriter.ours(rail(30, 30, false), LANE));
    }

    @Test
    void aPlayersStylelessRailBetweenTheSameNodesIsNot() {
        assertFalse(TrackWriter.ours(rail(80, 80, false), LANE), "another speed: a player's rail");
        assertFalse(TrackWriter.ours(rail(30, 30, true), LANE), "a platform: a player's rail");
    }

    @Test
    void aLaneSpeedOffMtrsSpeedsTakesTheNearestTypeBelow() {
        assertEquals(RailType.WOODEN, TrackWriter.railType(30));
        assertEquals(RailType.STONE, TrackWriter.railType(40));
        assertEquals(RailType.WOODEN, TrackWriter.railType(5));
    }
}
