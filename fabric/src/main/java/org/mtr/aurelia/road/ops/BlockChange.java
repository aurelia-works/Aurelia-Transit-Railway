/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.util.Objects;

/**
 * One block an operation changed: its position, its state before, and its state after; from F-1+ also its block
 * entity's data (a chest's items, a sign's text) before and after, as SNBT, or null where it had none.
 */
public record BlockChange(int x, int y, int z, String before, String after, String beforeData, String afterData) {
    public BlockChange {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }

    /** A change of a block with no block entity before or after. */
    public BlockChange(int x, int y, int z, String before, String after) {
        this(x, y, z, before, after, null, null);
    }

    /** The same position with before and after swapped: what undoing it writes. */
    public BlockChange inverse() {
        return new BlockChange(this.x, this.y, this.z, this.after, this.before, this.afterData, this.beforeData);
    }

    /** Whether it carries block-entity data, before or after. */
    public boolean hasData() {
        return this.beforeData != null || this.afterData != null;
    }

    /** A key for the position alone, for comparing sets of changes. */
    public long positionKey() {
        return positionKey(this.x, this.y, this.z);
    }

    /** x and z in 26 bits each, y in 12 bits: covers ±33 million blocks sideways and every build height. */
    public static long positionKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
