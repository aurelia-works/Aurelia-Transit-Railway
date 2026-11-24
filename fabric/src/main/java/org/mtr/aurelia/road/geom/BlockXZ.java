/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

/**
 * A block column in the block-centre frame: block {@code (x, z)} has its centre at {@code (x, z)}. Lane nodes and
 * MTR nodes are always blocks. The arithmetic is exact: it throws instead of wrapping round.
 */
public record BlockXZ(int x, int z) {
    /** The block's centre, as a frame point. */
    public Vec2 centre() {
        return new Vec2(this.x, this.z);
    }

    /** The block {@code step} away. */
    public BlockXZ plus(BlockStep step) {
        return new BlockXZ(StrictMath.addExact(this.x, step.dx()), StrictMath.addExact(this.z, step.dz()));
    }

    /** The whole-block vector from this block to {@code other}. */
    public BlockStep stepTo(BlockXZ other) {
        return new BlockStep(StrictMath.subtractExact(other.x, this.x), StrictMath.subtractExact(other.z, this.z));
    }
}
