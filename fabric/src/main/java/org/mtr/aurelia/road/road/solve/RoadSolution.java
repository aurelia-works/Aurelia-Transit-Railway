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

/**
 * What {@link RoadSolver} made of a road's two ends (task R-5): its solved pieces in order along the road, joined end
 * to end, or a refusal (roads-v1 §12).
 */
public sealed interface RoadSolution permits RoadSolution.Solved, RoadSolution.Refused {
    /** One piece, or (R-5) a graded bend split into two: each piece starts where the one before it ends. */
    record Solved(List<PieceSolution.Solved> pieces) implements RoadSolution {
        public Solved {
            pieces = List.copyOf(pieces);
            if (pieces.isEmpty()) {
                throw new IllegalArgumentException("A road has at least one piece");
            }
        }

        /** Whether the road was split into more than one piece. */
        public boolean isSplit() {
            return this.pieces.size() > 1;
        }
    }

    /** Refused with a roads-v1 §12 code and the arguments its message needs, in order. */
    record Refused(String code, Map<String, String> args) implements RoadSolution {
        public Refused {
            Objects.requireNonNull(code, "code");
            args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
    }
}
