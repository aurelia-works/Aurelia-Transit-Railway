/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.spi.BlockReader;

/**
 * The wrong-node checker (spec roads-v1 §9; ADR-0033; task R-6): reads the blocks at a road's lane nodes by runtime id
 * and says, for each node, whether an MTR node sits there with the lane's heading. The node block is
 * {@link LaneNode#nodeY()}, on top of the road surface (S6a); a wool road's marker is the surface block under it.
 * Each node gets the first status that fits, in this order:
 * <ol>
 *   <li>{@link Status#RIGHT}: an MTR node in the node block, with the lane's heading (modulo 180°).</li>
 *   <li>{@link Status#WRONG_HEADING}: an MTR node in the node block, with another heading.</li>
 *   <li>{@link Status#TOO_LOW} / {@link Status#TOO_HIGH}: none there, but one right below it (in the marker spot) or
 *       right above it.</li>
 *   <li>{@link Status#ONE_OFF}: one in a block beside the node block (the 8 around it, at its height).</li>
 *   <li>{@link Status#NOT_PLACED}: the marker is still there (a wool road), or the node block is empty (a road placed
 *       without markers).</li>
 *   <li>{@link Status#MISSING}: anything else, such as a wool road whose marker is gone with no node.</li>
 * </ol>
 * A node whose block isn't loaded here is {@link Status#NOT_LOADED}.
 */
public final class NodeCheck {
    /** What the checker found at a lane node. */
    public enum Status {
        RIGHT,
        WRONG_HEADING,
        TOO_LOW,
        TOO_HIGH,
        ONE_OFF,
        NOT_PLACED,
        MISSING,
        NOT_LOADED;

        /** Whether the node is placed wrong, or gone: the checker flags these in red. */
        public boolean wrong() {
            return this == WRONG_HEADING || this == TOO_LOW || this == TOO_HIGH || this == ONE_OFF || this == MISSING;
        }
    }

    /** A block position. */
    public record Spot(int x, int y, int z) {
    }

    /**
     * One lane node's result: the node, its number along its lane (from 1), its status, the heading found (for
     * {@link Status#WRONG_HEADING}), and where a misplaced node was found ({@link Status#TOO_LOW},
     * {@link Status#TOO_HIGH}, {@link Status#ONE_OFF}).
     */
    public record Result(LaneNode node, int number, Status status, OptionalInt found, Optional<Spot> foundAt) {
        public Result {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(found, "found");
            Objects.requireNonNull(foundAt, "foundAt");
        }
    }

    /** Every node's result, in the road's node order, and how many blue-wool markers are still in their spots. */
    public record Report(List<Result> results, int markers) {
        public Report {
            results = List.copyOf(results);
        }

        public int total() {
            return this.results.size();
        }

        public int right() {
            return count(Status.RIGHT);
        }

        public int wrong() {
            return (int) this.results.stream().filter(result -> result.status().wrong()).count();
        }

        public int toPlace() {
            return count(Status.NOT_PLACED);
        }

        private int count(Status status) {
            return (int) this.results.stream().filter(result -> result.status() == status).count();
        }
    }

    private static final int[][] AROUND = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};

    private NodeCheck() {
    }

    /**
     * Checks {@code nodes} (a road's, in {@code LaneNodes.of} order) in {@code world}. {@code marker} is the marker's
     * state for a road placed with markers, or empty for one placed without.
     */
    public static Report check(List<LaneNode> nodes, Optional<String> marker, BlockReader world) {
        List<Result> results = new ArrayList<>(nodes.size());
        Map<Integer, Integer> numbers = new HashMap<>();
        int markers = 0;
        for (LaneNode node : nodes) {
            int number = numbers.merge(node.lane(), 1, Integer::sum);
            Optional<String> below = world.state(node.x(), node.y(), node.z());
            if (marker.isPresent() && below.equals(marker)) {
                markers++;
            }
            results.add(check(node, number, marker, below, world));
        }
        return new Report(results, markers);
    }

    private static Result check(LaneNode node, int number, Optional<String> marker, Optional<String> below, BlockReader world) {
        int x = node.x();
        int y = node.nodeY();
        int z = node.z();
        Optional<String> at = world.state(x, y, z);
        if (at.isEmpty()) {
            return result(node, number, Status.NOT_LOADED);
        }
        if (MtrNodes.isNode(at.get())) {
            OptionalInt facing = MtrNodes.facing(at.get());
            if (facing.isPresent() && facing.getAsInt() == node.mtrFacing()) {
                return result(node, number, Status.RIGHT);
            }
            return new Result(node, number, Status.WRONG_HEADING, facing, Optional.empty());
        }
        if (below.filter(MtrNodes::isNode).isPresent()) {
            return new Result(node, number, Status.TOO_LOW, OptionalInt.empty(), Optional.of(new Spot(x, node.y(), z)));
        }
        if (world.state(x, y + 1, z).filter(MtrNodes::isNode).isPresent()) {
            return new Result(node, number, Status.TOO_HIGH, OptionalInt.empty(), Optional.of(new Spot(x, y + 1, z)));
        }
        for (int[] step : AROUND) {
            if (world.state(x + step[0], y, z + step[1]).filter(MtrNodes::isNode).isPresent()) {
                return new Result(node, number, Status.ONE_OFF, OptionalInt.empty(), Optional.of(new Spot(x + step[0], y, z + step[1])));
            }
        }
        boolean waiting = marker.isPresent() ? below.equals(marker) : isAir(at.get());
        return result(node, number, waiting ? Status.NOT_PLACED : Status.MISSING);
    }

    private static Result result(LaneNode node, int number, Status status) {
        return new Result(node, number, status, OptionalInt.empty(), Optional.empty());
    }

    private static boolean isAir(String state) {
        return state.equals("minecraft:air") || state.equals("minecraft:cave_air") || state.equals("minecraft:void_air");
    }
}
