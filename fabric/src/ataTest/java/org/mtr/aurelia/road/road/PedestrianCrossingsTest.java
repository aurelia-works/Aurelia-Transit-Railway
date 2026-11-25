package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.road.junction.JunctionTemplate;
import org.mtr.aurelia.road.road.junction.JunctionTemplates;
import org.mtr.aurelia.road.road.junction.PlacedJunction;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.tool.RoadSettings;
import org.mtr.aurelia.road.road.tool.StraightTool;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;

/**
 * #193: crossings come both ways. The road tool's Crossing option puts one across the middle of the road, kept in the
 * road's parameters so a rebuild (undo, redo and re-plans plan from them) puts it back; and a junction drops the kerbs
 * across each side road's mouth by itself, as part of its own footprint.
 */
class PedestrianCrossingsTest {
    private static final String DROPPED = "stratos:dropped_kerb[";

    @Test
    void theRoadToolsCrossingOptionDropsTheKerbsMidRoadAndARebuildKeepsThem() throws Exception {
        RoadSettings settings = new RoadSettings();
        settings.setNodeAids(RoadPlanParams.NodeAids.NONE);
        settings.toggleCrossings();
        StraightTool tool = new StraightTool(settings);
        tool.input(new ToolInput.Use(Optional.of(new Target(0, 64, 0, 5)), -90F));
        tool.input(new ToolInput.Aim(Optional.of(new Target(40, 64, 0, 5)), -90F));
        RoadPlanParams params = tool.params().orElseThrow();
        List<BlockPlan.Block> dropped = dropped(RoadPlanner.plan(params, WorldLimits.OVERWORLD));
        assertEquals(6, dropped.size(), "three blocks of flush kerb each side: " + dropped);
        for (BlockPlan.Block block : dropped) {
            assertTrue(block.x() >= 18 && block.x() <= 22, "mid-road: " + block);
        }

        // Undo, redo and rebuilds plan the road again from its parameters as they were recorded.
        RoadPlanParams recorded = RoadParamsJson.fromJson(RoadParamsJson.toJson(params));
        assertEquals(params, recorded);
        assertEquals(params, RoadPlanParams.decode(params.encode()));
        RoadPlanParams rebuild = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, new UUID(0, 1), recorded, Optional.empty());
        assertEquals(dropped, dropped(RoadPlanner.plan(rebuild, WorldLimits.OVERWORLD, Map.of(new UUID(0, 1), recorded))));
    }

    @Test
    void aJunctionDropsTheKerbsAcrossTheSideRoadsMouthOnly() {
        JunctionTemplate template = JunctionTemplates.of(JunctionTemplates.T_JUNCTION, 6, List.of("city-17", "city-17", "city-17"),
                RoadPlanParams.DrivingSide.LHT).orElseThrow();
        PlacedJunction placed = new PlacedJunction(template, 0, 64, 0, 0);
        RoadPlanParams junction = placed.params(List.of(Optional.empty(), Optional.empty(), Optional.empty())).withPalette(BuiltInPalettes.DEFAULT_ID);
        List<BlockPlan.Block> dropped = dropped(RoadPlanner.plan(junction, WorldLimits.OVERWORLD));
        // The stem runs south to its port at z = 12; its kerbs are at x = ±6. The crossing takes the rows from the port in
        // where the kerb runs straight: at radius 6 the corner's kerb comes straight at z = 11.
        assertEquals(Map.of("-6,11", "east", "-6,12", "east", "6,11", "west", "6,12", "west"), facings(dropped));

        assertEquals(List.of(), dropped(RoadPlanner.plan(junction.withPalette(BuiltInPalettes.VANILLA_ID), WorldLimits.OVERWORLD)),
                "a palette without dropped kerbs keeps its kerbs");
    }

    /** Each block's {@code x,z} and the way its flush stone faces: towards the road. */
    private static Map<String, String> facings(List<BlockPlan.Block> blocks) {
        Map<String, String> facings = new TreeMap<>();
        for (BlockPlan.Block block : blocks) {
            String state = block.state();
            int at = state.indexOf("facing=") + "facing=".length();
            facings.put(block.x() + "," + block.z(), state.substring(at, state.indexOf(',', at)));
        }
        return facings;
    }

    private static List<BlockPlan.Block> dropped(PlanResult result) {
        assertTrue(result.isPlanned(), () -> "refused " + result.code());
        return result.plan().orElseThrow().blocks().stream().filter(block -> block.state().startsWith(DROPPED)).toList();
    }
}
