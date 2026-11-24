/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Supplier;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.nodes.PlacedRoads;
import org.mtr.aurelia.road.tool.Target;

/**
 * The roads the road tools may join (task R-5; owner, 2026-10-09): the node aids' roads, yours you can still undo or
 * redo and every road placed this visit ({@link PlacedRoads#roads()}). Client thread.
 * <ul>
 *   <li><b>Joining is automatic:</b> an end the player sets, or a handle they move, within {@value #RADIUS} blocks of the
 *       middle of one of these roads' ends ({@link RoadJoins.Port}), at its height ±{@value #HEIGHT}, joins it. The
 *       nearest end wins; on a tie, the newest road's.</li>
 *   <li>An end another road already joins isn't offered: a road there would make a junction (Slice D).</li>
 * </ul>
 */
public final class JoinableRoads {
    /** How near the middle of a road's end an aimed block joins it, in blocks (owner, 2026-10-09). */
    public static final double RADIUS = 4;
    /** How far above or below a road's end an aimed block may be and still join it, in blocks. */
    public static final int HEIGHT = 2;

    /** No roads to join (tests, and tools with none). */
    public static final JoinableRoads NONE = new JoinableRoads(List::of);

    /** A road end an end of the new road joins: the road's operation id and its end. */
    public record Join(UUID road, RoadJoins.Port port) {
        public Join {
            Objects.requireNonNull(road, "road");
            Objects.requireNonNull(port, "port");
        }
    }

    private final Supplier<List<PlacedRoads.Road>> roads;
    /** R-10+: the road being edited, which is among the roads but can't be joined (empty when drawing a new road). */
    private final Optional<UUID> editing;
    /** The roads {@link #graph} was last built from (the node aids keep each road's object while it stands). */
    private List<PlacedRoads.Road> graphOf = List.of();
    /** The road graph of {@link #graphOf}, built when the roads listed change, not on every aim (council r4). */
    private RoadGraph graph = RoadGraph.EMPTY;

    /** The roads {@code roads} lists, newest first, asked each time. */
    public JoinableRoads(Supplier<List<PlacedRoads.Road>> roads) {
        this(roads, Optional.empty());
    }

    private JoinableRoads(Supplier<List<PlacedRoads.Road>> roads, Optional<UUID> editing) {
        this.roads = Objects.requireNonNull(roads, "roads");
        this.editing = Objects.requireNonNull(editing, "editing");
    }

    /**
     * For editing the road {@code road} (R-10, R-10+): {@code roads} lists it with the others, newest first. Its ends may
     * join the others' free ends, never its own; the others' ends drawn onto it are kept ({@link #incoming}).
     */
    public static JoinableRoads editing(UUID road, Supplier<List<PlacedRoads.Road>> roads) {
        return new JoinableRoads(roads, Optional.of(road));
    }

    /** The road being edited, if this is an edit (R-10+). */
    public Optional<UUID> editing() {
        return this.editing;
    }

    /** Every road listed, the edited one included, by operation id (newest first). */
    private Map<UUID, RoadPlanParams> all() {
        Map<UUID, RoadPlanParams> all = new LinkedHashMap<>();
        for (PlacedRoads.Road road : this.roads.get()) {
            all.putIfAbsent(road.operationId(), road.params());
        }
        return all;
    }

    /** The roads that can be joined now, by operation id: for the planner, which checks a join against them. */
    public Map<UUID, RoadPlanParams> placed() {
        Map<UUID, RoadPlanParams> placed = all();
        this.editing.ifPresent(placed::remove);
        return placed;
    }

    /** R-10+: the standing roads with the edited road among them, as the server plans an edit; {@link #placed} otherwise. */
    public Map<UUID, RoadPlanParams> standing() {
        return all();
    }

    /** R-10+: the other roads' ends drawn onto the edited road, each with its port; none when drawing a new road. */
    public List<RoadJoins.Incoming> incoming() {
        return this.editing.map(id -> RoadJoins.incoming(id, all())).orElse(List.of());
    }

    /**
     * R-10+: the join end {@code index} of the edited road {@code road} has, when it was drawn onto a road still standing,
     * to show it joined while it is edited.
     */
    public Optional<Join> joinOf(RoadPlanParams road, int index) {
        RoadPlanParams.End end = road.ends().get(index);
        if (end.snapRoadId().isEmpty() || end.snapPortIndex().isEmpty() || end.snapPortIndex().getAsInt() > 1) {
            return Optional.empty();
        }
        UUID other = end.snapRoadId().get();
        return this.roads.get().stream().filter(r -> r.operationId().equals(other) && !this.editing.equals(Optional.of(other))).findFirst()
                .filter(r -> r.ports().size() == 2).map(r -> new Join(other, r.ports().get(end.snapPortIndex().getAsInt())));
    }

    /**
     * R-10+: whether end {@code index} of the edited road {@code road} is joined, so it stays where it is while the road is
     * edited: it was drawn onto a standing road, or another road was drawn onto it.
     */
    public boolean pinned(RoadPlanParams road, int index) {
        return this.editing.isPresent() && (joinOf(road, index).isPresent() || incoming().stream().anyMatch(in -> in.index() == index));
    }

    /**
     * The free road end the block {@code (x, y, z)} joins, if any: within {@link #RADIUS} of its middle across, and
     * within {@link #HEIGHT} of its height.
     */
    public Optional<Join> near(int x, int y, int z) {
        List<PlacedRoads.Road> listed = this.roads.get();
        Map<UUID, PlacedRoads.Road> roads = new LinkedHashMap<>();
        for (PlacedRoads.Road road : listed) {
            roads.putIfAbsent(road.operationId(), road);
        }
        RoadGraph graph = graph(listed, roads); // G-10: its chunk index finds the ends nearby
        Join best = null;
        double bestDistance = RADIUS;
        for (RoadGraph.Port near : graph.portsNear(x, z, RADIUS)) { // newest road first, so the newest wins a tie
            if (this.editing.equals(Optional.of(near.road()))) {
                continue; // the road being edited can't join itself
            }
            double dx = near.x() - x;
            double dz = near.z() - z;
            double distance = StrictMath.sqrt(dx * dx + dz * dz);
            if (distance > bestDistance || (best != null && distance == bestDistance) || StrictMath.abs(near.y() - y) > HEIGHT
                    || graph.taken(near.road(), near.index())) {
                continue;
            }
            List<RoadJoins.Port> ports = roads.get(near.road()).ports();
            if (ports.size() == 2) {
                best = new Join(near.road(), ports.get(near.index()));
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The road graph of the roads {@code listed} ({@code roads} by id), the last one built while they are the same roads. */
    private RoadGraph graph(List<PlacedRoads.Road> listed, Map<UUID, PlacedRoads.Road> roads) {
        if (!listed.equals(this.graphOf)) { // a road's object is its identity: placed, undone or rebuilt, it is another
            Map<UUID, RoadPlanParams> standing = new LinkedHashMap<>();
            for (Map.Entry<UUID, PlacedRoads.Road> road : roads.entrySet()) {
                standing.put(road.getKey(), road.getValue().params());
            }
            this.graph = RoadGraph.of(standing);
            this.graphOf = List.copyOf(listed);
        }
        return this.graph;
    }

    /** The layout (profile id) of the road with operation {@code road}, if it can be joined. */
    public Optional<String> profile(UUID road) {
        return this.roads.get().stream().filter(r -> r.operationId().equals(road)).findFirst().map(r -> r.params().profileId());
    }

    /** As {@link #near(int, int, int)}, for the block aimed at. */
    public Optional<Join> near(Target target) {
        return near(target.x(), target.y(), target.z());
    }

    /**
     * A road the tool built, finished for the planner: stored the way round that lets its dashes carry on exactly
     * ({@link RoadJoins#orient}), with the dash pattern its joins call for ({@link RoadJoins#dashes}).
     */
    public RoadPlanParams finish(RoadPlanParams params) {
        List<RoadJoins.Incoming> incoming = incoming();
        // R-10+: a road others are joined to keeps its way round, as their joins name its ends.
        RoadPlanParams oriented = incoming.isEmpty() ? RoadJoins.orient(params) : params;
        Map<UUID, List<RoadJoins.Port>> ports = new LinkedHashMap<>();
        if (oriented.ends().stream().anyMatch(end -> end.snapRoadId().isPresent())) {
            for (PlacedRoads.Road road : this.roads.get()) {
                if (!this.editing.equals(Optional.of(road.operationId()))) {
                    ports.putIfAbsent(road.operationId(), road.ports());
                }
            }
        }
        return oriented.withDashes(RoadJoins.editDashes(oriented, id -> Optional.ofNullable(ports.get(id)).filter(list -> list.size() == 2),
                incoming));
    }

    /** {@code end} joined to {@code join}: at its end's block and height, with the join named. */
    static RoadPlanParams.End joined(RoadPlanParams.End end, Join join) {
        return new RoadPlanParams.End(join.port().x(), join.port().y(), join.port().z(), end.heading(), Optional.of(join.road()),
                OptionalInt.of(join.port().index()));
    }
}
