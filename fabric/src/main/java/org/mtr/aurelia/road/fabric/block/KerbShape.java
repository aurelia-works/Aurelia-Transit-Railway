/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.block;

import java.util.Locale;
import net.minecraft.util.StringIdentifiable;

/** A kerb's shape (task B-1), as core's {@code KerbEdge.Shape} names it in the Stratos UK palette's states. */
public enum KerbShape implements StringIdentifiable {
    /** The stone along the side the kerb faces. */
    STRAIGHT,
    /** The stone only in the corner between the side it faces and the next clockwise, closing a step on a bend. */
    INNER,
    /** The stone along the side it faces and the next clockwise. */
    OUTER;

    @Override
    public String asString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
