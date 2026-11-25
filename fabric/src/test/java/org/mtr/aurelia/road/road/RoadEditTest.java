/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Digest;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.undo.Replacement;

/**
 * Rebuilding and deleting placed roads in the planner and the roads module (task R-7): the plans and their checks on
 * both sides, and what the server's plan says it replaces.
 */
class RoadEditTest {
    private static final WorldLimits LIMITS = WorldLimits.OVERWORLD.withModBlocks(Set.of(MtrNodes.BLOCK));
    private static final UUID A = new UUID(2, 1);
    private static final UUID B = new UUID(2, 2);
    private static final RoadPlanParams ROAD = RoadPlanParams.straight("city-17", End.at(0, 64, 0, Heading.E), End.at(40, 64, 0, Heading.E),
            RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);

    private static RoadPlanParams edit(RoadPlanParams.Tool tool, RoadPlanParams road, Optional<RoadPlanParams.ManualEdits> answer) {
        return RoadPlanParams.edit(tool, A, road, answer);
    }

    private static String hashOf(PlanResult result) {
        return Digest.of(PlanHash.of(result.plan().orElseThrow(), result.sideEffects(), CoreInfo.version())).hex();
    }

    private static OperationRecord record(UUID id, RoadPlanParams params, int second, String planHash) {
        return new OperationRecord(id, "minecraft:overworld", "roads", params.tool().name(), RoadsModule.INSTANCE.paramsToJson(params), planHash,
                CoreInfo.version(), "alice", Instant.ofEpochSecond(second), OperationRecord.Status.APPLIED, true,
                List.of(new OperationRecord.SectionHash(0, 4, 0, "00".repeat(32))), List.of(), List.of(), List.of(), List.of(),
                List.of(BuiltInPalettes.vanilla().snapshotId())); // R-9: as the server records it
    }

    @Test
    void aStraightRoadPlansTheSameBlocksAsTheCurvedRoadOfItsEnds() {
        for (Heading heading : Heading.values()) {
            RoadPlanParams.End start = End.at(0, 64, 0, heading);
            RoadPlanParams straight = RoadPlanParams.straight("residential-15", start, RoadPlanner.snapEnd(start, 30), RoadPlanParams.Vertical.QUADRATIC,
                    RoadPlanParams.NodeAids.TRACK);
            PlanResult planned = RoadPlanner.plan(straight, LIMITS);
            PlanResult asRoad = RoadPlanner.plan(straight.road(), LIMITS);
            assertEquals(planned.plan(), asRoad.plan(), "heading " + heading);
            assertEquals(planned.sideEffects(), asRoad.sideEffects(), "heading " + heading);
        }
    }

    @Test
    void aRebuildPlansTheRoadAsPlacedWithItsTrackAndADeleteWithout() {
        PlanResult placed = RoadPlanner.plan(ROAD, LIMITS);
        Map<UUID, RoadPlanParams> standing = Map.of(A, ROAD);
        PlanResult rebuild = RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty()), LIMITS, standing);
        assertEquals(placed.plan(), rebuild.plan());
        assertEquals(placed.sideEffects(), rebuild.sideEffects());
        assertFalse(rebuild.sideEffects().isEmpty());
        PlanResult delete = RoadPlanner.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.of(RoadPlanParams.ManualEdits.KEEP)), LIMITS, standing);
        assertEquals(placed.plan(), delete.plan(), "a delete's plan is the road being removed, for the hash");
        assertEquals(List.of(), delete.sideEffects());
        assertEquals(Optional.empty(), delete.replacement(), "only the server's plan says what it replaces");
    }

    @Test
    void aRebuildOrDeleteOfARoadNotStandingOrChangedIsRefused() {
        RoadPlanParams rebuild = edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty());
        assertEquals(Optional.of(RoadPlanner.ROAD_GONE), RoadPlanner.plan(rebuild, LIMITS, Map.of()).code());
        assertEquals(Optional.of(RoadPlanner.ROAD_GONE), RoadPlanner.plan(rebuild, LIMITS, Map.of(B, ROAD)).code());
        assertEquals(Optional.of(RoadPlanner.ROAD_CHANGED), RoadPlanner.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty()), LIMITS,
                Map.of(A, ROAD.withNodeAids(RoadPlanParams.NodeAids.WOOL))).code(), "a delete names the road as it stands");
        assertTrue(RoadPlanner.plan(rebuild, LIMITS, Map.of(A, ROAD.withNodeAids(RoadPlanParams.NodeAids.WOOL))).isPlanned(),
                "R-10: a rebuild with other parameters edits the road");
        RoadPlanParams rebuilt = edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.of(RoadPlanParams.ManualEdits.OVERWRITE));
        assertTrue(RoadPlanner.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty()), LIMITS, Map.of(A, rebuilt)).isPlanned(),
                "a rebuilt road is the same road");
    }

    @Test
    void anEditIsPlannedAsTheRoadWithItsNewEndsAndLayout() {
        // R-10 (owner, 2026-10-13): a rebuild with other parameters edits the road.
        RoadPlanParams moved = RoadPlanParams.curve("residential-15", End.at(0, 64, 0, Heading.E), End.at(40, 64, 30, Heading.S),
                RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
        PlanResult edited = RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, moved, Optional.empty()), LIMITS, Map.of(A, ROAD));
        PlanResult asNew = RoadPlanner.plan(moved, LIMITS);
        assertEquals(asNew.plan(), edited.plan(), "the blocks of the road as drawn new");
        assertEquals(asNew.sideEffects(), edited.sideEffects(), "and its track");
        assertFalse(edited.sideEffects().isEmpty());

        RoadPlanParams tooShort = ROAD.withEnds(List.of(End.at(0, 64, 0, Heading.E), End.at(1, 64, 0, Heading.E)));
        assertEquals(RoadPlanner.plan(tooShort.road(), LIMITS).code(), RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, tooShort, Optional.empty()),
                LIMITS, Map.of(A, ROAD)).code(), "refused as a new road would be");
        assertEquals(Optional.of(RoadPlanner.ROAD_GONE), RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, moved, Optional.empty()), LIMITS, Map.of(B, ROAD))
                .code());
    }

    @Test
    void anEditedEndMayJoinAnotherRoadsFreeEndButNotItself() {
        // The owner, 2026-10-13: a moved end joins another road's free end, as when drawing; the planner checks it.
        RoadPlanParams other = RoadPlanParams.straight("city-17", End.at(0, 64, 100, Heading.E), End.at(40, 64, 100, Heading.E),
                RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
        Map<UUID, RoadPlanParams> standing = Map.of(A, ROAD, B, other);
        // A moved to run east into the start of the other road.
        RoadPlanParams joined = RoadPlanParams.curve("city-17", End.at(-60, 64, 100, Heading.E), new End(0, 64, 100, Heading.E, Optional.of(B),
                OptionalInt.of(0)), RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
        joined = joined.withDashes(RoadJoins.dashes(joined, id -> id.equals(B) ? RoadJoins.ports(other) : Optional.empty()));
        PlanResult edited = RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, joined, Optional.empty()), LIMITS, standing);
        assertTrue(edited.isPlanned(), () -> "refused " + edited.code());
        assertEquals(RoadPlanner.plan(joined, LIMITS, Map.of(B, other)).plan(), edited.plan());

        RoadPlanParams onItself = ROAD.withEnds(List.of(End.at(0, 64, 0, Heading.E), new End(40, 64, 0, Heading.E, Optional.of(A), OptionalInt.of(1))));
        assertEquals(Optional.of(RoadJoins.ROAD_END_GONE), RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, onItself, Optional.empty()), LIMITS,
                standing).code(), "the road being edited isn't there to join");
    }

    @Test
    void theServersPlanSaysWhatItReplacesWithTheGuardForTheAnswer() {
        PlanResult placed = RoadPlanner.plan(ROAD, LIMITS);
        List<OperationRecord> records = List.of(record(A, ROAD, 1, hashOf(placed)));
        PlanResult asked = RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty()), LIMITS, records);
        Replacement replacement = asked.replacement().orElseThrow();
        assertEquals(A, replacement.road());
        assertEquals(List.of(A), replacement.chain());
        assertEquals(placed.plan(), replacement.asPlaced(), "the same planner: the road's plan as placed");
        assertEquals(placed.plan(), replacement.firstPlaced(), "and the plan of the operation that placed it, for the ground");
        assertEquals(Set.of(), replacement.keep());
        assertFalse(replacement.delete());
        assertEquals(ApplyJob.Guard.ASK, replacement.guard());
        assertEquals(ApplyJob.Guard.SKIP_CHANGED, RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.DELETE, ROAD,
                Optional.of(RoadPlanParams.ManualEdits.KEEP)), LIMITS, records).replacement().orElseThrow().guard());
        Replacement overwrite = RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.of(RoadPlanParams.ManualEdits.OVERWRITE)),
                LIMITS, records).replacement().orElseThrow();
        assertEquals(ApplyJob.Guard.NONE, overwrite.guard());
        assertTrue(overwrite.delete());

        List<OperationRecord> older = List.of(record(A, ROAD, 1, "ff".repeat(32))); // planned by another Stratos
        assertEquals(Optional.empty(), RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty()), LIMITS, older)
                .replacement().orElseThrow().asPlaced());
    }

    @Test
    void anEditReplacesTheRoadAsItStandsAndIsThenTheRoadWithItsNewParameters() {
        // R-10: the replacement is the road as placed (its old blocks, for the ground and the hand-edit check); once
        // applied, the road keeps its id and stands with the edit's parameters, and the next edit replaces those.
        PlanResult placed = RoadPlanner.plan(ROAD, LIMITS);
        RoadPlanParams moved = RoadPlanParams.curve("residential-15", End.at(0, 64, 0, Heading.E), End.at(50, 64, 0, Heading.E),
                RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
        RoadPlanParams request = edit(RoadPlanParams.Tool.REBUILD, moved, Optional.empty());
        PlanResult asked = RoadsModule.INSTANCE.plan(request, LIMITS, List.of(record(A, ROAD, 1, hashOf(placed))));
        assertEquals(RoadPlanner.plan(moved, LIMITS).plan(), asked.plan());
        Replacement replacement = asked.replacement().orElseThrow();
        assertEquals(List.of(A), replacement.chain());
        assertEquals(placed.plan(), replacement.asPlaced(), "the road as it stands, not as edited");
        assertEquals(Set.of(), replacement.keep());

        UUID editId = new UUID(2, 9);
        List<OperationRecord> after = List.of(record(A, ROAD, 1, hashOf(placed)), record(editId, request, 2, hashOf(asked)));
        RoadHistory.Road road = RoadsModule.standing(after).get(A);
        assertEquals(moved.road(), road.params().road(), "the road keeps its id, with its new parameters");
        assertEquals(List.of(A, editId), road.chain());
        Replacement next = RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.DELETE, moved, Optional.empty()), LIMITS, after).replacement()
                .orElseThrow();
        assertEquals(asked.plan(), next.asPlaced(), "a later delete replaces the edited road");
        assertEquals(placed.plan(), next.firstPlaced(), "with the ground from the first placing");
    }

    @Test
    void theBlocksOfARoadJoinedToItAreKept() {
        PlanResult placed = RoadPlanner.plan(ROAD, LIMITS);
        RoadPlanParams joined = RoadPlanParams.straight("city-17", new End(40, 64, 0, Heading.E, Optional.of(A), OptionalInt.of(1)),
                End.at(80, 64, 0, Heading.E), RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
        PlanResult joinedPlan = RoadPlanner.asPlaced(joined, LIMITS);
        List<OperationRecord> records = List.of(record(A, ROAD, 1, hashOf(placed)), record(B, joined, 2, hashOf(joinedPlan)));
        Replacement replacement = RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty()), LIMITS, records).replacement()
                .orElseThrow();
        Set<Long> keep = new HashSet<>();
        joinedPlan.plan().orElseThrow().blocks().forEach(block -> keep.add(BlockChange.positionKey(block.x(), block.y(), block.z())));
        assertEquals(keep, replacement.keep());
        // and the other way round: deleting the joined road keeps the first road's blocks
        Replacement other = RoadsModule.INSTANCE.plan(RoadPlanParams.edit(RoadPlanParams.Tool.DELETE, B, joined, Optional.empty()), LIMITS, records)
                .replacement().orElseThrow();
        assertEquals(placed.plan().orElseThrow().blockCount(), other.keep().size());
    }

    @Test
    void theRecordsSayWhereARoadStands() {
        PlanResult placed = RoadPlanner.plan(ROAD, LIMITS);
        UUID rebuildId = new UUID(2, 3);
        UUID deleteId = new UUID(2, 4);
        RoadPlanParams rebuild = edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.of(RoadPlanParams.ManualEdits.KEEP));
        List<OperationRecord> records = new ArrayList<>(List.of(record(A, ROAD, 1, hashOf(placed)), record(rebuildId, rebuild, 2, hashOf(placed))));
        Replacement replacement = RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty()), LIMITS, records).replacement()
                .orElseThrow();
        assertEquals(List.of(A, rebuildId), replacement.chain());
        assertTrue(replacement.asPlaced().isPresent(), "the rebuild's plan hash is its plan's");
        assertEquals(placed.plan(), replacement.firstPlaced());
        assertEquals(Map.of(A, rebuild), RoadsModule.roads(records), "R-5's joins see the road with the rebuild's parameters, by its id");

        records.add(record(deleteId, edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty()), 3, "00".repeat(32)));
        assertEquals(Map.of(), RoadsModule.roads(records));
        assertEquals(Optional.of(RoadPlanner.ROAD_GONE), RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty()), LIMITS,
                records).code());

        List<OperationRecord> withoutPlacing = List.of(records.get(1)); // the cap dropped the placing before a restart
        assertEquals(Optional.of(RoadPlanner.ROAD_TOO_OLD), RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty()), LIMITS,
                withoutPlacing).code());
        assertTrue(RoadsModule.INSTANCE.plan(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty()), LIMITS, withoutPlacing).isPlanned(),
                "a rebuild needs no ground");
    }

    @Test
    void anEditUsesThePlacedRoadsAndTheRoadsKeepTheirParameters() {
        assertTrue(RoadsModule.INSTANCE.usesPlaced(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty())));
        assertTrue(RoadsModule.INSTANCE.usesPlaced(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty())));
        assertFalse(RoadsModule.INSTANCE.usesPlaced(ROAD));
        BlockPlan plan = RoadPlanner.asPlaced(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty()), LIMITS).plan().orElseThrow();
        assertEquals(RoadPlanner.plan(ROAD, LIMITS).plan().orElseThrow(), plan, "a rebuilt road as placed is the road");
        assertEquals(RoadJoins.ports(ROAD), RoadJoins.ports(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty())));
    }

    @Test
    void anEditThatPullsAJoinedRoadAwayIsRefusedButARoadsOwnFarEndMoves() {
        // B joins A's end.
        RoadPlanParams joining = RoadPlanParams.straight("city-17", new End(40, 64, 0, Heading.E, Optional.of(A), OptionalInt.of(1)),
                End.at(80, 64, 0, Heading.E), RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
        Map<UUID, RoadPlanParams> standing = Map.of(A, ROAD, B, joining);
        RoadPlanParams movedA = ROAD.withEnds(List.of(End.at(0, 64, 5, Heading.E), ROAD.ends().get(1)));
        assertEquals(Optional.of(RoadPlanner.ROAD_JOINED), RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, movedA, Optional.empty()), LIMITS, standing)
                .code(), "another road joins it");
        RoadPlanParams movedB = joining.withEnds(List.of(joining.ends().get(0), End.at(90, 64, 0, Heading.E)));
        // Master (802229e42, 2026-11-24): moving the far end of a road that joins another is fine, so it plans; upstream refused it.
        assertTrue(RoadPlanner.plan(RoadPlanParams.edit(RoadPlanParams.Tool.REBUILD, B, movedB, Optional.empty()), LIMITS, standing).isPlanned(),
                "it joins another road at its start, and its far end moves");
        assertTrue(RoadPlanner.plan(edit(RoadPlanParams.Tool.REBUILD, ROAD, Optional.empty()), LIMITS, standing).isPlanned(), "a plain rebuild");
        assertTrue(RoadPlanner.plan(edit(RoadPlanParams.Tool.DELETE, ROAD, Optional.empty()), LIMITS, standing).isPlanned(), "a delete");
    }
}
