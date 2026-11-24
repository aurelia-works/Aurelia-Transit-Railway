/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.geom;

/**
 * A whole-block vector, such as a lane-offset step (spec roads-v1 §5.1): adding it to a block centre gives another
 * block centre. The arithmetic is exact: it throws instead of wrapping round.
 */
public record BlockStep(int dx, int dz) {
    public BlockStep times(int factor) {
        return new BlockStep(StrictMath.multiplyExact(this.dx, factor), StrictMath.multiplyExact(this.dz, factor));
    }

    /**
     * Turned 90° clockwise seen from above, {@code (x, z) → (−z, x)}: the step for heading {@code i + 4} from the
     * step for heading {@code i} (spec roads-v1 §5.1).
     */
    public BlockStep rotateQuarterClockwise() {
        return new BlockStep(StrictMath.negateExact(this.dz), this.dx);
    }

    /** Reflected in the x axis, {@code (x, z) → (x, −z)}: the right-hand-traffic mirror (spec roads-v1 §11.4). */
    public BlockStep mirrored() {
        return new BlockStep(this.dx, StrictMath.negateExact(this.dz));
    }

    public Vec2 toVec2() {
        return new Vec2(this.dx, this.dz);
    }
}
