package org.mtr.aurelia.road.road.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A road's MTR node must face exactly down its lane. ATR's node block has a fourth angle property, {@code is_11_25}
 * (32 node angles, #49), and a state that leaves it out takes Minecraft's default, true: every road node then read
 * 11.25° off and the hidden track between two nodes bent into an S.
 */
class MtrNodesAngleTest {
    @Test
    void everyRoadNodeReadsItsOwnHeadingInMtr() {
        for (int facing = 0; facing < MtrNodes.FACINGS; facing++) {
            Map<String, String> properties = new HashMap<>();
            String state = MtrNodes.state(facing, true);
            for (String property : state.substring(state.indexOf('[') + 1, state.length() - 1).split(",")) {
                String[] pair = property.split("=");
                properties.put(pair[0], pair[1]);
            }
            // BlockNode.getAngle, with a property the state doesn't name taking its default (true).
            double angle = (on(properties, "facing") ? 0 : 90) + (on(properties, "is_22_5") ? 22.5 : 0) + (on(properties, "is_45") ? 45 : 0)
                    + (on(properties, "is_11_25") ? 11.25 : 0);
            assertEquals(facing * 22.5 % 180, angle % 180, 1e-9, state);
        }
    }

    private static boolean on(Map<String, String> properties, String name) {
        return !"false".equals(properties.get(name));
    }
}
