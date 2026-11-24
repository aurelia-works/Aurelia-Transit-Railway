/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

/** The block the player aims at, and how far away it is (from the eye, in blocks). */
public record Target(int x, int y, int z, double distance) {
    public Target {
        if (!(distance >= 0)) {
            throw new IllegalArgumentException("A distance can't be negative or NaN");
        }
    }

    public boolean sameBlock(Target other) {
        return this.x == other.x && this.y == other.y && this.z == other.z;
    }
}
