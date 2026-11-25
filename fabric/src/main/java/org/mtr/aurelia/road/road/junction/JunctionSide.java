/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.junction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.road.RoadPlanParams;

/**
 * A placed junction for the other driving side (P9; roads-v1 §11.4). {@link JunctionTemplate#mirrored} reflects a template
 * across its east–west axis, so a mirrored T has its stem on the other side of the main road and a mirrored ring or crossroads
 * lists its arms in another order. A junction standing in the world can't move, so its converted twin is the mirrored
 * template turned (a quarter turn, a half turn, or none) until its arms stand on the same blocks facing the same ways: the
 * lanes and give-way lines then keep to the other side while every road joined to it stays where it is.
 *
 * <p>The arms are then in the mirrored template's order, so the request's ends and layouts are reordered to follow, each end
 * keeping the road it names; {@link Converted#armOf} says where each old arm went, for the roads drawn onto them. Of the
 * turns that fit, the one that moves the fewest arms wins (the first, on a tie), so a crossroads or roundabout swaps only the
 * two arms that must change.
 */
public final class JunctionSide {
    private JunctionSide() {
    }

    /**
     * The junction {@code junction} converted to {@code side}: its parameters (same tool, palette, node aid and joins; ends and
     * layouts in the new arm order), and for each old arm the arm it became. Empty when it isn't a placed junction, or has
     * no template for that side that stands on the same blocks.
     */
    public static Optional<Converted> convert(RoadPlanParams junction, RoadPlanParams.DrivingSide side) {
        if (junction.tool() != RoadPlanParams.Tool.JUNCTION || junction.junction().isEmpty()) {
            return Optional.empty();
        }
        int arms = junction.ends().size();
        if (junction.drivingSide() == side) {
            int[] same = new int[arms];
            for (int i = 0; i < arms; i++) {
                same[i] = i;
            }
            return Optional.of(new Converted(junction, same));
        }
        RoadPlanParams.Junction piece = junction.junction().get();
        Optional<JunctionTemplate> probe = JunctionTemplates.of(piece, side);
        if (probe.isEmpty() || probe.get().arms().size() != arms) {
            return Optional.empty();
        }
        int[] best = null;
        int bestFixed = -1;
        for (int turns = 0; turns < 4; turns++) {
            for (int first = 0; first < arms; first++) {
                int[] oldOf = fit(probe.get(), junction.ends(), turns, first);
                int fixed = fixed(oldOf);
                if (oldOf != null && fixed > bestFixed) {
                    best = oldOf;
                    bestFixed = fixed;
                }
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        List<RoadPlanParams.End> ends = new ArrayList<>();
        List<String> layouts = new ArrayList<>();
        int[] armOf = new int[arms];
        for (int arm = 0; arm < arms; arm++) {
            ends.add(junction.ends().get(best[arm]));
            layouts.add(piece.armProfiles().get(best[arm]));
            armOf[best[arm]] = arm;
        }
        // The layouts' order gives the template its lane nodes: it stands on the same blocks, or there is no conversion.
        RoadPlanParams converted = junction.withEnds(ends).withDrivingSide(side).withArmProfiles(layouts);
        Optional<JunctionTemplate> template = JunctionTemplates.of(converted.junction().orElseThrow(), side);
        if (template.isEmpty() || PlacedJunction.from(template.get(), ends).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Converted(converted, armOf));
    }

    /**
     * The old end each arm of {@code template} stands on when it is turned {@code turns} quarter turns with its arm {@code first}
     * on the end that faces the way it must; null unless every arm lands on a different end, at its block and heading.
     */
    private static int[] fit(JunctionTemplate template, List<RoadPlanParams.End> ends, int turns, int first) {
        JunctionTemplate.Arm anchor = template.arms().get(first);
        BlockStep offset = new BlockStep(anchor.x(), anchor.z());
        for (int i = 0; i < turns; i++) {
            offset = offset.rotateQuarterClockwise();
        }
        for (RoadPlanParams.End end : ends) {
            if (end.heading() != anchor.outgoing().rotate(4 * turns)) {
                continue;
            }
            PlacedJunction placed = new PlacedJunction(template, end.x() - offset.dx(), end.y(), end.z() - offset.dz(), turns);
            int[] oldOf = new int[ends.size()];
            boolean[] used = new boolean[ends.size()];
            boolean fits = true;
            for (int arm = 0; arm < oldOf.length && fits; arm++) {
                RoadPlanParams.End port = placed.arm(arm);
                oldOf[arm] = -1;
                for (int old = 0; old < ends.size(); old++) {
                    RoadPlanParams.End there = ends.get(old);
                    if (!used[old] && there.x() == port.x() && there.y() == port.y() && there.z() == port.z() && there.heading() == port.heading()) {
                        oldOf[arm] = old;
                        used[old] = true;
                        break;
                    }
                }
                fits = oldOf[arm] >= 0;
            }
            if (fits) {
                return oldOf;
            }
        }
        return null;
    }

    private static int fixed(int[] oldOf) {
        int fixed = 0;
        for (int arm = 0; oldOf != null && arm < oldOf.length; arm++) {
            fixed += oldOf[arm] == arm ? 1 : 0;
        }
        return fixed;
    }

    /**
     * A junction converted: its parameters for the new side, and {@code armOf[old]}, the arm that old arm {@code old} is now
     * (the port index of a road end drawn onto it is {@code RoadJoins.ARM_PORTS} + that).
     */
    public record Converted(RoadPlanParams params, int[] armOf) {
        /** The arm that old arm {@code old} became. */
        public int armOf(int old) {
            return this.armOf[old];
        }
    }
}
