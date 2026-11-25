/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanParams.NodeAids;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.solve.LaneNode;

/**
 * R-6's planner acceptance: on 300 random City streets (straights, bends and S-bends, a third of them sloped) anywhere
 * in the world, every lane node has a full asphalt block under its spot; the wool plan differs from the plain one only
 * there (blue wool), the MTR-node plan only in the node blocks above (MTR's state for the lane's heading, where the
 * plain plan clears the air); and clearing markers writes the asphalt back, only where the wool is. From G-4+, 100 random
 * roads of each other layout too: residential 15 and 17, and the alley, whose one lane's nodes are its own end nodes.
 */
@Tag("slow")
@EnabledIfEnvironmentVariable(named = "ATR_SLOW_ROAD_TESTS", matches = ".+")
class NodeMarkersTest {
    private static final WorldLimits MTR = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
    private static final String ASPHALT = "minecraft:polished_deepslate";
    private static final String WOOL = "minecraft:blue_wool";

    @Test
    void markersAndNodesGoExactlyAtTheLaneNodes() {
        markers(RoadProfiles.CITY_17, 300, 20261006L);
    }

    @Test
    void markersAndNodesOfTheOtherLayouts() {
        for (RoadProfile profile : RoadProfiles.all()) {
            if (profile != RoadProfiles.CITY_17) {
                markers(profile, 100, 20261007L);
            }
        }
    }

    private static void markers(RoadProfile profile, int count, long seed) {
        Random random = new Random(seed);
        int roads = 0;
        int nodes = 0;
        int sloped = 0;
        int intermediateEnds = 0;
        for (int attempt = 0; roads < count; attempt++) {
            assertTrue(attempt < 5_000, "too few roads accepted");
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            int y = random.nextInt(200);
            Heading from = Heading.of(random.nextInt(16));
            int rise = attempt % 3 == 0 ? random.nextInt(5) - 2 : 0;
            End start = End.at(x, y, z, from);
            End end = switch (attempt % 3) {
                case 0 -> {
                    End along = RoadPlanner.snapEnd(start, 30 + random.nextInt(120));
                    yield End.at(along.x(), y + rise, along.z(), from);
                }
                case 1 -> {
                    Vec2 p = from.tangent().times(60 + random.nextInt(80)).plus(from.normal().times(random.nextInt(17) - 8));
                    yield End.at(x + (int) StrictMath.rint(p.x()), y + rise, z + (int) StrictMath.rint(p.z()), from);
                }
                default -> End.at(x + random.nextInt(241) - 120, y + rise, z + random.nextInt(241) - 120, Heading.of(random.nextInt(16)));
            };
            RoadPlanParams plain = RoadPlanParams.curve(profile.id(), start, end);
            PlanResult result = RoadPlanner.plan(plain, MTR);
            if (!result.isPlanned()) {
                continue;
            }
            roads++;
            sloped += rise != 0 ? 1 : 0;
            intermediateEnds += (start.heading().isIntermediate() ? 1 : 0) + (end.heading().isIntermediate() ? 1 : 0);
            Map<Long, String> road = states(result.plan().orElseThrow());
            List<LaneNode> laneNodes = RoadPlanner.laneNodes(plain).orElseThrow();
            nodes += laneNodes.size();
            assertEquals(2 * profile.lanes().size(), laneNodes.size(), plain + ": a node at each end of every lane");
            Map<Long, String> wool = new HashMap<>(road);
            Map<Long, String> preoriented = new HashMap<>(road);
            for (LaneNode node : laneNodes) {
                long spot = BlockChange.positionKey(node.x(), node.y(), node.z());
                long above = BlockChange.positionKey(node.x(), node.nodeY(), node.z());
                assertEquals(ASPHALT, road.get(spot), plain + ": a full asphalt block under " + node);
                assertEquals("minecraft:air", road.get(above), plain + ": the node block is cleared, " + node);
                wool.put(spot, WOOL);
                preoriented.put(above, MtrNodes.state(node.mtrFacing()));
            }
            assertEquals(wool, states(planned(plain.withNodeAids(NodeAids.WOOL))), plain + ": wool exactly at the nodes");
            assertEquals(preoriented, states(planned(plain.withNodeAids(NodeAids.PREORIENTED))), plain + ": nodes exactly in the node blocks");

            PlanResult clear = RoadPlanner.plan(RoadPlanParams.clearMarkers(plain.withNodeAids(NodeAids.WOOL)), MTR);
            Map<Long, String> asphalt = new HashMap<>();
            Map<Long, String> markers = new HashMap<>();
            laneNodes.forEach(node -> {
                asphalt.put(BlockChange.positionKey(node.x(), node.y(), node.z()), ASPHALT);
                markers.put(BlockChange.positionKey(node.x(), node.y(), node.z()), WOOL);
            });
            assertEquals(asphalt, states(clear.plan().orElseThrow()), plain + ": clearing writes asphalt at the marker spots");
            assertEquals(markers, states(clear.onlyWhere().orElseThrow()), plain + ": only where the wool is");
        }
        System.out.printf("NodeMarkersTest: %d %s roads (%d sloped, %d ends at 22.5 degrees), %d lane nodes, every one on asphalt%n", roads,
                profile.id(), sloped, intermediateEnds, nodes);
    }

    private static BlockPlan planned(RoadPlanParams params) {
        PlanResult result = RoadPlanner.plan(params, MTR);
        assertTrue(result.isPlanned(), params + ": " + result);
        return result.plan().orElseThrow();
    }

    private static Map<Long, String> states(BlockPlan plan) {
        Map<Long, String> states = new HashMap<>();
        plan.blocks().forEach(block -> states.put(BlockChange.positionKey(block.x(), block.y(), block.z()), block.state()));
        return states;
    }
}
