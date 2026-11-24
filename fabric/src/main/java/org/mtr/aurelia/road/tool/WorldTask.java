/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A world task a tool asks the server to run (task B-3): {@code task} is one of {@code Protocol.TASK_*}, {@code roadId}
 * the road it is about, if any (a road to bake to vanilla).
 */
public record WorldTask(String task, Optional<UUID> roadId) {
    public WorldTask {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(roadId, "roadId");
    }
}
