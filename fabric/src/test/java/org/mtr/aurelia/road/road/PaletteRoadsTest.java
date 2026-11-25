/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Digest;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.road.palette.BlockLookup;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteException;
import org.mtr.aurelia.road.road.palette.PaletteFile;
import org.mtr.aurelia.road.road.palette.PaletteRole;
import org.mtr.aurelia.road.road.palette.Palettes;
import org.mtr.aurelia.road.undo.OperationRecord;

/**
 * Roads in palettes (task R-9): the planner takes a road's blocks from its palette, warns of whole-block steps, rebuilds
 * a road in another palette, and the server plans a record as placed from the palette snapshot it names (or, before R-9,
 * the provisional vanilla palette).
 */
class PaletteRoadsTest {
    private static final UUID A = new UUID(9, 1);
    private static final RoadPlanParams ROAD = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E),
            RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.WOOL);

    /** A palette {@code id} with every role a full block only: brick roads, green wool markers. */
    private static Palette bricks(String id) {
        Map<PaletteRole, List<Family>> roles = new EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            if (role.required()) {
                roles.put(role, List.of(Family.full(role == PaletteRole.FOUNDATION ? "minecraft:stone"
                        : role == PaletteRole.NODE_MARKER ? "minecraft:green_wool" : "minecraft:bricks")));
            }
        }
        try {
            return Palette.resolve(new PaletteFile(id, Optional.of("Bricks"), 3465, roles), BlockLookup.VANILLA);
        } catch (PaletteException e) {
            throw new AssertionError(e);
        }
    }

    private static final Palette BRICKS = bricks("bricks");
    private static final WorldLimits LIMITS = WorldLimits.OVERWORLD.withPalettes(Palettes.of(List.of(BuiltInPalettes.vanilla(), BRICKS),
            List.of(BuiltInPalettes.provisional())));

    private static String hashOf(PlanResult result) {
        return Digest.of(PlanHash.of(result.plan().orElseThrow(), result.sideEffects(), CoreInfo.version())).hex();
    }

    private static OperationRecord record(UUID id, RoadPlanParams params, String planHash, List<String> palettes) {
        return new OperationRecord(id, "minecraft:overworld", "roads", params.tool().name(), RoadsModule.INSTANCE.paramsToJson(params), planHash,
                CoreInfo.version(), "alice", Instant.ofEpochSecond(1), OperationRecord.Status.APPLIED, true, List.of(), List.of(), List.of(), List.of(),
                List.of(), palettes);
    }

    private static Set<String> states(PlanResult result) {
        Set<String> states = new java.util.TreeSet<>();
        result.plan().orElseThrow().blocks().forEach(block -> states.add(block.state()));
        return states;
    }

    @Test
    void aRoadTakesItsBlocksAndMarkersFromItsPalette() {
        PlanResult vanilla = RoadPlanner.plan(ROAD, LIMITS);
        PlanResult bricks = RoadPlanner.plan(ROAD.withPalette("bricks"), LIMITS);
        assertEquals(Set.of("minecraft:air", "minecraft:blue_wool", "minecraft:polished_andesite", "minecraft:polished_deepslate", "minecraft:smooth_quartz",
                "minecraft:smooth_stone", "minecraft:stone"), states(vanilla));
        assertEquals(Set.of("minecraft:air", "minecraft:bricks", "minecraft:green_wool", "minecraft:stone"), states(bricks));
        assertEquals(vanilla.plan().orElseThrow().blockCount(), bricks.plan().orElseThrow().blockCount(), "the same road, other blocks");
        // The node aids read each road's marker from its plan.
        PlacedRoads placed = new PlacedRoads();
        placed.placed(A, ROAD.withPalette("bricks"), bricks.plan().orElseThrow());
        assertEquals(Optional.of("minecraft:green_wool"), placed.roads().get(0).marker());
    }

    @Test
    void anUnknownPaletteIsRefusedAndAClimbInWholeBlocksWarns() {
        assertEquals(Optional.of(RoadPlanner.UNKNOWN_PALETTE), RoadPlanner.plan(ROAD.withPalette("granite"), LIMITS).code());
        assertEquals(Map.of("palette", "granite"), RoadPlanner.plan(ROAD.withPalette("granite"), LIMITS).args());
        RoadPlanParams climbing = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(80, 67, 0, Heading.E));
        assertEquals(List.of(), RoadPlanner.plan(climbing, LIMITS).warnings(), "Vanilla has slabs");
        assertEquals(List.of(new PlanResult.Warning(RoadPlanner.FULL_BLOCK_STEPS, Map.of())),
                RoadPlanner.plan(climbing.withPalette("bricks"), LIMITS).warnings());
        assertEquals(List.of(), RoadPlanner.plan(ROAD.withPalette("bricks"), LIMITS).warnings(), "a level road doesn't step");
        assertFalse(RoadPlanner.plan(climbing.withPalette("bricks"), LIMITS).plan().orElseThrow().blocks().stream()
                .anyMatch(block -> block.state().contains("_slab")), "whole blocks only");
    }

    @Test
    void aRebuildMayPutTheRoadInAnotherPaletteButADeleteMayNot() {
        Map<UUID, RoadPlanParams> standing = Map.of(A, ROAD);
        PlanResult repaint = RoadPlanner.plan(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, ROAD.withPalette("bricks"), Optional.empty()), LIMITS,
                standing);
        assertEquals(RoadPlanner.plan(ROAD.withPalette("bricks"), LIMITS).plan(), repaint.plan(), "the road rebuilt in bricks");
        assertEquals(Optional.of(RoadPlanner.ROAD_CHANGED), RoadPlanner.plan(RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, A, ROAD.withPalette("bricks"),
                Optional.empty()), LIMITS, standing).code(), "a delete names the road as it is");
        RoadPlanParams moved = ROAD.withEnds(List.of(End.at(0, 64, 0, Heading.E), End.at(50, 64, 0, Heading.E))).withPalette("bricks");
        assertEquals(RoadPlanner.plan(moved, LIMITS).plan(), RoadPlanner.plan(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, moved, Optional.empty()),
                LIMITS, standing).plan(), "R-10: an edit may move an end and change the palette at once");
        assertEquals(Optional.of(RoadPlanner.UNKNOWN_PALETTE), RoadPlanner.plan(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A,
                ROAD.withPalette("granite"), Optional.empty()), LIMITS, standing).code());
        // Once rebuilt in bricks, the road stands in bricks: its next rebuild names bricks.
        RoadPlanParams rebuilt = RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, A, ROAD.withPalette("bricks"), Optional.of(RoadPlanParams.ManualEdits.KEEP));
        assertEquals("bricks", RoadHistory.params(RoadsModule.standing(List.of(record(A, ROAD, "00".repeat(32), List.of()),
                record(new UUID(9, 2), rebuilt, "00".repeat(32), List.of())))).get(A).paletteId());
    }

    @Test
    void theServerPlansARecordAsPlacedWithThePaletteItNames() {
        PlanResult inVanilla = RoadPlanner.plan(ROAD, LIMITS);
        Optional<BlockPlan> exact = RoadsModule.asPlaced(record(A, ROAD, hashOf(inVanilla), List.of(BuiltInPalettes.vanilla().snapshotId())), LIMITS);
        assertEquals(inVanilla.plan(), exact);

        // A road placed before R-9 names no palette: it was the provisional one (grey concrete), still exact.
        PlanResult before = RoadPlanner.plan(ROAD, LIMITS.withPalettes(LIMITS.palettes().pinned(BuiltInPalettes.provisional())));
        assertTrue(states(before).contains("minecraft:gray_concrete"));
        assertEquals(before.plan(), RoadsModule.asPlaced(record(A, ROAD, hashOf(before), List.of()), LIMITS));

        // The palette changed since (another "bricks" file): the record's snapshot still plans the road as it was placed.
        PlanResult inBricks = RoadPlanner.plan(ROAD.withPalette("bricks"), LIMITS);
        OperationRecord bricksRecord = record(A, ROAD.withPalette("bricks"), hashOf(inBricks), List.of(BRICKS.snapshotId()));
        Palette changed = bricksChangedTo("minecraft:mud_bricks");
        WorldLimits later = WorldLimits.OVERWORLD.withPalettes(Palettes.of(List.of(BuiltInPalettes.vanilla(), changed), List.of(BRICKS)));
        assertEquals(inBricks.plan(), RoadsModule.asPlaced(bricksRecord, later), "from the world's snapshot");
        WorldLimits forgotten = WorldLimits.OVERWORLD.withPalettes(Palettes.of(List.of(BuiltInPalettes.vanilla(), changed), List.of()));
        assertEquals(Optional.empty(), RoadsModule.asPlaced(bricksRecord, forgotten), "no snapshot: the undo images say what it placed");
        assertEquals(List.of(BRICKS.snapshotId()), RoadsModule.INSTANCE.palettes(ROAD.withPalette("bricks"), LIMITS), "what the record names");
    }

    /** The bricks palette with its road surface changed to {@code block}: same id, another snapshot. */
    private static Palette bricksChangedTo(String block) {
        Map<PaletteRole, List<Family>> roles = new EnumMap<>(PaletteRole.class);
        BRICKS.families().forEach((role, family) -> roles.put(role, List.of(role == PaletteRole.SURFACE ? Family.full(block) : family)));
        try {
            return Palette.resolve(new PaletteFile("bricks", Optional.empty(), 3465, roles), BlockLookup.VANILLA);
        } catch (PaletteException e) {
            throw new AssertionError(e);
        }
    }
}
