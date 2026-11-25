/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.ops.ApplyJob;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.PlanHash;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Digest;
import org.mtr.aurelia.road.proto.MalformedMessage;
import org.mtr.aurelia.road.proto.PlanModule;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Replacement;
import org.mtr.aurelia.road.undo.SideEffect;

/** The roads module on the {@code aurelia:roads} channel: {@code module = roads}, parameters {@link RoadPlanParams}. */
public final class RoadsModule implements PlanModule<RoadPlanParams> {
    public static final RoadsModule INSTANCE = new RoadsModule();
    public static final String ID = "roads";
    /** Placing new roads (editor-protocol §7). */
    public static final String NODE_APPLY = "stratos.roads.apply";
    /** Rebuilding (in the same palette or another) or deleting a placed road, and clearing its markers. */
    public static final String NODE_EDIT = "stratos.roads.edit";
    /** The pre-oriented MTR node aid (the "MTR nodes" choice; Track doesn't need it). */
    public static final String NODE_PREORIENTED = "stratos.nodes.preoriented";

    private RoadsModule() {
    }

    /**
     * F-6: a new road needs {@link #NODE_APPLY}, a rebuild, delete or clear-markers {@link #NODE_EDIT}; a plan that places
     * pre-oriented MTR nodes (a new road or a rebuild) needs {@link #NODE_PREORIENTED} too.
     */
    @Override
    public List<String> nodes(RoadPlanParams params) {
        RoadPlanParams.Tool tool = params.tool();
        boolean editing = tool == RoadPlanParams.Tool.REBUILD || tool == RoadPlanParams.Tool.DELETE || tool == RoadPlanParams.Tool.CLEAR_MARKERS;
        boolean placesNodes = params.nodeAids() == RoadPlanParams.NodeAids.PREORIENTED && tool != RoadPlanParams.Tool.DELETE
                && tool != RoadPlanParams.Tool.CLEAR_MARKERS;
        String node = editing ? NODE_EDIT : NODE_APPLY;
        return placesNodes ? List.of(node, NODE_PREORIENTED) : List.of(node);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String tool(RoadPlanParams params) {
        return params.tool().name();
    }

    @Override
    public byte[] encode(RoadPlanParams params) {
        return params.encode();
    }

    @Override
    public RoadPlanParams decode(String tool, byte[] bytes) throws MalformedMessage {
        RoadPlanParams params = RoadPlanParams.decode(bytes);
        if (!params.tool().name().equals(tool)) {
            throw new MalformedMessage("the request is for tool " + tool + " but its parameters are for " + params.tool());
        }
        return params;
    }

    @Override
    public PlanResult plan(RoadPlanParams params, WorldLimits limits) {
        return RoadPlanner.plan(params, limits);
    }

    /** The snapshot of the road's palette (R-9), if this world has one with its id. */
    @Override
    public List<String> palettes(RoadPlanParams params, WorldLimits limits) {
        return limits.palettes().byId(params.paletteId()).map(palette -> List.of(palette.snapshotId())).orElse(List.of());
    }

    /** Council r4: the dashes the road's joins call for, worked out here, not the client's ({@link RoadPlanner#settled}). */
    @Override
    public RoadPlanParams settle(RoadPlanParams params, List<OperationRecord> placed) {
        return RoadPlanner.settled(params, roads(placed));
    }

    /** A straight or curved road with an end that joins another road (R-5), or (R-7) a rebuild or delete of a placed road. */
    @Override
    public boolean usesPlaced(RoadPlanParams params) {
        return params.tool() == RoadPlanParams.Tool.REBUILD || params.tool() == RoadPlanParams.Tool.DELETE
                || (params.tool() == RoadPlanParams.Tool.STRAIGHT || params.tool() == RoadPlanParams.Tool.CURVE)
                && params.ends().stream().anyMatch(end -> end.snapRoadId().isPresent());
    }

    /**
     * Plans {@code params} with the roads standing among {@code placed}, the records of the operations applied in the
     * world (R-5: the roads it may join, by road id; R-7: through rebuilds and deletes, {@link RoadHistory}). A record
     * whose parameters no longer read is left out.
     *
     * <p><b>R-7:</b> a rebuild's or delete's plan also says what it replaces ({@link Replacement}), for the server to build
     * its job from the road's undo images:
     * <ul>
     *   <li>the road's operations that built it, oldest first; a delete of a road whose placing operation has gone from
     *       the records (the cap dropped it before a restart) is refused {@link RoadPlanner#ROAD_TOO_OLD};</li>
     *   <li>the road's plan as placed, when today's planner gives exactly the plan its latest operation placed (the
     *       record's plan hash, with that record's core version); and the same for the operation that first placed it, for
     *       the ground;</li>
     *   <li>the blocks of the roads joined to it, at either end ({@link RoadGraph#neighbours}; owner: they keep what they
     *       share);</li>
     *   <li>the guard for the player's answer: none yet asks, {@code KEEP} keeps hand edits, {@code OVERWRITE} writes over
     *       them.</li>
     * </ul>
     */
    @Override
    public PlanResult plan(RoadPlanParams params, WorldLimits limits, List<OperationRecord> placed) {
        Map<UUID, OperationRecord> byId = new LinkedHashMap<>();
        placed.forEach(record -> byId.put(record.id(), record));
        Map<UUID, RoadHistory.Road> roads = standing(placed);
        PlanResult result = RoadPlanner.plan(params, limits, RoadHistory.params(roads));
        boolean editing = params.tool() == RoadPlanParams.Tool.REBUILD || params.tool() == RoadPlanParams.Tool.DELETE;
        if (!editing || !result.isPlanned()) {
            return result;
        }
        RoadHistory.Road road = roads.get(params.targetRoadId().orElseThrow()); // the planner found it standing
        boolean delete = params.tool() == RoadPlanParams.Tool.DELETE;
        if (delete && !road.complete()) {
            return PlanResult.refused(RoadPlanner.ROAD_TOO_OLD);
        }
        ApplyJob.Guard guard = params.manualEdits().map(answer -> answer == RoadPlanParams.ManualEdits.KEEP ? ApplyJob.Guard.SKIP_CHANGED
                : ApplyJob.Guard.NONE).orElse(ApplyJob.Guard.ASK);
        // Owner, 2026-11-24: the roads a moved joined end takes with it are replaced in the same job.
        Map<UUID, RoadPlanParams> standing = RoadHistory.params(roads);
        Set<UUID> group = new LinkedHashSet<>();
        group.add(road.id());
        group.addAll(delete ? Set.of() : RoadPlanner.followers(params, standing).keySet());
        RoadGraph graph = RoadGraph.of(standing);
        List<Replacement> joined = new ArrayList<>();
        for (UUID follower : group) {
            if (!follower.equals(road.id())) {
                joined.add(part(roads.get(follower), byId, limits, keep(graph, follower, group, roads, limits), false, guard, List.of()));
            }
        }
        return result.withReplacement(part(road, byId, limits, keep(graph, road.id(), group, roads, limits), delete, guard, joined));
    }

    /**
     * What a delete or rebuild replaces of {@code road} ({@link Replacement}). Of an operation of its chain that also built
     * other roads, only its blocks before and after are its own, and of its latest operation's track, only its own.
     */
    private static Replacement part(RoadHistory.Road road, Map<UUID, OperationRecord> byId, WorldLimits limits, Set<Long> keep, boolean delete,
            ApplyJob.Guard guard, List<Replacement> joined) {
        Optional<BlockPlan> exact = road.shared().contains(road.latest()) ? Optional.empty() : asPlaced(byId.get(road.latest()), limits);
        Optional<BlockPlan> first = road.complete() ? asPlaced(byId.get(road.chain().get(0)), limits) : Optional.empty();
        Map<UUID, Set<Long>> only = new HashMap<>();
        for (int i = 0; i < road.chain().size(); i++) {
            UUID op = road.chain().get(i);
            if (road.shared().contains(op)) {
                Set<Long> positions = blocks(road.builds().get(i), limits);
                if (i > 0) {
                    positions.addAll(blocks(road.builds().get(i - 1), limits));
                }
                only.merge(op, positions, RoadsModule::union);
                // Where it took over another road's blocks, the ground is what was there before that road: its operations' images.
                for (RoadHistory.Prior prior : road.priors().getOrDefault(op, List.of())) {
                    Set<Long> took = new HashSet<>(blocks(prior.params(), limits));
                    took.retainAll(positions);
                    for (UUID earlier : prior.chain()) {
                        only.merge(earlier, new HashSet<>(took), RoadsModule::union);
                    }
                }
            }
        }
        Optional<List<SideEffect>> rails = road.shared().contains(road.latest())
                ? Optional.of(RoadPlanner.asPlaced(road.params(), limits).sideEffects())
                : Optional.empty();
        return new Replacement(road.id(), road.chain(), exact, first, keep, delete, guard, only, rails, joined);
    }

    private static Set<Long> union(Set<Long> a, Set<Long> b) {
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        return union;
    }

    /** The blocks of the roads joined to {@code road} that aren't in {@code group}, which its replacement keeps. */
    private static Set<Long> keep(RoadGraph graph, UUID road, Set<UUID> group, Map<UUID, RoadHistory.Road> roads, WorldLimits limits) {
        Set<Long> keep = new HashSet<>();
        for (UUID neighbour : graph.neighbours(road)) {
            if (!group.contains(neighbour)) {
                keep.addAll(blocks(roads.get(neighbour).params(), limits));
            }
        }
        return keep;
    }

    /** The positions of the blocks of the road {@code params} describe, planned as placed. */
    private static Set<Long> blocks(RoadPlanParams params, WorldLimits limits) {
        Set<Long> blocks = new HashSet<>();
        RoadPlanner.asPlaced(params, limits).plan().ifPresent(plan -> plan.blocks()
                .forEach(block -> blocks.add(BlockChange.positionKey(block.x(), block.y(), block.z()))));
        return blocks;
    }

    /**
     * The plan {@code record}'s road is as placed, when today's planner gives exactly that plan (its hash, with its core
     * version). From R-9 it is planned with the palette snapshot the record names, so a palette changed or gone since
     * doesn't matter; a roads record that names none was placed before R-9, with the provisional vanilla palette (owner,
     * 2026-10-10). A snapshot this world doesn't know gives no plan (the undo images then say what the road placed).
     */
    static Optional<BlockPlan> asPlaced(OperationRecord record, WorldLimits limits) {
        if (record == null) {
            return Optional.empty();
        }
        RoadPlanParams params;
        try {
            params = INSTANCE.paramsFromJson(record.tool(), record.params());
        } catch (JsonFormatException | IllegalArgumentException e) {
            return Optional.empty();
        }
        String snapshotId = record.palettes().isEmpty() ? BuiltInPalettes.provisional().snapshotId() : record.palettes().get(0);
        Optional<Palette> snapshot = limits.palettes().snapshot(snapshotId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        PlanResult planned = RoadPlanner.asPlaced(params, limits.withPalettes(limits.palettes().pinned(snapshot.get())));
        if (planned.isPlanned() && Digest.of(PlanHash.of(planned.plan().orElseThrow(), planned.sideEffects(), record.coreVersion())).hex()
                .equals(record.planHash())) {
            return planned.plan();
        }
        return Optional.empty();
    }

    /**
     * The roads standing among the operations {@code records} (R-7: through rebuilds and deletes), by road id: the roads
     * module's records whose parameters read, in the order they were made (their time).
     */
    public static Map<UUID, RoadHistory.Road> standing(List<OperationRecord> records) {
        List<OperationRecord> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(OperationRecord::time)); // the order they were made
        List<RoadHistory.Op> ops = new ArrayList<>();
        for (OperationRecord record : sorted) {
            if (!record.module().equals(ID)) {
                continue;
            }
            try {
                RoadPlanParams params = INSTANCE.paramsFromJson(record.tool(), record.params());
                ops.add(new RoadHistory.Op(record.id(), params, record.status() == OperationRecord.Status.APPLIED));
            } catch (JsonFormatException | IllegalArgumentException e) {
                // unreadable: it can't be joined or edited (the server logs unreadable records where it lists them)
            }
        }
        return RoadHistory.resolve(ops);
    }

    /** The parameters of the roads standing among {@code records}, by road id (R-5's roads a new road may join). */
    public static Map<UUID, RoadPlanParams> roads(List<OperationRecord> records) {
        return RoadHistory.params(standing(records));
    }

    /** Every field by name ({@link RoadParamsJson}; G-9). */
    @Override
    public String paramsToJson(RoadPlanParams params) {
        return StrictJson.compact(RoadParamsJson.toJson(params));
    }

    @Override
    public RoadPlanParams paramsFromJson(String tool, String json) throws JsonFormatException {
        RoadPlanParams params = RoadParamsJson.fromJson(StrictJson.parseObject(json, "params"));
        if (!params.tool().name().equals(tool)) {
            throw new JsonFormatException("params.tool is " + params.tool() + " but the record's tool is " + tool);
        }
        return params;
    }
}
