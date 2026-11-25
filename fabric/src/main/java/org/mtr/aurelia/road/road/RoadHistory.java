/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
     *
     * <p>From 2026-11-24 an edit that moves a joined end rebuilds the roads joined there too ({@link RoadJoins#followers}),
     * so one operation can be in several roads' chains: {@code shared} are those of its chain, whose undo images also hold
     * other roads' blocks. {@code builds} are its parameters after each operation of the chain, in the same order, and
     * {@code priors} the other roads each shared operation built, as they were just before it: where this road took over
     * one's blocks, the ground is what was there before that road.
     */
    public record Road(UUID id, RoadPlanParams params, List<UUID> chain, boolean complete, List<RoadPlanParams> builds, Set<UUID> shared,
            Map<UUID, List<Prior>> priors) {
        public Road {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(params, "params");
            chain = List.copyOf(chain);
            if (chain.isEmpty()) {
                throw new IllegalArgumentException("A standing road has an operation that built it");
            }
            builds = List.copyOf(builds);
            if (builds.size() != chain.size()) {
                throw new IllegalArgumentException("One build per operation: " + chain.size() + ", not " + builds.size());
            }
            shared = Set.copyOf(shared);
            priors = Map.copyOf(priors);
        }

        /** The operation that built it last. */
        public UUID latest() {
            return this.chain.get(this.chain.size() - 1);
        }
    }

    /** Another road a shared operation built, as it was just before it: its id, its chain then, and its parameters then. */
    public record Prior(UUID road, List<UUID> chain, RoadPlanParams params) {
        public Prior {
            Objects.requireNonNull(road, "road");
            chain = List.copyOf(chain);
            Objects.requireNonNull(params, "params");
        }
    }

    /** A road as {@link #resolve} works it out: null parameters while it is deleted. */
    private static final class Building {
        private final List<UUID> chain = new ArrayList<>();
        private final List<RoadPlanParams> builds = new ArrayList<>();
        private final Set<UUID> shared = new HashSet<>();
        private final Map<UUID, List<Prior>> priors = new HashMap<>();
        private RoadPlanParams params;
        private boolean complete;

        void placed(UUID op, RoadPlanParams params) {
            this.chain.clear();
            this.builds.clear();
            this.shared.clear();
            this.priors.clear();
            this.chain.add(op);
            this.builds.add(params);
            this.params = params;
            this.complete = true;
        }

        void rebuilt(UUID op, RoadPlanParams params, boolean shared) {
            this.chain.add(op);
            this.builds.add(params);
            if (shared) {
                this.shared.add(op);
            }
            this.params = params;
        }

        void deleted() {
            this.chain.clear();
            this.builds.clear();
            this.shared.clear();
            this.priors.clear();
            this.params = null;
            this.complete = false;
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

    /**
     * The roads standing after {@code ops}, given in the order they were made, by road id, in the order first placed. A
     * rebuild that moves a joined end of its road rebuilds the roads joined there too, as the planner planned it
     * ({@link RoadJoins#followers}, from the roads standing just before it).
     */
    public static Map<UUID, Road> resolve(List<Op> ops) {
        Map<UUID, Building> byRoad = new LinkedHashMap<>();
        for (Op op : ops) {
            Optional<UUID> target = op.applied() ? roadOf(op.id(), op.params()) : Optional.empty();
            if (target.isEmpty()) {
                continue;
            }
            Building road = byRoad.computeIfAbsent(target.get(), r -> new Building());
            switch (op.params().tool()) {
                case STRAIGHT, CURVE -> road.placed(op.id(), op.params());
                case REBUILD -> {
                    Map<UUID, RoadPlanParams> followers = Map.of();
                    if (road.params != null) {
                        Map<UUID, RoadPlanParams> standing = new LinkedHashMap<>();
                        byRoad.forEach((id, building) -> {
                            if (building.params != null) {
                                standing.put(id, building.params);
                            }
                        });
                        followers = RoadJoins.followers(target.get(), road.params.road(), op.params().road(), standing);
                    }
                    if (!followers.isEmpty()) {
                        List<Prior> priors = new ArrayList<>();
                        priors.add(new Prior(target.get(), road.chain, road.params));
                        followers.keySet().forEach(id -> priors.add(new Prior(id, byRoad.get(id).chain, byRoad.get(id).params)));
                        for (Prior prior : priors) {
                            byRoad.get(prior.road()).priors.put(op.id(), priors.stream().filter(other -> other != prior).toList());
                        }
                    }
                    followers.forEach((id, params) -> byRoad.get(id).rebuilt(op.id(), params, true));
                    road.rebuilt(op.id(), op.params(), !followers.isEmpty());
                }
                default -> road.deleted(); // a delete
            }
        }
        Map<UUID, Road> roads = new LinkedHashMap<>();
        byRoad.forEach((id, road) -> {
            if (road.params != null) {
                roads.put(id, new Road(id, road.params, road.chain, road.complete, road.builds, road.shared, road.priors));
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
