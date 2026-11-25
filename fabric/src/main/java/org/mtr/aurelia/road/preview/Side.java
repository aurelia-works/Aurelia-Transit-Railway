/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

/** The six sides of a block, in Minecraft's order (down, up, north, south, west, east). North is −z, east is +x. */
public enum Side {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private final int dx;
    private final int dy;
    private final int dz;

    Side(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public int dx() {
        return this.dx;
    }

    public int dy() {
        return this.dy;
    }

    public int dz() {
        return this.dz;
    }

    /** The axis the side faces along. */
    public Axis axis() {
        return this.dx != 0 ? Axis.X : this.dy != 0 ? Axis.Y : Axis.Z;
    }

    /** Whether the side faces the positive direction of its axis. */
    public boolean positive() {
        return this.dx + this.dy + this.dz > 0;
    }
}
