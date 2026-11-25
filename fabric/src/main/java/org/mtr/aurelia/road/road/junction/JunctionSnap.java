/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.junction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.graph.RoadGraph;

/**
 * Snaps a junction template onto the road graph (G-10, G-11): one of its arms takes a free road end near where the player
 * points, so the arm's lane nodes are that road's own end nodes, and any other arm that lands on a free road end facing
 * it takes that one too. Refused rather than placed crooked: a road end off the four cardinal headings, a layout whose
 * lanes differ, or a road that keeps to the other side.
 */
public final class JunctionSnap {
    /** No free road end within {@link #RADIUS} blocks of the block pointed at, at its height. */
    public static final String NO_ROAD_END = "NO_ROAD_END";
    /** How near a road end must be to snap to, across, in blocks: as near as the road tools join (R-5). */
    public static final double RADIUS = 4;

    /** The outcome of a snap. */
    public sealed interface Result permits Placed, Refused {
    }

    /** The junction placed, and the road request that records it with its joins. */
    public record Placed(PlacedJunction junction, RoadPlanParams params) implements Result {
    }

    /** Why it wasn't placed: {@link #NO_ROAD_END}, or one of {@link RoadJoins}' codes. */
    public record Refused(String code) implements Result {
    }

    private JunctionSnap() {
    }

    /**
     * {@code template} with its arm {@code arm} on the nearest free road end of {@code graph} within {@link #RADIUS} of
     * block {@code (x, y, z)} (the first given wins a tie, as {@link RoadGraph#portsNear} orders them), facing back along
     * that road.
     */
    public static Result snap(JunctionTemplate template, RoadGraph graph, int x, int y, int z, int arm) {
        RoadGraph.Port target = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (RoadGraph.Port port : graph.portsNear(x, z, RADIUS)) {
            double distance = StrictMath.hypot(port.x() - x, port.z() - z);
            if (port.y() == y && !graph.taken(port.road(), port.index()) && distance < nearest) {
                target = port;
                nearest = distance;
            }
        }
        if (target == null) {
            return new Refused(NO_ROAD_END);
        }
        Optional<String> refusal = refusal(template, graph, target, template.arms().get(arm).profileId());
        if (refusal.isPresent()) {
            return new Refused(refusal.get());
        }
        JunctionTemplate.Arm local = template.arms().get(arm);
        Heading facing = target.outgoing().opposite();
        int quarterTurns = StrictMath.floorMod(local.outgoing().stepsTo(facing), 16) / 4;
        BlockStep offset = new BlockStep(local.x(), local.z());
        for (int i = 0; i < quarterTurns; i++) {
            offset = offset.rotateQuarterClockwise();
        }
        PlacedJunction placed = new PlacedJunction(template, target.x() - offset.dx(), y, target.z() - offset.dz(), quarterTurns);
        List<Optional<RoadGraph.Port>> joined = new ArrayList<>();
        for (int i = 0; i < template.arms().size(); i++) {
            joined.add(i == arm ? Optional.of(target) : lined(placed, i, graph));
        }
        return new Placed(placed, placed.params(joined));
    }

    /**
     * Why the junction {@code placed} can't take the road ends its request's {@code ends} name (G-11), if it can't, among
     * the standing roads {@code roads}: each must be a standing road's end ({@code RoadJoins.ROAD_END_GONE}), at its arm's
     * port and facing into it ({@code PORT_MISMATCH}), not joined already (a third piece there: {@code NOT_AVAILABLE_YET}),
     * and one {@link #snap} would take: a cardinal end, the arm's lanes, the junction's side.
     */
    public static Optional<String> joinRefusal(PlacedJunction placed, List<RoadPlanParams.End> ends, Map<UUID, RoadPlanParams> roads) {
        RoadGraph graph = RoadGraph.of(roads);
        for (int i = 0; i < ends.size(); i++) {
            RoadPlanParams.End end = ends.get(i);
            if (end.snapRoadId().isEmpty()) {
                if (end.snapPortIndex().isPresent()) {
                    return Optional.of(RoadJoins.PORT_MISMATCH);
                }
                continue;
            }
            UUID road = end.snapRoadId().get();
            if (end.snapPortIndex().isEmpty() || end.snapPortIndex().getAsInt() > 1) {
                return Optional.of(RoadJoins.PORT_MISMATCH);
            }
            List<RoadGraph.Port> ports = graph.ports(road);
            if (ports.isEmpty()) {
                return Optional.of(RoadJoins.ROAD_END_GONE);
            }
            RoadGraph.Port port = ports.get(end.snapPortIndex().getAsInt());
            if (port.x() != end.x() || port.y() != end.y() || port.z() != end.z() || port.outgoing() != end.heading().opposite()) {
                return Optional.of(RoadJoins.PORT_MISMATCH);
            }
            if (graph.taken(road, port.index())) {
                return Optional.of(Protocol.NOT_AVAILABLE_YET);
            }
            Optional<String> refusal = refusal(placed.template(), graph, port, placed.template().arms().get(i).profileId());
            if (refusal.isPresent()) {
                return refusal;
            }
        }
        return Optional.empty();
    }

    /** Why arm layout {@code profileId} can't take the road end {@code port}, or empty. */
    private static Optional<String> refusal(JunctionTemplate template, RoadGraph graph, RoadGraph.Port port, String profileId) {
        if (!port.outgoing().isCardinal()) {
            return Optional.of(RoadJoins.PORT_MISMATCH); // arms turn in quarter turns only
        }
        if (!RoadJoins.sameLanes(RoadProfiles.byId(profileId).orElseThrow(), RoadProfiles.byId(port.profileId()).orElseThrow())) {
            return Optional.of(RoadJoins.PROFILE_MISMATCH);
        }
        if (graph.roads().get(port.road()).drivingSide() != template.side()) {
            return Optional.of(RoadJoins.SIDE_MISMATCH);
        }
        return Optional.empty();
    }

    /** The free road end arm {@code arm} of {@code placed} lands on, facing it, that it can take; or empty. */
    private static Optional<RoadGraph.Port> lined(PlacedJunction placed, int arm, RoadGraph graph) {
        RoadPlanParams.End at = placed.arm(arm);
        for (RoadGraph.Port port : graph.portsNear(at.x(), at.z(), 0)) {
            if (port.y() == at.y() && port.outgoing() == at.heading().opposite() && !graph.taken(port.road(), port.index())
                    && refusal(placed.template(), graph, port, placed.template().arms().get(arm).profileId()).isEmpty()) {
                return Optional.of(port);
            }
        }
        return Optional.empty();
    }
}
