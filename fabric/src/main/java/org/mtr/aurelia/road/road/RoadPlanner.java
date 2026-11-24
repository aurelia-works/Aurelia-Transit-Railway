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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.nodes.MtrNodes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.raster.Column;
import org.mtr.aurelia.road.road.raster.Rasteriser;
import org.mtr.aurelia.road.road.raster.RoadPiece;
import org.mtr.aurelia.road.road.raster.SurfaceFit;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.road.solve.LaneNodes;
import org.mtr.aurelia.road.road.solve.LaneRail;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.road.solve.RoadSolution;
import org.mtr.aurelia.road.road.solve.RoadSolver;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * Turns road parameters into a block plan, or refuses them (roads-v1 §12). The client runs it for the preview and the
 * hash, the server again before applying (editor-protocol §5), so it is deterministic and never "fixes" a request.
 *
 * <p><b>Slice A</b> (task F-3): {@code STRAIGHT} roads with a built-in profile, the {@code vanilla} palette, both ends at
 * the same height, no node aids and no overlay, on any of the 16 headings (owner, 2026-10-05), for either driving
 * side. Each road is G-6's ideal straight line along the start's heading. Everything else is refused with
 * {@code NOT_AVAILABLE_YET} and the feature's name, until the task that adds it.
 *
 * <p><b>R-2</b> adds warnings ({@link #STEP_22_5}), {@link #snapEnd} (where the straight tool lets a road end) and
 * {@link #outcome} (a refused road's shape, which the tool draws in red).
 *
 * <p><b>R-3</b> moves every road onto solved pieces (owner, 2026-10-06: G-6+): one piece per road, solved by G-5's
 * {@link PieceSolver} (its refusals and their arguments come through as they are) and rasterised by G-6+'s
 * {@link RoadPiece#solved}. {@code CURVE} roads have exactly two ends with any headings; {@code STRAIGHT} roads keep their
 * own checks first. A solved cardinal straight is exactly Slice A's road; 45° and 22.5° straights now follow their
 * solved lanes.
 *
 * <p><b>G-7</b> lets pieces climb (the solver checks their grades and cross-lane steps), fits every column's surface to
 * the palette's half blocks, and adds the foundation and clearance ({@code SurfaceFit}), cut off at the build height.
 *
 * <p><b>R-6</b> puts road under every lane node ({@link LaneNodes}; owner, 2026-10-06): at a 22.5° end about half the
 * lane nodes fall up to half a block past the piece's square end, and each such node's column gets the lane's own stack
 * (surface, foundation, clearance), so the node, its marker and the bus all start on the road. Then the node aids
 * (roads-v1 §9; ADR-0033), at every lane node:
 * <ul>
 *   <li>{@code WOOL}: the palette's marker (blue wool) in place of the surface block under the node's spot (owner,
 *       2026-10-06: markers sit in the road surface);</li>
 *   <li>{@code PREORIENTED}: an MTR node, already facing the lane's way ({@link MtrNodes#state}), in the node block on top
 *       of the surface, only where the game has {@value MtrNodes#BLOCK} ({@link #MTR_MISSING} otherwise);</li>
 *   <li>{@code CLEAR_MARKERS}: for a road as placed, the surface block that belongs at each marker spot, written only
 *       where the marker still is ({@link PlanResult#onlyWhere}), so nothing else (an MTR node least of all) is
 *       touched.</li>
 * </ul>
 *
 * <p><b>T-2</b> adds {@code TRACK} (ADR-0040): MTR nodes as {@code PREORIENTED} places them but already joined
 * ({@code is_connected=true}, as MTR's connector leaves them), so undo puts back exactly what the road placed; and the
 * plan's side effects, the lanes' hidden track ({@link #track}). Only where the game has {@value MtrNodes#BLOCK}
 * ({@link #MTR_MISSING} otherwise); whether the server can build track is the server's to say
 * ({@code TRACK_UNAVAILABLE}).
 *
 * <p><b>R-5</b> joins roads end to end ({@link RoadJoins}): an end may join an end of a placed road the planner is given
 * ({@code placed}, by operation id), checked before anything is solved; the road's dashes carry on that road's
 * ({@link RoadPlanParams.Dashes}). A road is solved whole by {@link RoadSolver}: one piece, or a graded bend split into a
 * straight and a bend ({@link #BEND_SPLIT}), whose pieces are rasterised in order with half-open joins and the dash
 * phase carried from one to the next (G-6), each lane's nodes shared between them and a rail per piece.
 *
 * <p><b>R-7</b> edits a placed road ({@link #edit}): {@code REBUILD} and {@code DELETE} name the road by its id
 * ({@code targetRoadId}, {@link RoadHistory}) and carry its parameters as they stand, which must be the placed road's
 * ({@link #ROAD_GONE}, {@link #ROAD_CHANGED}). A rebuild plans the road again as placed (today's planner, joins not
 * checked again), track included; a delete's plan is the road being removed, as today's planner plans it, for the hash
 * (what a delete writes back comes from the undo images, which only the server has).
 *
 * <p><b>R-9</b> takes a road's blocks from its palette ({@code paletteId}, one of the world's {@link WorldLimits#palettes};
 * {@link #UNKNOWN_PALETTE} otherwise): its surface, lines, kerb, footway and gutter, the foundation and the node marker,
 * fitted to the palette's steps ({@link #FULL_BLOCK_STEPS} warns of a climbing road in whole blocks). A rebuild may name
 * another palette than the road's: the road rebuilt in it (owner, 2026-10-10), every other parameter as placed.
 */
public final class RoadPlanner {
    /** A piece shorter than 2 blocks (roads-v1 §12). */
    public static final String TOO_SHORT = "TOO_SHORT";
    /** Part of the road is outside the build height or the world border (roads-v1 §12). */
    public static final String WORLD_LIMIT = "WORLD_LIMIT";
    /**
     * A straight road whose end isn't straight ahead of its start: more than half a block off the start's heading line,
     * behind the start, or with another heading.
     */
    public static final String END_NOT_IN_LINE = "END_NOT_IN_LINE";
    /** No built-in profile has this id; args {@code profile}. */
    public static final String UNKNOWN_PROFILE = "UNKNOWN_PROFILE";
    /**
     * A warning (roads-v1 §12): a 22.5° straight whose end is more than {@link #MTR_TOLERANCE} off its start's heading
     * line. MTR joins block-centre nodes at 22.5° with a slight S-bend, and this one strays further than S6 allows. Args
     * {@code d}: how far off, in blocks.
     */
    public static final String STEP_22_5 = "STEP_22_5";
    /**
     * A warning (roads-v1 §12; built in G-4+): the piece's tightest lane arc is under {@link #LONG_VEHICLE_RADIUS}, so long
     * vehicles may clip the kerb. It can't fire for City street, whose minimum is 30; residential and alley allow tighter.
     */
    public static final String TIGHT_FOR_LONG_VEHICLES = "TIGHT_FOR_LONG_VEHICLES";
    /**
     * News rather than a warning (R-5; the tools show it in the accent colour): a graded bend was split into a straight
     * and a bend so its lanes climb evenly ({@link RoadSolver}). Args {@code x}, {@code y}, {@code z} and {@code heading}:
     * the node where the two meet, and the heading there.
     */
    public static final String BEND_SPLIT = "BEND_SPLIT";

    /** The inner-lane radius below which {@link #TIGHT_FOR_LONG_VEHICLES} warns (roads-v1 §3: a lorry at a 25–30 m inside radius). */
    public static final double LONG_VEHICLE_RADIUS = 30;

    /**
     * Pre-oriented MTR nodes or track were asked for, but this game has no {@value MtrNodes#BLOCK} block (R-6, T-2;
     * roads-v1 §9: only when MTR is loaded).
     */
    public static final String MTR_MISSING = "MTR_MISSING";

    /** R-9: no palette here has the request's {@code paletteId}; args {@code palette}. */
    public static final String UNKNOWN_PALETTE = "UNKNOWN_PALETTE";
    /**
     * A warning (roads-v1 §12; built in R-9): a road whose ends are at different heights, in a palette with neither slabs
     * nor layers, so its surface climbs in whole-block steps.
     */
    public static final String FULL_BLOCK_STEPS = "FULL_BLOCK_STEPS";

    /** R-7: the road a rebuild or delete names isn't standing now (undone, deleted, unknown, or in another dimension). */
    public static final String ROAD_GONE = "ROAD_GONE";
    /** R-7: the road's parameters in a delete aren't the placed road's: it changed since it was picked. */
    public static final String ROAD_CHANGED = "ROAD_CHANGED";
    /**
     * R-10: an edit (a rebuild with other ends, layout, node aid or shape) that would break a join: from R-10+, a road
     * joined to the edited road would no longer join it as drawn (its end moved, or its lanes or dashes no longer carry
     * on). Moving a joined end, which would re-plan the roads joined there too, isn't built yet.
     */
    public static final String ROAD_JOINED = "ROAD_JOINED";
    /**
     * R-7: a delete needs the undo images of every operation that built the road (owner, 2026-10-09: delete puts back
     * exactly what was there), and the cap has dropped one.
     */
    public static final String ROAD_TOO_OLD = "ROAD_TOO_OLD";

    /** The shortest piece, in blocks (roads-v1 §12). */
    public static final double MIN_LENGTH = 2;

    /**
     * How far MTR's rail may stray from a lane's line, in blocks: spike S6's pass mark. A 22.5° road made with the straight
     * tool ends only where MTR's S-bend stays within it (owner, 2026-10-06: R-2).
     */
    public static final double MTR_TOLERANCE = 0.05;

    /** How far the end may be from the start's heading line: half a block, so exactly one block per row qualifies. */
    static final double MAX_SIDEWAYS = 0.5;

    /** How far either side of the aimed length {@link #snapEnd} looks first; wider than the widest gap between 22.5° ends (13 blocks). */
    private static final int SNAP_WINDOW = 20;

    /**
     * What the road tool shows for a request: the planner's answer and, when the road was refused only because part of
     * it leaves the world ({@code WORLD_LIMIT}), the road's shape, which the tool draws in red (roads-v1 §12).
     */
    public record Outcome(PlanResult result, Optional<BlockPlan> refusedShape) {
        public Outcome {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(refusedShape, "refusedShape");
            if (result.isPlanned() && refusedShape.isPresent()) {
                throw new IllegalArgumentException("Only a refused road has a refused shape");
            }
        }
    }

    private RoadPlanner() {
    }

    /** The plan for {@code params} in a world with {@code limits}, with no placed roads to join, or why there is none. */
    public static PlanResult plan(RoadPlanParams params, WorldLimits limits) {
        return plan(params, limits, Map.of());
    }

    /**
     * The plan for {@code params} in a world with {@code limits}, where {@code placed} are the placed roads its ends may
     * join (R-5: their parameters by operation id), or why there is none.
     */
    public static PlanResult plan(RoadPlanParams params, WorldLimits limits, Map<UUID, RoadPlanParams> placed) {
        return compute(params, limits, false, placed, true).result();
    }

    /**
     * The plan of a road as it was placed (R-5): {@code params} as an operation record or {@code YourRoads} keeps them,
     * planned without checking its joins again, as the roads it joined may have gone since. For the node aids and the
     * records, never for placing.
     */
    public static PlanResult asPlaced(RoadPlanParams params, WorldLimits limits) {
        return compute(params.tool() == RoadPlanParams.Tool.REBUILD ? params.road() : params, limits, false, Map.of(), false).result(); // R-7: a rebuilt road
    }

    /** As {@link #plan}, plus the road's shape when it was refused for leaving the world (the road tool's preview). */
    public static Outcome outcome(RoadPlanParams params, WorldLimits limits) {
        return outcome(params, limits, Map.of());
    }

    /** As {@link #plan(RoadPlanParams, WorldLimits, Map)}, plus the road's shape when it was refused for leaving the world. */
    public static Outcome outcome(RoadPlanParams params, WorldLimits limits, Map<UUID, RoadPlanParams> placed) {
        return compute(params, limits, true, placed, true);
    }

    private static Outcome compute(RoadPlanParams params, WorldLimits limits, boolean wantShape, Map<UUID, RoadPlanParams> placed,
            boolean checkJoins) {
        return compute(params, limits, wantShape, placed, checkJoins, List.of());
    }

    /** As {@link #compute(RoadPlanParams, WorldLimits, boolean, Map, boolean)}, for an edited road the ends {@code incoming} join. */
    private static Outcome compute(RoadPlanParams params, WorldLimits limits, boolean wantShape, Map<UUID, RoadPlanParams> placed,
            boolean checkJoins, List<RoadJoins.Incoming> incoming) {
        if (params.tool() == RoadPlanParams.Tool.REBUILD || params.tool() == RoadPlanParams.Tool.DELETE) {
            return edit(params, limits, wantShape, placed);
        }
        Outcome early = refusal(params);
        if (early != null) {
            return early;
        }
        Optional<Palette> palette = limits.palettes().byId(params.paletteId());
        if (palette.isEmpty()) {
            return refused(UNKNOWN_PALETTE, Map.of("palette", params.paletteId()));
        }
        RoadPlanParams.End start = params.ends().get(0);
        RoadPlanParams.End end = params.ends().get(1);
        List<PlanResult.Warning> warnings = new ArrayList<>();
        if (params.tool() == RoadPlanParams.Tool.STRAIGHT) {
            Heading heading = start.heading();
            // Measured from the start block's centre, in whole blocks, so the result doesn't depend on where the road is.
            Frame frame = new Frame(Vec2.ZERO, heading);
            Vec2 toEnd = new Vec2((double) end.x() - start.x(), (double) end.z() - start.z());
            double along = frame.along(toEnd);
            double side = frame.side(toEnd);
            if (StrictMath.abs(side) > MAX_SIDEWAYS || along < 0) {
                return refused(END_NOT_IN_LINE, Map.of());
            }
            if (along < MIN_LENGTH) {
                return refused(TOO_SHORT, Map.of());
            }
            if (heading.isIntermediate() && StrictMath.abs(side) > MTR_TOLERANCE) {
                warnings.add(new PlanResult.Warning(STEP_22_5, Map.of("d", String.format(Locale.ROOT, "%.3f", StrictMath.abs(side)))));
            }
        }
        boolean clearing = params.tool() == RoadPlanParams.Tool.CLEAR_MARKERS;
        RoadProfile profile = RoadProfiles.byId(params.profileId()).orElseThrow();
        if (checkJoins && !clearing) { // clearing markers takes a road as placed, whatever it joined (R-5)
            Optional<Outcome> joins = RoadJoins.refusal(params, profile, placed, incoming);
            if (joins.isPresent()) {
                return joins.get();
            }
            params = params.withDashes(RoadJoins.settledDashes(params, placed, incoming)); // the server's, not the client's
        }
        boolean mtrNodes = params.nodeAids() == RoadPlanParams.NodeAids.PREORIENTED || params.nodeAids() == RoadPlanParams.NodeAids.TRACK;
        if (!clearing && mtrNodes && !limits.hasBlock(MtrNodes.BLOCK)) {
            return refused(MTR_MISSING, Map.of());
        }
        boolean heightOutside = start.y() < limits.minY() || start.y() >= limits.maxY() || end.y() < limits.minY() || end.y() >= limits.maxY();
        if (heightOutside && !wantShape) {
            return refused(WORLD_LIMIT, Map.of()); // the server needn't rasterise a road it will refuse
        }
        RoadSolution solution = RoadSolver.solve(profile, params.preset(), params.vertical(), start, end);
        if (solution instanceof RoadSolution.Refused refusal) {
            return refused(refusal.code(), refusal.args());
        }
        List<PieceSolution.Solved> pieces = ((RoadSolution.Solved) solution).pieces();
        double tightest = Double.POSITIVE_INFINITY;
        for (PieceSolution.Solved piece : pieces) {
            tightest = StrictMath.min(tightest, tightestLane(piece));
        }
        if (tightest < LONG_VEHICLE_RADIUS) {
            warnings.add(new PlanResult.Warning(TIGHT_FOR_LONG_VEHICLES, Map.of()));
        }
        if (pieces.size() > 1) {
            RoadPlanParams.End middle = pieces.get(1).start();
            Map<String, String> at = new LinkedHashMap<>();
            at.put("x", Integer.toString(middle.x()));
            at.put("y", Integer.toString(middle.y()));
            at.put("z", Integer.toString(middle.z()));
            at.put("heading", middle.heading().name());
            warnings.add(new PlanResult.Warning(BEND_SPLIT, at));
        }
        if (palette.get().steps() == 1 && start.y() != end.y()) {
            warnings.add(new PlanResult.Warning(FULL_BLOCK_STEPS, Map.of()));
        }
        return build(params, profile, palette.get(), pieces, limits, wantShape, warnings);
    }

    /**
     * A rebuild or delete of a placed road (R-7), checked against {@code placed} (the standing roads by road id): the road
     * must stand ({@link #ROAD_GONE}). A delete must have the road's parameters ({@link #ROAD_CHANGED}); its plan is the
     * road's blocks with no track: the road being removed. A rebuild's plan is the road planned as placed, with its track.
     *
     * <p><b>R-10</b> (owner, 2026-10-13): a rebuild with other parameters than the road's (other ends, layout, node aid or
     * shape; R-9's other palette alone stays a plain rebuild) edits it. It is planned as a new road would be, its ends'
     * joins checked against the other standing roads (a moved end may join one, as when drawing a road).
     *
     * <p><b>R-10+</b>: a road that other roads join is edited too, as one operation, as long as each of them still joins
     * it as it was drawn: the edited road's end there keeps its block and heading and the same lanes, and what that
     * road's joins call for its dashes doesn't change ({@link RoadJoins#stillJoins}; otherwise {@link #ROAD_JOINED}). The
     * edited road's dashes stay in step with them ({@link RoadJoins#editDashes}). Its own joins are checked as a new
     * road's. The joined roads themselves aren't changed: what they share with it is kept (the module's replacement).
     */
    private static Outcome edit(RoadPlanParams params, WorldLimits limits, boolean wantShape, Map<UUID, RoadPlanParams> placed) {
        UUID id = params.targetRoadId().orElse(null);
        RoadPlanParams road = id == null ? null : placed.get(id);
        if (road == null) {
            return refused(ROAD_GONE, Map.of());
        }
        boolean rebuild = params.tool() == RoadPlanParams.Tool.REBUILD;
        // R-9: a rebuild may put the road in another palette (owner, 2026-10-10) without editing it.
        RoadPlanParams asked = rebuild ? params.withPalette(road.paletteId()) : params;
        if (!road.road().equals(asked.road())) {
            if (!rebuild) {
                return refused(ROAD_CHANGED, Map.of());
            }
            List<RoadJoins.Incoming> incoming = RoadJoins.incoming(id, placed);
            Map<UUID, RoadPlanParams> others = new LinkedHashMap<>(placed);
            others.remove(id); // the road can't join itself
            // Council r4: the server's dashes, not the client's, before the joins are checked against them.
            RoadPlanParams edited = params.road().withDashes(RoadJoins.settledDashes(params.road(), others, incoming));
            for (RoadJoins.Incoming in : incoming) { // R-10+: the roads joined to it keep their joins
                if (!RoadJoins.stillJoins(in, id, edited, placed)) {
                    return refused(ROAD_JOINED, Map.of());
                }
            }
            return compute(edited, limits, wantShape, others, true, incoming);
        }
        Outcome asPlaced = compute(params.road(), limits, wantShape, Map.of(), false);
        PlanResult result = asPlaced.result();
        if (!result.isPlanned()) {
            return asPlaced; // today's planner refuses the road (a world limit moved, say)
        }
        List<SideEffect> track = params.tool() == RoadPlanParams.Tool.REBUILD ? result.sideEffects() : List.of();
        return new Outcome(PlanResult.planned(result.plan().orElseThrow(), List.of(), track), Optional.empty());
    }

    /**
     * The plan of the road {@code params} describes once it is solved into {@code pieces} (in order, joined end to end):
     * its blocks, the road under its lane nodes, its node aid, and its track; or {@code WORLD_LIMIT}.
     */
    static Outcome build(RoadPlanParams params, RoadProfile profile, Palette palette, List<PieceSolution.Solved> pieces, WorldLimits limits,
            boolean wantShape, List<PlanResult.Warning> warnings) {
        RoadPlanParams.End start = pieces.get(0).start();
        RoadPlanParams.End end = pieces.get(pieces.size() - 1).end();
        boolean clearing = params.tool() == RoadPlanParams.Tool.CLEAR_MARKERS;
        boolean heightOutside = start.y() < limits.minY() || start.y() >= limits.maxY() || end.y() < limits.minY() || end.y() >= limits.maxY();
        // The foundation and the clearance stop at the build height; the surface itself is checked below.
        BlockPlan plan = Rasteriser.plan(rasterPieces(profile, pieces, params.dashes()), palette, limits.minY(), limits.maxY());
        List<LaneNode> nodes = LaneNodes.of(pieces);
        plan = withNodeColumns(plan, nodes, palette, limits);
        if (clearing) {
            return clearMarkers(plan, nodes, palette, limits, heightOutside);
        }
        plan = withNodeAids(plan, nodes, palette, params.nodeAids());
        boolean outside = heightOutside;
        for (BlockPlan.Block block : plan.blocks()) {
            if (outside || !limits.contains(block.x(), block.y(), block.z())) {
                outside = true;
                break;
            }
        }
        if (outside) {
            return new Outcome(PlanResult.refused(WORLD_LIMIT), wantShape ? Optional.of(plan) : Optional.empty());
        }
        List<SideEffect> track = params.nodeAids() == RoadPlanParams.NodeAids.TRACK ? track(params, profile, pieces) : List.of();
        return new Outcome(PlanResult.planned(plan, warnings, track), Optional.empty());
    }

    /**
     * The pieces of a road to rasterise (R-5): in order, each starting its dashes where the one before stopped
     * ({@link RoadPiece#phaseAfter}). The first starts at {@code dashes}' phase, or, when the phase belongs to the road's
     * end, at that phase less the road's length as the dashes measure it, so the pattern reaches it at the end.
     *
     * <p>The last piece closes the road's end (G-6), unless the phase belongs to the end: the road then runs into the
     * start of a road of the same layout ({@link RoadJoins#dashes}), which keeps its first row. Each road measures that row
     * along its own lanes, and the two can differ by a thousandth of a block, so a dash whose edge is the joint (as every
     * road's dashes start at its start) would otherwise depend on which road was placed last.
     */
    static List<RoadPiece> rasterPieces(RoadProfile profile, List<PieceSolution.Solved> pieces, RoadPlanParams.Dashes dashes) {
        double phase = dashes.phase();
        if (dashes.at() == RoadPlanParams.Dashes.Anchor.END) {
            double length = 0;
            for (int i = 0; i < pieces.size(); i++) {
                length = RoadPiece.solved(profile, pieces.get(i), i == pieces.size() - 1, length).phaseAfter();
            }
            phase -= length;
        }
        List<RoadPiece> raster = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            RoadPiece piece = RoadPiece.solved(profile, pieces.get(i), i == pieces.size() - 1 && dashes.at() != RoadPlanParams.Dashes.Anchor.END, phase);
            raster.add(piece);
            phase = piece.phaseAfter();
        }
        return raster;
    }

    /**
     * The hidden track of a road placed with {@code TRACK} (T-2; ADR-0040), made of {@code pieces}: lane by lane, in
     * order along the road ({@link LaneNodes}'s order), one rail per piece between the lane's two node blocks, each with
     * its node's MTR heading.
     * <ul>
     *   <li><b>One way with traffic:</b> lanes are authored for left-hand traffic ({@link Lane.Direction}), and right-hand
     *       traffic swaps them. A lane whose traffic runs from the road's start gets the speed for setting off from its
     *       start node and 0 from its end (MTR's one-way rule, spike S14); the other way round for the other side. A
     *       shared lane (the alley's) gets the speed both ways (owner, 2026-10-08).</li>
     *   <li><b>The speed:</b> the road group's ({@link RoadGroup#trackSpeed}, G-4+).</li>
     *   <li><b>The shape:</b> the road's vertical shape and radius, as the solver built each lane's rail (G-3), so MTR's
     *       rail follows the slope as the road does.</li>
     * </ul>
     */
    static List<SideEffect> track(RoadPlanParams params, RoadProfile profile, List<PieceSolution.Solved> pieces) {
        SideEffect.TrackRail.Shape shape = params.vertical().shape() == RoadPlanParams.Vertical.Shape.TWO_RADII ? SideEffect.TrackRail.Shape.TWO_RADII
                : SideEffect.TrackRail.Shape.QUADRATIC;
        double radius = params.vertical().radius().orElse(0);
        int speed = profile.group().trackSpeed();
        List<SideEffect> rails = new ArrayList<>();
        int lanes = pieces.get(0).lanes().size();
        for (int lane = 0; lane < lanes; lane++) {
            for (PieceSolution.Solved piece : pieces) {
                LaneRail rail = piece.lanes().get(lane);
                Lane.Direction direction = rail.lane().direction().on(params.drivingSide());
                LaneNode a = new LaneNode(rail.number(), rail.start().x(), rail.start().y(), rail.start().z(), rail.start().heading());
                LaneNode b = new LaneNode(rail.number(), rail.end().x(), rail.end().y(), rail.end().z(), rail.end().heading());
                int fromA = direction == Lane.Direction.AGAINST_REFERENCE ? 0 : speed;
                int fromB = direction == Lane.Direction.WITH_REFERENCE ? 0 : speed;
                rails.add(new SideEffect.TrackRail(a.x(), a.nodeY(), a.z(), a.mtrFacing(), b.x(), b.nodeY(), b.z(), b.mtrFacing(), fromA, fromB, shape,
                        radius));
            }
        }
        return rails;
    }

    /** The radius of the tightest arc of any of {@code solved}'s lanes; infinite when every lane is straight. */
    private static double tightestLane(PieceSolution.Solved solved) {
        double tightest = Double.POSITIVE_INFINITY;
        for (LaneRail lane : solved.lanes()) {
            tightest = StrictMath.min(tightest, PieceSolver.tightestArc(lane.rail()));
        }
        return tightest;
    }

    /**
     * {@code plan} with road under every one of {@code nodes} whose column it doesn't cover (a 22.5° end): a lane column
     * whose top is the node's floor, so its surface block is a full block at the node's height.
     */
    private static BlockPlan withNodeColumns(BlockPlan plan, List<LaneNode> nodes, Palette palette, WorldLimits limits) {
        Set<Long> columns = new HashSet<>();
        for (BlockPlan.Block block : plan.blocks()) {
            columns.add(BlockChange.positionKey(block.x(), 0, block.z()));
        }
        List<LaneNode> outside = new ArrayList<>();
        for (LaneNode node : nodes) {
            if (!columns.contains(BlockChange.positionKey(node.x(), 0, node.z()))) {
                outside.add(node);
            }
        }
        if (outside.isEmpty()) {
            return plan;
        }
        BlockPlan.Builder builder = BlockPlan.builder(plan.dataVersion());
        for (BlockPlan.Block block : plan.blocks()) {
            builder.set(block.x(), block.y(), block.z(), block.state());
        }
        for (LaneNode node : outside) {
            SurfaceFit.stack(builder, node.x(), node.z(), new Column(Band.LANE, false, true), node.nodeY(), palette,
                    limits.minY(), limits.maxY());
        }
        return builder.build();
    }

    /**
     * {@code plan} with the node aid {@code aids} at {@code nodes}: the marker in each node's surface block, or an MTR
     * node in each node block (joined for {@code TRACK}). Every node's surface block is a full block of the road (node ends are whole blocks, G-7),
     * and a node block is in the clearance, or just above the build height (then the road leaves the world).
     */
    private static BlockPlan withNodeAids(BlockPlan plan, List<LaneNode> nodes, Palette palette, RoadPlanParams.NodeAids aids) {
        if (aids == RoadPlanParams.NodeAids.NONE) {
            return plan;
        }
        Map<Long, BlockPlan.Block> extra = new HashMap<>();
        boolean connected = aids == RoadPlanParams.NodeAids.TRACK; // T-2: joined, as MTR's connector leaves them
        for (LaneNode node : nodes) {
            BlockPlan.Block block = aids == RoadPlanParams.NodeAids.WOOL
                    ? new BlockPlan.Block(node.x(), node.y(), node.z(), palette.nodeMarker())
                    : new BlockPlan.Block(node.x(), node.nodeY(), node.z(), MtrNodes.state(node.mtrFacing(), connected));
            extra.put(BlockChange.positionKey(block.x(), block.y(), block.z()), block);
        }
        BlockPlan.Builder builder = BlockPlan.builder(plan.dataVersion());
        for (BlockPlan.Block block : plan.blocks()) {
            BlockPlan.Block replaced = extra.remove(BlockChange.positionKey(block.x(), block.y(), block.z()));
            BlockPlan.Block kept = replaced != null ? replaced : block;
            builder.set(kept.x(), kept.y(), kept.z(), kept.state());
        }
        if (aids == RoadPlanParams.NodeAids.WOOL && !extra.isEmpty()) {
            throw new IllegalStateException("Lane nodes outside the road's surface: " + extra.values()); // can't happen: tested
        }
        extra.values().forEach(block -> builder.set(block.x(), block.y(), block.z(), block.state())); // above the clearance
        return builder.build();
    }

    /**
     * The plan that clears a road's markers: at each lane node's marker spot, the block the road has there without
     * markers, written only where the marker still is.
     */
    private static Outcome clearMarkers(BlockPlan plan, List<LaneNode> nodes, Palette palette, WorldLimits limits, boolean heightOutside) {
        Map<Long, String> road = new HashMap<>();
        for (BlockPlan.Block block : plan.blocks()) {
            road.put(BlockChange.positionKey(block.x(), block.y(), block.z()), block.state());
        }
        BlockPlan.Builder clear = BlockPlan.builder(plan.dataVersion());
        BlockPlan.Builder onlyWhere = BlockPlan.builder(plan.dataVersion());
        for (LaneNode node : nodes) {
            String surface = road.get(BlockChange.positionKey(node.x(), node.y(), node.z()));
            if (surface == null) {
                throw new IllegalStateException("Lane node " + node + " is outside the road's surface"); // can't happen: tested
            }
            if (heightOutside || !limits.contains(node.x(), node.y(), node.z())) {
                return refused(WORLD_LIMIT, Map.of());
            }
            clear.set(node.x(), node.y(), node.z(), surface);
            onlyWhere.set(node.x(), node.y(), node.z(), palette.nodeMarker());
        }
        return new Outcome(PlanResult.plannedWhere(clear.build(), onlyWhere.build()), Optional.empty());
    }

    /**
     * The lane nodes of the road {@code params} describe (G-8), or empty if the road is refused before it is solved or by
     * the solver. The node aids and the overlay don't change them, nor (R-5) the roads it joins; a split bend has three
     * nodes a lane.
     */
    public static Optional<List<LaneNode>> laneNodes(RoadPlanParams params) {
        if (params.ends().size() != 2 || RoadProfiles.byId(params.profileId()).isEmpty()) {
            return Optional.empty();
        }
        RoadProfile profile = RoadProfiles.byId(params.profileId()).orElseThrow();
        RoadSolution solution = RoadSolver.solve(profile, params.preset(), params.vertical(), params.ends().get(0), params.ends().get(1));
        return solution instanceof RoadSolution.Solved solved ? Optional.of(LaneNodes.of(solved.pieces())) : Optional.empty();
    }

    /**
     * The request {@code params} as the server records it, with the roads standing among {@code placed} (council r4): a
     * new road, or a rebuild that changes the road, takes the dashes its joins call for ({@link RoadJoins#settledDashes}),
     * the same ones {@link #plan} plans it with. Anything else, a rebuild that leaves the road as placed among them, is
     * returned as it is.
     */
    public static RoadPlanParams settled(RoadPlanParams params, Map<UUID, RoadPlanParams> placed) {
        RoadPlanParams.Tool tool = params.tool();
        if ((tool == RoadPlanParams.Tool.STRAIGHT || tool == RoadPlanParams.Tool.CURVE) && params.ends().size() == 2) {
            return params.withDashes(RoadJoins.settledDashes(params, placed, List.of()));
        }
        if (tool != RoadPlanParams.Tool.REBUILD || params.targetRoadId().isEmpty()) {
            return params;
        }
        UUID id = params.targetRoadId().get();
        RoadPlanParams road = placed.get(id);
        if (road == null || road.road().equals(params.withPalette(road.paletteId()).road()) || params.ends().size() != 2) {
            return params; // gone (refused), or as placed: it keeps its recorded dashes
        }
        Map<UUID, RoadPlanParams> others = new LinkedHashMap<>(placed);
        others.remove(id);
        return params.withDashes(RoadJoins.settledDashes(params.road(), others, RoadJoins.incoming(id, placed)));
    }

    /** Why {@code params} are refused before any geometry is looked at, or null. */
    private static Outcome refusal(RoadPlanParams params) {
        RoadPlanParams.Tool tool = params.tool();
        if (tool != RoadPlanParams.Tool.STRAIGHT && tool != RoadPlanParams.Tool.CURVE && tool != RoadPlanParams.Tool.CLEAR_MARKERS) {
            return notYet(params.tool().name().toLowerCase(Locale.ROOT));
        }
        if (params.ends().size() != 2) {
            return notYet("pieces"); // one piece per road (owner, 2026-10-06: R-3)
        }
        if (RoadProfiles.byId(params.profileId()).isEmpty()) {
            return refused(UNKNOWN_PROFILE, Map.of("profile", params.profileId()));
        }
        if (params.overlay()) {
            return notYet("overlay");
        }
        if (params.junction().isPresent()) {
            return notYet("junction");
        }
        if (params.targetRoadId().isPresent()) {
            return notYet("target_road");
        }
        if (params.manualEdits().isPresent()) {
            return notYet("manual_edits");
        }
        RoadPlanParams.End start = params.ends().get(0);
        RoadPlanParams.End end = params.ends().get(1);
        if (params.tool() == RoadPlanParams.Tool.STRAIGHT && end.heading() != start.heading()) {
            return refused(END_NOT_IN_LINE, Map.of());
        }
        return null;
    }

    /**
     * The end of a straight road about {@code length} blocks ahead of {@code start}, along its heading: of the blocks
     * whose centres are within half a block of the heading line (so {@link #plan} doesn't refuse them as
     * {@code END_NOT_IN_LINE}), the one whose distance along is nearest to {@code length}, but at least
     * {@link #MIN_LENGTH} when {@code length} is. Ties go to the block nearer the line, then the lower x, then the lower z.
     * At 22.5° an end off the line makes MTR draw a slight S-bend, which the solver refuses on roads under about 9 blocks
     * (R-3); the straight tool uses {@link #snapEnd}, whose ends are always accepted.
     */
    public static RoadPlanParams.End endAlong(RoadPlanParams.End start, double length) {
        checkLength(length);
        Heading heading = start.heading();
        Frame frame = new Frame(Vec2.ZERO, heading);
        Vec2 ideal = frame.point(length, 0);
        int cx = (int) StrictMath.rint(ideal.x());
        int cz = (int) StrictMath.rint(ideal.z());
        double minimum = length >= MIN_LENGTH ? MIN_LENGTH : 0;
        Best best = new Best(length);
        for (int dx = cx - 3; dx <= cx + 3; dx++) {
            for (int dz = cz - 3; dz <= cz + 3; dz++) {
                best.offer(frame, dx, dz, MAX_SIDEWAYS, minimum);
            }
        }
        if (!best.found()) {
            throw new IllegalStateException("No block in line " + length + " blocks along " + heading); // can't happen: tested
        }
        return RoadPlanParams.End.at(start.x() + best.x, start.y(), start.z() + best.z, heading);
    }

    /**
     * Where the straight tool ends a road aimed {@code length} blocks along {@code start}'s heading (owner, 2026-10-06:
     * R-2). Never under {@link #MIN_LENGTH}.
     * <ul>
     *   <li><b>Cardinal and 45° headings:</b> {@link #endAlong}. Their in-line ends are exactly on the line.</li>
     *   <li><b>22.5° headings:</b> only ends within {@link #MTR_TOLERANCE} of the line, where MTR's S-bend stays within
     *       S6's pass mark, the nearest along to {@code length}. They come about every 10 blocks, at most 13 apart;
     *       the shortest is the (12, 5) step, 13 blocks. Ties go to the end nearer the line, then the lower x, then the
     *       lower z.</li>
     * </ul>
     */
    public static RoadPlanParams.End snapEnd(RoadPlanParams.End start, double length) {
        checkLength(length);
        double wanted = StrictMath.max(length, MIN_LENGTH);
        Heading heading = start.heading();
        if (!heading.isIntermediate()) {
            return endAlong(start, wanted);
        }
        Frame frame = new Frame(Vec2.ZERO, heading);
        for (int window = SNAP_WINDOW; ; window *= 2) {
            Best best = new Best(wanted);
            int from = (int) StrictMath.floor(StrictMath.max(0, wanted - window));
            int to = (int) StrictMath.ceil(wanted + window);
            for (int along = from; along <= to; along++) {
                Vec2 ideal = frame.point(along, 0);
                int cx = (int) StrictMath.rint(ideal.x());
                int cz = (int) StrictMath.rint(ideal.z());
                for (int dx = cx - 1; dx <= cx + 1; dx++) {
                    for (int dz = cz - 1; dz <= cz + 1; dz++) {
                        best.offer(frame, dx, dz, MTR_TOLERANCE, MIN_LENGTH);
                    }
                }
            }
            // A candidate nearer than the window's edge can't be beaten by one outside it.
            if (best.found() && best.miss <= window) {
                return RoadPlanParams.End.at(start.x() + best.x, start.y(), start.z() + best.z, heading);
            }
            if (window > 4 * RoadPlanParams.MAX_XZ) {
                throw new IllegalStateException("No 22.5° end near " + length + " blocks along " + heading); // can't happen: tested
            }
        }
    }

    private static void checkLength(double length) {
        if (!(length >= 0) || length > 2 * RoadPlanParams.MAX_XZ) {
            throw new IllegalArgumentException("A length of " + length + " blocks isn't possible");
        }
    }

    /** The best end so far: nearest along to the wanted length, then nearest the line, then lowest x, then lowest z. */
    private static final class Best {
        private final double wanted;
        int x;
        int z;
        double miss = Double.POSITIVE_INFINITY;
        double side = Double.POSITIVE_INFINITY;

        Best(double wanted) {
            this.wanted = wanted;
        }

        void offer(Frame frame, int dx, int dz, double maxSide, double minAlong) {
            Vec2 offset = new Vec2(dx, dz);
            double s = StrictMath.abs(frame.side(offset));
            double along = frame.along(offset);
            if (s > maxSide || along < minAlong) {
                return;
            }
            double m = StrictMath.abs(along - this.wanted);
            if (m < this.miss || (m == this.miss && (s < this.side || (s == this.side && (dx < this.x || (dx == this.x && dz < this.z)))))) {
                this.miss = m;
                this.side = s;
                this.x = dx;
                this.z = dz;
            }
        }

        boolean found() {
            return this.miss != Double.POSITIVE_INFINITY;
        }
    }

    /** The preview's handles for a road: one at each end. */
    public static List<Handle> handles(RoadPlanParams params) {
        List<Handle> handles = new ArrayList<>();
        List<RoadPlanParams.End> ends = params.ends();
        for (int i = 0; i < ends.size(); i++) {
            RoadPlanParams.End end = ends.get(i);
            handles.add(new Handle(end.x(), end.y(), end.z(), i == 0 ? Handle.Kind.START : Handle.Kind.END));
        }
        return handles;
    }

    private static Outcome notYet(String feature) {
        return refused(Protocol.NOT_AVAILABLE_YET, Map.of("feature", feature));
    }

    private static Outcome refused(String code, Map<String, String> args) {
        return new Outcome(PlanResult.refused(code, args), Optional.empty());
    }
}
