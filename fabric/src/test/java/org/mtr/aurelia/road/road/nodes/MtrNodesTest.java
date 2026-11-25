/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.road.solve.LaneNode;

/** MTR's node block as state strings (task R-6; S6a's facts). */
class MtrNodesTest {
    /**
     * Spike S6a's {@code S6Check.nodeState}, as it was checked in game against MTR 4.0.5's own {@code BlockNode.getAngle}
     * (and is exactly the state MTR places for a player looking along the lane).
     */
    private static String s6a(LaneNode node) {
        int i = node.mtrFacing();
        return "mtr:rail[facing=" + (i < 4) + ",is_22_5=" + (i % 2 == 1) + ",is_45=" + (i % 4 >= 2) + ",is_11_25=false,is_connected=false,waterlogged=false]";
    }

    @Test
    void theStatesAreS6asAndEveryPropertyIsNamed() {
        for (Heading heading : Heading.values()) {
            LaneNode node = new LaneNode(1, 0, 64, 0, heading);
            assertEquals(s6a(node), MtrNodes.state(node.mtrFacing()), heading.toString());
        }
        assertEquals("mtr:rail[facing=true,is_22_5=false,is_45=false,is_11_25=false,is_connected=false,waterlogged=false]", MtrNodes.state(0));
        assertEquals("mtr:rail[facing=false,is_22_5=true,is_45=true,is_11_25=false,is_connected=false,waterlogged=false]", MtrNodes.state(7));
        assertThrows(IllegalArgumentException.class, () -> MtrNodes.state(8));
    }

    @Test
    void headingsAreReadBackInAnyOrder() {
        for (int i = 0; i < MtrNodes.FACINGS; i++) {
            assertEquals(OptionalInt.of(i), MtrNodes.facing(MtrNodes.state(i)));
        }
        assertEquals(OptionalInt.of(6), MtrNodes.facing("mtr:rail[waterlogged=false,is_45=true,is_connected=true,facing=false,is_22_5=false]"),
                "a connected node, its properties in another order");
        assertEquals(OptionalInt.empty(), MtrNodes.facing("mtr:rail[facing=true,is_45=false]"), "is_22_5 missing");
        assertEquals(OptionalInt.empty(), MtrNodes.facing("minecraft:blue_wool"));
        assertEquals(OptionalInt.empty(), MtrNodes.facing("mtr:rail_connector_40"));
        assertTrue(MtrNodes.isNode("mtr:rail"));
        assertTrue(MtrNodes.isNode(MtrNodes.state(3)));
        assertFalse(MtrNodes.isNode("mtr:rail_slope"));
    }

    @Test
    void theYawRuleIsMtrsAndNamesComeInPairs() {
        for (int quarter = -1440; quarter < 1440; quarter++) {
            float yaw = quarter / 4F;
            assertEquals(MtrRails.facing(yaw).index() % 8, MtrNodes.facingFor(yaw), "yaw " + yaw);
        }
        assertEquals(0, MtrNodes.facingFor(-90F), "facing east (yaw -90) gives an east-west node");
        assertEquals(0, MtrNodes.facingFor(90F), "and so does facing west");
        assertEquals(4, MtrNodes.facingFor(0F), "facing south gives a north-south node");
        assertEquals(6, MtrNodes.facingFor(-135F), "facing north-east gives SW-NE");
        assertArrayEquals(new String[] {"E", "W"}, MtrNodes.names(0));
        assertArrayEquals(new String[] {"ESE", "WNW"}, MtrNodes.names(1));
        assertArrayEquals(new String[] {"SW", "NE"}, MtrNodes.names(6));
        assertTrue(MtrNodes.isNodeItem("mtr:rail"));
        assertTrue(MtrNodes.isNodeItem("mtr:rail_connector_40_one_way"));
        assertFalse(MtrNodes.isNodeItem("mtr:brush"));
    }
}
