/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.raster.Rasteriser;
import org.mtr.aurelia.road.road.raster.RoadPiece;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.road.solve.LaneNodes;
import org.mtr.aurelia.road.road.solve.PieceSolution;
import org.mtr.aurelia.road.road.solve.RoadSolution;
import org.mtr.aurelia.road.road.solve.RoadSolver;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * R-5's acceptance, "lanes stay continuous" (owner, 2026-10-09). For each layout, 100 random roads A anywhere in the
 * world (straights, S-bends and bends, half of them climbing) and a random road B joined to A's end, then 50 joined to
 * A's start (stored the way the tools store it: running into A); and 50 tapers each way between City street and both
 * residential layouts. With Track, so the node blocks and the rails are checked too.
 * <ul>
 *   <li><b>The same layout:</b> A and B placed one after the other (B's blocks over A's) are block for block the same
 *       road planned as one road of A's pieces and B's, the dashes carried from one to the next (a road running into A's
 *       start leaves A its first row); and A's and B's rails are that road's rails.</li>
 *   <li><b>Tapers</b> (City street with Residential 15 and 17, both ways): the lane nodes A and B share are the same
 *       blocks, and their rails meet there; across the joint the two roads are the two pieces of one road, each in its
 *       own layout, except that a wider A keeps its footway on its last row.</li>
 * </ul>
 */
@Tag("slow")
@EnabledIfEnvironmentVariable(named = "ATR_SLOW_ROAD_TESTS", matches = ".+")
class JoinRasterTest {
    private static final WorldLimits WORLD = WorldLimits.OVERWORLD.withModBlocks(Set.of("mtr:rail"));
    private static final UUID A_ID = new UUID(20261009L, 1);

    @Test
    void roadsJoinedEndToEndAreOneRoad() {
        for (RoadProfile profile : RoadProfiles.all()) {
            Random random = new Random(20261009L + profile.id().hashCode());
            int forwards = 0;
            int backwards = 0;
            int split = 0;
            long blocks = 0;
            for (int attempt = 0; forwards + backwards < 150; attempt++) {
                assertTrue(attempt < 40_000, profile.id() + ": too few roads joined");
                RoadPlanParams a = road(random, profile);
                boolean back = forwards >= 100;
                Optional<RoadPlanParams> b = joined(random, profile, a, back);
                if (b.isEmpty()) {
                    continue;
                }
                Map<UUID, RoadPlanParams> placed = Map.of(A_ID, a);
                PlanResult planA = RoadPlanner.plan(a, WORLD);
                PlanResult planB = RoadPlanner.plan(b.get(), WORLD, placed);
                assertTrue(planB.isPlanned(), () -> "B refused " + planB.code() + planB.args() + ": " + b.get());
                List<PieceSolution.Solved> piecesA = pieces(a);
                List<PieceSolution.Solved> piecesB = pieces(b.get());
                split += piecesA.size() + piecesB.size() - 2;
                // One road: A's pieces then B's, or B's then A's (B runs into A's start), with the first road's dashes.
                List<PieceSolution.Solved> whole = new ArrayList<>(back ? piecesB : piecesA);
                whole.addAll(back ? piecesA : piecesB);
                RoadPlanParams first = back ? b.get() : a;
                double phase = RoadPlanner.rasterPieces(profile, back ? piecesB : piecesA, first.dashes()).get(0).dashPhase();
                RoadPlanParams wholeParams = first.withDashes(new RoadPlanParams.Dashes(phase, RoadPlanParams.Dashes.Anchor.START));
                PlanResult expected = RoadPlanner.build(wholeParams, profile, BuiltInPalettes.vanilla(), whole, WORLD, false, new ArrayList<>()).result();
                assertTrue(expected.isPlanned());
                String where = profile.id() + (back ? " into the start of " : " on from ") + a.ends() + " by " + b.get().ends();
                assertSame(blocks(expected.plan().orElseThrow()), union(planA.plan().orElseThrow(), planB.plan().orElseThrow()), where);
                assertEquals(new HashSet<>(expected.sideEffects()), rails(planA, planB), where + ": the rails");
                blocks += expected.plan().orElseThrow().blockCount();
                if (back) {
                    backwards++;
                } else {
                    forwards++;
                }
            }
            System.out.printf(Locale.ROOT, "R-5: %s, %d roads joined at an end and %d at a start (%d pieces split off), %,d blocks: each pair is "
                    + "one road%n", profile.id(), forwards, backwards, split, blocks);
        }
    }

    @Test
    void tapersKeepTheirLanesAndTrack() {
        List<RoadProfile[]> pairs = List.of(
                new RoadProfile[] {RoadProfiles.CITY_17, RoadProfiles.RESIDENTIAL_15},
                new RoadProfile[] {RoadProfiles.RESIDENTIAL_15, RoadProfiles.CITY_17},
                new RoadProfile[] {RoadProfiles.CITY_17, RoadProfiles.RESIDENTIAL_17},
                new RoadProfile[] {RoadProfiles.RESIDENTIAL_17, RoadProfiles.CITY_17},
                new RoadProfile[] {RoadProfiles.RESIDENTIAL_15, RoadProfiles.RESIDENTIAL_17});
        for (RoadProfile[] pair : pairs) {
            Random random = new Random(7L + pair[0].id().hashCode() * 31L + pair[1].id().hashCode());
            int joined = 0;
            int keptFootway = 0;
            for (int attempt = 0; joined < 50; attempt++) {
                assertTrue(attempt < 20_000, "too few tapers");
                RoadPlanParams a = road(random, pair[0]);
                Optional<RoadPlanParams> b = joined(random, pair[1], a, false);
                if (b.isEmpty()) {
                    continue;
                }
                assertEquals(RoadPlanParams.Dashes.FROM_START, b.get().dashes(), "a taper starts its dashes afresh");
                PlanResult planA = RoadPlanner.plan(a, WORLD);
                PlanResult planB = RoadPlanner.plan(b.get(), WORLD, Map.of(A_ID, a));
                assertTrue(planB.isPlanned(), () -> "B refused " + planB.code() + planB.args());
                List<PieceSolution.Solved> piecesA = pieces(a);
                List<PieceSolution.Solved> piecesB = pieces(b.get());
                // The lanes: A's last nodes are B's first, and the rails meet there.
                List<LaneNode> nodesA = LaneNodes.of(piecesA);
                List<LaneNode> nodesB = LaneNodes.of(piecesB);
                int perLaneA = piecesA.size() + 1;
                int perLaneB = piecesB.size() + 1;
                Set<P> railEndsA = new HashSet<>();
                Set<P> railStartsB = new HashSet<>();
                planA.sideEffects().forEach(e -> railEndsA.add(key(((SideEffect.TrackRail) e).bx(), ((SideEffect.TrackRail) e).by(), ((SideEffect.TrackRail) e).bz())));
                planB.sideEffects().forEach(e -> railStartsB.add(key(((SideEffect.TrackRail) e).ax(), ((SideEffect.TrackRail) e).ay(), ((SideEffect.TrackRail) e).az())));
                for (int lane = 0; lane < pair[0].lanes().size(); lane++) {
                    LaneNode lastA = nodesA.get(lane * perLaneA + perLaneA - 1);
                    LaneNode firstB = nodesB.get(lane * perLaneB);
                    assertEquals(List.of(lastA.x(), lastA.y(), lastA.z(), lastA.mtrFacing()), List.of(firstB.x(), firstB.y(), firstB.z(), firstB.mtrFacing()));
                    assertTrue(railEndsA.contains(key(lastA.x(), lastA.nodeY(), lastA.z())) && railStartsB.contains(key(firstB.x(), firstB.nodeY(), firstB.z())),
                            "lane " + (lane + 1) + "'s track meets at the joint");
                }
                // Across the joint: A's pieces then B's, each in its own layout.
                List<RoadPiece> raster = new ArrayList<>();
                List<RoadPiece> rasterA = RoadPlanner.rasterPieces(pair[0], piecesA, a.dashes());
                for (int i = 0; i < rasterA.size(); i++) {
                    RoadPiece piece = rasterA.get(i);
                    raster.add(new RoadPiece(piece.profile(), piece.anchor(), piece.lanes(), piece.y(), false, piece.dashPhase(), piece.ends(), piece.rails()));
                }
                raster.addAll(RoadPlanner.rasterPieces(pair[1], piecesB, b.get().dashes()));
                Map<P, String> expected = blocks(Rasteriser.plan(raster, BuiltInPalettes.vanilla(), WORLD.minY(), WORLD.maxY()));
                Map<P, String> union = union(planA.plan().orElseThrow(), planB.plan().orElseThrow());
                Map<P, String> fromA = blocks(planA.plan().orElseThrow());
                Set<P> nodeBlocks = new HashSet<>();
                Set<P> nodeColumns = new HashSet<>();
                for (LaneNode node : nodesB) {
                    nodeBlocks.add(key(node.x(), node.nodeY(), node.z()));
                    nodeColumns.add(key(node.x(), 0, node.z()));
                }
                for (LaneNode node : nodesA) {
                    nodeBlocks.add(key(node.x(), node.nodeY(), node.z()));
                    nodeColumns.add(key(node.x(), 0, node.z()));
                }
                Set<P> extraColumns = new HashSet<>();
                for (Map.Entry<P, String> block : union.entrySet()) {
                    String want = expected.get(block.getKey());
                    if (nodeBlocks.contains(block.getKey())) {
                        continue; // the MTR nodes of the Track aid, which the rasteriser alone doesn't plan
                    }
                    if (want == null && nodeColumns.contains(block.getKey().column())) {
                        continue; // R-6's road under a lane node past a 22.5° end, which the rasteriser alone doesn't plan either
                    }
                    if (want == null) {
                        assertEquals(fromA.get(block.getKey()), block.getValue(), "a block beyond both pieces is A's own: " + block.getKey());
                        extraColumns.add(block.getKey().column());
                    } else {
                        assertEquals(want, block.getValue(), pair[0].id() + " -> " + pair[1].id() + " at " + block.getKey());
                    }
                }
                for (P block : expected.keySet()) {
                    assertTrue(union.containsKey(block), "no gap at " + block);
                }
                // What A keeps beyond B on its last row is footway (a wider A), never a lane or a kerb.
                for (Map.Entry<P, String> block : union.entrySet()) {
                    if (extraColumns.contains(block.getKey().column()) && !block.getValue().startsWith("minecraft:air")
                            && !block.getValue().equals("minecraft:stone")) {
                        assertTrue(block.getValue().startsWith("minecraft:smooth_stone"), "A keeps " + block.getValue() + " beyond B");
                    }
                }
                keptFootway += extraColumns.isEmpty() ? 0 : 1;
                joined++;
            }
            System.out.printf(Locale.ROOT, "R-5: %s -> %s, 50 tapers: lanes and track continuous; %d kept a footway column on their last row%n",
                    pair[0].id(), pair[1].id(), keptFootway);
        }
    }

    /** A random road of {@code profile} the solver accepts: a straight, an S-bend or anything, half of them climbing. */
    static RoadPlanParams road(Random random, RoadProfile profile) {
        while (true) {
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            int y = 40 + random.nextInt(160);
            End start = End.at(x, y, z, Heading.of(random.nextInt(16)));
            Optional<RoadPlanParams> params = curve(random, profile, start, Optional.empty());
            if (params.isPresent()) {
                return params.get();
            }
        }
    }

    /**
     * A random road of {@code profile} joined to {@code a}'s end ({@code back}: to its start, stored running into it),
     * with the dashes the tools give it, or empty if the solver refuses the one tried.
     */
    static Optional<RoadPlanParams> joined(Random random, RoadProfile profile, RoadPlanParams a, boolean back) {
        RoadJoins.Port port = RoadJoins.ports(a).orElseThrow().get(back ? 0 : 1);
        End start = new End(port.x(), port.y(), port.z(), port.outgoing(), Optional.of(A_ID), OptionalInt.of(port.index()));
        Optional<RoadPlanParams> b = curve(random, profile, start, Optional.of(port));
        if (b.isEmpty()) {
            return Optional.empty();
        }
        RoadPlanParams stored = back ? RoadJoins.turned(b.get()) : b.get();
        return Optional.of(stored.withDashes(RoadJoins.dashes(stored, id -> id.equals(A_ID) ? RoadJoins.ports(a) : Optional.empty())));
    }

    private static Optional<RoadPlanParams> curve(Random random, RoadProfile profile, End start, Optional<RoadJoins.Port> port) {
        Heading from = start.heading();
        int rise = random.nextBoolean() ? 0 : (1 + random.nextInt(3)) * (random.nextBoolean() ? 1 : -1);
        int y = start.y() + rise;
        End end;
        switch (random.nextInt(3)) {
            case 0 -> {
                End along = RoadPlanner.snapEnd(End.at(start.x(), start.y(), start.z(), from), 30 + random.nextInt(90));
                end = End.at(along.x(), y, along.z(), from);
            }
            case 1 -> {
                Vec2 p = from.tangent().times(60 + random.nextInt(80)).plus(from.normal().times(random.nextInt(17) - 8));
                end = End.at(start.x() + (int) StrictMath.rint(p.x()), y, start.z() + (int) StrictMath.rint(p.z()), from);
            }
            default -> {
                Vec2 p = from.tangent().times(30 + random.nextInt(90)).plus(from.normal().times(random.nextInt(161) - 80));
                end = End.at(start.x() + (int) StrictMath.rint(p.x()), y, start.z() + (int) StrictMath.rint(p.z()), Heading.of(random.nextInt(16)));
            }
        }
        RoadPlanParams params = RoadPlanParams.curve(profile.id(), start, end, RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.TRACK);
        RoadSolution solution = RoadSolver.solve(profile, params.preset(), params.vertical(), start, end);
        if (!(solution instanceof RoadSolution.Solved)) {
            return Optional.empty();
        }
        // A road A must plan alone; a joined road B is planned with A by the caller.
        return port.isPresent() || RoadPlanner.plan(params, WORLD).isPlanned() ? Optional.of(params) : Optional.empty();
    }

    private static List<PieceSolution.Solved> pieces(RoadPlanParams params) {
        RoadProfile profile = RoadProfiles.byId(params.profileId()).orElseThrow();
        return ((RoadSolution.Solved) RoadSolver.solve(profile, params.preset(), params.vertical(), params.ends().get(0), params.ends().get(1))).pieces();
    }

    /** Fails with the blocks that differ (at most 12), if any do. */
    private static void assertSame(Map<P, String> expected, Map<P, String> actual, String where) {
        List<String> differences = new ArrayList<>();
        Set<P> all = new HashSet<>(expected.keySet());
        all.addAll(actual.keySet());
        for (P p : all) {
            String want = expected.get(p);
            String got = actual.get(p);
            if (want == null ? got != null : !want.equals(got)) {
                differences.add(p + ": expected " + want + ", got " + got);
            }
        }
        if (!differences.isEmpty()) {
            differences.sort(null);
            throw new AssertionError(where + ": " + differences.size() + " blocks differ, such as "
                    + String.join("; ", differences.subList(0, StrictMath.min(12, differences.size()))));
        }
    }

    /** A block position. */
    private record P(int x, int y, int z) {
        P column() {
            return new P(this.x, 0, this.z);
        }
    }

    private static Map<P, String> blocks(BlockPlan plan) {
        Map<P, String> blocks = new HashMap<>();
        for (BlockPlan.Block block : plan.blocks()) {
            blocks.put(key(block.x(), block.y(), block.z()), block.state());
        }
        return blocks;
    }

    /** {@code a}'s blocks with {@code b}'s placed over them. */
    private static Map<P, String> union(BlockPlan a, BlockPlan b) {
        Map<P, String> blocks = blocks(a);
        blocks.putAll(blocks(b));
        return blocks;
    }

    private static Set<SideEffect> rails(PlanResult a, PlanResult b) {
        Set<SideEffect> rails = new HashSet<>(a.sideEffects());
        rails.addAll(b.sideEffects());
        return rails;
    }

    private static P key(int x, int y, int z) {
        return new P(x, y, z);
    }
}
