/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.road.raster.KerbEdge.Facing;
import org.mtr.aurelia.road.road.raster.KerbEdge.Shape;

/** B-1: where a kerb meets the road, from the road's columns around it. */
class KerbEdgeTest {
    private static final BlockXZ HERE = new BlockXZ(0, 0);

    private static Optional<KerbEdge> with(BlockXZ... road) {
        Set<BlockXZ> set = Set.of(road);
        return KerbEdge.at(HERE, set::contains);
    }

    @Test
    void oneSideOnTheRoadIsAStraightKerbFacingIt() {
        assertEquals(Optional.of(new KerbEdge(Facing.NORTH, Shape.STRAIGHT)), with(new BlockXZ(0, -1), new BlockXZ(1, -1), new BlockXZ(-1, -1)));
        assertEquals(Optional.of(new KerbEdge(Facing.EAST, Shape.STRAIGHT)), with(new BlockXZ(1, 0)));
        assertEquals(Optional.of(new KerbEdge(Facing.SOUTH, Shape.STRAIGHT)), with(new BlockXZ(0, 1), new BlockXZ(1, 1)));
        assertEquals(Optional.of(new KerbEdge(Facing.WEST, Shape.STRAIGHT)), with(new BlockXZ(-1, 0)));
    }

    @Test
    void twoSidesBesideEachOtherAreAnOuterCornerFacingTheFirstClockwise() {
        assertEquals(Optional.of(new KerbEdge(Facing.NORTH, Shape.OUTER)), with(new BlockXZ(0, -1), new BlockXZ(1, 0)));
        assertEquals(Optional.of(new KerbEdge(Facing.EAST, Shape.OUTER)), with(new BlockXZ(1, 0), new BlockXZ(0, 1)));
        assertEquals(Optional.of(new KerbEdge(Facing.SOUTH, Shape.OUTER)), with(new BlockXZ(0, 1), new BlockXZ(-1, 0)));
        assertEquals(Optional.of(new KerbEdge(Facing.WEST, Shape.OUTER)), with(new BlockXZ(-1, 0), new BlockXZ(0, -1)));
    }

    @Test
    void onlyACornerOnTheRoadIsAnInnerCorner() {
        assertEquals(Optional.of(new KerbEdge(Facing.NORTH, Shape.INNER)), with(new BlockXZ(1, -1)));
        assertEquals(Optional.of(new KerbEdge(Facing.EAST, Shape.INNER)), with(new BlockXZ(1, 1)));
        assertEquals(Optional.of(new KerbEdge(Facing.SOUTH, Shape.INNER)), with(new BlockXZ(-1, 1)));
        assertEquals(Optional.of(new KerbEdge(Facing.WEST, Shape.INNER)), with(new BlockXZ(-1, -1)));
    }

    @Test
    void awayFromTheRoadThereIsNoKerb() {
        assertEquals(Optional.empty(), with());
        assertEquals(Optional.empty(), with(new BlockXZ(2, 0), new BlockXZ(0, -2)));
    }

    @Test
    void aStripOneBlockWideTakesTheFirstWay() {
        assertEquals(Optional.of(new KerbEdge(Facing.NORTH, Shape.STRAIGHT)), with(new BlockXZ(0, -1), new BlockXZ(0, 1)));
        assertEquals(Optional.of(new KerbEdge(Facing.NORTH, Shape.OUTER)), with(new BlockXZ(0, -1), new BlockXZ(1, 0), new BlockXZ(0, 1)));
    }
}
