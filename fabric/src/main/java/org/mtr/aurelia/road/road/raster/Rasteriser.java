/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Vec2;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.Section;
import org.mtr.aurelia.road.road.line.LineTracer;
import org.mtr.aurelia.road.road.line.MarkingCurve;

/**
 * Turns a road piece into columns and a block plan by per-column inverse classification (spec roads-v1 §7.1): every
 * column in the footprint is classified once, by asking which lane curves it lies between. No column is stamped twice
 * and none is skipped, because each one is asked exactly once and every interval is half-open.
 *
 * <p>From G-6+, lanes can be MTR's lines and arcs ({@link LanePath}), and a solved piece has square ends: a column is
 * in it when it lies between the lines through its centre-line nodes (see {@link RoadPiece}), and every lane is then
 * measured, carried on along its tangents where diagonal offsets stagger the nodes.
 *
 * <p>From G-7 every column also gets its target top (spec roads-v1 §6.2): between two lanes, their heights weighted by
 * how near each is; outside the outermost lanes, the outer lane's height; plus one block, as the rail runs on the
 * surface's top face (S6a). {@link #plan} fits it to the palette's steps and stacks the column ({@link SurfaceFit}).
 */
public final class Rasteriser {
    /** A classified column and its target top (absolute, unrounded but snapped). */
    public record Cell(Column column, double top) {
    }

    /**
     * Snapping step, 2⁻²⁰ block (about 10⁻⁶): exact in binary, so snapping is identical on every machine. Coarse enough to
     * absorb the errors in MTR's own positions far from the origin (about 10⁻⁸ at 30 million blocks), and still 10,000
     * times finer than the 0.01 lane tolerance.
     */
    private static final double SNAP = 0x1p20;

    private Rasteriser() {
    }

    /**
     * {@code value} rounded to the nearest 2⁻²⁰. Values that differ from a band boundary only by float leftovers (such as
     * MTR's cos 90° = 6 × 10⁻¹⁷) then sit exactly on it, and the half-open rule decides; otherwise the result could
     * depend on where the road is ("same input, different place", CLAUDE.md).
     */
    static double snap(double value) {
        return StrictMath.rint(value * SNAP) / SNAP;
    }

    /**
     * The columns of {@code piece}, sorted by z, then x. The work is done relative to the piece's anchor block (see
     * {@link RoadPiece}) and moved to world blocks only at the end, by whole-block addition.
     */
    public static Map<BlockXZ, Column> columns(RoadPiece piece) {
        Map<BlockXZ, Column> columns = new TreeMap<>(Rasteriser::compareZThenX);
        cells(piece).forEach((xz, cell) -> columns.put(xz, cell.column()));
        return columns;
    }

    /** As {@link #columns}, with each column's target top (G-7). */
    public static Map<BlockXZ, Cell> cells(RoadPiece piece) {
        Layout layout = new Layout(piece.profile().sections());
        int[] box = candidateBox(piece, layout);
        BlockXZ anchor = piece.anchor();
        Map<BlockXZ, Cell> columns = new TreeMap<>(Rasteriser::compareZThenX);
        for (int z = box[1]; z <= box[3]; z++) {
            // The server works out plans on another thread with a time limit (editor-protocol §5) and interrupts it
            // when the limit passes; a long road stops here, a row at a time.
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Stopped by the plan time limit");
            }
            for (int x = box[0]; x <= box[2]; x++) {
                Cell cell = classify(piece, layout, x, z);
                if (cell != null) {
                    columns.put(anchor.plus(new BlockStep(x, z)), cell);
                }
            }
        }
        return columns;
    }

    /**
     * The block plan of {@code piece} (G-7): each column's surface fitted to {@code palette}'s steps, with its foundation
     * and clearance ({@link SurfaceFit}), nothing outside any world's height.
     */
    public static BlockPlan plan(RoadPiece piece, BlockPalette palette) {
        return plan(piece, palette, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /** As {@link #plan(RoadPiece, BlockPalette)}, with the foundation and clearance cut off outside {@code [minY, maxY)}. */
    public static BlockPlan plan(RoadPiece piece, BlockPalette palette, int minY, int maxY) {
        return plan(List.of(piece), palette, minY, maxY);
    }

    /**
     * The block plan of a road made of {@code pieces} (R-5), in order along it: each piece's columns, stacked as
     * {@link #plan(RoadPiece, BlockPalette, int, int)} stacks them. Pieces are half-open where they meet, so no column is
     * planned twice (a double claim stops the plan with an error).
     *
     * <p>From B-1, with a palette whose kerb turns to face the road ({@link BlockPalette#kerbEdges()}), each kerb and
     * footway column first learns where it meets the road ({@link #withKerbs}). From B-2, with a palette whose markings
     * are line blocks ({@link BlockPalette#lines}), each marking is traced to the blocks its paint touches
     * ({@link #withLines}).
     */
    public static BlockPlan plan(List<RoadPiece> pieces, BlockPalette palette, int minY, int maxY) {
        BlockPlan.Builder plan = BlockPlan.builder(palette.dataVersion());
        for (Map.Entry<BlockXZ, Cell> entry : planCells(pieces, palette)) {
            SurfaceFit.stack(plan, entry.getKey().x(), entry.getKey().z(), entry.getValue().column(), entry.getValue().top(), palette, minY, maxY);
        }
        return plan.build();
    }

    /** The columns {@link #plan(List, BlockPalette, int, int)} stacks, in order: every piece's, with kerbs and lines as {@code palette} has them. */
    static List<Map.Entry<BlockXZ, Cell>> planCells(List<RoadPiece> pieces, BlockPalette palette) {
        List<Map.Entry<BlockXZ, Cell>> cells = new ArrayList<>();
        List<Set<BlockXZ>> owned = new ArrayList<>();
        for (RoadPiece piece : pieces) {
            Map<BlockXZ, Cell> own = cells(piece);
            cells.addAll(own.entrySet());
            owned.add(own.keySet());
        }
        if (palette.kerbEdges()) {
            cells = withKerbs(cells);
        }
        if (palette.lines(LineMarking.Colour.WHITE) || palette.lines(LineMarking.Colour.YELLOW)) {
            cells = withLines(cells, pieces, owned, palette);
        }
        return cells;
    }

    /**
     * {@code cells} (a road's, in order) with real kerbs (B-1; ADR-0044): a kerb wherever the raised footway meets the
     * road. Every kerb or footway column that the road touches, at a side or only at a corner, becomes a kerb facing it
     * ({@link KerbEdge#at}); every other one is footway. On a straight road along the grid that is exactly the kerb band;
     * on a bend the kerb steps with the blocks, an inner corner closing each step. Only this road's own columns count, so a
     * corner at a joint with another road may stay footway.
     */
    static List<Map.Entry<BlockXZ, Cell>> withKerbs(List<Map.Entry<BlockXZ, Cell>> cells) {
        Map<BlockXZ, Cell> at = new HashMap<>();
        for (Map.Entry<BlockXZ, Cell> entry : cells) {
            at.putIfAbsent(entry.getKey(), entry.getValue());
        }
        Predicate<BlockXZ> road = xz -> {
            Cell cell = at.get(xz);
            return cell != null && !raised(cell.column().band());
        };
        List<Map.Entry<BlockXZ, Cell>> kerbed = new ArrayList<>(cells.size());
        for (Map.Entry<BlockXZ, Cell> entry : cells) {
            Cell cell = entry.getValue();
            if (raised(cell.column().band())) {
                Optional<KerbEdge> kerb = KerbEdge.at(entry.getKey(), road);
                Column column = cell.column().with(kerb.isPresent() ? Band.KERB : Band.FOOTWAY, kerb);
                kerbed.add(Map.entry(entry.getKey(), new Cell(column, cell.top())));
            } else {
                kerbed.add(entry);
            }
        }
        return kerbed;
    }

    /**
     * {@code cells} (a road's, in order; {@code owned}, each piece's own columns) with its markings drawn as line blocks
     * (B-2; ADR-0046), for each colour {@code palette} draws so:
     * <ul>
     *   <li>no column is painted whole any more;</li>
     *   <li>each marked section between two lanes is traced along its curve ({@link MarkingCurve}: their middle, moved to
     *       the section's centre) to every block its paint touches ({@link LineTracer});</li>
     *   <li>a traced block in its piece's own columns, and not raised (footway, kerb), is painted with its chord where it is
     *       in a dash ({@link #dashes}): dashes stay whole blocks (ADR-0044), measured as the whole-column paint measured
     *       them, with the piece's dash phase.</li>
     * </ul>
     */
    static List<Map.Entry<BlockXZ, Cell>> withLines(List<Map.Entry<BlockXZ, Cell>> cells, List<RoadPiece> pieces, List<Set<BlockXZ>> owned,
            BlockPalette palette) {
        List<Map.Entry<BlockXZ, Cell>> lined = new ArrayList<>(cells.size());
        Map<BlockXZ, Integer> at = new HashMap<>();
        for (Map.Entry<BlockXZ, Cell> entry : cells) {
            Cell cell = entry.getValue();
            Column column = cell.column();
            boolean whole = column.paint().isPresent() && palette.lines(column.paint().get());
            at.putIfAbsent(entry.getKey(), lined.size());
            lined.add(whole ? Map.entry(entry.getKey(), new Cell(column.painted(Optional.empty(), OptionalInt.empty()), cell.top())) : entry);
        }
        for (int i = 0; i < pieces.size(); i++) {
            RoadPiece piece = pieces.get(i);
            Layout layout = new Layout(piece.profile().sections());
            for (int lane = 0; lane + 1 < piece.lanes().size(); lane++) {
                List<Section> gap = layout.between(lane);
                double width = Layout.width(gap);
                double start = 0;
                for (Section section : gap) {
                    double offset = start + section.width() / 2.0 - width / 2;
                    start += section.width();
                    if (section.marking().isEmpty() || !palette.lines(section.marking().get().colour())) {
                        continue;
                    }
                    LineMarking marking = section.marking().get();
                    MarkingCurve curve = new MarkingCurve(piece.lanes().get(lane), piece.lanes().get(lane + 1), offset);
                    List<LineTracer.Traced> line = LineTracer.trace(curve, marking.line());
                    Map<BlockXZ, Boolean> dashes = dashes(line, marking, piece.dashPhase());
                    for (LineTracer.Traced traced : line) {
                        BlockXZ block = piece.anchor().plus(new BlockStep(traced.block().x(), traced.block().z()));
                        Integer index = at.get(block);
                        if (index == null || !owned.get(i).contains(block) || !dashes.get(traced.block())) {
                            continue;
                        }
                        Cell cell = lined.get(index).getValue();
                        if (raised(cell.column().band())) {
                            continue;
                        }
                        Column column = cell.column().painted(Optional.of(marking.colour()), OptionalInt.of(traced.chord()));
                        lined.set(index, Map.entry(block, new Cell(column, cell.top())));
                    }
                }
            }
        }
        return lined;
    }

    /** A traced block whose stretch is shorter than this (blocks) is a sliver: the line only passes a corner of it. */
    static final double SLIVER = 0.25;
    /** A sliver whose neighbours disagree is unpainted when the other shares at least this much of what its best does. */
    private static final double SPLIT = 0.75;

    /**
     * Which of a line's traced blocks are in a dash. A block the line runs along (its stretch {@link #SLIVER} or longer) is
     * when most of its stretch is ({@link #inDash}); two rows the line runs between have the same stretch, so they agree.
     * A sliver, which the line only passes at a corner, follows the block of the line that shares the most of its stretch,
     * unless another that disagrees shares nearly as much ({@link #SPLIT}): at a corner where a dash ends, the slivers either
     * side stay unpainted and the dash ends in a clean point.
     */
    static Map<BlockXZ, Boolean> dashes(List<LineTracer.Traced> line, LineMarking marking, double phase) {
        Map<BlockXZ, Boolean> in = new HashMap<>();
        List<LineTracer.Traced> along = new ArrayList<>();
        for (LineTracer.Traced traced : line) {
            if (traced.to() - traced.from() >= SLIVER) {
                along.add(traced);
                in.put(traced.block(), inDash(marking, traced.from(), traced.to(), phase));
            }
        }
        for (LineTracer.Traced sliver : line) {
            if (sliver.to() - sliver.from() >= SLIVER) {
                continue;
            }
            double best = 0;
            boolean bestPainted = false;
            for (LineTracer.Traced block : along) {
                double shared = StrictMath.min(sliver.to(), block.to()) - StrictMath.max(sliver.from(), block.from());
                if (shared > best) {
                    best = shared;
                    bestPainted = in.get(block.block());
                }
            }
            boolean split = false;
            for (LineTracer.Traced block : along) {
                double shared = StrictMath.min(sliver.to(), block.to()) - StrictMath.max(sliver.from(), block.from());
                split |= shared > 0 && shared >= SPLIT * best && in.get(block.block()) != bestPainted;
            }
            in.put(sliver.block(), best > 0 ? bestPainted && !split : inDash(marking, sliver.from(), sliver.to(), phase));
        }
        return in;
    }

    /**
     * Whether a line block whose paint is touched by the stretch {@code from} to {@code to} of its curve is in a dash: when
     * more than half the stretch is painted ({@link #painted}); at exactly half (a block centred on a dash's end, as on
     * any road along the grid), when its middle is ({@link #isPainted}). Two blocks the line runs between share their
     * stretch, so they agree, and a dash end never leaves half the line's width; on a line along block centres it is
     * exactly the whole-column rule (a block is painted when its centre is).
     */
    static boolean inDash(LineMarking marking, double from, double to, double phase) {
        if (marking.isSolid()) {
            return true;
        }
        double half = snap((to - from) / 2);
        double painted = snap(painted(marking, from + phase, to + phase));
        return painted > half || painted == half && isPainted(marking, snap((from + to) / 2) + phase);
    }

    /** How much of {@code [a, b]} the pattern paints ({@code (s mod (mark + gap)) < mark}). */
    static double painted(LineMarking marking, double a, double b) {
        return paintedUpTo(marking, b) - paintedUpTo(marking, a);
    }

    /** How much of {@code [0, s]} the pattern paints, counted negative for {@code s < 0}. */
    private static double paintedUpTo(LineMarking marking, double s) {
        double period = marking.mark() + marking.gap();
        double whole = StrictMath.floor(s / period);
        return whole * marking.mark() + StrictMath.min(s - whole * period, marking.mark());
    }

    /** Whether a band is part of the footway, which a real kerb raises above the road. */
    private static boolean raised(Band band) {
        return band == Band.KERB || band == Band.FOOTWAY;
    }

    static int compareZThenX(BlockXZ a, BlockXZ b) {
        int byZ = Integer.compare(a.z(), b.z());
        return byZ != 0 ? byZ : Integer.compare(a.x(), b.x());
    }

    /** A lane the column lies beside: which lane, and the column's position relative to it. */
    private record Hit(int lane, double s, double d) {
    }

    private static Cell classify(RoadPiece piece, Layout layout, int x, int z) {
        boolean square = piece.ends().isPresent();
        if (square && !inside(piece, piece.ends().get(), x, z)) {
            return null;
        }
        Vec2 p = new Vec2(x, z);
        Hit left = null; // the nearest lane to the left of p (p is on its right: d ≥ 0)
        Hit right = null; // the nearest lane to the right of p (d < 0)
        List<LaneCurve> lanes = piece.lanes();
        for (int i = 0; i < lanes.size(); i++) {
            LaneCurve curve = lanes.get(i);
            LaneCurve.Local local = curve.local(p);
            double s = snap(local.s());
            double d = snap(local.d());
            double length = snap(curve.length());
            // A piece with square ends measures every lane; otherwise (G-6) a lane counts only along its own length.
            if (!square && (s < 0 || (piece.closedEnd() ? s > length : s >= length))) {
                continue;
            }
            // Strict comparisons keep the first lane on a tie: lane order is the tie-break.
            if (d >= 0) {
                if (left == null || d < left.d()) {
                    left = new Hit(i, s, d);
                }
            } else if (right == null || d > right.d()) {
                right = new Hit(i, s, d);
            }
        }
        if (left == null && right == null) {
            return null;
        }
        if (left != null && right != null) {
            if (right.lane() != left.lane() + 1) {
                throw new IllegalStateException("Column " + p + " lies between lanes " + left.lane() + " and " + right.lane()
                        + ", which aren't neighbours; staggered lane ends come with G-6+");
            }
            Column column = between(piece, layout, left, right);
            // Weighted by nearness: a column on a lane takes its height; one halfway takes the mean.
            double weightLeft = -right.d();
            double weightRight = left.d();
            double h = (piece.height(left.lane(), left.s()) * weightLeft + piece.height(right.lane(), right.s()) * weightRight)
                    / (weightLeft + weightRight);
            return new Cell(column, snap(h + 1));
        }
        Hit outer = left != null ? left : right;
        boolean rightSide = left != null;
        int outermost = rightSide ? lanes.size() - 1 : 0;
        if (outer.lane() != outermost) {
            throw new IllegalStateException("Column " + p + " is beside lane " + outer.lane() + " but no lane beyond it; staggered lane ends come with G-6+");
        }
        Column column = outside(layout, outer, rightSide);
        return column == null ? null : new Cell(column, snap(piece.height(outer.lane(), outer.s()) + 1));
    }

    /**
     * Whether column {@code (x, z)} (relative to the anchor, the start node) lies between a solved piece's square ends:
     * on or after the start line, and before the end line (or on it, for a closed end). Measured from whole-block offsets,
     * so a piece and the next one, which share a node, agree exactly.
     */
    static boolean inside(RoadPiece piece, RoadPiece.Ends ends, int x, int z) {
        // Each end's line clips only the columns nearer that end's node (R-5). On a bend of more than 90° the line through
        // one end runs back across the road near the other end, and would cut a strip out of it there.
        long dxEnd = (long) x - ends.end().dx();
        long dzEnd = (long) z - ends.end().dz();
        if ((long) x * x + (long) z * z <= dxEnd * dxEnd + dzEnd * dzEnd) {
            Vec2 from = ends.from().tangent();
            return snap(x * from.x() + z * from.z()) >= 0;
        }
        Vec2 to = ends.to().tangent();
        double past = snap(dxEnd * to.x() + dzEnd * to.z());
        return piece.closedEnd() ? past <= 0 : past < 0;
    }

    /** A column between two neighbouring lanes: the sections between them sit centred on their bisector. */
    private static Column between(RoadPiece piece, Layout layout, Hit left, Hit right) {
        double m = snap((left.d() + right.d()) / 2); // signed distance from the bisector, positive to the right
        List<Section> gap = layout.between(left.lane());
        double width = Layout.width(gap);
        double u = snap(m + width / 2); // position within the gap, from its left side
        if (u >= 0 && u < width) {
            Section section = Layout.at(gap, u);
            boolean painted = section.marking().isPresent()
                    && isPainted(section.marking().get(), snap((left.s() + right.s()) / 2) + piece.dashPhase());
            return new Column(section.band(), painted ? section.marking().map(LineMarking::colour) : Optional.empty(), false);
        }
        boolean centre = left.d() < 0.5 || right.d() >= -0.5; // a lane curve passes through this column
        return new Column(Band.LANE, false, centre);
    }

    /** A column beyond the outermost lane on one side: the outer sections are laid out from the lane's edge. */
    private static Column outside(Layout layout, Hit outer, boolean rightSide) {
        double distance = StrictMath.abs(outer.d());
        double e = snap(distance - layout.laneHalfWidth(outer.lane())); // beyond the lane's edge
        if (e < 0) {
            boolean centre = rightSide ? outer.d() < 0.5 : outer.d() >= -0.5;
            return new Column(Band.LANE, false, centre);
        }
        List<Section> outerSections = rightSide ? layout.rightOuter() : layout.leftOuter();
        if (e >= Layout.width(outerSections)) {
            return null;
        }
        return new Column(Layout.at(outerSections, e).band(), false, false);
    }

    /**
     * {@code (s + phase) mod (mark + gap) < mark}, with s measured along the line (spec roads-v1 §7.2). {@code s} arrives
     * with the phase added, and is snapped before the modulo (R-5): a road joined to another reaches its dash phase at the
     * joint only to within float leftovers, and −10⁻¹³ would otherwise wrap round to the end of the pattern.
     */
    static boolean isPainted(LineMarking marking, double s) {
        if (marking.isSolid()) {
            return true;
        }
        double period = marking.mark() + marking.gap();
        double at = snap(s);
        double phase = snap(at - period * StrictMath.floor(at / period));
        return phase < marking.mark();
    }

    /** The columns to examine: every lane curve's bounding box, widened by the road's reach beyond its lanes. */
    private static int[] candidateBox(RoadPiece piece, Layout layout) {
        double reach = layout.reach() + 1;
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        List<Vec2> extremes = new ArrayList<>();
        for (LaneCurve curve : piece.lanes()) {
            extremes(curve, extremes);
        }
        for (Vec2 point : extremes) {
            minX = StrictMath.min(minX, point.x());
            minZ = StrictMath.min(minZ, point.z());
            maxX = StrictMath.max(maxX, point.x());
            maxZ = StrictMath.max(maxZ, point.z());
        }
        return new int[] {
            (int) StrictMath.floor(minX - reach), (int) StrictMath.floor(minZ - reach),
            (int) StrictMath.ceil(maxX + reach), (int) StrictMath.ceil(maxZ + reach)};
    }

    /** The points that bound a lane curve: a line's ends; an arc's ends and wherever it passes due east, south, west or north. */
    private static void extremes(LaneCurve curve, List<Vec2> points) {
        if (curve instanceof LineSegment line) {
            points.add(line.start());
            points.add(line.end());
        } else if (curve instanceof ArcSegment arc) {
            points.add(arc.start());
            points.add(arc.end());
            double sweep = StrictMath.abs(arc.sweep());
            for (int quarter = 0; quarter < 4; quarter++) {
                double angle = quarter * StrictMath.PI / 2;
                double from = arc.sweep() > 0 ? angle - arc.startAngle() : arc.startAngle() - angle;
                double turned = ArcSegment.normalise(from);
                if (turned < 0) {
                    turned += 2 * StrictMath.PI;
                }
                if (turned <= sweep) {
                    points.add(new Vec2(arc.centre().x() + arc.radius() * StrictMath.cos(angle), arc.centre().z() + arc.radius() * StrictMath.sin(angle)));
                }
            }
        } else if (curve instanceof LanePath path) {
            path.pieces().forEach(piece -> extremes(piece, points));
        }
    }

    /** The profile's sections split around its lanes: the gaps between lanes and the outer sections on each side. */
    private static final class Layout {
        private final List<Section> sections;
        private final int[] laneSections; // section index of each lane
        private final List<Section> leftOuter; // walking outward from the leftmost lane
        private final List<Section> rightOuter; // walking outward from the rightmost lane

        Layout(List<Section> sections) {
            this.sections = sections;
            int lanes = 0;
            for (Section section : sections) {
                lanes += section.band() == Band.LANE ? 1 : 0;
            }
            this.laneSections = new int[lanes];
            int lane = 0;
            for (int i = 0; i < sections.size(); i++) {
                if (sections.get(i).band() == Band.LANE) {
                    this.laneSections[lane++] = i;
                }
            }
            int first = this.laneSections[0];
            int last = this.laneSections[lanes - 1];
            List<Section> left = new ArrayList<>(sections.subList(0, first));
            Collections.reverse(left);
            this.leftOuter = List.copyOf(left);
            this.rightOuter = List.copyOf(sections.subList(last + 1, sections.size()));
        }

        List<Section> between(int leftLane) {
            return this.sections.subList(this.laneSections[leftLane] + 1, this.laneSections[leftLane + 1]);
        }

        List<Section> leftOuter() {
            return this.leftOuter;
        }

        List<Section> rightOuter() {
            return this.rightOuter;
        }

        double laneHalfWidth(int lane) {
            return this.sections.get(this.laneSections[lane]).width() / 2.0;
        }

        /** How far the road reaches beyond a lane curve: half a lane plus the wider side's outer sections. */
        double reach() {
            double half = 0;
            for (int lane = 0; lane < this.laneSections.length; lane++) {
                half = StrictMath.max(half, laneHalfWidth(lane));
            }
            return half + StrictMath.max(width(this.leftOuter), width(this.rightOuter));
        }

        static double width(List<Section> sections) {
            double width = 0;
            for (Section section : sections) {
                width += section.width();
            }
            return width;
        }

        /** The section containing {@code u} blocks in from the start of {@code sections}; half-open, so u = edge goes on. */
        static Section at(List<Section> sections, double u) {
            double start = 0;
            for (Section section : sections) {
                if (u < start + section.width()) {
                    return section;
                }
                start += section.width();
            }
            throw new IllegalArgumentException("Position " + u + " is past the sections");
        }
    }
}
