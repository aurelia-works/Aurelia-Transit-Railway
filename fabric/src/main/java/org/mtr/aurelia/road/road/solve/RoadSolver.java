/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mtr.aurelia.road.geom.Frame;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.VerticalProfile;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfile;

/**
 * Solves a whole road between its two ends (task R-5): G-5's one piece ({@link PieceSolver}), or a graded bend split
 * into a straight and a bend when one piece won't do (owner, 2026-10-09).
 *
 * <p><b>Why split:</b> on a climbing line + arc piece each lane has its own share of line and arc, so at the same
 * cross-section the lanes are at different fractions of their length, and so at different heights (roads-v1 §5.3). G-7
 * allows that while neighbouring lanes stay within 0.25 block ({@link PieceSolver#CROSS_LANE_STEP}). A piece refused
 * for it, whose centre line is a line and an arc, is split where the straight part meets the curve:
 * <ul>
 *   <li><b>The split node</b> is a block centre on the straight part, at or before the point where the curve starts
 *       (on the straight's side), within {@link RoadPlanner#MTR_TOLERANCE} of the straight's line, leaving each part at
 *       least {@link RoadPlanner#MIN_LENGTH} long. The curve rarely starts exactly on a block centre (on 45° and 22.5°
 *       bends its distance along is irrational), so the nearest such block is taken; the bend's part then keeps a short
 *       straight of under a block or so, whose lanes climb almost evenly. The nearest {@value #CANDIDATES} are
 *       tried in turn.</li>
 *   <li><b>Its height</b> is a whole block (a node is a block), the rise shared between the parts so that the steeper
 *       one is as gentle as it can be; ties go to the share nearest the parts' lengths. MTR's shapes are level at every
 *       node, so the road has a level spot there.</li>
 *   <li><b>Each part is solved and checked as a piece of its own</b>, grades and cross-lane steps included.</li>
 * </ul>
 * If no split passes: {@link #GRADE_ON_MIXED_PIECE}, with the one piece's {@code dy}. A piece refused for anything
 * else, or refused for its cross-lane step but not a line and an arc (an S-bend), keeps its refusal. A road one piece
 * solves is that piece, exactly as before R-5.
 */
public final class RoadSolver {
    /**
     * A climbing bend whose lanes would be more than 0.25 block apart in height, which couldn't be split into a straight
     * and a bend that each climb evenly (roads-v1 §12; R-5); args {@code dy} (blocks), the one piece's step.
     */
    public static final String GRADE_ON_MIXED_PIECE = "GRADE_ON_MIXED_PIECE";

    /** How many split nodes are tried, nearest the start of the curve first. */
    static final int CANDIDATES = 3;

    private RoadSolver() {
    }

    /**
     * Solves the road of {@code profile} from {@code start} to {@code end} (centre-line nodes, each with the heading the
     * road runs there) under {@code preset}'s limits, with {@code vertical}'s shape.
     */
    public static RoadSolution solve(RoadProfile profile, Presets.Preset preset, RoadPlanParams.Vertical vertical, RoadPlanParams.End start,
            RoadPlanParams.End end) {
        PieceSolution one = PieceSolver.solve(profile, preset, vertical, start, end);
        if (one instanceof PieceSolution.Solved solved) {
            return new RoadSolution.Solved(List.of(solved));
        }
        PieceSolution.Refused refused = (PieceSolution.Refused) one;
        if (!refused.code().equals(PieceSolver.CROSS_LANE_STEP)) {
            return new RoadSolution.Refused(refused.code(), refused.args());
        }
        Optional<Shape> shape = shape(start, end);
        if (shape.isEmpty()) {
            return new RoadSolution.Refused(refused.code(), refused.args()); // not a line and an arc: an S-bend
        }
        Optional<List<PieceSolution.Solved>> split = split(profile, preset, vertical, start, end, shape.get());
        if (split.isPresent()) {
            return new RoadSolution.Solved(split.get());
        }
        Map<String, String> args = new LinkedHashMap<>();
        args.put("dy", refused.args().get("dy"));
        return new RoadSolution.Refused(GRADE_ON_MIXED_PIECE, args);
    }

    /** The centre line's straight part: whether it comes first, and how long it is. */
    private record Shape(boolean lineFirst, double lineLength) {
    }

    /** The shape of the centre line from {@code start} to {@code end}, when it is a line and an arc (either way round). */
    private static Optional<Shape> shape(RoadPlanParams.End start, RoadPlanParams.End end) {
        Optional<RailMath> centre = PieceSolver.centreLine(start, end);
        if (centre.isEmpty()) {
            return Optional.empty();
        }
        List<MtrRails.Piece> pieces = MtrRails.pieces(centre.get());
        if (pieces.size() != 2 || pieces.get(0).straight() == pieces.get(1).straight()) {
            return Optional.empty();
        }
        boolean lineFirst = pieces.get(0).straight();
        return Optional.of(new Shape(lineFirst, (lineFirst ? pieces.get(0) : pieces.get(1)).length()));
    }

    private static Optional<List<PieceSolution.Solved>> split(RoadProfile profile, Presets.Preset preset, RoadPlanParams.Vertical vertical,
            RoadPlanParams.End start, RoadPlanParams.End end, Shape shape) {
        Heading at = shape.lineFirst() ? start.heading() : end.heading(); // the heading at the split node: the straight's
        long rise = (long) end.y() - start.y();
        for (int[] node : candidates(start, end, shape)) {
            int x = start.x() + node[0];
            int z = start.z() + node[1];
            Optional<RailMath> first = PieceSolver.centreLine(start, RoadPlanParams.End.at(x, start.y(), z, at));
            Optional<RailMath> second = PieceSolver.centreLine(RoadPlanParams.End.at(x, start.y(), z, at), end);
            if (first.isEmpty() || second.isEmpty()) {
                continue;
            }
            for (long share : shares(vertical, first.get().getLength(), second.get().getLength(), rise)) {
                RoadPlanParams.End middle = RoadPlanParams.End.at(x, (int) (start.y() + share), z, at);
                PieceSolution a = PieceSolver.solve(profile, preset, vertical, start, middle);
                PieceSolution b = a.isSolved() ? PieceSolver.solve(profile, preset, vertical, middle, end) : a;
                if (a instanceof PieceSolution.Solved solvedA && b instanceof PieceSolution.Solved solvedB) {
                    return Optional.of(List.of(solvedA, solvedB));
                }
                String code = ((PieceSolution.Refused) (a.isSolved() ? b : a)).code();
                if (!code.equals(PieceSolver.GRADE_TOO_STEEP) && !code.equals(PieceSolver.CROSS_LANE_STEP)) {
                    break; // the split node itself doesn't work; another share of the rise won't change that
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Where the road may be split, relative to the start node: block centres on the straight part (within
     * {@link RoadPlanner#MTR_TOLERANCE} of its line), at or before the start of the curve, at least
     * {@link RoadPlanner#MIN_LENGTH} from the road's end on the straight's side; the nearest the curve first, at most
     * {@value #CANDIDATES}.
     */
    private static List<int[]> candidates(RoadPlanParams.End start, RoadPlanParams.End end, Shape shape) {
        // Measured along the straight from the end of the road it belongs to: forwards from the start, or backwards
        // from the end (an arc first).
        Heading heading = shape.lineFirst() ? start.heading() : end.heading().opposite();
        int ox = shape.lineFirst() ? 0 : end.x() - start.x();
        int oz = shape.lineFirst() ? 0 : end.z() - start.z();
        Frame frame = new Frame(new Vec2(ox, oz), heading);
        record Candidate(int dx, int dz, double along) {
        }
        List<Candidate> found = new ArrayList<>();
        int furthest = (int) StrictMath.floor(shape.lineLength() + 1e-9);
        for (int a = furthest; a >= 0 && found.size() < 4 * CANDIDATES; a--) {
            Vec2 ideal = frame.point(a, 0);
            int cx = (int) StrictMath.rint(ideal.x());
            int cz = (int) StrictMath.rint(ideal.z());
            for (int dx = cx - 1; dx <= cx + 1; dx++) {
                for (int dz = cz - 1; dz <= cz + 1; dz++) {
                    Vec2 point = new Vec2(dx, dz);
                    double along = frame.along(point);
                    if (StrictMath.abs(frame.side(point)) <= RoadPlanner.MTR_TOLERANCE && along >= RoadPlanner.MIN_LENGTH
                            && along <= shape.lineLength() + 1e-9 && found.stream().noneMatch(c -> c.dx() == point.x() && c.dz() == point.z())) {
                        found.add(new Candidate((int) point.x(), (int) point.z(), along));
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(Candidate::along).reversed().thenComparingInt(Candidate::dx).thenComparingInt(Candidate::dz));
        List<int[]> nodes = new ArrayList<>();
        for (Candidate candidate : found.subList(0, StrictMath.min(CANDIDATES, found.size()))) {
            nodes.add(new int[] {candidate.dx(), candidate.dz()});
        }
        return nodes;
    }

    /**
     * The ways to share {@code rise} between a first part {@code a} long and a second {@code b} long, as the first
     * part's rise in whole blocks: the gentlest steepest part first (measured on the centre line with the road's shape),
     * then the share nearest the lengths'.
     */
    static List<Long> shares(RoadPlanParams.Vertical vertical, double a, double b, long rise) {
        Rail.Shape shape = vertical.shape() == RoadPlanParams.Vertical.Shape.TWO_RADII ? Rail.Shape.TWO_RADII : Rail.Shape.QUADRATIC;
        double radius = vertical.radius().orElse(0);
        double proportional = rise * a / (a + b);
        List<Long> shares = new ArrayList<>();
        Map<Long, Double> steepest = new LinkedHashMap<>();
        for (long share = StrictMath.min(0, rise); share <= StrictMath.max(0, rise); share++) {
            double ga = new VerticalProfile(shape, a, share, radius).steepestGrade();
            double gb = new VerticalProfile(shape, b, rise - share, radius).steepestGrade();
            double worst = Double.isNaN(ga) || Double.isNaN(gb) ? Double.POSITIVE_INFINITY : StrictMath.max(ga, gb);
            steepest.put(share, worst);
            shares.add(share);
        }
        shares.sort(Comparator.<Long>comparingDouble(steepest::get).thenComparingDouble(s -> StrictMath.abs(s - proportional))
                .thenComparingLong(s -> s));
        return shares;
    }
}
