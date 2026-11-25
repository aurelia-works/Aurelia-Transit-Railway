/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.VerticalProfile;
import org.mtr.aurelia.road.geom.mtr.Vector;
import org.mtr.aurelia.road.geom.mtr.Angle;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.Position;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Grade;
import org.mtr.aurelia.road.road.Lane;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfile;

/**
 * Solves one road piece lane by lane (spec roads-v1 §5; ADR-0011; task G-5): each lane is its own MTR rail between
 * its own nodes, and the piece is valid only if every lane is (§5). The rails come only from the RailMath port.
 *
 * <p><b>MTR's heading convention</b> (spike S5, finding 1): every rail is built as MTR's own {@code Rail.getAngles()}
 * builds it, with the end angle pointing back toward the start. MTR's S-curves only join up that way.
 *
 * <p><b>A rail counts only if</b> MTR calls it valid, it starts and ends on its nodes' centres (within {@link #CLOSE}),
 * leaves the start along the start heading and arrives along the end heading. MTR quietly flips a heading when an end
 * lies behind the other, which would send the road the wrong way.
 *
 * <p><b>Rails are built relative to the start node</b> (its block is (0, 0) in the rails' coordinates; heights stay
 * absolute), so a piece solves the same wherever it is. Add the start node to place a rail in the world. The lanes'
 * nodes ({@link LaneRail#start()}, {@link LaneRail#end()}) are world blocks.
 *
 * <p>From G-8, each lane must also be one MTR's own connector would join the same way from its nodes
 * ({@link #NODES_JOIN_OTHER_WAY}), so hidden track laid by a player follows it exactly.
 *
 * <p>From G-7, a piece that climbs is checked last for its grades ({@link #GRADE_TOO_STEEP}) and for neighbouring lanes
 * at different heights ({@link #CROSS_LANE_STEP}); a TWO_RADII rail MTR itself would draw with NaN heights (G-3) is
 * {@link #NO_MTR_PATH}.
 *
 * <p>The checks run in a fixed order and the first failure is the answer (roads-v1 §12). Pure and deterministic, so
 * the client's preview and the server agree.
 */
public final class PieceSolver {
    /** §4.2 finds no rail between the centre line's ends (including an end behind the start). */
    public static final String NO_MTR_PATH = "NO_MTR_PATH";
    /** Same heading at both ends, with the end level with the start. */
    public static final String PARALLEL_BESIDE = "PARALLEL_BESIDE";
    /** The end heading is the start heading turned round. */
    public static final String REVERSE_HEADING = "REVERSE_HEADING";
    /** One lane has no rail although the centre line does; args {@code n} (1 is the leftmost lane). */
    public static final String LANE_SOLVE_FAILED = "LANE_SOLVE_FAILED";
    /** An S-bend turning more than 22.5°; args {@code phi} (degrees) and {@code minLength} (blocks). */
    public static final String S_CURVE_TOO_SHARP = "S_CURVE_TOO_SHARP";
    /** A lane's tightest arc is under the preset minimum; args {@code r}, {@code group}, {@code preset}, {@code min}. */
    public static final String RADIUS_TOO_SMALL = "RADIUS_TOO_SMALL";
    /** The centre line's tightest arc is no wider than half the road. */
    public static final String INNER_EDGE_DEGENERATE = "INNER_EDGE_DEGENERATE";
    /** Neighbouring lane centres closer than allowed; args {@code w} (blocks). */
    public static final String LANE_SPACING = "LANE_SPACING";
    /**
     * MTR's own connector ({@code Rail.getAngles}) would join lane n's nodes the other way round, so hidden track
     * couldn't follow the lane (owner, 2026-10-06: task G-8); args {@code n}.
     */
    public static final String NODES_JOIN_OTHER_WAY = "NODES_JOIN_OTHER_WAY";
    /**
     * A lane's steepest grade is over the preset's limit (G-7); args {@code grade} and {@code max} (percent) and
     * {@code minLength} (the centre line's length that would bring the steepest lane within the limit, in blocks).
     */
    public static final String GRADE_TOO_STEEP = "GRADE_TOO_STEEP";
    /** Neighbouring lanes more than {@link #MAX_CROSS_LANE_STEP} apart in height (G-7); args {@code dy} (blocks). */
    public static final String CROSS_LANE_STEP = "CROSS_LANE_STEP";
    /** How far apart in height neighbouring lanes may be at any cross-section (roads-v1 §5.3). */
    public static final double MAX_CROSS_LANE_STEP = 0.25;

    /** How far, in blocks, a rail may start or end from its node's centre. MTR's own S-curves join within 4.4e-5 (S5). */
    static final double CLOSE = 1e-3;
    /** MTR's {@code ACCEPT_THRESHOLD}: closer than this counts as level, in line or parallel. */
    static final double ACCEPT = 1e-4;
    /** The sharpest S-bend: each of its two arcs turns at most this much (roads-v1 §5.2). */
    static final double MAX_S_CURVE_DEGREES = 22.5;
    /**
     * How much closer than the profile's spacing neighbouring lane centres may get (owner, 2026-10-06): City street's
     * lanes may come to 5.2 blocks, so every S-bend the 22.5° rule allows passes on every heading (the tightest, at 45°,
     * squeezes 5.66 to 5.23). Later profiles confirm their own limit when they arrive.
     */
    static final double SQUEEZE = 0.8;
    /** The longest step between the points where lane spacing is measured, in blocks; at most {@link #MAX_SAMPLES} points. */
    private static final double SAMPLE_STEP = 0.25;
    private static final int MAX_SAMPLES = 2_000;
    /** The start node, where the rails' coordinates have their origin. */
    private static final BlockStep ORIGIN = new BlockStep(0, 0);

    private PieceSolver() {
    }

    /**
     * Solves the piece of {@code profile} from {@code start} to {@code end} (centre-line nodes; each end's heading is the
     * way the road runs there) under {@code preset}'s limits, with {@code vertical}'s shape for the rails' heights.
     */
    public static PieceSolution solve(RoadProfile profile, Presets.Preset preset, RoadPlanParams.Vertical vertical, RoadPlanParams.End start,
            RoadPlanParams.End end) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(vertical, "vertical");
        if (!profile.referenceOnBlockCentre()) {
            throw new IllegalArgumentException(profile.id() + ": even-width profiles (centre line on a block edge) aren't supported (ADR-0041)");
        }
        Heading from = start.heading();
        Heading to = end.heading();
        Rail.Shape shape = vertical.shape() == RoadPlanParams.Vertical.Shape.TWO_RADII ? Rail.Shape.TWO_RADII : Rail.Shape.QUADRATIC;
        double radius = vertical.radius().orElse(0);
        BlockStep span = new BlockStep(end.x() - start.x(), end.z() - start.z());

        // 1-3: what the ends alone decide.
        if (span.dx() == 0 && span.dz() == 0) {
            return refused(RoadPlanner.TOO_SHORT);
        }
        if (to == from.opposite()) {
            return refused(REVERSE_HEADING);
        }
        if (to == from) {
            Frame frame = new Frame(Vec2.ZERO, from);
            double along = frame.along(span.toVec2());
            double side = StrictMath.abs(frame.side(span.toVec2()));
            if (StrictMath.abs(along) <= ACCEPT) {
                return refused(PARALLEL_BESIDE);
            }
            if (along > 0 && side > ACCEPT) {
                double phi = StrictMath.toDegrees(2 * StrictMath.atan(side / along));
                if (phi > MAX_S_CURVE_DEGREES + 1e-9) {
                    double minLength = StrictMath.ceil(side / StrictMath.tan(StrictMath.toRadians(MAX_S_CURVE_DEGREES / 2)) - 1e-9);
                    // One decimal, unless that would read as the limit itself (22.5).
                    String angle = String.format(Locale.ROOT, "%.1f", phi);
                    if (Double.parseDouble(angle) <= MAX_S_CURVE_DEGREES) {
                        angle = String.format(Locale.ROOT, "%.3f", phi);
                    }
                    return refused(S_CURVE_TOO_SHARP, "phi", angle,
                            "minLength", String.format(Locale.ROOT, "%d", (long) minLength));
                }
            }
        }

        // 4-5: the centre line.
        RailMath reference = rail(ORIGIN, start.y(), span, end.y(), from, to, shape, radius);
        if (!usable(reference, ORIGIN, span, from, to)) {
            return refused(NO_MTR_PATH);
        }
        if (reference.getLength() < RoadPlanner.MIN_LENGTH) {
            return refused(RoadPlanner.TOO_SHORT);
        }

        // 6: each lane between its own nodes.
        List<LaneRail> lanes = new ArrayList<>();
        for (Lane lane : profile.lanes()) {
            BlockStep laneStart = LaneOffsets.of(from, lane.offset());
            BlockStep laneEnd = plus(span, LaneOffsets.of(to, lane.offset()));
            RailMath laneRail = rail(laneStart, start.y(), laneEnd, end.y(), from, to, shape, radius);
            int number = lane.index() + 1;
            if (!usable(laneRail, laneStart, laneEnd, from, to)) {
                return refused(LANE_SOLVE_FAILED, "n", Integer.toString(number));
            }
            if (!mtrJoinsAsLaid(laneStart, laneEnd, from, to)) {
                return refused(NODES_JOIN_OTHER_WAY, "n", Integer.toString(number));
            }
            lanes.add(new LaneRail(number, lane, at(start, laneStart, start.y(), from), at(start, laneEnd, end.y(), to), laneRail));
        }

        // 7-8: radii.
        if (tightestArc(reference) <= profile.width() / 2.0) {
            return refused(INNER_EDGE_DEGENERATE);
        }
        Presets.Limits limits = Presets.limits(profile.group(), preset);
        double tightestLane = Double.POSITIVE_INFINITY;
        for (LaneRail lane : lanes) {
            tightestLane = StrictMath.min(tightestLane, tightestArc(lane.rail()));
        }
        if (tightestLane < limits.minInnerLaneRadius()) {
            return refused(RADIUS_TOO_SMALL, "r", String.format(Locale.ROOT, "%.1f", StrictMath.floor(tightestLane * 10) / 10),
                    "group", profile.group().name().toLowerCase(Locale.ROOT), "preset", preset.name().toLowerCase(Locale.ROOT),
                    "min", Integer.toString(limits.minInnerLaneRadius()));
        }

        // 9: lane spacing, neighbour by neighbour.
        for (int i = 0; i + 1 < lanes.size(); i++) {
            LaneRail left = lanes.get(i);
            LaneRail right = lanes.get(i + 1);
            double allowed = right.lane().offset() - left.lane().offset() - SQUEEZE;
            double spacing = closestApproach(left.rail(), right.rail());
            if (spacing < allowed) {
                return refused(LANE_SPACING, "w", String.format(Locale.ROOT, "%.2f", StrictMath.floor(spacing * 100) / 100));
            }
        }

        // G-7: a piece that climbs. Grades first, then neighbouring lanes' heights.
        long rise = (long) end.y() - start.y();
        if (rise != 0) {
            Grade max = limits.maxGrade();
            double steepest = 0;
            LaneRail steepestLane = lanes.get(0);
            for (LaneRail lane : lanes) {
                VerticalProfile laneProfile = VerticalProfile.of(lane.rail(), rise);
                if (!laneProfile.mtrCanDraw()) {
                    return refused(NO_MTR_PATH); // MTR itself would draw this rail with NaN heights (G-3)
                }
                double grade = laneProfile.steepestGrade();
                if (grade > steepest) {
                    steepest = grade;
                    steepestLane = lane;
                }
            }
            if (max.isExceededBy(steepest, 1)) {
                double laneMin = VerticalProfile.minLength(shape, rise, radius, max.value());
                double centreMin = laneMin * reference.getLength() / steepestLane.rail().getLength();
                String[] percents = distinct(steepest * 100, max.value() * 100);
                return refused(GRADE_TOO_STEEP, "grade", percents[0], "max", percents[1], "minLength",
                        String.format(Locale.ROOT, "%d", (long) StrictMath.ceil(centreMin - 1e-9)));
            }
            for (int i = 0; i + 1 < lanes.size(); i++) {
                double dy = heightStep(lanes.get(i).rail(), lanes.get(i + 1).rail());
                if (dy > MAX_CROSS_LANE_STEP) {
                    return refused(CROSS_LANE_STEP, "dy", distinct(dy, MAX_CROSS_LANE_STEP)[0]);
                }
            }
        }
        return new PieceSolution.Solved(start, end, reference, lanes);
    }

    /**
     * The steepest grade of any lane of the piece from {@code start} to {@code end} (R-4's HUD), worked out exactly as
     * {@link #GRADE_TOO_STEEP} is, from the same lane rails; 0 for a level piece, empty where a lane can't be built. Cheap:
     * a rail per lane, no other checks.
     */
    public static OptionalDouble steepestGrade(RoadProfile profile, RoadPlanParams.Vertical vertical, RoadPlanParams.End start,
            RoadPlanParams.End end) {
        long rise = (long) end.y() - start.y();
        if (rise == 0) {
            return OptionalDouble.of(0);
        }
        Rail.Shape shape = vertical.shape() == RoadPlanParams.Vertical.Shape.TWO_RADII ? Rail.Shape.TWO_RADII : Rail.Shape.QUADRATIC;
        double radius = vertical.radius().orElse(0);
        BlockStep span = new BlockStep(end.x() - start.x(), end.z() - start.z());
        if ((span.dx() == 0 && span.dz() == 0) || end.heading() == start.heading().opposite()) {
            return OptionalDouble.empty();
        }
        double steepest = 0;
        for (Lane lane : profile.lanes()) {
            BlockStep laneStart = LaneOffsets.of(start.heading(), lane.offset());
            BlockStep laneEnd = plus(span, LaneOffsets.of(end.heading(), lane.offset()));
            RailMath laneRail = rail(laneStart, start.y(), laneEnd, end.y(), start.heading(), end.heading(), shape, radius);
            if (!usable(laneRail, laneStart, laneEnd, start.heading(), end.heading())) {
                return OptionalDouble.empty();
            }
            double grade = VerticalProfile.of(laneRail, rise).steepestGrade();
            if (Double.isNaN(grade)) {
                return OptionalDouble.empty();
            }
            steepest = StrictMath.max(steepest, grade);
        }
        return OptionalDouble.of(steepest);
    }

    /**
     * {@code value} and {@code limit} to one decimal, or more where one decimal would make a value over the limit read as
     * the limit itself (such as 8.34% against 8.33%). The HUD shows grades the same way (R-4).
     */
    public static String[] percents(double value, double limit) {
        return distinct(value, limit);
    }

    private static String[] distinct(double value, double limit) {
        for (int decimals = 1; ; decimals++) {
            String format = "%." + decimals + "f";
            String a = String.format(Locale.ROOT, format, value);
            String b = String.format(Locale.ROOT, format, limit);
            if (decimals >= 4 || value <= limit || Double.parseDouble(a) > Double.parseDouble(b)) {
                return new String[] {a, b};
            }
        }
    }

    /**
     * How far apart in height two neighbouring lanes' rails come (G-7): every half block along {@code a}, against the
     * nearest point of {@code b} sampled every quarter block. Both rails run the same way, so the nearest point only moves
     * forward.
     */
    static double heightStep(RailMath a, RailMath b) {
        Vector[] onB = positions(b, 0.25);
        double worst = 0;
        int j = 0;
        for (Vector p : positions(a, 0.5)) {
            while (j + 1 < onB.length && horizontalDistance(p, onB[j + 1]) <= horizontalDistance(p, onB[j])) {
                j++;
            }
            worst = StrictMath.max(worst, StrictMath.abs(p.y() - onB[j].y()));
        }
        return worst;
    }

    private static Vector[] positions(RailMath rail, double step) {
        double length = rail.getLength();
        int count = (int) StrictMath.min(MAX_SAMPLES, StrictMath.ceil(length / step)) + 1;
        Vector[] positions = new Vector[count];
        for (int i = 0; i < count; i++) {
            positions[i] = rail.getPosition(length * i / (count - 1), false);
        }
        return positions;
    }

    private static double horizontalDistance(Vector p, Vector q) {
        double dx = p.x() - q.x();
        double dz = p.z() - q.z();
        return dx * dx + dz * dz;
    }

    /**
     * The centre line's rail from {@code start} to {@code end} (relative to the start node, level, quadratic), as step 4
     * builds it, or empty where MTR can't build it there. Cheap: one rail, no lanes. For the tools' HUD (R-3).
     */
    public static Optional<RailMath> centreLine(RoadPlanParams.End start, RoadPlanParams.End end) {
        BlockStep span = new BlockStep(end.x() - start.x(), end.z() - start.z());
        if ((span.dx() == 0 && span.dz() == 0) || end.heading() == start.heading().opposite()) {
            return Optional.empty();
        }
        RailMath rail = rail(ORIGIN, 0, span, 0, start.heading(), end.heading(), Rail.Shape.QUADRATIC, 0);
        return usable(rail, ORIGIN, span, start.heading(), end.heading()) ? Optional.of(rail) : Optional.empty();
    }

    /**
     * G-11: the level rail MTR lays from a lane node to another {@code span} away, leaving at {@code from} and arriving at
     * {@code to}, or empty unless MTR lays it as given: built, on its two nodes, running forwards, and joined the same way
     * whichever facing each node has (the bar every road lane meets). For junction templates' turning lanes.
     */
    public static Optional<RailMath> laneLink(BlockStep span, Heading from, Heading to) {
        if ((span.dx() == 0 && span.dz() == 0) || to == from.opposite()) {
            return Optional.empty();
        }
        RailMath rail = rail(ORIGIN, 0, span, 0, from, to, Rail.Shape.QUADRATIC, 0);
        return usable(rail, ORIGIN, span, from, to) && mtrJoinsAsLaid(ORIGIN, span, from, to) ? Optional.of(rail) : Optional.empty();
    }

    /** A rail from {@code a} to {@code b} (relative to the start node), built with MTR's heading convention. */
    private static RailMath rail(BlockStep a, int ya, BlockStep b, int yb, Heading from, Heading to, Rail.Shape shape, double radius) {
        return new RailMath(new Position(a.dx(), ya, a.dz()), MtrRails.angle(from), new Position(b.dx(), yb, b.dz()), MtrRails.angle(to.opposite()),
                shape, radius);
    }

    /** MTR built it, it starts and ends on its nodes, and it runs from {@code from} to {@code to}, not backwards. */
    static boolean usable(RailMath rail, BlockStep a, BlockStep b, Heading from, Heading to) {
        if (!MtrRails.isValid(rail)) {
            return false;
        }
        double length = rail.getLength();
        if (!(length > 0)) {
            return false;
        }
        Vec2 first = MtrRails.horizontal(rail.getPosition(0, false));
        Vec2 last = MtrRails.horizontal(rail.getPosition(length, false));
        if (first.distanceTo(a.toVec2()) > CLOSE || last.distanceTo(b.toVec2()) > CLOSE) {
            return false;
        }
        double step = StrictMath.min(0.5, length / 4);
        Vec2 leaving = MtrRails.horizontal(rail.getPosition(step, false)).minus(first);
        Vec2 arriving = last.minus(MtrRails.horizontal(rail.getPosition(length - step, false)));
        return leaving.dot(from.tangent()) > 0 && arriving.dot(to.tangent()) > 0;
    }

    /**
     * Whether MTR's connector, given this lane's two nodes, picks the lane's own angles (the start heading, and the end
     * heading turned round: MTR's convention). A node knows its heading only modulo 180°, so it must work whichever of
     * a node's two facings MTR is given. MTR faces each end towards the other node when the line between them is within
     * 90° of the node's heading; a big bend that swings that line further would be joined the other way round.
     */
    static boolean mtrJoinsAsLaid(BlockStep a, BlockStep b, Heading from, Heading to) {
        Angle start = MtrRails.angle(from);
        Angle end = MtrRails.angle(to.opposite());
        Position nodeA = new Position(a.dx(), 0, a.dz());
        Position nodeB = new Position(b.dx(), 0, b.dz());
        for (float facingA : new float[] {(float) from.degrees(), (float) from.degrees() + 180}) {
            for (float facingB : new float[] {(float) to.degrees(), (float) to.degrees() + 180}) {
                var angles = Rail.getAngles(nodeA, facingA, nodeB, facingB);
                if (angles.left() != start || angles.right() != end) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The radius of a rail's tightest arc; infinite for a straight rail. */
    public static double tightestArc(RailMath rail) {
        double tightest = Double.POSITIVE_INFINITY;
        for (double r : new double[] {rail.getHorizontalRadii().leftDouble(), rail.getHorizontalRadii().rightDouble()}) {
            if (r > 0) {
                tightest = StrictMath.min(tightest, r);
            }
        }
        return tightest;
    }

    /**
     * How close two lane rails come: from points along {@code a} (at most {@link #SAMPLE_STEP} apart) to the polyline of
     * {@code b} sampled the same way.
     */
    static double closestApproach(RailMath a, RailMath b) {
        Vec2[] pointsA = samples(a);
        Vec2[] pointsB = samples(b);
        double closest = Double.POSITIVE_INFINITY;
        for (Vec2 p : pointsA) {
            for (int j = 0; j + 1 < pointsB.length; j++) {
                closest = StrictMath.min(closest, distanceToSegment(p, pointsB[j], pointsB[j + 1]));
            }
        }
        return closest;
    }

    private static Vec2[] samples(RailMath rail) {
        double length = rail.getLength();
        int count = (int) StrictMath.min(MAX_SAMPLES, StrictMath.max(16, StrictMath.ceil(length / SAMPLE_STEP)));
        Vec2[] points = new Vec2[count + 1];
        for (int i = 0; i <= count; i++) {
            points[i] = MtrRails.horizontal(rail.getPosition(length * i / count, false));
        }
        return points;
    }

    private static double distanceToSegment(Vec2 p, Vec2 a, Vec2 b) {
        Vec2 ab = b.minus(a);
        double lengthSquared = ab.dot(ab);
        double t = lengthSquared == 0 ? 0 : StrictMath.max(0, StrictMath.min(1, p.minus(a).dot(ab) / lengthSquared));
        return p.distanceTo(a.plus(ab.times(t)));
    }

    private static BlockStep plus(BlockStep a, BlockStep b) {
        return new BlockStep(a.dx() + b.dx(), a.dz() + b.dz());
    }

    /** A lane node in the world: the piece's start node plus {@code step}. */
    private static RoadPlanParams.End at(RoadPlanParams.End start, BlockStep step, int y, Heading heading) {
        return RoadPlanParams.End.at(start.x() + step.dx(), y, start.z() + step.dz(), heading);
    }

    private static PieceSolution refused(String code, String... args) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            map.put(args[i], args[i + 1]);
        }
        return new PieceSolution.Refused(code, map);
    }
}
