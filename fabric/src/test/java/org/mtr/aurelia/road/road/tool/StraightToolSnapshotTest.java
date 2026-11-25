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
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.tool.Planned;
import org.mtr.aurelia.road.tool.Target;
import org.mtr.aurelia.road.tool.ToolInput;

/**
 * The road tool's snapshot (R-2, the road tasks' standard check): from block (0, 64, 0), aiming at the block nearest 10,
 * 40 and 100 blocks along each of the 16 headings, the end the tool picks, its length, how far it is off the start's
 * heading line, the block count and the plan hash, compared with core/src/test/resources/snapshots/tool/. To accept a
 * deliberate change, run {@code .\gradlew.bat :core:test -PupdateSnapshots=true} and review the diff before committing.
 */
class StraightToolSnapshotTest {
    private static final Path FILE = Path.of("src", "test", "resources", "road-snapshots", "tool", "straight-city-17.txt");
    private static final String CORE_VERSION = "snapshot";
    private static final int[] DISTANCES = {10, 40, 100};

    @Test
    void theToolsRoadsAtSixteenHeadings() throws IOException {
        StringBuilder actual = new StringBuilder("The straight tool's City street from block (0, 64, 0), aiming at the block nearest each distance along each heading\n"
                + "heading, distance aimed -> end block, length, off the heading line, blocks, plan hash (core version \"" + CORE_VERSION + "\")\n");
        for (Heading heading : Heading.values()) {
            Frame frame = new Frame(Vec2.ZERO, heading);
            for (int distance : DISTANCES) {
                StraightTool tool = new StraightTool();
                tool.input(new ToolInput.Use(Optional.of(new Target(0, 64, 0, 5))));
                Vec2 aimed = frame.point(distance, 0);
                tool.input(new ToolInput.Aim(Optional.of(new Target((int) StrictMath.rint(aimed.x()), 64, (int) StrictMath.rint(aimed.z()), distance))));
                RoadPlanParams params = tool.params().orElseThrow();
                Planned planned = new StraightTool.RoadJob(params).run(WorldLimits.OVERWORLD);
                BlockPlan plan = planned.result().plan().orElseThrow(() -> new AssertionError(heading + " " + distance + ": " + planned.result().code()));
                assertTrue(planned.result().warnings().isEmpty(), heading + " " + distance);
                RoadPlanParams.End end = params.ends().get(1);
                Vec2 offset = new Vec2(end.x(), end.z());
                actual.append(String.format(Locale.ROOT, "%-3s %3d -> (%4d, %4d) %8.3f %+.4f %5d %s\n", heading, distance, end.x(), end.z(),
                        frame.along(offset), frame.side(offset), plan.blockCount(), PlanHash.hex(plan, CORE_VERSION)));
            }
        }
        if (Boolean.getBoolean("stratos.updateSnapshots")) {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, actual.toString(), StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.isRegularFile(FILE), FILE + " is missing; run with -PupdateSnapshots=true to create it");
        assertEquals(Files.readString(FILE, StandardCharsets.UTF_8).replace("\r\n", "\n"), actual.toString(), FILE + " differs from the tool's output");
    }
}
