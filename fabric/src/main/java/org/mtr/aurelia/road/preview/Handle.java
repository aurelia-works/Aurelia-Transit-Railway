/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

import java.util.Objects;

/**
 * A gizmo the preview draws at a block: a road's start or end handle. From R-3 a handle can be picked up and moved;
 * {@code active} marks the one under the crosshair, or the one held, which is drawn in the accent colour.
 */
public record Handle(int x, int y, int z, Kind kind, boolean active) {
    public enum Kind {
        START,
        END
    }

    public Handle {
        Objects.requireNonNull(kind, "kind");
    }

    /** A handle that isn't aimed at or held. */
    public Handle(int x, int y, int z, Kind kind) {
        this(x, y, z, kind, false);
    }
}
