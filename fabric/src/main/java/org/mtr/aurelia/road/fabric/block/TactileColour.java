/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.block;

import java.util.Locale;
import net.minecraft.util.StringIdentifiable;

/** A dropped kerb's tactile paving (task B-1; research 13): red at signal crossings, buff elsewhere. */
public enum TactileColour implements StringIdentifiable {
    BUFF, RED;

    @Override
    public String asString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
