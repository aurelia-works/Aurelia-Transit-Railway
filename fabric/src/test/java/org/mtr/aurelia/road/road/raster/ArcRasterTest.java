/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.mtr.MtrRails;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.Section;
import org.mtr.aurelia.road.road.solve.LaneRail;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/**
 * G-6+'s acceptance: 1,000 random City street roads (200 of them two pieces joined at a node), anywhere in the world,
 * rasterised and compared column by column with an independent oracle. The oracle knows nothing of the rasteriser's
 * lines and arcs: it samples each lane's MTR rail every half block ({@code getPosition}), carries it on along its end
 * tangents (the square ends), and classifies every column from its signed distances to those polylines with the
 * profile's layout. Columns within 0.01 block of a band edge, or 0.05 of a dash edge, are left out, as the sampling
 * can't decide them: measured along half-block chords, a point 3 blocks beside a curving lane is up to about 0.03 off.
 * From G-4+, 300 random roads (60 of two pieces) of each other layout too: residential 15 and 17, alley 7. From R-5 a
 * piece's end lines bound only the columns nearer their own node, in the oracle as in the rasteriser.
 */
@Tag("slow")
@EnabledIfEnvironmentVariable(named = "ATR_SLOW_ROAD_TESTS", matches = ".+")
class ArcRasterTest {
    private static final double EDGE = 0.01;
    private static final double DASH_EDGE = 0.05;
    private static final double SAMPLE = 0.5;

    @Test
    void aThousandRandomRoadsHaveNoGapsOrDoubleClaims() {
        Stats stats = randomRoads(RoadProfiles.CITY_17, 1_000, 200, 20261006L);
        assertTrue(stats.compared > 1_000_000, "enough columns compared: " + stats.compared);
    }

    @Test
    void randomRoadsOfTheOtherLayoutsHaveNoGapsOrDoubleClaims() {
        for (RoadProfile profile : RoadProfiles.all()) {
            if (profile != RoadProfiles.CITY_17) {
                Stats stats = randomRoads(profile, 300, 60, 20261007L);
                assertTrue(stats.compared > 300 * 200, profile.id() + ": enough columns compared: " + stats.compared);
            }
        }
    }

    /** {@code count} random roads of {@code profile}, {@code twoPieces} of them of two pieces, checked against the oracle. */
    private static Stats randomRoads(RoadProfile profile, int count, int twoPieces, long seed) {
        Random random = new Random(seed);
        Stats stats = new Stats();
        int roads = 0;
        int twoPiece = 0;
        int[] byStartHeading = new int[16];
        while (roads < count) {
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            Heading from = Heading.of(random.nextInt(16));
            Heading to = Heading.of(random.nextInt(16));
            PieceSolution first = solve(profile, x, z, from, x + random.nextInt(241) - 120, z + random.nextInt(241) - 120, to);
            if (!(first instanceof PieceSolution.Solved one)) {
                continue;
            }
            List<PieceSolution.Solved> solved = new ArrayList<>(List.of(one));
            for (int attempt = 0; twoPiece < twoPieces && solved.size() == 1 && attempt < 40; attempt++) {
                End joint = one.end();
                PieceSolution second = solve(profile, joint.x(), joint.z(), joint.heading(), joint.x() + random.nextInt(241) - 120,
                        joint.z() + random.nextInt(241) - 120, Heading.of(random.nextInt(16)));
                if (second instanceof PieceSolution.Solved two) {
                    solved.add(two);
                    twoPiece++;
                }
            }
            List<RoadPiece> pieces = new ArrayList<>();
            double phase = 0;
            for (int i = 0; i < solved.size(); i++) {
                RoadPiece piece = RoadPiece.solved(profile, solved.get(i), i == solved.size() - 1, phase);
                pieces.add(piece);
                phase = piece.phaseAfter();
            }
            check(profile, solved, pieces, stats);
            roads++;
            byStartHeading[from.index()]++;
        }
        System.out.println(String.format(Locale.ROOT, "G-6+: %,d random %s roads (%d of two pieces), start headings %s:%n"
                + "  %,d columns placed, %,d columns compared with the oracle, %,d left out near an edge, %d mismatches%s",
                count, profile.id(), twoPiece, java.util.Arrays.toString(byStartHeading), stats.placed, stats.compared, stats.skipped,
                stats.mismatches.size(), stats.mismatches.isEmpty() ? "" : "; first: " + stats.mismatches.subList(0, StrictMath.min(5, stats.mismatches.size()))));
        assertEquals(twoPieces, twoPiece, profile.id());
        assertTrue(stats.mismatches.isEmpty(), stats.mismatches.subList(0, StrictMath.min(10, stats.mismatches.size())).toString());
        for (int headingCount : byStartHeading) {
            assertTrue(headingCount > count / 50, profile.id() + ": every start heading is covered");
        }
        return stats;
    }

    static PieceSolution solve(int x1, int z1, Heading from, int x2, int z2, Heading to) {
        return solve(RoadProfiles.CITY_17, x1, z1, from, x2, z2, to);
    }

    static PieceSolution solve(RoadProfile profile, int x1, int z1, Heading from, int x2, int z2, Heading to) {
        return PieceSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, End.at(x1, 64, z1, from),
                End.at(x2, 64, z2, to));
    }

    private static final class Stats {
        long placed;
        long compared;
        long skipped;
        final List<String> mismatches = new ArrayList<>();
    }

    /** One road: rasterise every piece (a column claimed twice fails), then compare every column near it with the oracle. */
    private static void check(RoadProfile profile, List<PieceSolution.Solved> solved, List<RoadPiece> pieces, Stats stats) {
        Map<BlockXZ, Column> plan = new HashMap<>();
        for (RoadPiece piece : pieces) {
            Map<BlockXZ, Column> columns;
            try {
                columns = Rasteriser.columns(piece);
            } catch (IllegalStateException e) {
                throw new AssertionError(e.getMessage() + " in " + describe(solved), e);
            }
            for (Map.Entry<BlockXZ, Column> entry : columns.entrySet()) {
                Column previous = plan.put(entry.getKey(), entry.getValue());
                assertTrue(previous == null, "column " + entry.getKey() + " claimed twice by " + describe(solved));
            }
        }
        stats.placed += plan.size();
        List<OraclePiece> oracle = new ArrayList<>();
        for (int i = 0; i < solved.size(); i++) {
            oracle.add(new OraclePiece(profile, solved.get(i), i == solved.size() - 1, pieces.get(i).dashPhase()));
        }
        LongOpenHashSet candidates = new LongOpenHashSet();
        plan.keySet().forEach(block -> candidates.add(key(block.x(), block.z())));
        for (OraclePiece piece : oracle) {
            piece.addNearbyColumns(candidates);
        }
        for (long key : candidates) {
            int x = (int) (key >> 32);
            int z = (int) key;
            Verdict expected = Verdict.OUTSIDE;
            boolean ambiguous = false;
            for (OraclePiece piece : oracle) {
                Verdict verdict = piece.classify(x, z);
                if (verdict == Verdict.AMBIGUOUS) {
                    ambiguous = true;
                } else if (verdict != Verdict.OUTSIDE) {
                    expected = verdict;
                }
            }
            if (ambiguous) {
                stats.skipped++;
                continue;
            }
            stats.compared++;
            Column actual = plan.get(new BlockXZ(x, z));
            String got = actual == null ? "outside" : actual.band() + (actual.painted() ? " painted" : "");
            if (!got.equals(expected.text) && stats.mismatches.size() < 50) {
                stats.mismatches.add("(" + x + ", " + z + ") is " + got + ", oracle " + expected.text + oracle.get(0).lastLine + " in " + describe(solved));
            }
        }
    }

    static String describe(List<PieceSolution.Solved> solved) {
        StringBuilder text = new StringBuilder();
        for (PieceSolution.Solved piece : solved) {
            text.append(String.format(Locale.ROOT, "[%s (%d, %d) -> %s (%d, %d)]", piece.start().heading(), piece.start().x(), piece.start().z(),
                    piece.end().heading(), piece.end().x(), piece.end().z()));
        }
        return text.toString();
    }

    static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** The oracle's answer for a column. */
    private record Verdict(String text) {
        static final Verdict OUTSIDE = new Verdict("outside");
        static final Verdict AMBIGUOUS = new Verdict("ambiguous");
    }

    /**
     * One piece as the oracle sees it: its square ends, and each lane as a polyline sampled from MTR's rail; from the
     * profile only its outer sections (from the lanes' edge outwards) and whether its centre column is dashed.
     */
    private static final class OraclePiece {
        private final PieceSolution.Solved solved;
        private final boolean closedEnd;
        private final double phase;
        private final List<Polyline> lanes = new ArrayList<>();
        private final List<Section> outer;
        private final boolean dashed;
        /** How far the road reaches beyond a lane's centre: half a lane plus the outer sections. */
        private final double reach;
        String lastLine = "";

        OraclePiece(RoadProfile profile, PieceSolution.Solved solved, boolean closedEnd, double phase) {
            this.solved = solved;
            this.closedEnd = closedEnd;
            this.phase = phase;
            List<Section> sections = profile.sections();
            int firstLane = 0;
            while (sections.get(firstLane).band() != Band.LANE) {
                firstLane++;
            }
            this.outer = new ArrayList<>(sections.subList(0, firstLane));
            java.util.Collections.reverse(this.outer);
            this.dashed = sections.stream().anyMatch(section -> section.band() == Band.CENTRE_LINE && section.marking().isPresent());
            this.reach = 2.5 + this.outer.stream().mapToInt(Section::width).sum();
            End start = solved.start();
            for (LaneRail lane : solved.lanes()) {
                this.lanes.add(new Polyline(lane.rail(), start.x(), start.z(), start.heading().tangent(), solved.end().heading().tangent()));
            }
        }

        void addNearbyColumns(LongOpenHashSet columns) {
            int reach = (int) StrictMath.ceil(this.reach + 1);
            for (Polyline lane : this.lanes) {
                for (Vec2 p : lane.points) {
                    int cx = (int) StrictMath.floor(p.x());
                    int cz = (int) StrictMath.floor(p.z());
                    for (int dx = -reach; dx <= reach + 1; dx++) {
                        for (int dz = -reach; dz <= reach + 1; dz++) {
                            columns.add(key(cx + dx, cz + dz));
                        }
                    }
                }
            }
        }

        Verdict classify(int x, int z) {
            End start = this.solved.start();
            End end = this.solved.end();
            Vec2 from = start.heading().tangent();
            Vec2 to = end.heading().tangent();
            // R-5: each end's line bounds only the columns nearer that end's node (on a bend of more than 90° it runs back
            // across the road near the other end).
            long toStart = sq(x - start.x()) + sq(z - start.z());
            long toEnd = sq(x - end.x()) + sq(z - end.z());
            double line = toStart <= toEnd ? (double) (x - start.x()) * from.x() + (double) (z - start.z()) * from.z()
                    : -((double) (x - end.x()) * to.x() + (double) (z - end.z()) * to.z());
            if (StrictMath.abs(line) < EDGE) {
                return Verdict.AMBIGUOUS;
            }
            if (line < 0) {
                return Verdict.OUTSIDE;
            }
            Vec2 p = new Vec2(x, z);
            double[] left = null; // {d, s} of the nearest lane with d >= 0
            double[] right = null;
            for (Polyline lane : this.lanes) {
                double[] ds = lane.measure(p);
                if (ds == null) {
                    continue;
                }
                if (StrictMath.abs(ds[0]) < EDGE) {
                    return Verdict.AMBIGUOUS;
                }
                if (ds[0] >= 0 ? left == null || ds[0] < left[0] : right == null || ds[0] > right[0]) {
                    if (ds[0] >= 0) {
                        left = ds;
                    } else {
                        right = ds;
                    }
                }
            }
            if (left != null && right != null) {
                double m = (left[0] + right[0]) / 2;
                if (StrictMath.abs(m + 0.5) < EDGE || StrictMath.abs(m - 0.5) < EDGE) {
                    return Verdict.AMBIGUOUS;
                }
                if (m < -0.5 || m >= 0.5) {
                    return new Verdict(Band.LANE.name());
                }
                if (!this.dashed) {
                    return new Verdict(Band.CENTRE_LINE.name());
                }
                double sm = (left[1] + right[1]) / 2 + this.phase;
                double inPeriod = sm - 6 * StrictMath.floor(sm / 6);
                if (inPeriod < DASH_EDGE || StrictMath.abs(inPeriod - 2) < DASH_EDGE || 6 - inPeriod < DASH_EDGE) {
                    return Verdict.AMBIGUOUS;
                }
                this.lastLine = String.format(Locale.ROOT, " (oracle s %.4f + %.4f)", left[1], right[1]);
                return new Verdict(Band.CENTRE_LINE.name() + (inPeriod < 2 ? " painted" : ""));
            }
            double[] outer = left != null ? left : right;
            if (outer == null) {
                return Verdict.OUTSIDE;
            }
            double e = StrictMath.abs(outer[0]) - 2.5;
            if (StrictMath.abs(e) < EDGE) {
                return Verdict.AMBIGUOUS;
            }
            if (e < 0) {
                return new Verdict(Band.LANE.name());
            }
            double edge = 0;
            for (Section section : this.outer) {
                edge += section.width();
                if (StrictMath.abs(e - edge) < EDGE) {
                    return Verdict.AMBIGUOUS;
                }
                if (e < edge) {
                    return new Verdict(section.band().name());
                }
            }
            return Verdict.OUTSIDE;
        }
    }

    /**
     * A lane sampled from its MTR rail every half block (world coordinates), carried on 3 blocks along its end tangents,
     * with a grid of 8-block cells for finding the nearest segment.
     */
    private static final class Polyline {
        private static final int CELL = 8;
        final List<Vec2> points = new ArrayList<>();
        private final double[] along; // distance along the lane at each point, 0 at the rail's start
        private final Long2ObjectOpenHashMap<List<Integer>> cells = new Long2ObjectOpenHashMap<>();

        Polyline(RailMath rail, int anchorX, int anchorZ, Vec2 startTangent, Vec2 endTangent) {
            double length = rail.getLength();
            int count = (int) StrictMath.max(4, StrictMath.ceil(length / SAMPLE));
            List<Vec2> sampled = new ArrayList<>();
            for (int i = 0; i <= count; i++) {
                Vec2 local = MtrRails.horizontal(rail.getPosition(length * i / count, false));
                sampled.add(new Vec2(anchorX + local.x(), anchorZ + local.z()));
            }
            this.points.add(sampled.get(0).minus(startTangent.times(3)));
            this.points.addAll(sampled);
            this.points.add(sampled.get(sampled.size() - 1).plus(endTangent.times(3)));
            this.along = new double[this.points.size()];
            this.along[0] = -3;
            for (int i = 1; i < this.points.size(); i++) {
                this.along[i] = this.along[i - 1] + this.points.get(i).distanceTo(this.points.get(i - 1));
            }
            for (int i = 0; i + 1 < this.points.size(); i++) {
                Vec2 a = this.points.get(i);
                Vec2 b = this.points.get(i + 1);
                for (long cell : new long[] {cell(a), cell(b)}) {
                    List<Integer> list = this.cells.computeIfAbsent(cell, c -> new ArrayList<>());
                    if (list.isEmpty() || list.get(list.size() - 1) != i) {
                        list.add(i);
                    }
                }
            }
        }

        private static long cell(Vec2 p) {
            return key((int) StrictMath.floor(p.x() / CELL), (int) StrictMath.floor(p.z() / CELL));
        }

        /** {d, s} of {@code p} against the nearest segment within a cell's reach (d positive to the right), or null if none is near. */
        double[] measure(Vec2 p) {
            int cx = (int) StrictMath.floor(p.x() / CELL);
            int cz = (int) StrictMath.floor(p.z() / CELL);
            double best = Double.POSITIVE_INFINITY;
            double[] result = null;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<Integer> segments = this.cells.get(key(cx + dx, cz + dz));
                    if (segments == null) {
                        continue;
                    }
                    for (int i : segments) {
                        Vec2 a = this.points.get(i);
                        Vec2 ab = this.points.get(i + 1).minus(a);
                        double lengthSquared = ab.dot(ab);
                        double t = StrictMath.max(0, StrictMath.min(1, p.minus(a).dot(ab) / lengthSquared));
                        Vec2 q = a.plus(ab.times(t));
                        double distance = p.distanceTo(q);
                        if (distance < best) {
                            best = distance;
                            double segmentLength = StrictMath.sqrt(lengthSquared);
                            Vec2 direction = ab.times(1 / segmentLength);
                            Vec2 v = p.minus(q);
                            double d = v.z() * direction.x() - v.x() * direction.z();
                            result = new double[] {d, this.along[i] + t * segmentLength};
                        }
                    }
                }
            }
            return result != null && best <= CELL ? result : null;
        }
    }

    private static long sq(int value) {
        return (long) value * value;
    }
}
