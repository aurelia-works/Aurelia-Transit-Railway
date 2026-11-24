/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.mtr.aurelia.road.road.solve.LaneNode;
import org.mtr.aurelia.road.spi.BlockReader;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Line;
import org.mtr.aurelia.road.tool.HudModel.Text;
import org.mtr.aurelia.road.tool.HudModel.Tone;
import org.mtr.aurelia.road.tool.Target;

/**
 * The node aids on the client (task R-6; owner, 2026-10-06): the roads placed this visit ({@link PlacedRoads}), the
 * checker's latest answer for each ({@link NodeCheck}), and the "Lane nodes" panel's rows, which the Lane nodes tool's
 * HUD shares. The platform calls {@link #refresh} twice a second while the overlay shows, and {@link #player} every
 * tick. Client thread only.
 *
 * <p><b>The road the panel shows</b> ({@link #focus}): the road whose node is aimed at (its marker spot or its node
 * block); else the road whose blocks are aimed at; else the road with the node nearest the player, within
 * {@link #RANGE} blocks.
 *
 * <p><b>The rows</b> (the same in every state, dashes where there is nothing to show): Road (its length), Nodes ("2 of 4
 * right · 1 wrong"), Markers ("3 blue wool"), Aimed ("Lane 2, node 1 · E–W"), Facing ("E–W ✓", from MTR's yaw rule),
 * and three problem rows: wrong nodes first, then those still to place; the third says "and 4 more" when there are.
 */
public final class NodeAids {
    /** How far from the player the overlay draws nodes, and the panel looks for a road, in blocks. */
    public static final double RANGE = 48;
    /** How many problem rows the panel has. */
    public static final int PROBLEM_ROWS = 3;

    /** The road the panel shows, and the index of the node aimed at, if any. */
    public record Focus(PlacedRoads.Road road, Optional<Integer> node) {
        public Focus {
            Objects.requireNonNull(road, "road");
            Objects.requireNonNull(node, "node");
        }
    }

    private final PlacedRoads roads;
    private final Map<UUID, NodeCheck.Report> reports = new HashMap<>();
    private double playerX;
    private double playerZ;

    public NodeAids(PlacedRoads roads) {
        this.roads = Objects.requireNonNull(roads, "roads");
    }

    public PlacedRoads roads() {
        return this.roads;
    }

    /** Where the player stands now (every tick). */
    public void player(double x, double z) {
        this.playerX = x;
        this.playerZ = z;
    }

    /** Checks every placed road's nodes again in {@code world}. */
    public void refresh(BlockReader world) {
        Map<UUID, NodeCheck.Report> fresh = new HashMap<>();
        for (PlacedRoads.Road road : this.roads.roads()) {
            NodeCheck.Report report = NodeCheck.check(road.nodes(), marker(road), world);
            fresh.put(road.operationId(), report);
        }
        this.reports.clear();
        this.reports.putAll(fresh);
    }

    /** Forgets every road and check (leaving the world). */
    public void clear() {
        this.roads.clear();
        this.reports.clear();
    }

    /** The checker's latest answer for {@code road}, once it has run since the road was placed. */
    public Optional<NodeCheck.Report> report(PlacedRoads.Road road) {
        return Optional.ofNullable(this.reports.get(road.operationId()));
    }

    /** The marker a road's nodes have under them: its palette's (blue wool in Vanilla) for a wool road, none otherwise. */
    public static Optional<String> marker(PlacedRoads.Road road) {
        return road.marker();
    }

    /** The road the panel shows for {@code aim}, as the class comment says. */
    public Optional<Focus> focus(Optional<Target> aim) {
        if (aim.isPresent()) {
            Target target = aim.get();
            Optional<PlacedRoads.Aimed> node = this.roads.nodeAt(target.x(), target.y(), target.z());
            if (node.isPresent()) {
                return Optional.of(new Focus(node.get().road(), node.get().node()));
            }
            Optional<PlacedRoads.Road> road = this.roads.containing(target.x(), target.y(), target.z());
            if (road.isPresent()) {
                return Optional.of(new Focus(road.get(), Optional.empty()));
            }
        }
        return this.roads.nearest(this.playerX, this.playerZ, RANGE).map(road -> new Focus(road, Optional.empty()));
    }

    /** What the panel's second line says: what to do, or that there is no road to show. */
    public Text state(Optional<Focus> focus, boolean tool) {
        if (focus.isEmpty()) {
            return Text.of(this.roads.roads().isEmpty() ? "stratos.nodes.state.no_roads" : "stratos.nodes.state.go_near");
        }
        return Text.of(tool ? "stratos.nodes.state.clear" : "stratos.nodes.state.place");
    }

    /** The panel's rows for {@code focus}, with the player facing yaw {@code yaw}. */
    public List<Line> lines(Optional<Focus> focus, float yaw) {
        List<Line> lines = new ArrayList<>();
        int facing = MtrNodes.facingFor(yaw);
        String[] facingNames = MtrNodes.names(facing);
        if (focus.isEmpty()) {
            lines.add(new Line("stratos.nodes.line.road", HudModel.NONE, false));
            lines.add(new Line("stratos.nodes.line.nodes", HudModel.NONE, false));
            lines.add(new Line("stratos.nodes.line.markers", HudModel.NONE, false));
            lines.add(new Line("stratos.nodes.line.aimed", HudModel.NONE, false));
            lines.add(new Line("stratos.nodes.line.facing", Text.of("stratos.nodes.value.facing", facingNames[0], facingNames[1]), false));
            for (int i = 0; i < PROBLEM_ROWS; i++) {
                lines.add(emptyProblem());
            }
            return lines;
        }
        PlacedRoads.Road road = focus.get().road();
        Optional<NodeCheck.Report> report = report(road);
        lines.add(new Line("stratos.nodes.line.road", Text.of("stratos.tool.value.blocks", String.format(Locale.ROOT, "%.1f", road.length())),
                false));
        if (report.isPresent()) {
            NodeCheck.Report r = report.get();
            String right = Integer.toString(r.right());
            String total = Integer.toString(r.total());
            if (r.wrong() > 0) {
                lines.add(new Line(Text.of("stratos.nodes.line.nodes"), Text.of("stratos.nodes.value.right_wrong", right, total,
                        Integer.toString(r.wrong())), Tone.WARNING));
            } else {
                lines.add(new Line(Text.of("stratos.nodes.line.nodes"), Text.of("stratos.nodes.value.right", right, total),
                        r.right() == r.total() ? Tone.GOOD : Tone.NORMAL));
            }
            Text markers = r.markers() > 0 ? Text.of("stratos.nodes.value.markers", Integer.toString(r.markers()))
                    : Text.of(road.markers() ? "stratos.nodes.value.markers_cleared" : "stratos.nodes.value.markers_none");
            lines.add(new Line("stratos.nodes.line.markers", markers, false));
        } else {
            lines.add(new Line("stratos.nodes.line.nodes", HudModel.NONE, false));
            lines.add(new Line("stratos.nodes.line.markers", HudModel.NONE, false));
        }
        Optional<Integer> aimed = focus.get().node();
        if (aimed.isPresent()) {
            LaneNode node = road.nodes().get(aimed.get());
            String[] needs = MtrNodes.names(node.mtrFacing());
            lines.add(new Line("stratos.nodes.line.aimed", Text.of("stratos.nodes.value.aimed", Integer.toString(node.lane()),
                    Integer.toString(number(road, aimed.get())), needs[0], needs[1]), false));
            boolean right = facing == node.mtrFacing();
            lines.add(new Line(Text.of("stratos.nodes.line.facing"), Text.of(right ? "stratos.nodes.value.facing_right"
                    : "stratos.nodes.value.facing_wrong", facingNames[0], facingNames[1]), right ? Tone.GOOD : Tone.WARNING));
        } else {
            lines.add(new Line("stratos.nodes.line.aimed", HudModel.NONE, false));
            lines.add(new Line("stratos.nodes.line.facing", Text.of("stratos.nodes.value.facing", facingNames[0], facingNames[1]), false));
        }
        List<NodeCheck.Result> problems = new ArrayList<>();
        report.ifPresent(r -> r.results().stream().filter(result -> result.status().wrong()).forEach(problems::add));
        report.ifPresent(r -> r.results().stream().filter(result -> result.status() == NodeCheck.Status.NOT_PLACED).forEach(problems::add));
        for (int i = 0; i < PROBLEM_ROWS; i++) {
            if (i == PROBLEM_ROWS - 1 && problems.size() > PROBLEM_ROWS) {
                lines.add(new Line(Text.of("stratos.tool.value.none"), Text.of("stratos.nodes.problem.more",
                        Integer.toString(problems.size() - (PROBLEM_ROWS - 1))), Tone.NORMAL));
            } else if (i < problems.size()) {
                lines.add(problem(problems.get(i)));
            } else {
                lines.add(emptyProblem());
            }
        }
        return lines;
    }

    /** The node's number along its lane, from 1. */
    private static int number(PlacedRoads.Road road, int index) {
        int lane = road.nodes().get(index).lane();
        int number = 0;
        for (int i = 0; i <= index; i++) {
            if (road.nodes().get(i).lane() == lane) {
                number++;
            }
        }
        return number;
    }

    private static Line problem(NodeCheck.Result result) {
        Text label = Text.of("stratos.nodes.line.problem", Integer.toString(result.node().lane()), Integer.toString(result.number()));
        Text value = switch (result.status()) {
            case WRONG_HEADING -> {
                String[] needs = MtrNodes.names(result.node().mtrFacing());
                if (result.found().isPresent()) {
                    String[] has = MtrNodes.names(result.found().getAsInt());
                    yield Text.of("stratos.nodes.problem.wrong_heading", needs[0], needs[1], has[0], has[1]);
                }
                yield Text.of("stratos.nodes.problem.wrong_state", needs[0], needs[1]);
            }
            default -> Text.of("stratos.nodes.problem." + result.status().name().toLowerCase(Locale.ROOT));
        };
        return new Line(label, value, result.status().wrong() ? Tone.WARNING : Tone.NORMAL);
    }

    private static Line emptyProblem() {
        return new Line(Text.of("stratos.tool.value.none"), HudModel.NONE, Tone.NORMAL);
    }
}
