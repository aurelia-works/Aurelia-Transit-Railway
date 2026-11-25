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
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.raster.RoadPiece;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.road.solve.RoadSolution;
import org.mtr.aurelia.road.road.solve.RoadSolver;

/**
 * Roads joined end to end (task R-5; roads-v1 §10). A new road may start or end at an end of a road already placed: the
 * end of the request says which ({@code snapRoadId}, the road's operation id; {@code snapPortIndex}, 0 for its start and
 * 1 for its end), and must take that end's block, height and direction, so the lanes carry straight on through the same
 * lane nodes and the hidden track meets there (owner, 2026-10-09).
 *
 * <p><b>Which roads:</b> the planner is given the placed roads it may join, as parameters by operation id. The client
 * gives the node aids' roads (yours you can still undo or redo, and those placed this visit); the server the records
 * of applied roads in the player's dimension: the roads standing, whose joins make the road graph ({@link RoadGraph}; G-10).
 *
 * <p><b>Layouts</b> (owner, 2026-10-09): a road may join one with another layout when both have the same lanes (offsets
 * and directions): City street, Residential 15 and Residential 17 in any combination. Their lanes and kerbs line up, so
 * the layout simply changes at the joint: the footway steps there, and City street's dashes stop or start. The alley
 * joins only the alley ({@link #PROFILE_MISMATCH}).
 *
 * <p><b>Driving side</b> (P9): a road joins only a road that keeps to the same side ({@link #SIDE_MISMATCH}), except the
 * alley, whose one lane is shared.
 *
 * <p><b>Dashes</b> carry on in step across a joint between two roads of the same layout ({@link RoadPlanParams.Dashes}):
 * see {@link #dashes}.
 */
public final class RoadJoins {
    /** The road an end joins isn't placed now (undone, rolled back, unknown, in another dimension). */
    public static final String ROAD_END_GONE = "ROAD_END_GONE";
    /**
     * An end that says it joins a road end doesn't take that end's block, height or heading, or its dash pattern
     * doesn't carry on that road's (roads-v1 §12, widened from junction arms to road ends in R-5).
     */
    public static final String PORT_MISMATCH = "PORT_MISMATCH";
    /**
     * The roads' layouts don't have the same lanes, so one can't carry on from the other without a taper (roads-v1 §12);
     * args {@code layout} (this road's profile id) and {@code joins} (the other's).
     */
    public static final String PROFILE_MISMATCH = "PROFILE_MISMATCH";
    /**
     * P9: the roads keep to different sides (one placed before the world's driving side changed), so their one-way lanes
     * would meet head-on at the joint. Roads with only shared lanes (the alley) join whatever their side.
     */
    public static final String SIDE_MISMATCH = "SIDE_MISMATCH";

    /**
     * One end of a placed road: {@code index} 0 for its start and 1 for its end, the centre-line node (its block and the
     * surface height), the heading a road leaving it runs ({@code outgoing}: the end heading at a road's end, the start
     * heading turned round at its start; roads-v1 §10.1), the road's layout, and the dash phase there, measured in the
     * road's own direction.
     */
    public record Port(int index, int x, int y, int z, Heading outgoing, String profileId, double phase) {
        public Port {
            if (index < 0) {
                throw new IllegalArgumentException("A road's ends are 0 (its start) and 1 (its end), a junction's arms from 2, not " + index);
            }
            Objects.requireNonNull(outgoing, "outgoing");
            Objects.requireNonNull(profileId, "profileId");
        }

        /** Whether {@code end} is at this port's block. */
        public boolean at(RoadPlanParams.End end) {
            return end.x() == this.x && end.y() == this.y && end.z() == this.z;
        }
    }

    /**
     * G-11: the port index of a junction's first arm: a road end drawn onto arm {@code i} names port {@code ARM_PORTS + i}
     * ({@code snapPortIndex}), so it never reads as a road's start or end (0 and 1), and the wire and the records carry it
     * as they carry any port.
     */
    public static final int ARM_PORTS = 2;

    private RoadJoins() {
    }

    /**
     * A junction's arms as ports (G-11): arm {@code i} is port {@link #ARM_PORTS} + {@code i}, at its end's block, facing
     * the way a road leaving it runs, with the arm's layout; no dash phase (a road drawn from an arm starts its own). None
     * for anything but a junction.
     */
    public static List<Port> armPorts(RoadPlanParams params) {
        if (params.tool() != RoadPlanParams.Tool.JUNCTION || params.junction().isEmpty()) {
            return List.of();
        }
        List<String> profiles = params.junction().get().armProfiles();
        List<Port> arms = new ArrayList<>();
        for (int i = 0; i < params.ends().size(); i++) {
            RoadPlanParams.End end = params.ends().get(i);
            arms.add(new Port(ARM_PORTS + i, end.x(), end.y(), end.z(), end.heading(), i < profiles.size() ? profiles.get(i) : params.profileId(), 0));
        }
        return arms;
    }

    /** The two ends of the straight, curved or (R-7) rebuilt road {@code params} describe, or empty if it doesn't plan. */
    public static Optional<List<Port>> ports(RoadPlanParams params) {
        if (!params.isRoad() || params.ends().size() != 2) {
            return Optional.empty();
        }
        Optional<RoadProfile> profile = RoadProfiles.byId(params.profileId());
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        RoadSolution solution = RoadSolver.solve(profile.get(), params.preset(), params.vertical(), params.ends().get(0), params.ends().get(1));
        if (!(solution instanceof RoadSolution.Solved solved)) {
            return Optional.empty();
        }
        List<RoadPiece> pieces = RoadPlanner.rasterPieces(profile.get(), solved.pieces(), params.dashes());
        double startPhase = pieces.get(0).dashPhase();
        double endPhase = params.dashes().at() == RoadPlanParams.Dashes.Anchor.END ? params.dashes().phase() : pieces.get(pieces.size() - 1).phaseAfter();
        RoadPlanParams.End start = params.ends().get(0);
        RoadPlanParams.End end = params.ends().get(1);
        return Optional.of(List.of(
                new Port(0, start.x(), start.y(), start.z(), start.heading().opposite(), params.profileId(), startPhase),
                new Port(1, end.x(), end.y(), end.z(), end.heading(), params.profileId(), endPhase)));
    }

    /** Whether roads of layouts {@code a} and {@code b} can join: the same lanes, at the same offsets, the same way. */
    public static boolean sameLanes(RoadProfile a, RoadProfile b) {
        return a.lanes().equals(b.lanes());
    }

    /**
     * An end of another road drawn onto end {@code index} of the road being edited (R-10+): that road's id
     * ({@code road}), which of its ends ({@code roadEnd}), and its port there ({@code port}, with its dash phase).
     */
    public record Incoming(int index, UUID road, int roadEnd, Port port) {
        public Incoming {
            Objects.requireNonNull(road, "road");
            Objects.requireNonNull(port, "port");
        }
    }

    /**
     * The ends of the standing roads {@code placed} drawn onto an end of road {@code road} (R-10+; {@link RoadGraph#incoming}),
     * each with its port; one whose road doesn't plan any more is left out.
     */
    public static List<Incoming> incoming(UUID road, Map<UUID, RoadPlanParams> placed) {
        List<Incoming> incoming = new ArrayList<>();
        for (RoadGraph.Join join : RoadGraph.of(placed).incoming(road)) {
            ports(placed.get(join.road())).ifPresent(ports -> incoming.add(new Incoming(join.joinsIndex(), join.road(), join.index(),
                    ports.get(join.index()))));
        }
        return incoming;
    }

    /**
     * The dash pattern a road with {@code params}' ends must have (owner, 2026-10-09: dashes carry on in step), given its
     * joined ends' ports ({@code ports}: a joined road's ports by operation id, empty when it isn't placed):
     * <ol>
     *   <li>its start joins a road's <b>end</b> of the same layout: that end's phase, at its start;</li>
     *   <li>else its end joins a road's <b>start</b> of the same layout: that start's phase, at its end;</li>
     *   <li>else {@link RoadPlanParams.Dashes#FROM_START}: a road starting at a change of layout begins with a whole
     *       dash.</li>
     * </ol>
     */
    public static RoadPlanParams.Dashes dashes(RoadPlanParams params, Function<UUID, Optional<List<Port>>> ports) {
        List<RoadPlanParams.End> ends = params.ends();
        if (ends.size() == 2) {
            Optional<Port> start = joined(ends.get(0), ports);
            if (start.isPresent() && start.get().index() == 1 && start.get().profileId().equals(params.profileId())) {
                return new RoadPlanParams.Dashes(start.get().phase(), RoadPlanParams.Dashes.Anchor.START);
            }
            Optional<Port> end = joined(ends.get(1), ports);
            if (end.isPresent() && end.get().index() == 0 && end.get().profileId().equals(params.profileId())) {
                return new RoadPlanParams.Dashes(end.get().phase(), RoadPlanParams.Dashes.Anchor.END);
            }
        }
        return RoadPlanParams.Dashes.FROM_START;
    }

    /**
     * The dash pattern of an edited road (R-10+) with {@code params}' ends, given its own joins' ports ({@code ports}) and
     * the other roads' ends drawn onto it ({@code incoming}): {@link #dashes} when that keeps every such road's dashes in
     * step, so an edit that doesn't change the road's length leaves its dashes as they were. Otherwise the dashes are tied
     * to the joined end:
     * <ol>
     *   <li>a road of the same layout <b>ends</b> on its start: that road's end phase, at its start;</li>
     *   <li>else a road of the same layout <b>starts</b> on its end: that road's start phase, at its end (the road then
     *       leaves it the joint row, as a road running into another's start does).</li>
     * </ol>
     */
    public static RoadPlanParams.Dashes editDashes(RoadPlanParams params, Function<UUID, Optional<List<Port>>> ports, List<Incoming> incoming) {
        RoadPlanParams.Dashes own = dashes(params, ports);
        List<Incoming> inStep = incoming.stream().filter(in -> in.port().index() != in.index() && in.port().profileId().equals(params.profileId()))
                .toList();
        if (inStep.isEmpty()) {
            return own;
        }
        Optional<List<Port>> mine = ports(params.withDashes(own));
        if (mine.isPresent() && inStep.stream().allMatch(in -> mine.get().get(in.index()).phase() == in.port().phase())) {
            return own;
        }
        for (Incoming in : inStep) {
            if (in.index() == 0) {
                return new RoadPlanParams.Dashes(in.port().phase(), RoadPlanParams.Dashes.Anchor.START);
            }
        }
        return new RoadPlanParams.Dashes(inStep.get(0).port().phase(), RoadPlanParams.Dashes.Anchor.END);
    }

    /**
     * Whether the road whose end {@code in} names still joins the edited road {@code edited} there (R-10+), given the
     * standing roads {@code placed} (the edited road as it was among them): the edited road's end is still at that end's
     * block, facing on from it, with the same lanes, and what that road's joins call for its dashes is unchanged.
     */
    public static boolean stillJoins(Incoming in, UUID editedId, RoadPlanParams edited, Map<UUID, RoadPlanParams> placed) {
        RoadPlanParams road = placed.get(in.road());
        Optional<List<Port>> now = ports(edited);
        Optional<RoadProfile> profile = RoadProfiles.byId(edited.profileId());
        if (road == null || now.isEmpty() || profile.isEmpty()) {
            return false;
        }
        Port port = now.get().get(in.index());
        RoadPlanParams.End end = road.ends().get(in.roadEnd());
        Heading wanted = in.roadEnd() == 0 ? port.outgoing() : port.outgoing().opposite();
        if (!port.at(end) || end.heading() != wanted || !sameLanes(profile.get(), RoadProfiles.byId(road.profileId()).orElseThrow())) {
            return false;
        }
        Map<UUID, Optional<List<Port>>> before = new LinkedHashMap<>();
        Function<UUID, Optional<List<Port>>> portsBefore = id -> before.computeIfAbsent(id, key -> Optional.ofNullable(placed.get(key))
                .flatMap(RoadJoins::ports));
        Function<UUID, Optional<List<Port>>> portsAfter = id -> id.equals(editedId) ? now : portsBefore.apply(id);
        return dashes(road, portsBefore).equals(dashes(road, portsAfter));
    }

    /**
     * The roads that follow an edit of road {@code id} from {@code before} to {@code edited} (owner, 2026-11-24: moving a
     * joined end re-plans the roads joined there, in the same operation): each road among {@code placed} with an end
     * drawn onto an end of it that the edit moves (its block, height or heading), where that road's end sat on the end as
     * it was. Each is given as a road ({@link RoadPlanParams#road}) with that end moved onto the end's new place, facing on
     * from it, and the dashes its joins then call for ({@link #settledDashes}). By road id, in id order (the client lists
     * the roads newest first, the server oldest first, and both must plan the same); none when no joined end moves.
     *
     * <p>Only the ends decide, so the planner, the server's records ({@link RoadHistory}) and the node aids work out the
     * same roads from the same operations, and nothing about them is stored.
     */
    public static Map<UUID, RoadPlanParams> followers(UUID id, RoadPlanParams before, RoadPlanParams edited, Map<UUID, RoadPlanParams> placed) {
        Map<UUID, RoadPlanParams> followers = new TreeMap<>();
        if (before.ends().size() != 2 || edited.ends().size() != 2) {
            return followers;
        }
        for (Map.Entry<UUID, RoadPlanParams> entry : placed.entrySet()) {
            RoadPlanParams road = entry.getValue();
            if (entry.getKey().equals(id) || road.ends().size() != 2) {
                continue;
            }
            List<RoadPlanParams.End> ends = new ArrayList<>(road.ends());
            boolean moved = false;
            for (int j = 0; j < 2; j++) {
                RoadPlanParams.End end = ends.get(j);
                if (!end.snapRoadId().equals(Optional.of(id)) || end.snapPortIndex().isEmpty() || end.snapPortIndex().getAsInt() > 1) {
                    continue;
                }
                int index = end.snapPortIndex().getAsInt();
                RoadPlanParams.End was = before.ends().get(index);
                RoadPlanParams.End now = edited.ends().get(index);
                boolean sat = end.x() == was.x() && end.y() == was.y() && end.z() == was.z();
                if (!sat || now.x() == was.x() && now.y() == was.y() && now.z() == was.z() && now.heading() == was.heading()) {
                    continue; // drawn elsewhere (a stale join), or the end stays
                }
                // A start leaves the port the way a road leaving it runs; an end arrives the opposite way.
                Heading outgoing = index == 0 ? now.heading().opposite() : now.heading();
                ends.set(j, new RoadPlanParams.End(now.x(), now.y(), now.z(), j == 0 ? outgoing : outgoing.opposite(), end.snapRoadId(),
                        end.snapPortIndex()));
                moved = true;
            }
            if (moved) {
                followers.put(entry.getKey(), road.road().withEnds(ends));
            }
        }
        if (followers.isEmpty()) {
            return followers;
        }
        Map<UUID, RoadPlanParams> after = new LinkedHashMap<>(placed);
        after.put(id, edited);
        after.putAll(followers);
        followers.replaceAll((road, params) -> {
            Map<UUID, RoadPlanParams> others = new LinkedHashMap<>(after);
            others.remove(road);
            return params.withDashes(settledDashes(params, others, incoming(road, placed)));
        });
        return followers;
    }

    private static Optional<Port> joined(RoadPlanParams.End end, Function<UUID, Optional<List<Port>>> ports) {
        if (end.snapRoadId().isEmpty() || end.snapPortIndex().isEmpty() || end.snapPortIndex().getAsInt() > 1) {
            return Optional.empty();
        }
        return ports.apply(end.snapRoadId().get()).map(list -> list.get(end.snapPortIndex().getAsInt()));
    }

    /** Why a joined end of {@code params} is refused, if one is (the planner's step before solving). */
    static Optional<RoadPlanner.Outcome> refusal(RoadPlanParams params, RoadProfile profile, Map<UUID, RoadPlanParams> placed) {
        return refusal(params, profile, placed, List.of());
    }

    /**
     * As {@link #refusal(RoadPlanParams, RoadProfile, Map)}, for an edited road that the ends {@code incoming} join
     * (R-10+). Its dashes aren't checked: the planner gives it {@link #settledDashes}.
     */
    static Optional<RoadPlanner.Outcome> refusal(RoadPlanParams params, RoadProfile profile, Map<UUID, RoadPlanParams> placed,
            List<Incoming> incoming) {
        Map<UUID, Optional<List<Port>>> ports = new LinkedHashMap<>();
        Function<UUID, Optional<List<Port>>> portsOf = id -> ports.computeIfAbsent(id, key -> {
            RoadPlanParams road = placed.get(key);
            return road == null ? Optional.empty() : ports(road);
        });
        RoadGraph graph = RoadGraph.of(placed); // G-10: which ends are joined already
        List<RoadPlanParams.End> ends = params.ends();
        for (int i = 0; i < ends.size(); i++) {
            RoadPlanParams.End end = ends.get(i);
            if (end.snapRoadId().isEmpty()) {
                if (end.snapPortIndex().isPresent()) {
                    return refused(PORT_MISMATCH, Map.of());
                }
                continue;
            }
            UUID road = end.snapRoadId().get();
            if (end.snapPortIndex().isEmpty()) {
                return refused(PORT_MISMATCH, Map.of());
            }
            int index = end.snapPortIndex().getAsInt();
            if (index >= ARM_PORTS) { // G-11: a junction's arm
                Optional<RoadPlanner.Outcome> arm = armRefusal(params, profile, placed, graph, i, road, index - ARM_PORTS);
                if (arm.isPresent()) {
                    return arm;
                }
                continue;
            }
            Optional<List<Port>> found = portsOf.apply(road);
            if (found.isEmpty()) {
                return refused(ROAD_END_GONE, Map.of());
            }
            Port port = found.get().get(index);
            if (!port.at(end)) {
                return refused(PORT_MISMATCH, Map.of());
            }
            // A start leaves the port the way a road leaving it runs; an end arrives the opposite way.
            Heading wanted = i == 0 ? port.outgoing() : port.outgoing().opposite();
            if (end.heading() == wanted.opposite()) {
                return refused(PieceSolver.REVERSE_HEADING, Map.of()); // back over the road it joins
            }
            if (end.heading() != wanted) {
                return refused(PORT_MISMATCH, Map.of());
            }
            if (graph.taken(road, index)) { // a second road there would make a junction (Slice D)
                return refused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "junction"));
            }
            RoadProfile other = RoadProfiles.byId(port.profileId()).orElseThrow(); // its ports exist, so its profile does
            if (!sameLanes(profile, other)) {
                Map<String, String> args = new LinkedHashMap<>();
                args.put("layout", profile.id());
                args.put("joins", other.id());
                return refused(PROFILE_MISMATCH, args);
            }
            if (placed.get(road).drivingSide() != params.drivingSide() && !shared(profile)) {
                return refused(SIDE_MISMATCH, Map.of());
            }
        }
        return Optional.empty();
    }

    /** Why end {@code i} of {@code params} can't take arm {@code arm} of the junction {@code junction}, if it can't. */
    private static Optional<RoadPlanner.Outcome> armRefusal(RoadPlanParams params, RoadProfile profile, Map<UUID, RoadPlanParams> placed, RoadGraph graph,
            int i, UUID junction, int arm) {
        RoadPlanParams.End end = params.ends().get(i);
        RoadPlanParams placedJunction = placed.get(junction);
        List<Port> arms = placedJunction == null ? List.of() : armPorts(placedJunction);
        if (arms.isEmpty()) {
            return refused(ROAD_END_GONE, Map.of());
        }
        if (arm >= arms.size()) {
            return refused(PORT_MISMATCH, Map.of());
        }
        Port port = arms.get(arm);
        // A start leaves the arm the way a road leaving it runs; an end arrives the opposite way.
        Heading wanted = i == 0 ? port.outgoing() : port.outgoing().opposite();
        if (!port.at(end)) {
            return refused(PORT_MISMATCH, Map.of());
        }
        if (end.heading() == wanted.opposite()) {
            return refused(PieceSolver.REVERSE_HEADING, Map.of());
        }
        if (end.heading() != wanted) {
            return refused(PORT_MISMATCH, Map.of());
        }
        if (graph.armTaken(junction, arm)) {
            return refused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", "junction")); // two roads on one arm
        }
        RoadProfile other = RoadProfiles.byId(port.profileId()).orElseThrow();
        if (!sameLanes(profile, other)) {
            Map<String, String> args = new LinkedHashMap<>();
            args.put("layout", profile.id());
            args.put("joins", other.id());
            return refused(PROFILE_MISMATCH, args);
        }
        if (placedJunction.drivingSide() != params.drivingSide() && !shared(profile)) {
            return refused(SIDE_MISMATCH, Map.of());
        }
        return Optional.empty();
    }

    /**
     * The dashes the planner gives {@code params} (council r4): {@link #editDashes}' from the roads standing
     * ({@code placed}) and the ends {@code incoming} drawn onto it, whatever the request says. A client that doesn't know
     * every joined road works out other dashes; the server's are the ones planned and recorded.
     */
    public static RoadPlanParams.Dashes settledDashes(RoadPlanParams params, Map<UUID, RoadPlanParams> placed, List<Incoming> incoming) {
        return editDashes(params, id -> {
            RoadPlanParams road = placed.get(id);
            return road == null ? Optional.empty() : ports(road);
        }, incoming);
    }

    /** Whether every lane of {@code profile} is shared by both directions (the alley's), so its driving side changes nothing. */
    private static boolean shared(RoadProfile profile) {
        return profile.lanes().stream().allMatch(lane -> lane.direction() == Lane.Direction.BOTH);
    }

    private static Optional<RoadPlanner.Outcome> refused(String code, Map<String, String> args) {
        return Optional.of(new RoadPlanner.Outcome(PlanResult.refused(code, args), Optional.empty()));
    }

    /**
     * {@code params} the way round a tool should store it (R-5), so that a joint is one whose dashes can carry on exactly:
     * the road's start on another road's end, or its end on another road's start. A road joined only the other way (its
     * start on a road's start, or its end on a road's end) is turned round: the same road, its ends swapped and its
     * headings turned.
     */
    public static RoadPlanParams orient(RoadPlanParams params) {
        if (params.ends().size() != 2) {
            return params;
        }
        OptionalInt start = params.ends().get(0).snapPortIndex();
        OptionalInt end = params.ends().get(1).snapPortIndex();
        boolean startWith = start.equals(OptionalInt.of(1)) && params.ends().get(0).snapRoadId().isPresent();
        boolean endWith = end.equals(OptionalInt.of(0)) && params.ends().get(1).snapRoadId().isPresent();
        boolean startAgainst = start.equals(OptionalInt.of(0)) && params.ends().get(0).snapRoadId().isPresent();
        boolean endAgainst = end.equals(OptionalInt.of(1)) && params.ends().get(1).snapRoadId().isPresent();
        if (startWith || endWith || !(startAgainst || endAgainst)) {
            return params;
        }
        return turned(params);
    }

    /** The same road from its end to its start: the ends swapped, each heading turned round. */
    public static RoadPlanParams turned(RoadPlanParams params) {
        List<RoadPlanParams.End> turned = new ArrayList<>();
        for (int i = params.ends().size() - 1; i >= 0; i--) {
            RoadPlanParams.End end = params.ends().get(i);
            turned.add(new RoadPlanParams.End(end.x(), end.y(), end.z(), end.heading().opposite(), end.snapRoadId(), end.snapPortIndex()));
        }
        return params.withEnds(turned);
    }
}
