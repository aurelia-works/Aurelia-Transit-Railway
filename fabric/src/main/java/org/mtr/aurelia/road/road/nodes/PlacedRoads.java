/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.road.RoadHistory;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/**
 * The roads the player placed this visit (owner, 2026-10-06: R-6), for the node aids: the overlay and the checker look
 * at their lane nodes, and the Lane nodes tool clears their markers. Client only, client thread.
 * <ul>
 *   <li>A road is remembered once the server reports it placed ({@link #placed}); only straight and curved roads.</li>
 *   <li>A road whose operation is undone is set aside ({@link #undone}), and comes back when it is redone
 *       ({@link #redone}).</li>
 *   <li>Leaving the world forgets them all ({@link #clear}). On joining, the server lists the player's roads they can
 *       still undo or redo (F-5: {@code YourRoads}; owner, 2026-10-08), and {@link #replace} puts them back.</li>
 *   <li><b>From R-7</b> it keeps the operations themselves, rebuilds and deletes included, in the order they were made,
 *       and a road stands as {@link RoadHistory} says: a rebuilt road takes the rebuild's parameters, a deleted one is set
 *       aside, and undoing either brings the road back as it was. A road keeps its id (the operation that first placed
 *       it) throughout.</li>
 * </ul>
 */
public final class PlacedRoads {
    /**
     * A placed road: its id (the operation that first placed it), the operation that built it last (R-7), its parameters,
     * its lane nodes (G-8), its centre line's length, its blocks (to know which road the player aims at) and (R-5) its two
     * ends, which a new road may join.
     */
    public static final class Road {
        private final UUID operationId;
        private final UUID latest;
        private final RoadPlanParams params;
        private final List<LaneNode> nodes;
        private final double length;
        private final Set<Long> blocks;
        private final List<RoadJoins.Port> ports;
        private final Optional<String> marker;

        Road(UUID operationId, UUID latest, RoadPlanParams params, List<LaneNode> nodes, double length, Set<Long> blocks, List<RoadJoins.Port> ports,
                Optional<String> marker) {
            this.operationId = operationId;
            this.latest = latest;
            this.params = params;
            this.nodes = List.copyOf(nodes);
            this.length = length;
            this.blocks = blocks;
            this.ports = List.copyOf(ports);
            this.marker = marker;
        }

        /**
         * The road's id: the operation that first placed it. A rebuild keeps it (R-7), so R-5's {@code snapRoadId} and the
         * Edit roads tool's {@code targetRoadId} name the road by it.
         */
        public UUID operationId() {
            return this.operationId;
        }

        /** The operation that built the road last (R-7): the one that placed it, or its latest rebuild. */
        public UUID latest() {
            return this.latest;
        }

        /** Its parameters as they stand: the placing operation's, or (R-7) its latest rebuild's. */
        public RoadPlanParams params() {
            return this.params;
        }

        /** Its lane nodes, in {@code LaneNodes.of} order: lane 1's along the road, then lane 2's, and so on. */
        public List<LaneNode> nodes() {
            return this.nodes;
        }

        /** The centre line's length, in blocks. */
        public double length() {
            return this.length;
        }

        /** Its start (index 0) and its end (index 1), which a new road may join (R-5). */
        public List<RoadJoins.Port> ports() {
            return this.ports;
        }

        /** Whether it was placed with blue-wool markers. */
        public boolean markers() {
            return this.params.nodeAids() == RoadPlanParams.NodeAids.WOOL;
        }

        /** The marker under its nodes, as its plan placed it (R-9: its palette's {@code node_marker}); none unless {@link #markers()}. */
        public Optional<String> marker() {
            return this.marker;
        }

        /** Whether the block at {@code (x, y, z)} is one of the road's. */
        public boolean contains(int x, int y, int z) {
            return this.blocks.contains(BlockChange.positionKey(x, y, z));
        }

        /**
         * The index (into {@link #nodes()}) of the node whose marker spot or node block is {@code (x, y, z)}, if any.
         */
        public Optional<Integer> nodeAt(int x, int y, int z) {
            for (int i = 0; i < this.nodes.size(); i++) {
                LaneNode node = this.nodes.get(i);
                if (node.x() == x && node.z() == z && (y == node.y() || y == node.nodeY())) {
                    return Optional.of(i);
                }
            }
            return Optional.empty();
        }

        /** The horizontal distance from {@code (x, z)} to the road's nearest node, in blocks. */
        public double distanceTo(double x, double z) {
            double best = Double.POSITIVE_INFINITY;
            for (LaneNode node : this.nodes) {
                double dx = node.x() + 0.5 - x;
                double dz = node.z() + 0.5 - z;
                best = StrictMath.min(best, StrictMath.sqrt(dx * dx + dz * dz));
            }
            return best;
        }
    }

    /** An operation the aids know: placing a road, or (R-7) rebuilding or deleting one; its plan (null for a delete). */
    private record Entry(UUID id, RoadPlanParams params, BlockPlan plan) {
    }

    /** Every road operation known this visit, oldest first; undone ones too, until redone or replaced. */
    private final List<Entry> entries = new ArrayList<>();
    private final Set<UUID> undone = new HashSet<>();
    /** The roads built so far, by the operation that built them last, so a road is worked out once. */
    private final Map<UUID, Road> built = new HashMap<>();

    /**
     * Remembers the operation {@code operationId}, placed with {@code params} and the blocks of {@code plan}: placing a
     * road, or (R-7) rebuilding or deleting one ({@code plan} may then be null). Returns the road it leaves standing, or
     * empty for a delete, anything but a road operation, or a road the planner doesn't solve.
     */
    public Optional<Road> placed(UUID operationId, RoadPlanParams params, BlockPlan plan) {
        Objects.requireNonNull(operationId, "operationId");
        Optional<UUID> road = RoadHistory.roadOf(operationId, params);
        if (road.isEmpty() || (plan == null && params.tool() != RoadPlanParams.Tool.DELETE)) {
            return Optional.empty();
        }
        this.entries.removeIf(entry -> entry.id().equals(operationId));
        this.undone.remove(operationId);
        this.built.remove(operationId);
        this.entries.add(new Entry(operationId, params, plan));
        return roads().stream().filter(r -> r.operationId.equals(road.get())).findFirst();
    }

    /** The operation {@code operationId} was undone: its road is set aside, or (R-7) back as it was before it. */
    public void undone(UUID operationId) {
        if (this.entries.stream().anyMatch(entry -> entry.id().equals(operationId))) {
            this.undone.add(operationId);
        }
    }

    /** The operation {@code operationId} was redone: its road is back. */
    public void redone(UUID operationId) {
        this.undone.remove(operationId);
    }

    /**
     * An operation the server listed (F-5): its id, parameters and plan, and whether it is undone. From R-7 also rebuilds
     * and deletes; a delete's plan is null.
     */
    public record Known(UUID operationId, RoadPlanParams params, BlockPlan plan, boolean undone) {
        public Known {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(params, "params");
            if (plan == null && params.tool() != RoadPlanParams.Tool.DELETE) {
                throw new IllegalArgumentException("Only a delete has no plan");
            }
        }
    }

    /**
     * Replaces every operation with {@code roads}, the server's list in the order they were made (F-5: after joining, or a
     * change of dimension); undone ones are set aside. Anything but a road operation is left out.
     */
    public void replace(List<Known> roads) {
        clear();
        for (Known road : roads) {
            if (RoadHistory.roadOf(road.operationId(), road.params()).isPresent()) {
                this.entries.add(new Entry(road.operationId(), road.params(), road.plan()));
                if (road.undone()) {
                    this.undone.add(road.operationId());
                }
            }
        }
    }

    /** Forgets every road (leaving the world). */
    public void clear() {
        this.entries.clear();
        this.undone.clear();
        this.built.clear();
    }

    /** The roads that are placed now, newest first: by the operation that built each last. */
    public List<Road> roads() {
        List<RoadHistory.Op> ops = new ArrayList<>();
        Map<UUID, Entry> byId = new HashMap<>();
        for (Entry entry : this.entries) {
            ops.add(new RoadHistory.Op(entry.id(), entry.params(), !this.undone.contains(entry.id())));
            byId.put(entry.id(), entry);
        }
        Map<UUID, RoadHistory.Road> standing = RoadHistory.resolve(ops);
        List<Road> active = new ArrayList<>();
        for (int i = this.entries.size() - 1; i >= 0; i--) {
            Entry entry = this.entries.get(i);
            Optional<UUID> road = RoadHistory.roadOf(entry.id(), entry.params());
            RoadHistory.Road stands = road.map(standing::get).orElse(null);
            if (stands == null || !stands.latest().equals(entry.id())) {
                continue; // not the operation that built a standing road last
            }
            Road built = this.built.get(entry.id());
            if (built == null) {
                built = build(stands.id(), entry);
                if (built == null) {
                    continue;
                }
                this.built.put(entry.id(), built);
            }
            active.add(built);
        }
        return active;
    }

    /** The road {@code entry} builds, as road {@code id}; null when the planner doesn't solve it. */
    private static Road build(UUID id, Entry entry) {
        RoadPlanParams params = entry.params();
        Optional<List<LaneNode>> nodes = RoadPlanner.laneNodes(params);
        if (nodes.isEmpty() || entry.plan() == null) {
            return null;
        }
        Set<Long> blocks = new HashSet<>();
        Optional<String> marker = Optional.empty();
        LaneNode first = nodes.get().get(0);
        for (BlockPlan.Block block : entry.plan().blocks()) {
            blocks.add(BlockChange.positionKey(block.x(), block.y(), block.z()));
            if (params.nodeAids() == RoadPlanParams.NodeAids.WOOL && block.x() == first.x() && block.y() == first.y() && block.z() == first.z()) {
                marker = Optional.of(block.state()); // R-9: the road's palette's marker
            }
        }
        double length = PieceSolver.centreLine(params.ends().get(0), params.ends().get(1)).map(rail -> rail.getLength()).orElse(0.0);
        List<RoadJoins.Port> ports = RoadJoins.ports(params).orElse(List.of());
        return new Road(id, entry.id(), params, nodes.get(), length, blocks, ports, marker);
    }

    /** The newest placed road whose node (marker spot or node block) is at {@code (x, y, z)}, with the node's index. */
    public Optional<Aimed> nodeAt(int x, int y, int z) {
        for (Road road : roads()) {
            Optional<Integer> index = road.nodeAt(x, y, z);
            if (index.isPresent()) {
                return Optional.of(new Aimed(road, Optional.of(index.get())));
            }
        }
        return Optional.empty();
    }

    /** The newest placed road with a block at {@code (x, y, z)}. */
    public Optional<Road> containing(int x, int y, int z) {
        return roads().stream().filter(road -> road.contains(x, y, z)).findFirst();
    }

    /** The placed road with the node nearest {@code (x, z)}, within {@code radius} blocks (the newest on a tie). */
    public Optional<Road> nearest(double x, double z, double radius) {
        Road best = null;
        double bestDistance = radius;
        for (Road road : roads()) {
            double distance = road.distanceTo(x, z);
            if (distance <= bestDistance && (best == null || distance < bestDistance)) {
                best = road;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** A road and, if a node of it is aimed at, that node's index. */
    public record Aimed(Road road, Optional<Integer> node) {
        public Aimed {
            Objects.requireNonNull(road, "road");
            Objects.requireNonNull(node, "node");
        }
    }
}
