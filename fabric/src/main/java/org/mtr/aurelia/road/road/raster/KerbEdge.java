/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.BlockXZ;

/**
 * Where a kerb block meets the road (task B-1; ADR-0044): the side its kerb stone runs along, and its shape. A kerb is the
 * footway's road-side block: a 125 mm stone along the edge the road is on, flags behind it.
 * <ul>
 *   <li>{@link Shape#STRAIGHT}: the road on side {@code facing}; the stone runs along that edge.</li>
 *   <li>{@link Shape#OUTER}: the road on {@code facing} and the side clockwise from it (seen from above); the stone runs
 *       along both edges.</li>
 *   <li>{@link Shape#INNER}: the road only past the corner between {@code facing} and the side clockwise from it; the
 *       stone is a small square in that corner, closing the step between two kerbs on a bend.</li>
 * </ul>
 * The names are the kerb block's {@code facing} and {@code shape} values.
 */
public record KerbEdge(Facing facing, Shape shape) {
    public KerbEdge {
        Objects.requireNonNull(facing, "facing");
        Objects.requireNonNull(shape, "shape");
    }

    /** A side of a block, as Minecraft names it: north is −z, east is +x. */
    public enum Facing {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0);

        private final BlockStep step;

        Facing(int dx, int dz) {
            this.step = new BlockStep(dx, dz);
        }

        /** The step to the neighbour on this side. */
        public BlockStep step() {
            return this.step;
        }

        /** The next side clockwise, seen from above (north, east, south, west). */
        public Facing clockwise() {
            return values()[(ordinal() + 1) % 4];
        }

        /** Its name in a block state, such as {@code north}. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A kerb's shape: straight, or a corner (its name in a block state is the lowercase name). */
    public enum Shape {
        STRAIGHT, INNER, OUTER;

        /** Its name in a block state, such as {@code outer}. */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The kerb at {@code at}, if the road touches it: {@code road} says whether a column is the road's (lower than the
     * footway). Sides first: one gives a straight kerb, two beside each other an outer corner. With no side on the road,
     * a corner touching it gives an inner corner. Several ways at once (a strip one block wide) take the first in the
     * order north, east, south, west; none, no kerb.
     */
    public static Optional<KerbEdge> at(BlockXZ at, Predicate<BlockXZ> road) {
        boolean[] side = new boolean[4];
        int sides = 0;
        for (Facing facing : Facing.values()) {
            side[facing.ordinal()] = road.test(at.plus(facing.step()));
            sides += side[facing.ordinal()] ? 1 : 0;
        }
        if (sides > 0) {
            for (Facing facing : Facing.values()) {
                if (side[facing.ordinal()] && side[facing.clockwise().ordinal()]) {
                    return Optional.of(new KerbEdge(facing, Shape.OUTER));
                }
            }
            for (Facing facing : Facing.values()) {
                if (side[facing.ordinal()]) {
                    return Optional.of(new KerbEdge(facing, Shape.STRAIGHT));
                }
            }
        }
        for (Facing facing : Facing.values()) {
            BlockStep corner = new BlockStep(facing.step().dx() + facing.clockwise().step().dx(), facing.step().dz() + facing.clockwise().step().dz());
            if (road.test(at.plus(corner))) {
                return Optional.of(new KerbEdge(facing, Shape.INNER));
            }
        }
        return Optional.empty();
    }
}
