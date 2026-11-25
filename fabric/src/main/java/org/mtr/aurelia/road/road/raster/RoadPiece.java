/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.Lane;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.Section;
import org.mtr.aurelia.road.road.solve.LaneRail;
import org.mtr.aurelia.road.road.solve.PieceSolution;

/**
 * One piece of road to rasterise: its profile, its anchor block, one lane curve per profile lane (left to right), the
 * surface height {@code y}, whether the piece closes its end, and the dash phase its markings start at.
 *
 * <p><b>Lane curves are relative to the anchor block's centre</b> ({@code (0, 0)} is the anchor's centre). The numbers
 * then stay small wherever the road is, so a road 30 million blocks out is rasterised exactly like the same road at
 * the origin, moved by whole blocks: far from the origin a {@code double} only holds positions to about 4 × 10⁻⁹
 * block, which is enough to make two lanes disagree about a column ("same input, different place", CLAUDE.md).
 *
 * <p>Pieces are half-open along their length ({@code 0 ≤ s < L}) so a column on the join between two pieces belongs to
 * exactly one of them. The last piece of a road closes its end ({@code s ≤ L}) so the road reaches its end node.
 *
 * <p><b>Square ends</b> (G-6+; owner, 2026-10-06). A solved piece ({@link #solved}) has {@link Ends}: it ends on the
 * line through each centre-line node at right angles to the heading there, and its lanes ({@link LanePath}s) carry on
 * along their tangents to reach those lines. A column belongs to the piece when it lies after the start line
 * (inclusive) and before the end line (exclusive, or inclusive on a closed end), measured as whole-block offsets from
 * the nodes, so two pieces sharing a node agree to the last bit. From R-5 each line bounds only the columns nearer its
 * own node: on a bend of more than 90° the line through one end runs back across the road near the other. Pieces
 * without ends (G-6's straights) keep the per-lane rule above.
 *
 * <p><b>Heights</b> (G-7). A solved piece keeps each lane's MTR rail ({@code rails}, in lane order): a lane's height at
 * {@code s} is its rail's height there, and beyond the rail's ends its end height. A piece without rails (G-6's
 * straights) is level at {@code y}. Heights are absolute block heights of the rail, which runs along the surface's top
 * face (S6a), so a column's target top is the lanes' height plus one.
 */
public record RoadPiece(RoadProfile profile, BlockXZ anchor, List<LaneCurve> lanes, int y, boolean closedEnd, double dashPhase,
        Optional<Ends> ends, List<RailMath> rails) {
    /**
     * Where a solved piece ends: its end node relative to the anchor (the start node), and the headings at the start
     * and the end.
     */
    public record Ends(BlockStep end, Heading from, Heading to) {
        public Ends {
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

    /** A piece without square ends (G-6's rule), level at {@code y}. */
    public RoadPiece(RoadProfile profile, BlockXZ anchor, List<LaneCurve> lanes, int y, boolean closedEnd, double dashPhase) {
        this(profile, anchor, lanes, y, closedEnd, dashPhase, Optional.empty(), List.of());
    }

    /** A piece level at {@code y}, with or without square ends. */
    public RoadPiece(RoadProfile profile, BlockXZ anchor, List<LaneCurve> lanes, int y, boolean closedEnd, double dashPhase, Optional<Ends> ends) {
        this(profile, anchor, lanes, y, closedEnd, dashPhase, ends, List.of());
    }

    public RoadPiece {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(ends, "ends");
        lanes = List.copyOf(lanes);
        rails = List.copyOf(rails);
        int expected = profile.lanes().size();
        if (lanes.size() != expected) {
            throw new IllegalArgumentException(profile.id() + " has " + expected + " lanes, but the piece has " + lanes.size() + " lane curves");
        }
        if (!rails.isEmpty() && rails.size() != expected) {
            throw new IllegalArgumentException(profile.id() + " has " + expected + " lanes, but the piece has " + rails.size() + " rails");
        }
        if (!Double.isFinite(dashPhase)) {
            throw new IllegalArgumentException("The dash phase must be finite");
        }
        List<Section> sections = profile.sections();
        int first = firstLaneIndex(sections);
        int last = lastLaneIndex(sections);
        for (int i = 0; i < sections.size(); i++) {
            if ((i < first || i > last) && sections.get(i).marking().isPresent()) {
                throw new IllegalArgumentException(profile.id() + ": edge-line markings aren't supported yet; they come with the profiles that use them");
            }
        }
    }

    /**
     * A straight, standalone piece (its end closed, dashes starting at 0) starting at {@code anchor} and running
     * {@code length} blocks along {@code heading}. The reference line starts at the anchor block's centre for odd
     * widths, and half a block to its right for even widths (G-4's parity rule). Each lane is the reference line moved
     * sideways by its exact offset {@code d · n(θ)}: on cardinal headings the lane nodes are then block centres;
     * diagonal lane-node vectors come with G-5/G-8.
     */
    public static RoadPiece straight(RoadProfile profile, BlockXZ anchor, Heading heading, double length, int y) {
        Vec2 normal = heading.normal();
        Vec2 reference = profile.referenceOnBlockCentre() ? Vec2.ZERO : normal.times(0.5);
        List<LaneCurve> lanes = new ArrayList<>();
        for (Lane lane : profile.lanes()) {
            lanes.add(new LineSegment(reference.plus(normal.times(lane.offset())), heading.tangent(), length));
        }
        return new RoadPiece(profile, anchor, lanes, y, true, 0);
    }

    /**
     * The piece G-5 solved, anchored at its start node, at the start's height, with square ends: each lane follows its
     * own MTR rail exactly ({@link LanePath#of}), and its height too (G-7). {@code closedEnd} for a road's last piece;
     * {@code dashPhase} from the piece before ({@link #phaseAfter()}), or 0.
     */
    public static RoadPiece solved(RoadProfile profile, PieceSolution.Solved solution, boolean closedEnd, double dashPhase) {
        List<LaneCurve> lanes = new ArrayList<>();
        List<RailMath> rails = new ArrayList<>();
        for (LaneRail lane : solution.lanes()) {
            lanes.add(LanePath.of(lane.rail()));
            rails.add(lane.rail());
        }
        RoadPlanParams.End start = solution.start();
        RoadPlanParams.End end = solution.end();
        Ends ends = new Ends(new BlockStep(end.x() - start.x(), end.z() - start.z()), start.heading(), end.heading());
        return new RoadPiece(profile, new BlockXZ(start.x(), start.z()), lanes, start.y(), closedEnd, dashPhase, Optional.of(ends), rails);
    }

    /**
     * The height of lane {@code lane}'s rail at {@code s} along it (absolute, in blocks): its MTR rail's, held at the end
     * heights beyond its ends; {@code y} on a piece without rails.
     */
    public double height(int lane, double s) {
        if (this.rails.isEmpty()) {
            return this.y;
        }
        RailMath rail = this.rails.get(lane);
        double length = rail.getLength();
        return rail.getPosition(StrictMath.max(0, StrictMath.min(length, s)), false).y();
    }

    /**
     * The dash phase the next piece of the same road starts with: this piece's phase plus how far the centre line runs
     * along it (the lanes' average length, as dashes are measured; spec roads-v1 §7.2).
     */
    public double phaseAfter() {
        return this.dashPhase + meanLength();
    }

    /**
     * How far the road runs along this piece: the lanes' average length, as dashes, gullies and crossings are measured
     * (spec roads-v1 §7.2).
     */
    public double meanLength() {
        double total = 0;
        for (LaneCurve lane : this.lanes) {
            total += lane.length();
        }
        return total / this.lanes.size();
    }

    static int firstLaneIndex(List<Section> sections) {
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).band() == Band.LANE) {
                return i;
            }
        }
        throw new IllegalArgumentException("A profile without lanes can't be rasterised");
    }

    static int lastLaneIndex(List<Section> sections) {
        for (int i = sections.size() - 1; i >= 0; i--) {
            if (sections.get(i).band() == Band.LANE) {
                return i;
            }
        }
        throw new IllegalArgumentException("A profile without lanes can't be rasterised");
    }
}
