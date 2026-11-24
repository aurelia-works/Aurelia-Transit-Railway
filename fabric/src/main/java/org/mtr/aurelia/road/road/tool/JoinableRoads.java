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

    /** The roads {@code roads} lists, newest first, asked each time. */
    public JoinableRoads(Supplier<List<PlacedRoads.Road>> roads) {
        this.roads = Objects.requireNonNull(roads, "roads");
    }

    /** The roads that can be joined now, by operation id: for the planner, which checks a join against them. */
    public Map<UUID, RoadPlanParams> placed() {
        Map<UUID, RoadPlanParams> placed = new LinkedHashMap<>();
        for (PlacedRoads.Road road : this.roads.get()) {
            placed.putIfAbsent(road.operationId(), road.params());
        }
        return placed;
    }

    /**
     * The free road end the block {@code (x, y, z)} joins, if any: within {@link #RADIUS} of its middle across, and
     * within {@link #HEIGHT} of its height.
     */
    public Optional<Join> near(int x, int y, int z) {
        List<PlacedRoads.Road> roads = this.roads.get();
        Join best = null;
        double bestDistance = RADIUS;
        Map<UUID, RoadPlanParams> placed = null;
        for (PlacedRoads.Road road : roads) { // newest first, so the newest wins a tie
            for (RoadJoins.Port port : road.ports()) {
                double dx = port.x() - x;
                double dz = port.z() - z;
                double distance = StrictMath.sqrt(dx * dx + dz * dz);
                if (distance > bestDistance || (best != null && distance == bestDistance) || StrictMath.abs(port.y() - y) > HEIGHT) {
                    continue;
                }
                if (placed == null) {
                    placed = placed();
                }
                if (!RoadJoins.taken(road.operationId(), port.index(), placed)) {
                    best = new Join(road.operationId(), port);
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
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
        RoadPlanParams oriented = RoadJoins.orient(params);
        Map<UUID, List<RoadJoins.Port>> ports = new LinkedHashMap<>();
        if (oriented.ends().stream().anyMatch(end -> end.snapRoadId().isPresent())) {
            for (PlacedRoads.Road road : this.roads.get()) {
                ports.putIfAbsent(road.operationId(), road.ports());
            }
        }
        return oriented.withDashes(RoadJoins.dashes(oriented, id -> Optional.ofNullable(ports.get(id)).filter(list -> list.size() == 2)));
    }

    /** {@code end} joined to {@code join}: at its end's block and height, with the join named. */
    static RoadPlanParams.End joined(RoadPlanParams.End end, Join join) {
        return new RoadPlanParams.End(join.port().x(), join.port().y(), join.port().z(), end.heading(), Optional.of(join.road()),
                OptionalInt.of(join.port().index()));
    }
}
