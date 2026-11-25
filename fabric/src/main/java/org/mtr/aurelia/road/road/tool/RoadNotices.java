/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import java.util.Map;
import java.util.Optional;
import org.mtr.aurelia.road.proto.PlanResult;
import org.mtr.aurelia.road.road.RoadJoins;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.tool.HudModel.Level;
import org.mtr.aurelia.road.tool.HudModel.Notice;
import org.mtr.aurelia.road.tool.Planned;

/**
 * The road tools' message strip (R-2, R-5), in order of what matters most: why the road wasn't sent; else why it is
 * refused; else a warning; else news in the accent colour: a bend split in two ({@link RoadPlanner#BEND_SPLIT}), or the
 * road end the road joins ({@code JOINED}, or {@code JOINED_TAPER} where the layout changes; args {@code layout}, the
 * joined road's profile id).
 */
final class RoadNotices {
    /** The news that the road joins a road end of the same layout. */
    static final String JOINED = "JOINED";
    /** The news that the road joins a road end of another layout, so the layout changes there. */
    static final String JOINED_TAPER = "JOINED_TAPER";

    private RoadNotices() {
    }

    static Optional<Notice> notice(Notice notSent, Optional<Planned> current, Optional<RoadPlanParams> params, JoinableRoads joins) {
        if (notSent != null) {
            return Optional.of(notSent);
        }
        if (current.isPresent()) {
            PlanResult result = current.get().result();
            if (!result.isPlanned()) {
                return Optional.of(Notice.reason(Level.ERROR, result.code().orElseThrow(), result.args()));
            }
            Optional<PlanResult.Warning> warning = result.warnings().stream().filter(w -> !w.code().equals(RoadPlanner.BEND_SPLIT)).findFirst();
            if (warning.isPresent()) {
                return Optional.of(Notice.reason(Level.WARNING, warning.get().code(), warning.get().args()));
            }
            Optional<PlanResult.Warning> split = result.warnings().stream().filter(w -> w.code().equals(RoadPlanner.BEND_SPLIT)).findFirst();
            if (split.isPresent()) {
                return Optional.of(Notice.reason(Level.INFO, split.get().code(), split.get().args()));
            }
        }
        if (params.isEmpty()) {
            return Optional.empty();
        }
        for (RoadPlanParams.End end : params.get().ends()) {
            if (end.snapRoadId().isPresent()) {
                int port = end.snapPortIndex().orElse(0);
                Optional<String> layout = port >= RoadJoins.ARM_PORTS ? joins.armProfile(end.snapRoadId().get(), port) // G-11
                        : joins.profile(end.snapRoadId().get());
                if (layout.isPresent()) {
                    String code = layout.get().equals(params.get().profileId()) ? JOINED : JOINED_TAPER;
                    return Optional.of(Notice.reason(Level.INFO, code, Map.of("layout", layout.get())));
                }
            }
        }
        return Optional.empty();
    }

    /** Whether {@code planning}, the road as the planner gets it, runs the other way from {@code drawn}, as the player set it. */
    static boolean turned(Optional<RoadPlanParams> drawn, Optional<RoadPlanParams> planning) {
        if (drawn.isEmpty() || planning.isEmpty()) {
            return false;
        }
        RoadPlanParams.End a = drawn.get().ends().get(0);
        RoadPlanParams.End b = planning.get().ends().get(0);
        return a.x() != b.x() || a.y() != b.y() || a.z() != b.z();
    }
}
