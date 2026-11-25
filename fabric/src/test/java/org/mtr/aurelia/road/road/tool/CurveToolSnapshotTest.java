/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;

/**
 * The curve tool's snapshot (R-3, the road tasks' standard check): a start at block (0, 64, 0) facing east, and ends on a
 * grid every 15 blocks out to 60 with each of the 8 facings a node can have. For each: the headings the tool gives the
 * road, and the planner's answer (the centre line's length and tightest radius, the block count and the plan hash, or
 * the refusal), compared with core/src/test/resources/snapshots/tool/. To accept a deliberate change, run
 * {@code .\gradlew.bat :core:test -PupdateSnapshots=true} and review the diff before committing.
 */
class CurveToolSnapshotTest {
    private static final Path FILE = Path.of("src", "test", "resources", "road-snapshots", "tool", "curve-city-17.txt");
    private static final String CORE_VERSION = "snapshot";

    @Test
    void theToolsRoadsOnAGrid() throws IOException {
        StringBuilder actual = new StringBuilder("The curve tool's City street from block (0, 64, 0) facing E, to ends every 15 blocks out to 60 with each facing\n"
                + "end block, its facing -> headings, then length, radius, blocks, plan hash (core version \"" + CORE_VERSION + "\"), or the refusal\n");
        int solved = 0;
        int refused = 0;
        for (int z = -60; z <= 60; z += 15) {
            for (int x = -60; x <= 60; x += 15) {
                if (x == 0 && z == 0) {
                    continue;
                }
                for (int facing = 0; facing < 8; facing++) {
                    Heading endFacing = Heading.of(facing);
                    CurveTool tool = new CurveTool();
                    tool.input(new ToolInput.Use(Optional.of(new Target(0, 64, 0, 5)), (float) Heading.E.minecraftYaw()));
                    tool.input(new ToolInput.Aim(Optional.of(new Target(x, 64, z, 20)), (float) endFacing.minecraftYaw()));
                    RoadPlanParams params = tool.params().orElseThrow();
                    RoadPlanParams.End from = params.ends().get(0);
                    RoadPlanParams.End to = params.ends().get(1);
                    PlanResult result = new StraightTool.RoadJob(params).run(WorldLimits.OVERWORLD).result();
                    actual.append(String.format(Locale.ROOT, "(%3d, %3d) %-3s -> %-3s %-3s ", x, z, endFacing, from.heading(), to.heading()));
                    if (result.isPlanned()) {
                        RailMath centre = PieceSolver.centreLine(from, to).orElseThrow();
                        double radius = PieceSolver.tightestArc(centre);
                        BlockPlan plan = result.plan().orElseThrow();
                        actual.append(String.format(Locale.ROOT, "%7.2f %8s %5d %s\n", centre.getLength(),
                                Double.isInfinite(radius) ? "straight" : String.format(Locale.ROOT, "%.2f", radius), plan.blockCount(),
                                PlanHash.hex(plan, CORE_VERSION)));
                        solved++;
                    } else {
                        actual.append("refused ").append(result.code().orElseThrow()).append(' ').append(result.args()).append('\n');
                        refused++;
                    }
                }
            }
        }
        actual.append(String.format(Locale.ROOT, "%d solved, %d refused\n", solved, refused));
        assertTrue(solved > 50, "many ends make a road: " + solved);
        if (Boolean.getBoolean("stratos.updateSnapshots")) {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, actual.toString(), StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.isRegularFile(FILE), FILE + " is missing; run with -PupdateSnapshots=true to create it");
        assertEquals(Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n"), actual.toString(), FILE + " differs from the tool's output");
    }
}
