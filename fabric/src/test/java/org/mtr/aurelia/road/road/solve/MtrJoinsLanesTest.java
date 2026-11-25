/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.raster.LaneCurve;
import org.mtr.aurelia.road.road.raster.LanePath;

/**
 * G-8's acceptance, end to end through MTR 4.0.5: on 500 random roads (100 of 2 or 3 pieces), anywhere in the world,
 * MTR's own connector ({@code org.mtr.core.data.Rail.getAngles}, each node's facing chosen at random between its two
 * equal values) picks the angles between each pair of consecutive lane nodes, MTR's own {@code RailMath} draws the
 * rail, and every sample of it, every quarter block, must be within 0.01 block of our lane (G-6+'s {@link LanePath}).
 * From G-4+, 150 roads (30 of 2 or 3 pieces) of each other layout too: residential 15 and 17, alley 7.
 */
class MtrJoinsLanesTest {
    private static final double TOLERANCE = 0.01;

    @Test
    void mtrsOwnConnectorFollowsEveryLane() {
        connectorFollows(RoadProfiles.CITY_17, 500, 100, 20261006L);
    }

    @Test
    void mtrsOwnConnectorFollowsTheOtherLayoutsLanes() {
        for (RoadProfile profile : RoadProfiles.all()) {
            if (profile != RoadProfiles.CITY_17) {
                connectorFollows(profile, 150, 30, 20261007L);
            }
        }
    }

    /** {@code count} random roads of {@code profile}, {@code multiPieces} of them of 2 or 3 pieces. */
    private static void connectorFollows(RoadProfile profile, int count, int multiPieces, long seed) {
        Random random = new Random(seed);
        int roads = 0;
        int multi = 0;
        long rails = 0;
        long samples = 0;
        double worst = 0;
        int lanes = profile.lanes().size();
        while (roads < count) {
            int x = random.nextInt(2_000_001) - 1_000_000;
            int z = random.nextInt(2_000_001) - 1_000_000;
            List<PieceSolution.Solved> pieces = new ArrayList<>();
            End start = End.at(x, 64, z, Heading.of(random.nextInt(16)));
            int wanted = multi < multiPieces ? 2 + random.nextInt(2) : 1;
            for (int attempt = 0; pieces.size() < wanted && attempt < 200; attempt++) {
                End from = pieces.isEmpty() ? start : pieces.get(pieces.size() - 1).end();
                End to = End.at(from.x() + random.nextInt(241) - 120, 64, from.z() + random.nextInt(241) - 120, Heading.of(random.nextInt(16)));
                if (PieceSolver.solve(profile, Presets.Preset.GAME_SCALE, RoadPlanParams.Vertical.QUADRATIC, from, to)
                        instanceof PieceSolution.Solved solved) {
                    pieces.add(solved);
                }
            }
            if (pieces.size() < wanted) {
                continue;
            }
            roads++;
            multi += wanted > 1 ? 1 : 0;
            List<LaneNode> nodes = LaneNodes.of(pieces);
            int perLane = pieces.size() + 1;
            for (int lane = 0; lane < lanes; lane++) {
                for (int i = 0; i < pieces.size(); i++) {
                    LaneNode a = nodes.get(lane * perLane + i);
                    LaneNode b = nodes.get(lane * perLane + i + 1);
                    PieceSolution.Solved piece = pieces.get(i);
                    LanePath ours = LanePath.of(piece.lanes().get(lane).rail()); // relative to the piece's start node
                    var mtr = connect(a, b, random);
                    rails++;
                    double length = mtr.getLength();
                    for (double u = 0; u <= length; u += 0.25) {
                        var position = mtr.getPosition(u, false);
                        Vec2 p = new Vec2(position.x() - 0.5 - piece.start().x(), position.z() - 0.5 - piece.start().z());
                        LaneCurve.Local local = ours.local(p);
                        worst = StrictMath.max(worst, StrictMath.abs(local.d()));
                        samples++;
                        assertTrue(StrictMath.abs(local.d()) <= TOLERANCE, "MTR's rail from " + a + " to " + b + " is " + local.d() + " off our lane at " + u);
                    }
                    assertEquals(ours.length(), length, TOLERANCE, "the same length");
                }
            }
        }
        System.out.printf(Locale.ROOT, "G-8: MTR 4.0.5's connector on %d %s roads (%d of 2-3 pieces): %,d rails, %,d samples, worst %.2e block from our lanes%n",
                count, profile.id(), multi, rails, samples, worst);
        assertEquals(multiPieces, multi, profile.id());
    }

    /** What MTR does when a player joins two placed nodes: its own getAngles, then its own RailMath. */
    private static org.mtr.core.data.RailMath connect(LaneNode a, LaneNode b, Random random) {
        var positionA = new org.mtr.core.data.Position(a.x(), a.y(), a.z());
        var positionB = new org.mtr.core.data.Position(b.x(), b.y(), b.z());
        float facingA = (float) a.heading().degrees() + (random.nextBoolean() ? 180 : 0);
        float facingB = (float) b.heading().degrees() + (random.nextBoolean() ? 180 : 0);
        var angles = org.mtr.core.data.Rail.getAngles(positionA, facingA, positionB, facingB);
        return new org.mtr.core.data.RailMath(positionA, angles.left(), positionB, angles.right(), org.mtr.core.data.Rail.Shape.QUADRATIC, 0);
    }
}
