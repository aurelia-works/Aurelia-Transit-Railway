/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.line.LineChords;
import org.mtr.aurelia.road.road.line.LineKind;
import org.mtr.aurelia.road.road.line.MarkingCurve;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/**
 * B-2's acceptance (roadmap; ADR-0046): 1,000 random City streets (200 of them of two pieces, anywhere within a million
 * blocks of the origin, every start heading) in Stratos UK. For each, the blocks the centre line's paint touches are
 * found again here, independently (the curve sampled every {@value #STEP} block; a block is touched where the line comes
 * within the paint's reach of it), and:
 * <ul>
 *   <li>every touched block of the road (not a footway or kerb), in a dash by the whole-block rule, is a line block; no
 *       block the paint doesn't touch, or clearly out of every dash, is (a share of the stretch within {@value #CLEAR} of a
 *       half either way is left to the planner). A sliver (the paint's edge only) follows the blocks the line runs through
 *       beside it: checked where they are all clearly in a dash, or all clearly out;</li>
 *   <li>every line block's chord, where its paint can be (the block and the paint's reach around it), is within 1/64 block
 *       of the curve, plus the curve's sag over the chord (its length² / 8 × the curve's tightest radius).</li>
 * </ul>
 * Blocks the paint only grazes (by less than {@value #GRAZE} block) are left out of the first check: the planner's walk
 * may miss a touch that small.
 */
@Tag("slow")
@EnabledIfEnvironmentVariable(named = "ATR_SLOW_ROAD_TESTS", matches = ".+")
class LineSweepTest {
    private static final double STEP = 0.005;
    private static final double GRAZE = 0.01;
    /**
     * How far from a half a block's dash share must be to be checked: the planner's stretch (the paint within reach of the
     * block) and this test's (paint sampled inside it) differ a little at each end, and a near tie may go either way.
     */
    private static final double CLEAR = 0.06;
    /** How far from the sliver length a block's stretch must be to be judged as one kind or the other. */
    private static final double AMBIGUOUS = 0.05;
    private static final LineKind KIND = LineKind.WHITE;
    private static final LineMarking MARKING = LineMarking.dashed(LineMarking.Colour.WHITE, 2, 4);

    @Test
    void aThousandRandomCityStreetsHaveEveryLineBlockAndTrueChords() {
        Random random = new Random(20261015L);
        int roads = 0;
        int twoPiece = 0;
        int lineBlocks = 0;
        int touchedChecked = 0;
        double farthest = 0;
        double farthestOverLimit = Double.NEGATIVE_INFINITY;
        String worstAt = "";
        int[] byStartHeading = new int[16];
        List<String> problems = new ArrayList<>();
        List<String> unrasterised = new ArrayList<>();
        while (roads < 1_000) {
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            Heading from = Heading.of(random.nextInt(16));
            Heading to = Heading.of(random.nextInt(16));
            PieceSolution first = solve(x, z, from, x + random.nextInt(241) - 120, z + random.nextInt(241) - 120, to);
            if (!(first instanceof PieceSolution.Solved one)) {
                continue;
            }
            List<PieceSolution.Solved> solved = new ArrayList<>(List.of(one));
            for (int attempt = 0; twoPiece < 200 && attempt < 40; attempt++) {
                End joint = one.end();
                PieceSolution second = solve(joint.x(), joint.z(), joint.heading(), joint.x() + random.nextInt(241) - 120, joint.z() + random.nextInt(241) - 120,
                        Heading.of(random.nextInt(16)));
                if (second instanceof PieceSolution.Solved two) {
                    solved.add(two);
                    twoPiece++;
                    break;
                }
            }
            List<RoadPiece> pieces = new ArrayList<>();
            double phase = 0;
            for (int i = 0; i < solved.size(); i++) {
                RoadPiece piece = RoadPiece.solved(RoadProfiles.CITY_17, solved.get(i), i == solved.size() - 1, phase);
                pieces.add(piece);
                phase = piece.phaseAfter();
            }
            String road = "road " + roads + " (" + x + ", " + z + ") " + from + " to " + to + ", " + pieces.size() + " piece(s)";
            Map<BlockXZ, Column> columns = new HashMap<>();
            try {
                for (Map.Entry<BlockXZ, Rasteriser.Cell> entry : Rasteriser.planCells(pieces, org.mtr.aurelia.road.road.palette.BuiltInPalettes.stratosUk())) {
                    columns.putIfAbsent(entry.getKey(), entry.getValue().column());
                }
            } catch (IllegalStateException e) {
                // The rasteriser's own limit (G-6+'s staggered lane ends), older than B-2 and as Vanilla has it: counted, not lines'.
                unrasterised.add(describe(solved) + ": " + e.getMessage());
                continue;
            }
            Map<BlockXZ, Integer> owner = new HashMap<>();
            for (int i = 0; i < pieces.size(); i++) {
                for (BlockXZ block : Rasteriser.cells(pieces.get(i)).keySet()) {
                    owner.putIfAbsent(block, i);
                }
            }
            Map<BlockXZ, double[]> lines = new HashMap<>(); // every block a dash's paint reaches into by more than a graze: {from, to}
            Map<BlockXZ, double[]> touches = new HashMap<>(); // every block the paint touches at all
            for (int i = 0; i < pieces.size(); i++) {
                RoadPiece piece = pieces.get(i);
                MarkingCurve curve = new MarkingCurve(piece.lanes().get(0), piece.lanes().get(1), 0);
                double rMin = tightestRadius(curve);
                Map<BlockXZ, double[]> stretch = new HashMap<>();
                Map<BlockXZ, double[]> deep = new HashMap<>();
                for (int n = 0; -1.5 + n * STEP <= curve.length() + 1.5; n++) {
                    double s = -1.5 + n * STEP;
                    Vec2 p = curve.at(s);
                    BlockXZ centre = p.block();
                    for (int lx = centre.x() - 1; lx <= centre.x() + 1; lx++) {
                        for (int lz = centre.z() - 1; lz <= centre.z() + 1; lz++) {
                            // The paint reaches into the block where the line comes within its reach of the block.
                            double gap = StrictMath.hypot(StrictMath.max(0, StrictMath.abs(p.x() - lx) - 0.5), StrictMath.max(0, StrictMath.abs(p.z() - lz) - 0.5));
                            if (gap >= KIND.halfExtent()) {
                                continue;
                            }
                            BlockXZ block = piece.anchor().plus(new BlockStep(lx, lz));
                            double[] st = stretch.computeIfAbsent(block, b -> new double[] {Double.MAX_VALUE, -Double.MAX_VALUE});
                            st[0] = StrictMath.min(st[0], s);
                            st[1] = StrictMath.max(st[1], s);
                            if (gap < KIND.halfExtent() - GRAZE) {
                                deep.putIfAbsent(block, st);
                            }
                        }
                    }
                }
                for (Map.Entry<BlockXZ, double[]> entry : stretch.entrySet()) {
                    BlockXZ block = entry.getKey();
                    if (owner.getOrDefault(block, -1) != i) {
                        continue;
                    }
                    touches.put(block, entry.getValue());
                    Column column = columns.get(block);
                    boolean raised = column.band() == Band.KERB || column.band() == Band.FOOTWAY;
                    // The planner measures the stretch a little differently: only a clear share either way is checked.
                    double[] st = entry.getValue();
                    double length = st[1] - st[0];
                    if (length >= Rasteriser.SLIVER + AMBIGUOUS) {
                        // A block the line runs along is in a dash when most of its stretch is.
                        double share = share(st, piece.dashPhase());
                        if (deep.containsKey(block) && !raised && share > 0.5 + CLEAR) {
                            lines.put(block, st);
                        }
                        if (share < 0.5 - CLEAR && column.chord().isPresent()) {
                            problems.add(road + ": " + block + " is a line block outside every dash");
                        }
                    } else if (length <= Rasteriser.SLIVER - AMBIGUOUS && deep.containsKey(block) && !raised) {
                        // A sliver follows the blocks the line runs along beside it: checked where they all clearly agree.
                        boolean allIn = true;
                        boolean allOut = true;
                        int partners = 0;
                        for (Map.Entry<BlockXZ, double[]> other : stretch.entrySet()) {
                            double[] os = other.getValue();
                            // Any piece's: the line and its dashes carry on across a joint (a road's next piece starts in step).
                            if (os[1] - os[0] < Rasteriser.SLIVER + AMBIGUOUS || StrictMath.min(st[1], os[1]) - StrictMath.max(st[0], os[0]) <= 0.01) {
                                continue;
                            }
                            partners++;
                            double share = share(os, piece.dashPhase());
                            allIn &= share > 0.5 + CLEAR;
                            allOut &= share < 0.5 - CLEAR;
                        }
                        if (partners > 0 && allIn) {
                            lines.put(block, st);
                        }
                        if (partners > 0 && allOut && column.chord().isPresent()) {
                            problems.add(road + ": " + block + " is a sliver painted beside no dash");
                        }
                    }
                }
                // Every line block of this piece: its chord within 1/64 + the sag of the curve.
                for (Map.Entry<BlockXZ, Column> entry : columns.entrySet()) {
                    if (entry.getValue().chord().isEmpty() || owner.getOrDefault(entry.getKey(), -1) != i) {
                        continue;
                    }
                    BlockXZ local = new BlockXZ(entry.getKey().x() - piece.anchor().x(), entry.getKey().z() - piece.anchor().z());
                    double[] e = LineChords.ends(entry.getValue().chord().getAsInt());
                    double length = StrictMath.hypot(e[2] - e[0], e[3] - e[1]);
                    double limit = 1.0 / 64 + length * length / (8 * rMin);
                    double[] near = stretch.getOrDefault(entry.getKey(), new double[] {-1.5, curve.length() + 1.5});
                    double reach = KIND.halfExtent();
                    for (int j = 0; j <= 16; j++) {
                        double u = e[0] + j / 16.0 * (e[2] - e[0]);
                        double v = e[1] + j / 16.0 * (e[3] - e[1]);
                        if (u < -reach || u > 1 + reach || v < -reach || v > 1 + reach) {
                            continue;
                        }
                        // A chord runs across its band square, up to 1.8 blocks: search the curve that far round the block's stretch.
                        double far = distance(curve, new Vec2(local.x() - 0.5 + u, local.z() - 0.5 + v), near[0] - 2, near[1] + 2);
                        if (far > farthest) {
                            farthest = far;
                            worstAt = String.format(Locale.ROOT, "%s, piece %d, %s (local %s), chord %d, limit %.4f, tightest radius %.2f", road, i, entry.getKey(), local,
                                    entry.getValue().chord().getAsInt(), limit, rMin);
                        }
                        farthestOverLimit = StrictMath.max(farthestOverLimit, far - limit);
                        if (far > limit + 1e-9) {
                            problems.add(String.format(Locale.ROOT, "%s: the chord at %s is %.4f from the line (limit %.4f)", road, entry.getKey(), far, limit));
                        }
                    }
                }
            }
            for (Map.Entry<BlockXZ, Column> entry : columns.entrySet()) {
                boolean line = entry.getValue().chord().isPresent();
                lineBlocks += line ? 1 : 0;
                if (line && !touches.containsKey(entry.getKey())) {
                    problems.add(road + ": " + entry.getKey() + " is a line block the paint doesn't touch");
                }
            }
            for (BlockXZ block : lines.keySet()) {
                touchedChecked++;
                if (columns.get(block).chord().isEmpty()) {
                    problems.add(road + ": the paint of a dash touches " + block + ", which isn't a line block");
                }
            }
            roads++;
            byStartHeading[from.index()]++;
        }
        System.out.println(String.format(Locale.ROOT, "B-2: 1,000 random City streets (%d of two pieces): %,d line blocks; %,d blocks a dash's paint touches checked; "
                + "farthest chord %.4f from the line (at %s; the closest to its limit %.4f inside it); %d problems%s; %d roads the rasteriser itself refused%s",
                twoPiece, lineBlocks, touchedChecked, farthest, worstAt, -farthestOverLimit, problems.size(), problems.isEmpty() ? "" : "; first: " + problems.subList(0, StrictMath.min(5, problems.size())),
                unrasterised.size(), unrasterised.isEmpty() ? "" : ": " + unrasterised));
        assertEquals(200, twoPiece);
        assertTrue(touchedChecked > 20_000, "enough blocks checked: " + touchedChecked);
        for (int count : byStartHeading) {
            assertTrue(count > 20, "every start heading is covered");
        }
        assertTrue(problems.isEmpty(), problems.subList(0, StrictMath.min(10, problems.size())).toString());
    }

    /** The share of a stretch {s from, s to} the dash pattern paints. */
    private static double share(double[] stretch, double phase) {
        return Rasteriser.painted(MARKING, stretch[0] + phase, stretch[1] + phase) / (stretch[1] - stretch[0]);
    }

    private static String describe(List<PieceSolution.Solved> solved) {
        StringBuilder text = new StringBuilder();
        for (PieceSolution.Solved piece : solved) {
            text.append(text.length() == 0 ? "" : " then ").append(piece.start()).append(" to ").append(piece.end());
        }
        return text.toString();
    }

    /** The curve's tightest radius, from three points 0.5 block apart all along it (a straight one: very large). */
    private static double tightestRadius(MarkingCurve curve) {
        double tightest = 1e9;
        for (double s = 0.5; s <= curve.length() - 0.5; s += 0.25) {
            Vec2 a = curve.at(s - 0.5);
            Vec2 b = curve.at(s);
            Vec2 c = curve.at(s + 0.5);
            double twice = StrictMath.abs(b.minus(a).cross(c.minus(a)));
            if (twice > 1e-12) {
                tightest = StrictMath.min(tightest, a.distanceTo(b) * b.distanceTo(c) * c.distanceTo(a) / (2 * twice));
            }
        }
        return tightest;
    }

    /** The distance from {@code p} to the curve between {@code from} and {@code to}: steps of 0.01, then refined. */
    private static double distance(MarkingCurve curve, Vec2 p, double from, double to) {
        double bestT = from;
        double best = Double.POSITIVE_INFINITY;
        for (double t = from; t <= to; t += 0.01) {
            double d = curve.at(t).distanceTo(p);
            if (d < best) {
                best = d;
                bestT = t;
            }
        }
        double lo = bestT - 0.01;
        double hi = bestT + 0.01;
        for (int i = 0; i < 40; i++) {
            double a = lo + (hi - lo) / 3;
            double b = hi - (hi - lo) / 3;
            if (curve.at(a).distanceTo(p) <= curve.at(b).distanceTo(p)) {
                hi = b;
            } else {
                lo = a;
            }
        }
        return StrictMath.min(best, curve.at((lo + hi) / 2).distanceTo(p));
    }

    private static PieceSolution solve(int x1, int z1, Heading from, int x2, int z2, Heading to) {
        return PieceSolver.solve(RoadProfiles.CITY_17, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, End.at(x1, 64, z1, from),
                End.at(x2, 64, z2, to));
    }
}
