/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.nodes.NodeCheck.Status;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.spi.BlockReader;

/** The wrong-node checker (task R-6; roads-v1 §9). */
class NodeCheckTest {
    private static final String WOOL = "minecraft:blue_wool";
    private static final String ASPHALT = "minecraft:polished_deepslate";
    private static final String AIR = "minecraft:air";

    /** A world of air above y 64 and asphalt at it, with blocks set on top; nothing loaded beyond x 1000. */
    private final Map<String, String> blocks = new HashMap<>();
    private final BlockReader world = (x, y, z) -> {
        if (x > 1000) {
            return Optional.empty();
        }
        String set = this.blocks.get(x + " " + y + " " + z);
        return Optional.of(set != null ? set : y <= 64 ? ASPHALT : AIR);
    };

    private void set(int x, int y, int z, String state) {
        this.blocks.put(x + " " + y + " " + z, state);
    }

    /** Lane 1's two nodes and lane 2's two, east-west, the surface at y 64 (nodes in y 65). */
    private static final List<LaneNode> NODES = List.of(new LaneNode(1, 0, 64, -3, Heading.E), new LaneNode(1, 40, 64, -3, Heading.E),
            new LaneNode(2, 0, 64, 3, Heading.E), new LaneNode(2, 40, 64, 3, Heading.E));

    @Test
    void eachNodeGetsTheFirstStatusThatFits() {
        NODES.forEach(node -> set(node.x(), node.y(), node.z(), WOOL)); // a wool road
        set(0, 65, -3, MtrNodes.state(0)); // lane 1, node 1: right
        set(40, 65, -3, MtrNodes.state(6)); // lane 1, node 2: SW-NE, not E-W
        // lane 2, node 1: still to place (its marker is there)
        set(40, 64, 3, MtrNodes.state(0)); // lane 2, node 2: in the marker spot, a block too low
        NodeCheck.Report report = NodeCheck.check(NODES, Optional.of(WOOL), this.world);
        assertEquals(List.of(Status.RIGHT, Status.WRONG_HEADING, Status.NOT_PLACED, Status.TOO_LOW), statuses(report));
        assertEquals(OptionalInt.of(6), report.results().get(1).found());
        assertEquals(Optional.of(new NodeCheck.Spot(40, 64, 3)), report.results().get(3).foundAt());
        assertEquals(List.of(1, 2, 1, 2), report.results().stream().map(NodeCheck.Result::number).toList(), "numbered along each lane");
        assertEquals(1, report.right());
        assertEquals(2, report.wrong());
        assertEquals(1, report.toPlace());
        assertEquals(3, report.markers(), "three markers are still wool");
    }

    @Test
    void misplacedAndMissingNodes() {
        NODES.forEach(node -> set(node.x(), node.y(), node.z(), WOOL));
        set(1, 65, -3, MtrNodes.state(0)); // lane 1, node 1: one block east of its spot
        set(40, 66, -3, MtrNodes.state(0)); // lane 1, node 2: a block too high
        set(0, 64, 3, ASPHALT); // lane 2, node 1: marker cleared, no node
        set(40, 65, 3, "minecraft:torch"); // lane 2, node 2: something else on its marker: still to place
        NodeCheck.Report report = NodeCheck.check(NODES, Optional.of(WOOL), this.world);
        assertEquals(List.of(Status.ONE_OFF, Status.TOO_HIGH, Status.MISSING, Status.NOT_PLACED), statuses(report));
        assertEquals(Optional.of(new NodeCheck.Spot(1, 65, -3)), report.results().get(0).foundAt());
        assertEquals(3, report.wrong());
    }

    @Test
    void roadsWithoutMarkersWaitOnEmptyNodeBlocks() {
        set(0, 65, -3, MtrNodes.state(0));
        set(40, 65, 3, "minecraft:oak_fence");
        NodeCheck.Report report = NodeCheck.check(NODES, Optional.empty(), this.world);
        assertEquals(List.of(Status.RIGHT, Status.NOT_PLACED, Status.NOT_PLACED, Status.MISSING), statuses(report));
        assertEquals(0, report.markers());
    }

    @Test
    void connectedNodesAndUnloadedChunks() {
        set(0, 65, -3, "mtr:rail[facing=true,is_22_5=false,is_45=false,is_connected=true,waterlogged=false]");
        List<LaneNode> far = List.of(NODES.get(0), new LaneNode(1, 2000, 64, -3, Heading.E));
        NodeCheck.Report report = NodeCheck.check(far, Optional.of(WOOL), this.world);
        assertEquals(List.of(Status.RIGHT, Status.NOT_LOADED), statuses(report), "a joined node is right; an unloaded one isn't judged");
        assertEquals(0, report.wrong());
    }

    @Test
    void headingsCompareModulo180() {
        List<LaneNode> westward = List.of(new LaneNode(1, 0, 64, -3, Heading.W), new LaneNode(1, 40, 64, -3, Heading.WNW));
        set(0, 65, -3, MtrNodes.state(0));
        set(40, 65, -3, MtrNodes.state(1));
        assertEquals(List.of(Status.RIGHT, Status.RIGHT), statuses(NodeCheck.check(westward, Optional.empty(), this.world)),
                "nodes have no front: W is E-W, WNW is ESE-WNW");
    }

    private static List<Status> statuses(NodeCheck.Report report) {
        return report.results().stream().map(NodeCheck.Result::status).toList();
    }
}
