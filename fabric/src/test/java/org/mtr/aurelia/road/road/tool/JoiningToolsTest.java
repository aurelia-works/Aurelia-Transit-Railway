/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.Dashes;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Level;
import org.mtr.aurelia.road.tool.HudModel.Notice;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.tool.ToolInput;

/**
 * The road tools joining road ends (task R-5; owner, 2026-10-09): an end set, or a handle moved, within 4 blocks of the
 * middle of one of the player's road ends joins it; the road is stored the way round that lets its dashes carry on; the
 * strip says so; and a bend split in two reads its steeper part.
 */
class JoiningToolsTest {
    private static final float EAST = CurveToolTest.EAST;
    private static final float SOUTH = CurveToolTest.SOUTH;
    private static final float WEST = CurveToolTest.WEST;
    private static final UUID A = new UUID(1, 1);
    /** The player's City street, 40 blocks east from (0, 64, 0). */
    private static final RoadPlanParams ROAD_A = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E));

    private final PlacedRoads placed = new PlacedRoads();
    private final JoinableRoads joins = new JoinableRoads(this.placed::roads);
    private final RoadSettings settings = new RoadSettings();

    JoiningToolsTest() {
        this.placed.placed(A, ROAD_A, RoadPlanner.plan(ROAD_A, WorldLimits.OVERWORLD).plan().orElseThrow());
    }

    private static Optional<Target> at(int x, int y, int z) {
        return CurveToolTest.at(x, y, z);
    }

    private static End joined(int x, int y, int z, Heading heading, int port) {
        return new End(x, y, z, heading, Optional.of(A), OptionalInt.of(port));
    }

    /** The session's part: works out the tool's job and hands the answer back. */
    private static void arrive(Tool tool) {
        StraightTool.RoadJob job = (StraightTool.RoadJob) tool.output().job().orElseThrow();
        tool.planned(job, job.run(WorldLimits.OVERWORLD));
    }

    @Test
    void aCurveStartedNearARoadsEndCarriesItOn() {
        CurveTool tool = new CurveTool(this.settings, this.joins);
        tool.input(new ToolInput.Use(at(42, 64, 2), SOUTH)); // 2.8 blocks from A's end; the facing doesn't matter
        assertEquals(40, tool.start().orElseThrow().x());
        assertEquals(Heading.E, tool.start().orElseThrow().facing());
        tool.input(new ToolInput.Aim(at(80, 64, 40), SOUTH));
        RoadPlanParams road = tool.params().orElseThrow();
        assertEquals(List.of(joined(40, 64, 0, Heading.E, 1), End.at(80, 64, 40, Heading.S)), road.ends());
        assertEquals(new Dashes(40, Dashes.Anchor.START), road.dashes(), "A's dashes carry on");
        assertEquals(Map.of(A, ROAD_A), ((StraightTool.RoadJob) tool.output().job().orElseThrow()).placed());
        assertEquals(Optional.of(Notice.reason(Level.INFO, RoadNotices.JOINED, Map.of("layout", "city-17"))), tool.hud().notice(),
                "the strip says so before the plan comes");
        arrive(tool);
        assertTrue(tool.planned().orElseThrow().isPlanned());
        assertEquals(Optional.of(Notice.reason(Level.INFO, RoadNotices.JOINED, Map.of("layout", "city-17"))), tool.hud().notice());
    }

    @Test
    void nothingJoinsFurtherThanFourBlocksOrAnEndAlreadyJoined() {
        CurveTool tool = new CurveTool(this.settings, this.joins);
        tool.input(new ToolInput.Use(at(45, 64, 0), EAST)); // 5 blocks from A's end
        assertEquals(new CurveTool.Point(45, 64, 0, Heading.E), tool.start().orElseThrow());
        // A road on from A's end takes it: no second road can start there (a junction, Slice D).
        UUID d = new UUID(2, 2);
        RoadPlanParams roadD = RoadPlanParams.straight("city-17", joined(40, 64, 0, Heading.E, 1), End.at(70, 64, 0, Heading.E))
                .withDashes(new Dashes(40, Dashes.Anchor.START));
        this.placed.placed(d, roadD, RoadPlanner.plan(roadD, WorldLimits.OVERWORLD, Map.of(A, ROAD_A)).plan().orElseThrow());
        assertEquals(Optional.empty(), this.joins.near(41, 64, 1));
        assertTrue(this.joins.near(1, 64, 1).isPresent(), "A's start is still free");
    }

    @Test
    void aRoadBuiltOffARoadsStartIsStoredRunningIntoIt() {
        CurveTool tool = new CurveTool(this.settings, this.joins);
        tool.input(new ToolInput.Use(at(-1, 64, 1), WEST));
        tool.input(new ToolInput.Aim(at(-40, 64, 40), SOUTH));
        RoadPlanParams road = tool.params().orElseThrow();
        assertEquals(List.of(End.at(-40, 64, 40, Heading.N), joined(0, 64, 0, Heading.E, 0)), road.ends(), "stored running into A");
        assertEquals(new Dashes(0, Dashes.Anchor.END), road.dashes());
        // The HUD shows it the player's way: from A westwards, then south.
        assertEquals(HudModel.Text.of("stratos.tool.value.headings", "W", "S"), tool.hud().lines().get(3).value());
        arrive(tool);
        assertTrue(tool.planned().orElseThrow().isPlanned(), tool.planned().toString());
    }

    @Test
    void theEndJoinsToo() {
        CurveTool tool = new CurveTool(this.settings, this.joins);
        tool.input(new ToolInput.Use(at(100, 64, 0), WEST));
        tool.input(new ToolInput.Aim(at(41, 64, 0), EAST));
        RoadPlanParams road = tool.params().orElseThrow();
        assertEquals(List.of(joined(40, 64, 0, Heading.E, 1), End.at(100, 64, 0, Heading.E)), road.ends(), "turned to run on from A's end");
        arrive(tool);
        assertTrue(tool.planned().orElseThrow().isPlanned());
    }

    @Test
    void anotherLayoutJoinsWithTheLayoutChangingThere() {
        this.settings.setProfile(RoadProfiles.RESIDENTIAL_15);
        StraightTool tool = new StraightTool(this.settings, this.joins);
        tool.input(new ToolInput.Use(at(41, 64, -1)));
        tool.input(new ToolInput.Aim(at(60, 64, 0)));
        RoadPlanParams road = tool.params().orElseThrow();
        assertEquals(List.of(joined(40, 64, 0, Heading.E, 1), End.at(60, 64, 0, Heading.E)), road.ends());
        assertEquals(Dashes.FROM_START, road.dashes());
        arrive(tool);
        assertTrue(tool.planned().orElseThrow().isPlanned());
        assertEquals(Optional.of(Notice.reason(Level.INFO, RoadNotices.JOINED_TAPER, Map.of("layout", "city-17"))), tool.hud().notice());
        // The alley's lanes don't line up: refused, in red.
        this.settings.setProfile(RoadProfiles.ALLEY_7);
        tool.input(new ToolInput.Aim(at(61, 64, 0)));
        arrive(tool);
        assertEquals(Level.ERROR, tool.hud().notice().orElseThrow().level());
        assertEquals("PROFILE_MISMATCH", tool.hud().notice().orElseThrow().reason().orElseThrow().code());
    }

    @Test
    void aStraightStartedOnARoadsEndRunsOnItsWay() {
        StraightTool tool = new StraightTool(this.settings, this.joins);
        tool.input(new ToolInput.Use(at(41, 64, -1)));
        tool.input(new ToolInput.Aim(at(62, 64, 9)));
        assertEquals(List.of(joined(40, 64, 0, Heading.E, 1), End.at(62, 64, 0, Heading.E)), tool.params().orElseThrow().ends(),
                "east, whatever the aim's bearing");
        tool.input(new ToolInput.Aim(at(30, 64, 0)));
        assertEquals(End.at(42, 64, 0, Heading.E), tool.params().orElseThrow().ends().get(1), "aimed behind: the shortest road");
        // A straight ends on a road end ahead that faces it.
        StraightTool into = new StraightTool(this.settings, this.joins);
        into.input(new ToolInput.Use(at(100, 64, 0)));
        into.input(new ToolInput.Aim(at(41, 64, 0)));
        assertEquals(List.of(joined(40, 64, 0, Heading.E, 1), End.at(100, 64, 0, Heading.E)), into.params().orElseThrow().ends());
    }

    @Test
    void aSplitBendReadsItsSteeperPart() {
        CurveTool tool = new CurveTool(this.settings, this.joins);
        tool.input(new ToolInput.Use(at(0, 64, 200), EAST));
        tool.input(new ToolInput.Aim(at(100, 68, 240), SOUTH)); // G-7's example rising 4: split 60 blocks along, at +2
        arrive(tool);
        assertEquals(Level.INFO, tool.hud().notice().orElseThrow().level());
        assertEquals(RoadPlanner.BEND_SPLIT, tool.hud().notice().orElseThrow().reason().orElseThrow().code());
        End middle = End.at(60, 66, 200, Heading.E);
        double steeper = StrictMath.max(PieceSolver.steepestGrade(RoadProfiles.CITY_17, RoadPlanParams.Vertical.QUADRATIC, End.at(0, 64, 200, Heading.E),
                middle).getAsDouble(), PieceSolver.steepestGrade(RoadProfiles.CITY_17, RoadPlanParams.Vertical.QUADRATIC, middle,
                End.at(100, 68, 240, Heading.S)).getAsDouble());
        String[] percents = PieceSolver.percents(steeper * 100,
                Presets.limits(RoadProfiles.CITY_17.group(), Presets.Preset.GAME_SCALE).maxGrade().value() * 100);
        assertEquals(HudModel.Text.of("stratos.tool.value.slope", "+4", percents[0], percents[1]), tool.hud().lines().get(5).value());
    }
}
