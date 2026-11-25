/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.nodes.NodeAids;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.spi.BlockReader;
import org.mtr.aurelia.road.tool.Answer;
import org.mtr.aurelia.road.tool.Commit;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Action;
import org.mtr.aurelia.road.tool.HudModel.Hint;
import org.mtr.aurelia.road.tool.HudModel.Line;
import org.mtr.aurelia.road.tool.HudModel.Text;
import org.mtr.aurelia.road.tool.HudModel.Tone;
import org.mtr.aurelia.road.tool.PlanJob;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.tool.ToolOutput;
import org.mtr.aurelia.road.tool.ToolState;

/** The Lane nodes tool and the panel's rows (task R-6). */
class NodesToolTest {
    private static final RoadPlanParams ROAD = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E))
            .withNodeAids(RoadPlanParams.NodeAids.WOOL);
    private static final float EAST = -90F;
    private static final float NORTH_EAST = -135F;

    private final Map<String, String> world = new HashMap<>();
    private final BlockReader reader = (x, y, z) -> Optional.of(this.world.getOrDefault(x + " " + y + " " + z, "minecraft:air"));
    private final NodeAids aids = new NodeAids(new PlacedRoads());
    private final NodesTool tool = new NodesTool(this.aids);
    private List<LaneNode> nodes;

    /** The road placed with its wool, as the server would place it, and checked. */
    private void placeRoad() {
        BlockPlan plan = RoadPlanner.plan(ROAD, WorldLimits.OVERWORLD).plan().orElseThrow();
        plan.blocks().forEach(b -> this.world.put(b.x() + " " + b.y() + " " + b.z(), b.state()));
        this.nodes = this.aids.roads().placed(UUID.randomUUID(), ROAD, plan).orElseThrow().nodes();
        this.aids.refresh(this.reader);
    }

    private static Optional<Target> at(int x, int y, int z) {
        return Optional.of(new Target(x, y, z, 10));
    }

    @Test
    void withNoRoadTheRowsAreDashes() {
        this.tool.input(new ToolInput.Aim(at(0, 64, 0), EAST));
        HudModel hud = this.tool.hud();
        assertEquals("stratos.tool.roads.nodes", hud.titleKey());
        assertEquals(0, hud.steps());
        assertEquals(Text.of("stratos.nodes.state.no_roads"), hud.state());
        assertEquals(8, hud.lines().size());
        assertEquals(HudModel.NONE, hud.lines().get(0).value());
        assertEquals(Text.of("stratos.nodes.value.facing", "E", "W"), hud.lines().get(4).value(), "the way you face, always");
        assertEquals(Optional.empty(), hud.hint(Action.USE));
        assertEquals(ToolOutput.EMPTY, this.tool.output());
        assertEquals(EnumSet.of(Action.USE, Action.ATTACK, Action.TOOL_KEY, Action.UNDO, Action.REDO), this.tool.keys());
    }

    @Test
    void aimingAtAMarkerShowsItsHeadingAndWhetherYourFacingWouldPlaceItRight() {
        placeRoad();
        LaneNode first = this.nodes.get(0);
        this.tool.input(new ToolInput.Aim(at(first.x(), first.y(), first.z()), EAST));
        HudModel hud = this.tool.hud();
        assertEquals(Text.of("stratos.nodes.state.clear"), hud.state());
        assertEquals(List.of(
                new Line("stratos.nodes.line.road", Text.of("stratos.tool.value.blocks", "40.0"), false),
                new Line(Text.of("stratos.nodes.line.nodes"), Text.of("stratos.nodes.value.right", "0", "4"), Tone.NORMAL),
                new Line("stratos.nodes.line.markers", Text.of("stratos.nodes.value.markers", "4"), false),
                new Line("stratos.nodes.line.aimed", Text.of("stratos.nodes.value.aimed", "1", "1", "E", "W"), false),
                new Line(Text.of("stratos.nodes.line.facing"), Text.of("stratos.nodes.value.facing_right", "E", "W"), Tone.GOOD),
                new Line(Text.of("stratos.nodes.line.problem", "1", "1"), Text.of("stratos.nodes.problem.not_placed"), Tone.NORMAL),
                new Line(Text.of("stratos.nodes.line.problem", "1", "2"), Text.of("stratos.nodes.problem.not_placed"), Tone.NORMAL),
                new Line(Text.of("stratos.tool.value.none"), Text.of("stratos.nodes.problem.more", "2"), Tone.NORMAL)), hud.lines());

        this.tool.input(new ToolInput.Aim(at(first.x(), first.nodeY(), first.z()), NORTH_EAST));
        assertEquals(new Line(Text.of("stratos.nodes.line.facing"), Text.of("stratos.nodes.value.facing_wrong", "SW", "NE"), Tone.WARNING),
                this.tool.hud().lines().get(4), "the node block counts as aiming at the node too");
    }

    @Test
    void aWrongNodeIsFlaggedFirst() {
        placeRoad();
        LaneNode first = this.nodes.get(0);
        LaneNode third = this.nodes.get(2);
        this.world.put(first.x() + " " + first.nodeY() + " " + first.z(), MtrNodes.state(0));
        this.world.put(third.x() + " " + third.nodeY() + " " + third.z(), MtrNodes.state(6)); // lane 2, node 1: SW-NE
        this.aids.refresh(this.reader);
        this.tool.input(new ToolInput.Aim(at(20, 64, 0), EAST));
        HudModel hud = this.tool.hud();
        assertEquals(new Line(Text.of("stratos.nodes.line.nodes"), Text.of("stratos.nodes.value.right_wrong", "1", "4", "1"), Tone.WARNING),
                hud.lines().get(1));
        assertEquals(new Line("stratos.nodes.line.aimed", HudModel.NONE, false), hud.lines().get(3), "the road, but no node, is aimed at");
        assertEquals(new Line(Text.of("stratos.nodes.line.problem", "2", "1"), Text.of("stratos.nodes.problem.wrong_heading", "E", "W", "SW", "NE"),
                Tone.WARNING), hud.lines().get(5), "a wrong heading is flagged, before the nodes still to place");
    }

    @Test
    void useClearsTheMarkersOfTheRoadShown() {
        placeRoad();
        this.tool.input(new ToolInput.Aim(at(20, 64, 0), EAST));
        RoadPlanParams clear = RoadPlanParams.clearMarkers(ROAD);
        assertEquals(Optional.of(clear), this.tool.clearParams());
        ToolOutput output = this.tool.output();
        assertEquals(new ToolOutput(Optional.of(new StraightTool.RoadJob(clear)), List.of()), output);
        this.tool.input(new ToolInput.Use(at(20, 64, 0), EAST));
        assertEquals(Optional.empty(), this.tool.takeCommit(), "nothing to send until the plan is worked out");

        PlanJob job = output.job().orElseThrow();
        this.tool.planned(job, job.run(WorldLimits.OVERWORLD));
        assertEquals(Optional.of(new Hint(Action.USE, "stratos.tool.hint.clear_markers")), this.tool.hud().hint(Action.USE));
        this.tool.input(new ToolInput.Use(at(20, 64, 0), EAST));
        Commit<?> commit = this.tool.takeCommit().orElseThrow();
        assertEquals(clear, commit.params());
        assertEquals(4, commit.plan().blockCount());
        assertEquals(ToolState.COMMITTING, this.tool.state());
        assertEquals(Text.of("stratos.nodes.state.clearing"), this.tool.hud().state());
        this.tool.answered(new Answer.Done());
        assertEquals(ToolState.IDLE, this.tool.state());

        // The server cleared them: the checker sees asphalt, and there is nothing left to clear.
        this.nodes.forEach(n -> this.world.put(n.x() + " " + n.y() + " " + n.z(), "minecraft:polished_deepslate"));
        this.aids.refresh(this.reader);
        assertEquals(Optional.empty(), this.tool.clearParams());
        assertEquals(Text.of("stratos.nodes.value.markers_cleared"), this.tool.hud().lines().get(2).value());
        assertEquals(Optional.empty(), this.tool.hud().hint(Action.USE));
        assertTrue(this.tool.hud().lines().get(1).value().key().equals("stratos.nodes.value.right_wrong"), "4 nodes missing now");
    }
}
