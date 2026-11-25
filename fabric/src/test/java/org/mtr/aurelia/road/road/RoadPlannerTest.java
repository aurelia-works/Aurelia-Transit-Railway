/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.raster.Rasteriser;
import org.mtr.aurelia.road.road.raster.RoadPiece;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.road.solve.RoadSolver;

/**
 * The roads planner: straight roads on all 16 headings and its refusals (roads-v1 §12, task F-3); from R-3 every road is a
 * solved piece, and curved roads are planned too.
 */
class RoadPlannerTest {
    private static final WorldLimits WORLD = WorldLimits.OVERWORLD;

    private static PlanResult plan(RoadPlanParams params) {
        return RoadPlanner.plan(params, WORLD);
    }

    private static RoadPlanParams straight(int x0, int z0, int x1, int z1, Heading heading) {
        return RoadPlanParams.straight("city-17", End.at(x0, 64, z0, heading), End.at(x1, 64, z1, heading));
    }

    /** A block offset roughly 20 blocks along {@code heading} that lies within half a block of its line. */
    private static int[] endOffset(Heading heading) {
        Frame frame = new Frame(Vec2.ZERO, heading);
        for (int radius = 0; radius < 5; radius++) {
            Vec2 ideal = frame.point(20, 0);
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = (int) StrictMath.rint(ideal.x()) + dx;
                    int z = (int) StrictMath.rint(ideal.z()) + dz;
                    if (StrictMath.abs(frame.side(new Vec2(x, z))) <= 0.5) {
                        return new int[] {x, z};
                    }
                }
            }
        }
        throw new AssertionError("no end in line for " + heading);
    }

    private static void assertRefused(String code, Map<String, String> args, PlanResult result) {
        assertRefused(code, args, result, "refused");
    }

    private static void assertRefused(String code, Map<String, String> args, PlanResult result, String why) {
        assertFalse(result.isPlanned(), why);
        assertEquals(Optional.of(code), result.code(), why);
        assertEquals(args, result.args(), why);
    }

    @Test
    void straightRoadsOnAllSixteenHeadingsAreSolvedPiecesAndCardinalOnesAreSliceAsBlocks() {
        for (Heading heading : Heading.values()) {
            int[] end = endOffset(heading);
            RoadPlanParams params = straight(100, -50, 100 + end[0], -50 + end[1], heading);
            PlanResult result = plan(params);
            assertTrue(result.isPlanned(), heading + ": " + result);
            PieceSolution.Solved solved = (PieceSolution.Solved) PieceSolver.solve(RoadProfiles.CITY_17, params.preset(), params.vertical(),
                    params.ends().get(0), params.ends().get(1));
            BlockPlan expected = Rasteriser.plan(RoadPiece.solved(RoadProfiles.CITY_17, solved, true, 0), BuiltInPalettes.vanilla());
            assertEquals(expected, withoutNodeColumns(result.plan().orElseThrow(), expected, RoadPlanner.laneNodes(params).orElseThrow()), heading.toString());
            if (!heading.isIntermediate()) {
                assertEquals(expected, result.plan().orElseThrow(), heading + ": every lane node is on the rasterised road");
            }
            assertTrue(expected.blockCount() > 17 * 18, heading + " has a road's worth of blocks: " + expected.blockCount());
            if (heading.isCardinal()) {
                double along = new Frame(Vec2.ZERO, heading).along(new Vec2(end[0], end[1]));
                BlockPlan sliceA = Rasteriser.plan(RoadPiece.straight(RoadProfiles.CITY_17, new BlockXZ(100, -50), heading, along, 64),
                        BuiltInPalettes.vanilla());
                assertEquals(sliceA, result.plan().orElseThrow(), heading + ": a solved cardinal straight is Slice A's road");
            }
        }
    }

    /**
     * {@code planned} without the columns the planner added under lane nodes past a 22.5° end (R-6), after checking that
     * those are exactly the lane nodes' columns {@code raster} doesn't cover, each a full lane stack with its surface at
     * the node's height.
     */
    static BlockPlan withoutNodeColumns(BlockPlan planned, BlockPlan raster, List<LaneNode> nodes) {
        Set<Long> nodeColumns = new HashSet<>();
        nodes.forEach(n -> nodeColumns.add(BlockChange.positionKey(n.x(), 0, n.z())));
        Set<Long> rasterColumns = new HashSet<>();
        raster.blocks().forEach(b -> rasterColumns.add(BlockChange.positionKey(b.x(), 0, b.z())));
        BlockPlan.Builder kept = BlockPlan.builder(planned.dataVersion());
        Map<Long, List<BlockPlan.Block>> extra = new HashMap<>();
        for (BlockPlan.Block block : planned.blocks()) {
            long column = BlockChange.positionKey(block.x(), 0, block.z());
            if (rasterColumns.contains(column)) {
                kept.set(block.x(), block.y(), block.z(), block.state());
            } else {
                extra.computeIfAbsent(column, c -> new ArrayList<>()).add(block);
            }
        }
        for (Map.Entry<Long, List<BlockPlan.Block>> entry : extra.entrySet()) {
            List<BlockPlan.Block> column = entry.getValue();
            assertTrue(nodeColumns.contains(entry.getKey()), "only lane nodes' columns are added: " + column);
            assertEquals(1 + 2 + 5, column.size(), "a node column is a full lane stack: " + column);
            assertEquals(1, column.stream().filter(b -> b.state().equals("minecraft:polished_deepslate")).count(), "one asphalt block: " + column);
        }
        return kept.build();
    }

    @Test
    void nodeAidsAreInTheRoadsPlan() {
        RoadPlanParams base = straight(0, 0, 40, 0, Heading.E);
        BlockPlan plain = plan(base).plan().orElseThrow();
        BlockPlan wool = plan(base.withNodeAids(RoadPlanParams.NodeAids.WOOL)).plan().orElseThrow();
        assertEquals(plain.blockCount(), wool.blockCount(), "markers replace surface blocks");
        assertEquals(4, wool.blocks().stream().filter(b -> b.state().equals("minecraft:blue_wool")).count(), "one per lane node");
        assertTrue(plan(base.withNodeAids(RoadPlanParams.NodeAids.WOOL)).onlyWhere().isEmpty(), "a road is written everywhere");

        assertRefused(RoadPlanner.MTR_MISSING, Map.of(), plan(base.withNodeAids(RoadPlanParams.NodeAids.PREORIENTED)),
                "no mtr:rail in this game");
        WorldLimits mtr = WORLD.withModBlocks(Set.of("mtr:rail"));
        BlockPlan nodes = RoadPlanner.plan(base.withNodeAids(RoadPlanParams.NodeAids.PREORIENTED), mtr).plan().orElseThrow();
        assertEquals(plain.blockCount(), nodes.blockCount(), "nodes replace cleared air");
        assertEquals(4, nodes.blocks().stream().filter(b -> b.state().startsWith("mtr:rail[facing=true,is_22_5=false,is_45=false,")).count());

        PlanResult clear = plan(RoadPlanParams.clearMarkers(base.withNodeAids(RoadPlanParams.NodeAids.PREORIENTED)));
        assertTrue(clear.isPlanned(), "clearing needs no MTR: it only reads the road's lane nodes");
        assertEquals(4, clear.plan().orElseThrow().blockCount());
        assertTrue(clear.plan().orElseThrow().blocks().stream().allMatch(b -> b.state().equals("minecraft:polished_deepslate") && b.y() == 64));
        assertTrue(clear.onlyWhere().orElseThrow().blocks().stream().allMatch(b -> b.state().equals("minecraft:blue_wool")));
        WorldLimits border = new WorldLimits(-64, 320, -100, 30, -100, 100);
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), RoadPlanner.plan(RoadPlanParams.clearMarkers(base), border), "its end is past the border");
    }

    @Test
    void curvedRoadsAreSolvedAndRasterised() {
        RoadPlanParams bend = RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 40, Heading.S));
        PlanResult result = plan(bend);
        assertTrue(result.isPlanned(), result.toString());
        PieceSolution.Solved solved = (PieceSolution.Solved) PieceSolver.solve(RoadProfiles.CITY_17, bend.preset(), bend.vertical(),
                bend.ends().get(0), bend.ends().get(1));
        assertEquals(Rasteriser.plan(RoadPiece.solved(RoadProfiles.CITY_17, solved, true, 0), BuiltInPalettes.vanilla()), result.plan().orElseThrow());
        assertEquals(List.of(), result.warnings());
        // An S-bend and a line + arc piece.
        assertTrue(plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(80, 64, 8, Heading.E))).isPlanned());
        assertTrue(plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(60, 64, 20, Heading.SE))).isPlanned());
    }

    @Test
    void everyColumnHasItsFoundationAndClearance() {
        BlockPlan plan = plan(straight(0, 0, 20, 0, Heading.E)).plan().orElseThrow();
        assertEquals(17 * 21 * 8, plan.blockCount());
        Map<Integer, String> column = new java.util.TreeMap<>();
        plan.blocks().stream().filter(b -> b.x() == 5 && b.z() == 0).forEach(b -> column.put(b.y(), b.state()));
        assertEquals(Map.of(62, "minecraft:stone", 63, "minecraft:stone", 64, "minecraft:polished_deepslate", 65, "minecraft:air", 66, "minecraft:air",
                67, "minecraft:air", 68, "minecraft:air", 69, "minecraft:air"), column);

        // Near the top of the world the clearance stops at the build height; the road itself is still fine.
        PlanResult high = plan(RoadPlanParams.straight("city-17", End.at(0, 317, 0, Heading.E), End.at(20, 317, 0, Heading.E)));
        assertTrue(high.isPlanned());
        assertEquals(319, high.plan().orElseThrow().blocks().stream().mapToInt(BlockPlan.Block::y).max().orElseThrow());
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), plan(RoadPlanParams.straight("city-17", End.at(0, 320, 0, Heading.E),
                End.at(20, 320, 0, Heading.E))));
    }

    @Test
    void slopedRoadsArePlannedWithSlabsOnTheHalfSteps() {
        RoadPlanParams slope = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(80, 67, 0, Heading.E));
        PlanResult result = plan(slope);
        assertTrue(result.isPlanned(), result.toString());
        Map<Integer, Integer> surfaceTop = new java.util.TreeMap<>(); // x -> the top of the centre line's column, in half blocks
        for (BlockPlan.Block b : result.plan().orElseThrow().blocks()) {
            if (b.z() == 0 && !b.state().equals("minecraft:air") && !b.state().equals("minecraft:stone")) {
                int top = 2 * b.y() + (b.state().contains("_slab") ? 1 : 2);
                surfaceTop.merge(b.x(), top, StrictMath::max);
            }
        }
        assertEquals(130, surfaceTop.get(0), "the start: a full block at 64, top 65");
        assertEquals(136, surfaceTop.get(80), "the end: a full block at 67, top 68");
        int previous = 0;
        boolean slabs = false;
        for (int top : surfaceTop.values()) {
            assertTrue(top >= previous && top - previous <= 1 || previous == 0, "the surface climbs a half block at a time");
            slabs |= top % 2 == 1;
            previous = top;
        }
        assertTrue(slabs, "half steps are slabs");
        assertTrue(result.plan().orElseThrow().blocks().stream().anyMatch(b -> b.state().equals("minecraft:polished_deepslate_slab[type=bottom,waterlogged=false]")));
    }

    @Test
    void tooSteepAndLanesApartAreRefused() {
        assertRefused(PieceSolver.GRADE_TOO_STEEP, Map.of("grade", "25.0", "max", "8.3", "minLength", "120"),
                plan(RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 69, 0, Heading.E))));
        assertRefused(PieceSolver.GRADE_TOO_STEEP, Map.of("grade", "8.8", "max", "8.3", "minLength", "77"),
                plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(50, 67, 40, Heading.S))), "the inner lane is steepest");
        // A line + arc bend: rising 3 its lanes stay within a quarter block of each other, so it stays one piece. Rising 4
        // they don't (0.29 apart), so from R-5 it is split where the curve starts, 60 blocks along, half the rise each.
        PlanResult three = plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(100, 67, 40, Heading.S)));
        assertTrue(three.isPlanned());
        assertTrue(three.warnings().stream().noneMatch(w -> w.code().equals(RoadPlanner.BEND_SPLIT)));
        PlanResult four = plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(100, 68, 40, Heading.S)));
        assertTrue(four.isPlanned());
        assertEquals(List.of(new PlanResult.Warning(RoadPlanner.BEND_SPLIT, Map.of("x", "60", "y", "66", "z", "0", "heading", "E"))), four.warnings());
        // A bend that can't be split so that each part climbs evenly (the harness's check.mixedRefused).
        PlanResult mixed = plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(118, 69, -34, Heading.NNE)));
        assertEquals(Optional.of(RoadSolver.GRADE_ON_MIXED_PIECE), mixed.code(), mixed.toString());
        // Falling is the same as rising.
        assertRefused(PieceSolver.GRADE_TOO_STEEP, Map.of("grade", "25.0", "max", "8.3", "minLength", "120"),
                plan(RoadPlanParams.straight("city-17", End.at(0, 69, 0, Heading.E), End.at(40, 64, 0, Heading.E))));
    }

    @Test
    void theSolversRefusalsComeThroughWithTheirArguments() {
        End start = End.at(0, 64, 0, Heading.E);
        assertRefused(PieceSolver.RADIUS_TOO_SMALL, Map.of("r", "12.0", "group", "city_street", "preset", "game_scale", "min", "30"),
                plan(RoadPlanParams.curve("city-17", start, End.at(15, 64, 15, Heading.S))));
        assertRefused(PieceSolver.REVERSE_HEADING, Map.of(), plan(RoadPlanParams.curve("city-17", start, End.at(-40, 64, 0, Heading.W))));
        assertRefused(PieceSolver.PARALLEL_BESIDE, Map.of(), plan(RoadPlanParams.curve("city-17", start, End.at(0, 64, 30, Heading.E))));
        assertRefused(PieceSolver.S_CURVE_TOO_SHARP, Map.of("phi", "36.9", "minLength", "41"),
                plan(RoadPlanParams.curve("city-17", start, End.at(24, 64, 8, Heading.E))));
        assertRefused(PieceSolver.NO_MTR_PATH, Map.of(), plan(RoadPlanParams.curve("city-17", start, End.at(40, 64, -40, Heading.S))));
        assertRefused(RoadPlanner.TOO_SHORT, Map.of(), plan(RoadPlanParams.curve("city-17", start, End.at(0, 64, 0, Heading.S))));
        assertRefused(PieceSolver.NODES_JOIN_OTHER_WAY, Map.of("n", "2"),
                plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.S), End.at(-130, 64, 1, Heading.NNW))));
    }

    @Test
    void theSameRoadFarAwayIsTheSameBlocksMovedByWholeBlocks() {
        for (Heading heading : Heading.values()) {
            int[] end = endOffset(heading);
            BlockPlan near = plan(straight(0, 0, end[0], end[1], heading)).plan().orElseThrow();
            int far = 29_000_000;
            BlockPlan away = plan(straight(far, -far, far + end[0], -far + end[1], heading)).plan().orElseThrow();
            BlockPlan.Builder moved = BlockPlan.builder(near.dataVersion());
            near.blocks().forEach(b -> moved.set(b.x() + far, b.y(), b.z() - far, b.state()));
            assertEquals(moved.build(), away, heading + ": same input, different place, same result");
        }
    }

    @Test
    void plansAreDeterministicAndIgnoreDrivingSideAndPresetForCity21() {
        RoadPlanParams lht = straight(0, 0, 30, 30, Heading.SE);
        RoadPlanParams rht = new RoadPlanParams(lht.tool(), lht.profileId(), Presets.Preset.UK_FAITHFUL, lht.paletteId(),
                RoadPlanParams.DrivingSide.RHT, lht.ends(), new RoadPlanParams.Vertical(RoadPlanParams.Vertical.Shape.TWO_RADII,
                        OptionalDouble.of(500)), lht.nodeAids(), false, Optional.empty(), Optional.empty(), Optional.empty());
        byte[] first = PlanHash.of(plan(lht).plan().orElseThrow(), "test");
        assertArrayEquals(first, PlanHash.of(plan(lht).plan().orElseThrow(), "test"));
        // City street is symmetric, so mirroring for right-hand traffic places the same blocks (its lanes' directions
        // swap, which matters to MTR rails later, not to blocks).
        assertArrayEquals(first, PlanHash.of(plan(rht).plan().orElseThrow(), "test"));
    }

    @Test
    void tooShortPiecesAreRefused() {
        assertRefused(RoadPlanner.TOO_SHORT, Map.of(), plan(straight(0, 0, 1, 0, Heading.E)));
        assertRefused(RoadPlanner.TOO_SHORT, Map.of(), plan(straight(0, 0, 0, 0, Heading.E)));
        assertRefused(RoadPlanner.TOO_SHORT, Map.of(), plan(straight(0, 0, 1, 1, Heading.SE))); // √2 ≈ 1.41
        assertTrue(plan(straight(0, 0, 2, 0, Heading.E)).isPlanned(), "2 blocks is long enough");
        assertTrue(plan(straight(0, 0, 2, 2, Heading.SE)).isPlanned(), "2√2 is long enough");
    }

    @Test
    void endsThatArentStraightAheadAreRefused() {
        assertRefused(RoadPlanner.END_NOT_IN_LINE, Map.of(), plan(straight(0, 0, 20, 1, Heading.E))); // one block to the side
        assertRefused(RoadPlanner.END_NOT_IN_LINE, Map.of(), plan(straight(0, 0, -20, 0, Heading.E))); // behind the start
        assertRefused(RoadPlanner.END_NOT_IN_LINE, Map.of(), plan(straight(0, 0, 20, 21, Heading.SE))); // off the diagonal
        assertRefused(RoadPlanner.END_NOT_IN_LINE, Map.of(),
                plan(RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.ESE)))); // another heading
        // 22.5°: 20 along x needs exactly 8 along z (0.26 to the side); 7 and 9 are more than half a block off.
        assertTrue(plan(straight(0, 0, 20, 8, Heading.ESE)).isPlanned());
        assertRefused(RoadPlanner.END_NOT_IN_LINE, Map.of(), plan(straight(0, 0, 20, 7, Heading.ESE)));
        assertRefused(RoadPlanner.END_NOT_IN_LINE, Map.of(), plan(straight(0, 0, 20, 9, Heading.ESE)));
    }

    @Test
    void unknownProfilesAreRefused() {
        RoadPlanParams params = RoadPlanParams.straight("motorway-99", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E));
        assertRefused(RoadPlanner.UNKNOWN_PROFILE, Map.of("profile", "motorway-99"), plan(params));
    }

    @Test
    void whatLaterTasksAddIsNotAvailableYet() {
        RoadPlanParams base = straight(0, 0, 20, 0, Heading.E);
        End start = base.ends().get(0);
        End end = base.ends().get(1);
        for (RoadPlanParams.Tool tool : List.of(RoadPlanParams.Tool.TAPER)) { // JUNCTION is built since P10
            RoadPlanParams other = new RoadPlanParams(tool, "city-17", base.preset(), "vanilla", base.drivingSide(), base.ends(), base.vertical(),
                    base.nodeAids(), false, Optional.empty(), Optional.empty(), Optional.empty());
            assertRefused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", tool.name().toLowerCase(java.util.Locale.ROOT)), plan(other));
        }
        // R-7: rebuilding and deleting are available; a road the planner wasn't given (or no road named) is gone.
        for (RoadPlanParams.Tool tool : List.of(RoadPlanParams.Tool.REBUILD, RoadPlanParams.Tool.DELETE)) {
            RoadPlanParams other = new RoadPlanParams(tool, "city-17", base.preset(), "vanilla", base.drivingSide(), base.ends(), base.vertical(),
                    base.nodeAids(), false, Optional.empty(), Optional.empty(), Optional.empty());
            assertRefused(RoadPlanner.ROAD_GONE, Map.of(), plan(other));
            assertRefused(RoadPlanner.ROAD_GONE, Map.of(), plan(RoadPlanParams.edit(tool, new UUID(3, 3), base, Optional.empty())));
        }
        assertRefused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "pieces"), plan(new RoadPlanParams(RoadPlanParams.Tool.CURVE, "city-17",
                base.preset(), "vanilla", base.drivingSide(), List.of(start, End.at(20, 64, 20, Heading.S), End.at(40, 64, 40, Heading.S)),
                base.vertical(), base.nodeAids(), false, Optional.empty(), Optional.empty(), Optional.empty())));
        // R-9: palettes are available; one this game hasn't got is refused with its id.
        assertRefused(RoadPlanner.UNKNOWN_PALETTE, Map.of("palette", "brick"), plan(with(base, "brick", RoadPlanParams.NodeAids.NONE, false)));
        assertRefused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "overlay"), plan(with(base, "vanilla", RoadPlanParams.NodeAids.NONE, true)));
        // R-5: joining a road end is available; an end naming no road, or a road the planner wasn't given, is refused.
        assertRefused(RoadJoins.PORT_MISMATCH, Map.of(),
                plan(RoadPlanParams.straight("city-17", start, new End(20, 64, 0, Heading.E, Optional.of(new UUID(1, 1)), OptionalInt.empty()))));
        assertRefused(RoadJoins.PORT_MISMATCH, Map.of(),
                plan(RoadPlanParams.straight("city-17", new End(0, 64, 0, Heading.E, Optional.empty(), OptionalInt.of(0)), end)));
        assertRefused(RoadJoins.ROAD_END_GONE, Map.of(),
                plan(RoadPlanParams.straight("city-17", start, new End(20, 64, 0, Heading.E, Optional.of(new UUID(1, 1)), OptionalInt.of(0)))));
        assertRefused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "junction"), plan(new RoadPlanParams(base.tool(), "city-17", base.preset(),
                "vanilla", base.drivingSide(), base.ends(), base.vertical(), base.nodeAids(), false,
                Optional.of(new RoadPlanParams.Junction("tee", 1, List.of())), Optional.empty(), Optional.empty())));
        assertRefused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "target_road"), plan(new RoadPlanParams(base.tool(), "city-17", base.preset(),
                "vanilla", base.drivingSide(), base.ends(), base.vertical(), base.nodeAids(), false, Optional.empty(), Optional.of(new UUID(2, 2)),
                Optional.empty())));
        assertRefused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "manual_edits"), plan(new RoadPlanParams(base.tool(), "city-17",
                base.preset(), "vanilla", base.drivingSide(), base.ends(), base.vertical(), base.nodeAids(), false, Optional.empty(),
                Optional.empty(), Optional.of(RoadPlanParams.ManualEdits.OVERWRITE))));
    }

    private static RoadPlanParams with(RoadPlanParams base, String palette, RoadPlanParams.NodeAids aids, boolean overlay) {
        return new RoadPlanParams(base.tool(), base.profileId(), base.preset(), palette, base.drivingSide(), base.ends(), base.vertical(), aids,
                overlay, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void roadsOutsideTheBuildHeightOrTheBorderAreRefused() {
        assertTrue(RoadPlanner.plan(roadAtY(319), WORLD).isPlanned(), "the top layer is inside");
        assertTrue(RoadPlanner.plan(roadAtY(-64), WORLD).isPlanned(), "the bottom layer is inside");
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), RoadPlanner.plan(roadAtY(320), WORLD));
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), RoadPlanner.plan(roadAtY(-65), WORLD));

        WorldLimits small = new WorldLimits(-64, 320, -100, 100, -100, 100);
        // Along the road: the end column x = 100 is outside (x < maxX fails); 99 is the last column inside.
        assertTrue(RoadPlanner.plan(straight(70, 0, 99, 0, Heading.E), small).isPlanned());
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), RoadPlanner.plan(straight(70, 0, 100, 0, Heading.E), small));
        // Sideways: the centre line is inside but the footway (8 blocks to the side) crosses z = -100.
        assertTrue(RoadPlanner.plan(straight(0, -92, 20, -92, Heading.E), small).isPlanned());
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), RoadPlanner.plan(straight(0, -93, 20, -93, Heading.E), small));
    }

    private static RoadPlanParams roadAtY(int y) {
        return RoadPlanParams.straight("city-17", End.at(0, y, 0, Heading.E), End.at(20, y, 0, Heading.E));
    }

    @Test
    void endAlongGivesTheNearestEndThePlannerAccepts() {
        for (Heading heading : Heading.values()) {
            for (int length = 2; length <= 400; length += 7) {
                End start = End.at(-123, 70, 456, heading);
                End end = RoadPlanner.endAlong(start, length);
                assertEquals(heading, end.heading());
                assertEquals(70, end.y());
                PlanResult result = plan(RoadPlanParams.straight("city-17", start, end));
                // At 22.5°, an end up to half a block off the line makes MTR draw an S-bend, which a short road can't take.
                assertTrue(result.isPlanned() || (heading.isIntermediate() && length < 9), heading + " " + length + ": " + result.code());
                // No other accepted end is nearer to the length asked for.
                Frame frame = new Frame(Vec2.ZERO, heading);
                double miss = StrictMath.abs(frame.along(new Vec2(end.x() - start.x(), end.z() - start.z())) - length);
                for (int dx = -length - 2; dx <= length + 2; dx += 1) {
                    for (int dz = -length - 2; dz <= length + 2; dz += 1) {
                        Vec2 offset = new Vec2(dx, dz);
                        if (StrictMath.abs(frame.side(offset)) <= 0.5 && frame.along(offset) >= RoadPlanner.MIN_LENGTH) {
                            assertTrue(StrictMath.abs(frame.along(offset) - length) >= miss, heading + " " + length + ": (" + dx + ", " + dz + ") is nearer");
                        }
                    }
                }
                if (length > 7) {
                    length += 40; // fewer, longer cases further out: the brute force is quadratic
                }
            }
        }
        assertEquals(End.at(20, 64, 0, Heading.E), RoadPlanner.endAlong(End.at(0, 64, 0, Heading.E), 20));
        assertEquals(End.at(2, 64, 2, Heading.SE), RoadPlanner.endAlong(End.at(0, 64, 0, Heading.SE), 2), "at least 2 long, so not (1, 1)");
    }

    /** R-2 (owner, 2026-10-06): 22.5° roads end only within 0.05 of their line; the others on any block in line. */
    @Test
    void snapEndKeepsTwentyTwoPointFiveDegreeRoadsWithinMtrsTolerance() {
        int[] lengths = {0, 1, 2, 5, 12, 13, 20, 31, 35, 50, 75, 100, 150, 250};
        for (Heading heading : Heading.values()) {
            Frame frame = new Frame(Vec2.ZERO, heading);
            for (int length : lengths) {
                End start = End.at(-321, 70, 654, heading);
                End end = RoadPlanner.snapEnd(start, length);
                assertEquals(heading, end.heading());
                assertEquals(70, end.y());
                Vec2 offset = new Vec2(end.x() - start.x(), end.z() - start.z());
                PlanResult result = plan(RoadPlanParams.straight("city-17", start, end));
                assertTrue(result.isPlanned(), heading + " " + length + ": " + result.code());
                assertEquals(List.of(), result.warnings(), heading + " " + length + ": the tool's own roads never get STEP_22_5");
                assertTrue(frame.along(offset) >= RoadPlanner.MIN_LENGTH, heading + " " + length);
                if (!heading.isIntermediate()) {
                    assertEquals(RoadPlanner.endAlong(start, StrictMath.max(length, 2)), end, heading + " " + length);
                    continue;
                }
                assertTrue(StrictMath.abs(frame.side(offset)) <= RoadPlanner.MTR_TOLERANCE, heading + " " + length + ": " + frame.side(offset));
                // No other end within the tolerance is nearer to the length asked for.
                double wanted = StrictMath.max(length, 2);
                double miss = StrictMath.abs(frame.along(offset) - wanted);
                int box = length + 30;
                for (int dx = -box; dx <= box; dx++) {
                    for (int dz = -box; dz <= box; dz++) {
                        Vec2 other = new Vec2(dx, dz);
                        if (StrictMath.abs(frame.side(other)) <= RoadPlanner.MTR_TOLERANCE && frame.along(other) >= RoadPlanner.MIN_LENGTH) {
                            assertTrue(StrictMath.abs(frame.along(other) - wanted) >= miss, heading + " " + length + ": (" + dx + ", " + dz + ") is nearer");
                        }
                    }
                }
            }
        }
        // ESE (22.5°): the shortest road is the (12, 5) step; about 35 blocks out is the (29, 12) step; 100 is (87, 36).
        End origin = End.at(0, 64, 0, Heading.ESE);
        assertEquals(End.at(12, 64, 5, Heading.ESE), RoadPlanner.snapEnd(origin, 3));
        assertEquals(End.at(29, 64, 12, Heading.ESE), RoadPlanner.snapEnd(origin, 35));
        assertEquals(End.at(87, 64, 36, Heading.ESE), RoadPlanner.snapEnd(origin, 100));
    }

    @Test
    void twentyTwoPointFiveDegreeEndsAreNeverFurtherApartThanThirteenBlocks() {
        Frame frame = new Frame(Vec2.ZERO, Heading.ESE);
        End origin = End.at(0, 64, 0, Heading.ESE);
        for (int length = 13; length <= 20_000; length++) {
            End end = RoadPlanner.snapEnd(origin, length);
            double miss = StrictMath.abs(frame.along(new Vec2(end.x(), end.z())) - length);
            assertTrue(miss <= 6.6, length + ": the nearest end is " + miss + " away, so a gap is wider than 13 blocks");
        }
    }

    @Test
    void tightForLongVehiclesWarnsBelowAnInnerLaneRadiusOfThirty() {
        PlanResult tight = plan(RoadPlanParams.curve("alley-7", End.at(0, 64, 0, Heading.E), End.at(12, 64, 12, Heading.S)));
        assertTrue(tight.isPlanned(), "a warning doesn't stop the road");
        assertEquals(List.of(new PlanResult.Warning(RoadPlanner.TIGHT_FOR_LONG_VEHICLES, Map.of())), tight.warnings());
        assertEquals(1, plan(RoadPlanParams.curve("alley-7", End.at(0, 64, 0, Heading.E), End.at(29, 64, 29, Heading.S))).warnings().size(), "29");
        assertEquals(List.of(), plan(RoadPlanParams.curve("alley-7", End.at(0, 64, 0, Heading.E), End.at(30, 64, 30, Heading.S))).warnings(), "30");
        assertEquals(1, plan(RoadPlanParams.curve("alley-7", End.at(0, 64, 0, Heading.E), End.at(12, 64, 2, Heading.E))).warnings().size(),
                "an S-bend of radius 18.5");
        // Residential's inner lane is 3 inside the centre line: 32 gives 29, 33 gives 30.
        assertEquals(1, plan(RoadPlanParams.curve("residential-15", End.at(0, 64, 0, Heading.E), End.at(32, 64, 32, Heading.S))).warnings().size());
        assertEquals(List.of(), plan(RoadPlanParams.curve("residential-17", End.at(0, 64, 0, Heading.E), End.at(33, 64, 33, Heading.S))).warnings());
        // City street's minimum is 30, so it never warns; straights never do.
        assertEquals(List.of(), plan(RoadPlanParams.curve("city-17", End.at(0, 64, 0, Heading.E), End.at(33, 64, 33, Heading.S))).warnings());
        assertEquals(List.of(), plan(RoadPlanParams.straight("alley-7", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E))).warnings());
    }

    @Test
    void residentialAndAlleyRoadsHaveTheirOwnColumns() {
        Map<Long, String> alley = blocksByPosition(plan(RoadPlanParams.straight("alley-7", End.at(0, 64, 0, Heading.E), End.at(20, 64, 0, Heading.E))));
        List<String> across = new ArrayList<>();
        for (int z = -4; z <= 4; z++) {
            across.add(alley.getOrDefault(BlockChange.positionKey(10, 64, z), "-"));
        }
        assertEquals(List.of("-", "minecraft:polished_andesite", "minecraft:polished_deepslate", "minecraft:polished_deepslate", "minecraft:polished_deepslate",
                "minecraft:polished_deepslate", "minecraft:polished_deepslate", "minecraft:polished_andesite", "-"), across, "gutter · lane 5 · gutter");

        Map<Long, String> residential = blocksByPosition(plan(RoadPlanParams.straight("residential-15", End.at(0, 64, 0, Heading.E),
                End.at(20, 64, 0, Heading.E))));
        across.clear();
        for (int z = -8; z <= 8; z++) {
            across.add(residential.getOrDefault(BlockChange.positionKey(10, 64, z), "-"));
        }
        List<String> expected = new ArrayList<>(List.of("-", "minecraft:smooth_stone", "minecraft:polished_andesite"));
        for (int i = 0; i < 11; i++) {
            expected.add("minecraft:polished_deepslate"); // lane 5, the plain centre, lane 5
        }
        expected.addAll(List.of("minecraft:polished_andesite", "minecraft:smooth_stone", "-"));
        assertEquals(expected, across, "footway 1 · kerb · lane 5 · centre 1 (no line) · lane 5 · kerb · footway 1");

        // On a slope, the gutters' half steps are polished andesite slabs.
        PlanResult sloped = plan(RoadPlanParams.curve("alley-7", End.at(0, 64, 0, Heading.E), End.at(40, 66, 0, Heading.E)));
        assertTrue(sloped.plan().orElseThrow().blocks().stream()
                .anyMatch(block -> block.state().equals("minecraft:polished_andesite_slab[type=bottom,waterlogged=false]")), sloped.toString());
    }

    /** The plan's blocks by position (its surface, foundation and cleared air alike). */
    private static Map<Long, String> blocksByPosition(PlanResult result) {
        assertTrue(result.isPlanned(), result.toString());
        Map<Long, String> states = new HashMap<>();
        result.plan().orElseThrow().blocks().forEach(block -> states.put(BlockChange.positionKey(block.x(), block.y(), block.z()), block.state()));
        return states;
    }

    @Test
    void step22_5WarnsWhenMtrWouldBendMoreThanItsTolerance() {
        PlanResult onStep = plan(straight(0, 0, 12, 5, Heading.ESE));
        assertEquals(List.of(), onStep.warnings(), "(12, 5) is 0.027 off");
        PlanResult twoSteps = plan(straight(0, 0, 24, 10, Heading.ESE));
        assertTrue(twoSteps.isPlanned(), "a warning doesn't stop the road");
        assertEquals(List.of(new PlanResult.Warning(RoadPlanner.STEP_22_5, Map.of("d", "0.054"))), twoSteps.warnings());
        PlanResult farOff = plan(straight(0, 0, 10, 4, Heading.ESE));
        assertEquals(List.of(new PlanResult.Warning(RoadPlanner.STEP_22_5, Map.of("d", "0.131"))), farOff.warnings());
        // Mirrored and rotated 22.5° headings warn too; cardinal and 45° roads never do.
        assertEquals(1, plan(straight(0, 0, -24, -10, Heading.WNW)).warnings().size());
        assertEquals(1, plan(straight(0, 0, 10, 24, Heading.SSE)).warnings().size());
        assertEquals(List.of(), plan(straight(0, 0, 40, 0, Heading.E)).warnings());
        assertEquals(List.of(), plan(straight(0, 0, 30, 30, Heading.SE)).warnings());
        assertEquals(List.of(), plan(straight(0, 0, 1, 0, Heading.E)).warnings(), "a refusal has no warnings");
    }

    @Test
    void outcomeKeepsTheShapeOfARoadThatLeavesTheWorld() {
        WorldLimits small = new WorldLimits(-64, 320, -100, 100, -100, 100);
        RoadPlanParams crossing = straight(70, 0, 110, 0, Heading.E);
        RoadPlanner.Outcome outcome = RoadPlanner.outcome(crossing, small);
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), outcome.result());
        assertEquals(RoadPlanner.plan(crossing, small), outcome.result(), "the same answer as plan()");
        BlockPlan shape = outcome.refusedShape().orElseThrow();
        assertEquals(Rasteriser.plan(RoadPiece.straight(RoadProfiles.CITY_17, new BlockXZ(70, 0), Heading.E, 40, 64), BuiltInPalettes.vanilla()), shape);

        RoadPlanner.Outcome tooHigh = RoadPlanner.outcome(roadAtY(320), WORLD);
        assertRefused(RoadPlanner.WORLD_LIMIT, Map.of(), tooHigh.result());
        assertTrue(tooHigh.refusedShape().isPresent(), "a road above the build height is drawn in red too");

        assertEquals(Optional.empty(), RoadPlanner.outcome(straight(0, 0, 1, 0, Heading.E), WORLD).refusedShape(), "too short: nothing to draw");
        RoadPlanner.Outcome fine = RoadPlanner.outcome(straight(0, 0, 20, 0, Heading.E), WORLD);
        assertEquals(plan(straight(0, 0, 20, 0, Heading.E)), fine.result());
        assertEquals(Optional.empty(), fine.refusedShape());
    }
}
