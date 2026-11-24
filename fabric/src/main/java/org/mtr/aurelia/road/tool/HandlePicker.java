/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import java.util.List;
import java.util.Optional;
import org.mtr.aurelia.road.preview.Handle;

/**
 * Which handle the player aims at, to pick it up (owner, 2026-10-06: R-3). A handle counts when the aimed block is its
 * block or one of the 8 around it, at the same height or one above or below; the nearest counts, and on a tie the later
 * one (the end before the start).
 *
 * <p><b>Arming:</b> right after the player fixes an end or puts a handle down, they are still aiming at it, and the next
 * use must place the road (R-2's "use, use" flow). So a tool keeps a picker per road: it can pick a handle only once the
 * crosshair has been off every handle since it was {@link #disarm disarmed}.
 */
public final class HandlePicker {
    private boolean armed;

    /** After an end is fixed or a handle put down: nothing can be picked until the aim leaves the handles. */
    public void disarm() {
        this.armed = false;
    }

    /** Every aim: once the crosshair is off every handle, handles can be picked. */
    public void aim(Optional<Target> aim, List<Handle> handles) {
        if (aimed(aim, handles).isEmpty()) {
            this.armed = true;
        }
    }

    /** The handle the player could pick up now, if any. */
    public Optional<Handle> pickable(Optional<Target> aim, List<Handle> handles) {
        return this.armed ? aimed(aim, handles) : Optional.empty();
    }

    public static Optional<Handle> aimed(Optional<Target> aim, List<Handle> handles) {
        if (aim.isEmpty()) {
            return Optional.empty();
        }
        Target target = aim.get();
        Handle best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Handle handle : handles) {
            int dx = target.x() - handle.x();
            int dy = target.y() - handle.y();
            int dz = target.z() - handle.z();
            if (StrictMath.abs(dx) > 1 || StrictMath.abs(dy) > 1 || StrictMath.abs(dz) > 1) {
                continue;
            }
            int distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                best = handle;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
