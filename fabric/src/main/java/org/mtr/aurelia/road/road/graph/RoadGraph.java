/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.RoadPlanParams;

/**
 * The road graph (task G-10; roads-v1 §10.1 and §11.1, world-data-format §4), built from the roads standing now: each
 * road is an edge between its two ends, each end a <b>port</b>, and a <b>join</b> links one road's end to the end of
 * another it was drawn onto ({@code snapRoadId}, {@code snapPortIndex}; R-5). A chunk index finds the ports near a block.
 *
 * <p><b>Rebuilt, not saved:</b> the graph is worked out from the roads standing ({@code RoadHistory}: the server from its
 * operation records, the client from the node aids' roads), so worlds saved before it load unchanged and
 * {@code formatVersion} stays 1. A join to a road that isn't standing (deleted, undone) is no join.
 *
 * <p>Ports come from the ends' blocks and headings alone, with nothing solved, so the graph is cheap to build for every
 * request. Not yet here: the continuation classifier's other statuses, and ADR-0045's structure types (Phase 3).
 *
 * <p><b>Junctions</b> (G-11, Slice D): a junction's parameters ({@link RoadPlanParams.Tool#JUNCTION}) give it one
 * {@link #arms arm} per end, each a port a road joins; an arm drawn onto a road end ({@link #armJoins}) takes that end
 * ({@link #taken}). Arms have their own accessors: {@link #ports}, {@link #portsNear}, {@link #joins} and
 * {@link #neighbours} stay roads only. With no junctions given, the graph is as it was.
 */
public final class RoadGraph {
    /** No roads. */
    public static final RoadGraph EMPTY = of(Map.of());

    /**
     * One end of a road: {@code index} 0 for its start and 1 for its end, its centre-line block, the heading a road leaving
     * it runs (the end heading at a road's end, the start heading turned round at its start), and the road's layout.
     */
    public record Port(UUID road, int index, int x, int y, int z, Heading outgoing, String profileId) {
        public Port {
            Objects.requireNonNull(road, "road");
            Objects.requireNonNull(outgoing, "outgoing");
            Objects.requireNonNull(profileId, "profileId");
        }
    }

    /** End {@code index} of {@code road} was drawn onto end {@code joinsIndex} of the standing road {@code joins}. */
    public record Join(UUID road, int index, UUID joins, int joinsIndex) {
        public Join {
            Objects.requireNonNull(road, "road");
            Objects.requireNonNull(joins, "joins");
        }
    }

    private final Map<UUID, RoadPlanParams> roads;
    private final Map<UUID, List<Port>> ports = new LinkedHashMap<>();
    private final List<Join> joins = new ArrayList<>();
    /** Ports by chunk ({@link #chunkKey}), in the order the roads were given. */
    private final Map<Long, List<Port>> chunks = new HashMap<>();
    /** Each road's place in the order given. */
    private final Map<UUID, Integer> rank = new HashMap<>();
    /** Each junction's arms, in the order given. */
    private final Map<UUID, List<Port>> arms = new LinkedHashMap<>();
    /** Junction arms drawn onto road ends: {@code road} is the junction, {@code index} its arm. */
    private final List<Join> armJoins = new ArrayList<>();

    private RoadGraph(Map<UUID, RoadPlanParams> roads) {
        this.roads = Collections.unmodifiableMap(new LinkedHashMap<>(roads));
        this.roads.forEach((id, params) -> {
            if (params.tool() == RoadPlanParams.Tool.JUNCTION && params.junction().isPresent()) {
                this.arms.put(id, arms(id, params));
                return;
            }
            if (!params.isRoad() || params.ends().size() != 2) {
                return;
            }
            RoadPlanParams.End start = params.ends().get(0);
            RoadPlanParams.End end = params.ends().get(1);
            List<Port> two = List.of(new Port(id, 0, start.x(), start.y(), start.z(), start.heading().opposite(), params.profileId()),
                    new Port(id, 1, end.x(), end.y(), end.z(), end.heading(), params.profileId()));
            this.ports.put(id, two);
            this.rank.put(id, this.rank.size());
            two.forEach(port -> this.chunks.computeIfAbsent(chunkKey(port.x() >> 4, port.z() >> 4), key -> new ArrayList<>()).add(port));
        });
        this.ports.forEach((id, two) -> {
            List<RoadPlanParams.End> ends = this.roads.get(id).ends();
            for (int i = 0; i < 2; i++) {
                Optional<UUID> other = ends.get(i).snapRoadId();
                OptionalInt otherIndex = ends.get(i).snapPortIndex();
                if (other.isPresent() && !other.get().equals(id) && otherIndex.isPresent() && otherIndex.getAsInt() <= 1
                        && this.ports.containsKey(other.get())) {
                    this.joins.add(new Join(id, i, other.get(), otherIndex.getAsInt()));
                }
            }
        });
        this.arms.forEach((id, ports) -> {
            List<RoadPlanParams.End> ends = this.roads.get(id).ends();
            for (int i = 0; i < ends.size(); i++) {
                Optional<UUID> other = ends.get(i).snapRoadId();
                OptionalInt otherIndex = ends.get(i).snapPortIndex();
                if (other.isPresent() && otherIndex.isPresent() && otherIndex.getAsInt() <= 1 && this.ports.containsKey(other.get())) {
                    this.armJoins.add(new Join(id, i, other.get(), otherIndex.getAsInt()));
                }
            }
        });
    }

    /** A junction's arms: one per end, facing the way a road leaving it runs, each with its own layout (or the junction's). */
    private static List<Port> arms(UUID id, RoadPlanParams params) {
        List<String> profiles = params.junction().orElseThrow().armProfiles();
        List<Port> arms = new ArrayList<>();
        for (int i = 0; i < params.ends().size(); i++) {
            RoadPlanParams.End end = params.ends().get(i);
            arms.add(new Port(id, i, end.x(), end.y(), end.z(), end.heading(), i < profiles.size() ? profiles.get(i) : params.profileId()));
        }
        return List.copyOf(arms);
    }

    /** The graph of the standing roads {@code roads} (their parameters by road id), in the order given. */
    public static RoadGraph of(Map<UUID, RoadPlanParams> roads) {
        return new RoadGraph(roads);
    }

    /** The roads, by road id, in the order given. */
    public Map<UUID, RoadPlanParams> roads() {
        return this.roads;
    }

    /** The two ends of road {@code road}, or none when it isn't here (or isn't a two-ended road). */
    public List<Port> ports(UUID road) {
        return this.ports.getOrDefault(road, List.of());
    }

    /** The arms of junction {@code junction}, or none when it isn't here. */
    public List<Port> arms(UUID junction) {
        return this.arms.getOrDefault(junction, List.of());
    }

    /** The junctions, in the order given. */
    public Set<UUID> junctions() {
        return Collections.unmodifiableSet(this.arms.keySet());
    }

    /** Every junction arm drawn onto a standing road's end: {@code road} is the junction and {@code index} its arm. */
    public List<Join> armJoins() {
        return Collections.unmodifiableList(this.armJoins);
    }

    /** Every join between two standing roads. */
    public List<Join> joins() {
        return Collections.unmodifiableList(this.joins);
    }

    /**
     * Whether end {@code index} of road {@code road} is joined: it was drawn onto a standing road's end, or a standing
     * road's end was drawn onto it, or a junction's arm was (G-11). A second road there would make a junction (Slice D).
     */
    public boolean taken(UUID road, int index) {
        for (Join join : this.joins) {
            if (join.road().equals(road) && join.index() == index || join.joins().equals(road) && join.joinsIndex() == index) {
                return true;
            }
        }
        for (Join join : this.armJoins) {
            if (join.joins().equals(road) && join.joinsIndex() == index) {
                return true;
            }
        }
        return false;
    }

    /** The joins of other roads' ends drawn onto an end of {@code road} (R-10+: the ends an edit must leave in place). */
    public List<Join> incoming(UUID road) {
        List<Join> incoming = new ArrayList<>();
        for (Join join : this.joins) {
            if (join.joins().equals(road)) {
                incoming.add(join);
            }
        }
        return incoming;
    }

    /** The roads joined to {@code road} at either end, either way round, in the order given. */
    public Set<UUID> neighbours(UUID road) {
        Set<UUID> neighbours = new LinkedHashSet<>();
        for (Join join : this.joins) {
            if (join.road().equals(road)) {
                neighbours.add(join.joins());
            } else if (join.joins().equals(road)) {
                neighbours.add(join.road());
            }
        }
        return neighbours;
    }

    /**
     * The ports whose block is within {@code radius} of block {@code (x, z)} across (any height), found through the chunk
     * index, in the order the roads were given and each road's start before its end.
     */
    public List<Port> portsNear(int x, int z, double radius) {
        int reach = (int) StrictMath.ceil(radius);
        List<Port> near = new ArrayList<>();
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++) {
            for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++) {
                for (Port port : this.chunks.getOrDefault(chunkKey(cx, cz), List.of())) {
                    double dx = port.x() - x;
                    double dz = port.z() - z;
                    if (dx * dx + dz * dz <= radius * radius) {
                        near.add(port);
                    }
                }
            }
        }
        near.sort((a, b) -> a.road().equals(b.road()) ? Integer.compare(a.index(), b.index())
                : Integer.compare(this.rank.get(a.road()), this.rank.get(b.road())));
        return near;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }
}
