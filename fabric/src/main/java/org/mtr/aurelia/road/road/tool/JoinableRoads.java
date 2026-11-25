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
 *   <li>G-11: a standing junction's free arms are offered too, as ports {@link RoadJoins#ARM_PORTS} + arm.</li>
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
    /** G-11: the standing junctions, by operation id (newest first). */
    private final Supplier<Map<UUID, RoadPlanParams>> junctions;
    /** R-10+: the road being edited, which is among the roads but can't be joined (empty when drawing a new road). */
    private final Optional<UUID> editing;
    /** The roads {@link #graph} was last built from (the node aids keep each road's object while it stands). */
    private List<PlacedRoads.Road> graphOf = List.of();
    /** The junctions {@link #graph} was last built with. */
    private Map<UUID, RoadPlanParams> graphJunctions = Map.of();
    /** The road graph of {@link #graphOf}, built when the roads listed change, not on every aim (council r4). */
    private RoadGraph graph = RoadGraph.EMPTY;

    /** The roads {@code roads} lists, newest first, asked each time. */
    public JoinableRoads(Supplier<List<PlacedRoads.Road>> roads) {
        this(roads, Map::of);
    }

    /** The roads {@code roads} lists, newest first, and (G-11) the junctions {@code junctions} lists, asked each time. */
    public JoinableRoads(Supplier<List<PlacedRoads.Road>> roads, Supplier<Map<UUID, RoadPlanParams>> junctions) {
        this(roads, junctions, Optional.empty());
    }

    private JoinableRoads(Supplier<List<PlacedRoads.Road>> roads, Supplier<Map<UUID, RoadPlanParams>> junctions, Optional<UUID> editing) {
        this.roads = Objects.requireNonNull(roads, "roads");
        this.junctions = Objects.requireNonNull(junctions, "junctions");
        this.editing = Objects.requireNonNull(editing, "editing");
    }

    /**
     * For editing the road {@code road} (R-10, R-10+): {@code roads} lists it with the others, newest first. Its ends may
     * join the others' free ends, never its own; the others' ends drawn onto it are kept ({@link #incoming}).
     */
    public static JoinableRoads editing(UUID road, Supplier<List<PlacedRoads.Road>> roads) {
        return editing(road, roads, Map::of);
    }

    /** As {@link #editing(UUID, Supplier)}, with (G-11) the standing junctions. */
    public static JoinableRoads editing(UUID road, Supplier<List<PlacedRoads.Road>> roads, Supplier<Map<UUID, RoadPlanParams>> junctions) {
        return new JoinableRoads(roads, junctions, Optional.of(road));
    }

    /** The road being edited, if this is an edit (R-10+). */
    public Optional<UUID> editing() {
        return this.editing;
    }

    /** Every road listed, the edited one included, then (G-11) the junctions, by operation id (newest first). */
    private Map<UUID, RoadPlanParams> all() {
        Map<UUID, RoadPlanParams> all = new LinkedHashMap<>();
        for (PlacedRoads.Road road : this.roads.get()) {
            all.putIfAbsent(road.operationId(), road.params());
        }
        this.junctions.get().forEach(all::putIfAbsent);
        return all;
    }

    /** The road graph of the roads and junctions listed now (the junction tool snaps onto it). */
    public RoadGraph graph() {
        List<PlacedRoads.Road> listed = this.roads.get();
        Map<UUID, PlacedRoads.Road> roads = new LinkedHashMap<>();
        for (PlacedRoads.Road road : listed) {
            roads.putIfAbsent(road.operationId(), road);
        }
        return graph(listed, roads);
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
        if (end.snapRoadId().isEmpty() || end.snapPortIndex().isEmpty()) {
            return Optional.empty();
        }
        UUID other = end.snapRoadId().get();
        if (end.snapPortIndex().getAsInt() >= RoadJoins.ARM_PORTS) { // G-11: drawn onto a junction's arm
            int arm = end.snapPortIndex().getAsInt() - RoadJoins.ARM_PORTS;
            List<RoadJoins.Port> arms = Optional.ofNullable(this.junctions.get().get(other)).map(RoadJoins::armPorts).orElse(List.of());
            return arm < arms.size() ? Optional.of(new Join(other, arms.get(arm))) : Optional.empty();
        }
        if (end.snapPortIndex().getAsInt() > 1) {
            return Optional.empty();
        }
        return this.roads.get().stream().filter(r -> r.operationId().equals(other) && !this.editing.equals(Optional.of(other))).findFirst()
                .filter(r -> r.ports().size() == 2).map(r -> new Join(other, r.ports().get(end.snapPortIndex().getAsInt())));
    }

    /**
     * R-10+: whether end {@code index} of the edited road {@code road} stays where it is while the road is edited: it was
     * drawn onto a standing road. An end another road was drawn onto can be moved (owner, 2026-11-24): that road's end
     * follows it, in the same operation ({@code RoadJoins.followers}). G-11: an end a junction's arm was drawn onto stays
     * too, as the junction doesn't follow.
     */
    public boolean pinned(RoadPlanParams road, int index) {
        return this.editing.isPresent() && (joinOf(road, index).isPresent() || graph().atJunction(this.editing.get(), index));
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
        Map<UUID, RoadPlanParams> junctions = this.junctions.get();
        for (RoadGraph.Port arm : graph.armsNear(x, z, RADIUS)) { // G-11: a junction's free arm
            double distance = StrictMath.hypot(arm.x() - x, arm.z() - z);
            if (distance >= bestDistance || StrictMath.abs(arm.y() - y) > HEIGHT || graph.armTaken(arm.road(), arm.index())
                    || !junctions.containsKey(arm.road())) {
                continue;
            }
            List<RoadJoins.Port> arms = RoadJoins.armPorts(junctions.get(arm.road()));
            if (arm.index() < arms.size()) {
                best = new Join(arm.road(), arms.get(arm.index()));
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The road graph of the roads {@code listed} ({@code roads} by id), the last one built while they are the same roads. */
    private RoadGraph graph(List<PlacedRoads.Road> listed, Map<UUID, PlacedRoads.Road> roads) {
        Map<UUID, RoadPlanParams> junctions = this.junctions.get();
        if (!listed.equals(this.graphOf) || !junctions.equals(this.graphJunctions)) { // a road's object is its identity: placed, undone or rebuilt, it is another
            Map<UUID, RoadPlanParams> standing = new LinkedHashMap<>();
            for (Map.Entry<UUID, PlacedRoads.Road> road : roads.entrySet()) {
                standing.put(road.getKey(), road.getValue().params());
            }
            junctions.forEach(standing::putIfAbsent);
            this.graph = RoadGraph.of(standing);
            this.graphOf = List.copyOf(listed);
            this.graphJunctions = Map.copyOf(junctions);
        }
        return this.graph;
    }

    /** The layout (profile id) of the road with operation {@code road}, if it can be joined. */
    public Optional<String> profile(UUID road) {
        return this.roads.get().stream().filter(r -> r.operationId().equals(road)).findFirst().map(r -> r.params().profileId());
    }

    /** G-11: the layout of arm port {@code port} of the junction {@code junction}, if it stands. */
    public Optional<String> armProfile(UUID junction, int port) {
        List<RoadJoins.Port> arms = Optional.ofNullable(this.junctions.get().get(junction)).map(RoadJoins::armPorts).orElse(List.of());
        int arm = port - RoadJoins.ARM_PORTS;
        return arm >= 0 && arm < arms.size() ? Optional.of(arms.get(arm).profileId()) : Optional.empty();
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
