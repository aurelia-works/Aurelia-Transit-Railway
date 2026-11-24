/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Where each road stands (task R-7; owner, 2026-10-09), worked out from the operations that placed, rebuilt and deleted
 * it, before G-10's road graph:
 * <ul>
 *   <li><b>A road keeps one id for life:</b> the id of the operation that first placed it. For every road placed before
 *       R-7 that is its operation id, so R-5's {@code snapRoadId} names the same road as before.</li>
 *   <li>A rebuild or a delete names the road it edits ({@code targetRoadId}).</li>
 *   <li><b>A road stands</b> while its latest applied operation places or rebuilds it; its parameters are that
 *       operation's. It is gone when that is a delete, or when none is applied.</li>
 * </ul>
 * Nothing is stored for this: undo, redo and the repair after a crash keep it right by keeping each operation's status.
 * The server works it out from its records; the client from {@code YourRoads} and this visit's edits
 * ({@code PlacedRoads}).
 */
public final class RoadHistory {
    /** One roads operation: its id, its parameters, and whether it is applied now. */
    public record Op(UUID id, RoadPlanParams params, boolean applied) {
        public Op {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(params, "params");
        }
    }

    /**
     * A road as it stands: its id, its parameters now (its latest operation's) and its applied operations that built it,
     * oldest first: placing it, then each rebuild. {@code complete} when that list starts with the operation that placed
     * it (its record may have gone after a restart, when the cap dropped it from the history).
     */
    public record Road(UUID id, RoadPlanParams params, List<UUID> chain, boolean complete) {
        public Road {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(params, "params");
            chain = List.copyOf(chain);
            if (chain.isEmpty()) {
                throw new IllegalArgumentException("A standing road has an operation that built it");
            }
        }

        /** The operation that built it last. */
        public UUID latest() {
            return this.chain.get(this.chain.size() - 1);
        }
    }

    private RoadHistory() {
    }

    /** The road {@code params}' operation {@code id} is about: its own id when it places a road, its target for a rebuild or delete. */
    public static Optional<UUID> roadOf(UUID id, RoadPlanParams params) {
        return switch (params.tool()) {
            case STRAIGHT, CURVE -> Optional.of(id);
            case REBUILD, DELETE -> params.targetRoadId();
            default -> Optional.empty();
        };
    }

    /** The roads standing after {@code ops}, given in the order they were made, by road id, in the order first placed. */
    public static Map<UUID, Road> resolve(List<Op> ops) {
        Map<UUID, List<Op>> byRoad = new LinkedHashMap<>();
        for (Op op : ops) {
            if (op.applied()) {
                roadOf(op.id(), op.params()).ifPresent(road -> byRoad.computeIfAbsent(road, r -> new ArrayList<>()).add(op));
            }
        }
        Map<UUID, Road> roads = new LinkedHashMap<>();
        byRoad.forEach((id, list) -> {
            List<UUID> chain = new ArrayList<>();
            RoadPlanParams params = null;
            boolean complete = false;
            for (Op op : list) {
                switch (op.params().tool()) {
                    case STRAIGHT, CURVE -> {
                        chain.clear();
                        chain.add(op.id());
                        params = op.params();
                        complete = true;
                    }
                    case REBUILD -> {
                        chain.add(op.id());
                        params = op.params();
                    }
                    default -> { // a delete
                        chain.clear();
                        params = null;
                        complete = false;
                    }
                }
            }
            if (params != null) {
                roads.put(id, new Road(id, params, chain, complete));
            }
        });
        return roads;
    }

    /** Each standing road's parameters by road id, in the order first placed (R-5's placed roads a new road may join). */
    public static Map<UUID, RoadPlanParams> params(Map<UUID, Road> roads) {
        Map<UUID, RoadPlanParams> params = new LinkedHashMap<>();
        roads.forEach((id, road) -> params.put(id, road.params()));
        return params;
    }
}
