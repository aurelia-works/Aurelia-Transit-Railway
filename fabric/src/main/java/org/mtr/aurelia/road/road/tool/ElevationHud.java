/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.VerticalProfile;
import org.mtr.aurelia.road.geom.mtr.Rail;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.Grade;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.solve.PieceSolver;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.HudModel.Action;
import org.mtr.aurelia.road.tool.HudModel.Hint;
import org.mtr.aurelia.road.tool.HudModel.Line;
import org.mtr.aurelia.road.tool.HudModel.Text;
import org.mtr.aurelia.road.tool.Planned;
import org.mtr.aurelia.road.tool.ToolInput;

/**
 * The road tools' elevation rows and key hints (task R-4; owner, 2026-10-06), shared by both tools; from R-6 also the
 * node aid's row and key.
 * <ul>
 *   <li><b>Slope:</b> "Level", or the rise and the steepest lane's grade against the preset's limit, such as
 *       "+3 · 7.5% (max 8.3%)", in the warning colour when over it. The grade is {@link PieceSolver#steepestGrade}, the
 *       same number {@code GRADE_TOO_STEEP} reports, written the same way.</li>
 *   <li><b>Shape:</b> "Quadratic", or "Two radii · R 100", with "→ 85.4" when MTR shortens the radius on the centre line
 *       (its limit is (L² + H²)/(4H), G-3).</li>
 *   <li><b>Nodes</b> (R-6): "Blue wool", "MTR nodes" or "None".</li>
 *   <li><b>Hints:</b> Page Up / Page Down while an end follows the aim (or a handle is held); Home, the radius keys, the
 *       radius box and (R-6) the node aid whenever the road isn't being placed.</li>
 * </ul>
 */
final class ElevationHud {
    private ElevationHud() {
    }

    static Line slope(RoadProfile profile, RoadSettings settings, Optional<RoadPlanParams> params) {
        return slope(profile, settings, params, Optional.empty(), false);
    }

    /**
     * As {@link #slope(RoadProfile, RoadSettings, Optional)}, for the road the planner gets ({@code params}, the way round
     * it stores it: {@code turned} when that is backwards from how the player set it, so the rise is shown the player's
     * way), with its answer once it has arrived: a bend split in two (R-5: {@code BEND_SPLIT}) reads its steeper part.
     */
    static Line slope(RoadProfile profile, RoadSettings settings, Optional<RoadPlanParams> params, Optional<Planned> current, boolean turned) {
        if (params.isEmpty()) {
            return new Line("stratos.tool.line.slope", HudModel.NONE, false);
        }
        RoadPlanParams.End from = params.get().ends().get(0);
        RoadPlanParams.End to = params.get().ends().get(1);
        long rise = (long) to.y() - from.y();
        if (rise == 0) {
            return new Line("stratos.tool.line.slope", Text.of("stratos.tool.value.level"), false);
        }
        String signed = String.format(Locale.ROOT, "%+d", turned ? -rise : rise);
        OptionalDouble grade = split(current).map(middle -> {
            OptionalDouble a = PieceSolver.steepestGrade(profile, settings.vertical(), from, middle);
            OptionalDouble b = PieceSolver.steepestGrade(profile, settings.vertical(), middle, to);
            return a.isPresent() && b.isPresent() ? OptionalDouble.of(StrictMath.max(a.getAsDouble(), b.getAsDouble())) : OptionalDouble.empty();
        }).orElseGet(() -> PieceSolver.steepestGrade(profile, settings.vertical(), from, to));
        if (grade.isEmpty()) {
            return new Line("stratos.tool.line.slope", Text.of("stratos.tool.value.rise", signed), false);
        }
        Grade max = Presets.limits(profile.group(), params.get().preset()).maxGrade();
        String[] percents = PieceSolver.percents(grade.getAsDouble() * 100, max.value() * 100);
        return new Line("stratos.tool.line.slope", Text.of("stratos.tool.value.slope", signed, percents[0], percents[1]),
                max.isExceededBy(grade.getAsDouble(), 1));
    }

    /** Where the planner split the road in two (R-5: {@code BEND_SPLIT}), if it did. */
    private static Optional<RoadPlanParams.End> split(Optional<Planned> current) {
        if (current.isEmpty() || !current.get().isPlanned()) {
            return Optional.empty();
        }
        return current.get().result().warnings().stream().filter(w -> w.code().equals(RoadPlanner.BEND_SPLIT)).findFirst().map(w -> RoadPlanParams.End.at(
                Integer.parseInt(w.args().get("x")), Integer.parseInt(w.args().get("y")), Integer.parseInt(w.args().get("z")),
                Heading.valueOf(w.args().get("heading"))));
    }

    static Line shape(RoadSettings settings, Optional<RoadPlanParams> params) {
        if (settings.shape() == RoadPlanParams.Vertical.Shape.QUADRATIC) {
            return new Line("stratos.tool.line.shape", Text.of("stratos.tool.value.quadratic"), false);
        }
        String radius = RoadSettings.format(settings.radius());
        if (params.isPresent()) {
            RoadPlanParams.End from = params.get().ends().get(0);
            RoadPlanParams.End to = params.get().ends().get(1);
            long rise = (long) to.y() - from.y();
            Optional<RailMath> centre = PieceSolver.centreLine(from, to);
            if (rise != 0 && centre.isPresent()) {
                double limit = new VerticalProfile(Rail.Shape.TWO_RADII, centre.get().getLength(), rise, settings.radius()).maxRadius();
                if (limit < settings.radius()) {
                    return new Line("stratos.tool.line.shape", Text.of("stratos.tool.value.two_radii_mtr", radius, RoadSettings.format(
                            StrictMath.floor(limit * 10) / 10)), false);
                }
            }
        }
        return new Line("stratos.tool.line.shape", Text.of("stratos.tool.value.two_radii", radius), false);
    }

    static Line nodes(RoadSettings settings) {
        String key = switch (settings.nodeAids()) {
            case TRACK -> "stratos.tool.value.nodes_track";
            case WOOL -> "stratos.tool.value.nodes_wool";
            case PREORIENTED -> "stratos.tool.value.nodes_mtr";
            case NONE -> "stratos.tool.value.nodes_none";
        };
        return new Line("stratos.tool.line.nodes", Text.of(key), false);
    }

    /**
     * The elevation keys' hints: the height keys while {@code following}; unless the road is being placed, the shape key
     * and the radius box, the radius steps with Two radii, and the node aid (R-6).
     */
    static void hints(List<Hint> hints, boolean following, boolean committing, RoadSettings settings) {
        if (following) {
            hints.add(new Hint(Action.RAISE, "stratos.tool.hint.height"));
            hints.add(new Hint(Action.LOWER, "stratos.tool.hint.height"));
        }
        if (!committing) {
            hints.add(new Hint(Action.SHAPE, "stratos.tool.hint.shape"));
            if (settings.shape() == RoadPlanParams.Vertical.Shape.TWO_RADII) {
                hints.add(new Hint(Action.RADIUS_UP, "stratos.tool.hint.radius"));
                hints.add(new Hint(Action.RADIUS_DOWN, "stratos.tool.hint.radius"));
            }
            hints.add(new Hint(Action.RADIUS_TYPE, "stratos.tool.hint.type_radius"));
            hints.add(new Hint(Action.NODES, "stratos.tool.hint.nodes"));
        }
    }

    /**
     * A shape, radius or node-aid key, applied to {@code settings}: the radius steps only with Two radii. False for the
     * height keys.
     */
    static boolean applySetting(ToolInput.Adjustment adjustment, RoadSettings settings) {
        switch (adjustment) {
            case SHAPE -> settings.toggleShape();
            case NODES -> settings.cycleNodeAids();
            case RADIUS_UP, RADIUS_DOWN -> {
                if (settings.shape() == RoadPlanParams.Vertical.Shape.TWO_RADII) {
                    settings.stepRadius(adjustment == ToolInput.Adjustment.RADIUS_UP);
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
