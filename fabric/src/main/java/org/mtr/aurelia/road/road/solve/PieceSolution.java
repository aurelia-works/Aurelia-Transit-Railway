/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.solve;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.mtr.aurelia.road.geom.mtr.RailMath;
import org.mtr.aurelia.road.road.RoadPlanParams;

/** What {@link PieceSolver} made of a road piece: a rail per lane, or a refusal (roads-v1 §12). */
public sealed interface PieceSolution permits PieceSolution.Solved, PieceSolution.Refused {
    /**
     * Every lane has its rail: the centre line's two ends (world blocks; from G-6+), its rail, and the lanes left to
     * right. Rails are relative to the start node.
     */
    record Solved(RoadPlanParams.End start, RoadPlanParams.End end, RailMath reference, List<LaneRail> lanes) implements PieceSolution {
        public Solved {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(reference, "reference");
            lanes = List.copyOf(lanes);
        }
    }

    /** Refused with a roads-v1 §12 code and the arguments its message needs, in order. */
    record Refused(String code, Map<String, String> args) implements PieceSolution {
        public Refused {
            Objects.requireNonNull(code, "code");
            args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
    }

    default boolean isSolved() {
        return this instanceof Solved;
    }
}
