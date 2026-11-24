/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

import java.util.List;
import java.util.Objects;
import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * What the preview draws for one 16³ section (spec mod-suite-v1 §6.3): the faces of the plan's blocks that face away
 * from the plan (each with its block's state, as an index into {@code states}), and the block-grid edges of those faces.
 * Coordinates are absolute block coordinates. Compared by value, so the renderer rebuilds a section only when its mesh
 * changed.
 */
public record SectionMesh(BlockPlan.SectionPos pos, List<String> states, List<Face> faces, List<Edge> edges) {
    /**
     * The {@code side} face of the block at {@code (x, y, z)}, whose state is {@code states().get(state)}, for a block
     * {@code height8} eighths of a block high (8 for a full block, 4 for a bottom slab; G-7).
     */
    public record Face(int x, int y, int z, Side side, int state, int height8) {
        public Face {
            Objects.requireNonNull(side, "side");
            if (height8 < 1 || height8 > 8) {
                throw new IllegalArgumentException("A block is 1 to 8 eighths high, not " + height8);
            }
        }

        /** A full block's face. */
        public Face(int x, int y, int z, Side side, int state) {
            this(x, y, z, side, state, 8);
        }
    }

    /**
     * An outline edge from {@code (x, y + lift8/8, z)}: one block along {@code axis} for X and Z, {@code length8}
     * eighths up for Y (G-7: the edges of partial blocks; the block grid is lift 0, length 8).
     */
    public record Edge(int x, int y, int z, Axis axis, int lift8, int length8) {
        public Edge {
            Objects.requireNonNull(axis, "axis");
            if (lift8 < 0 || lift8 > 7 || length8 < 1 || length8 > 8 || (axis != Axis.Y && length8 != 8)) {
                throw new IllegalArgumentException("Not an edge: lift " + lift8 + ", length " + length8 + " along " + axis);
            }
        }

        /** A block-grid edge. */
        public Edge(int x, int y, int z, Axis axis) {
            this(x, y, z, axis, 0, 8);
        }
    }

    public SectionMesh {
        Objects.requireNonNull(pos, "pos");
        states = List.copyOf(states);
        faces = List.copyOf(faces);
        edges = List.copyOf(edges);
    }
}
