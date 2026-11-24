/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.mtr.aurelia.road.preview.Handle;

/**
 * What a tool wants shown in the world: its handles and, once it has enough points, the plan to work out and preview
 * (R-2: worked out off the client thread, then drawn with these handles). Compared by value.
 */
public record ToolOutput(Optional<PlanJob> job, List<Handle> handles) {
    public static final ToolOutput EMPTY = new ToolOutput(Optional.empty(), List.of());

    public ToolOutput {
        Objects.requireNonNull(job, "job");
        handles = List.copyOf(handles);
    }

    public boolean isEmpty() {
        return this.job.isEmpty() && this.handles.isEmpty();
    }
}
