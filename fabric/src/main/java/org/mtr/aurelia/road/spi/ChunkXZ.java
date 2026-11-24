/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

/** A chunk column's position: block coordinates divided by 16, rounded down. */
public record ChunkXZ(int x, int z) {
    /** The chunk containing block {@code (blockX, blockZ)}. */
    public static ChunkXZ ofBlock(int blockX, int blockZ) {
        return new ChunkXZ(StrictMath.floorDiv(blockX, 16), StrictMath.floorDiv(blockZ, 16));
    }
}
