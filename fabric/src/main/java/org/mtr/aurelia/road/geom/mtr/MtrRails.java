/*
 * Ported from Minecraft Transit Railway's Transport-Simulation-Core, MIT licensed (see LICENSE-MTR.txt);
 * this copy came through Stratos. MTR's notice below is unchanged, and MIT governs this file.
 */
package org.mtr.aurelia.road.geom.mtr;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.mtr.aurelia.road.geom.BlockXZ;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.Vec2;

/**
 * Stratos's link between its own geometry types (G-1) and the literal MTR port in this package. This class is
 * Stratos code, not MTR's: it lives here only because {@link RailMath}'s {@code isValid()} is package-private.
 * Heading {@code i} is MTR angle {@code i}: both count 22.5° steps clockwise from east.
 *
 * <p>From G-6+, {@link #pieces} reads a rail's pieces (MTR's private segment fields) by reflection, so the rasteriser
 * can follow each line and arc exactly as MTR draws it without the port being changed.
 */
public final class MtrRails {
    /**
     * One piece of a rail, with MTR's own numbers, in MTR's block coordinates (positions add 0.5 to x and z).
     * <ul>
     *   <li>An arc: centre {@code (h, k)}, radius {@code r}; a point at parameter {@code t} is
     *       {@code (h + r cos(t/r), k + r sin(t/r))}.</li>
     *   <li>A straight: direction {@code (h, k)}; the parameter runs along it, so its length is {@code |tEnd − tStart|}.</li>
     * </ul>
     * {@code t} runs from {@code tStart} to {@code tEnd}, downwards when MTR reverses the piece.
     */
    public record Piece(boolean straight, double h, double k, double r, double tStart, double tEnd) {
        /** The piece's horizontal length: MTR's {@code count}. */
        public double length() {
            return StrictMath.abs(this.tEnd - this.tStart);
        }
    }

    private static final String[] PIECE_FIELDS = {"isStraight", "h", "k", "r", "tStart", "tEnd"};
    private static final Field[][] FIELDS = new Field[3][PIECE_FIELDS.length];

    private static final Angle[] ANGLES = Angle.values();

    private MtrRails() {
    }

    /** MTR's angle for {@code heading}: {@code Angle.values()[heading.index()]}. */
    public static Angle angle(Heading heading) {
        return ANGLES[heading.index()];
    }

    /**
     * The heading a player faces at Minecraft yaw {@code yaw}, rounded as MTR rounds it when the player places a node
     * ({@code BlockNode.getStateWithAngle} → {@code Angle.getQuadrant(yaw, true)}; spike S6a): the nearest of the 16, a
     * tie going clockwise. MTR stores it modulo 180°; this keeps the side the player faces.
     */
    public static Heading facing(float yaw) {
        return Heading.of((Angle.getQuadrant(yaw, true) + 4) % Heading.COUNT);
    }

    /** The heading for MTR's {@code angle}. */
    public static Heading heading(Angle angle) {
        return Heading.of(angle.ordinal());
    }

    /** The MTR node position of a block at height {@code y}: MTR's node coordinates are block coordinates. */
    public static Position position(BlockXZ block, long y) {
        return new Position(block.x(), y, block.z());
    }

    /**
     * Whether MTR can build {@code rail}. MTR signals "not possible" by leaving all its segment fields at 0; it then
     * has length 0 and every position is the block centre (0.5, y, 0.5).
     */
    public static boolean isValid(RailMath rail) {
        return rail.isValid();
    }

    /**
     * The rail's pieces in order: one or two (a straight; line + arc; arc + line; two arcs), none for a rail MTR can't
     * build. A piece of zero length (such as a line-first rail whose line is 0 long) is left out. Read by reflection
     * from the port's private fields, which keep MTR's names (CLAUDE.md), so nothing in the port changes.
     */
    public static List<Piece> pieces(RailMath rail) {
        List<Piece> pieces = new ArrayList<>(2);
        if (!rail.isValid()) {
            return pieces;
        }
        for (int i = 1; i <= 2; i++) {
            double tStart = number(rail, i, 4);
            double tEnd = number(rail, i, 5);
            if (tStart != tEnd) {
                pieces.add(new Piece(flag(rail, i, 0), number(rail, i, 1), number(rail, i, 2), number(rail, i, 3), tStart, tEnd));
            }
        }
        return pieces;
    }

    /**
     * Where {@code piece} is at parameter {@code t}, as MTR's own (private) {@code getPositionXZ} computes it, called by
     * reflection: a world position, with MTR's +0.5 (use {@link #horizontal} for the block-centre frame).
     */
    public static Vector point(Piece piece, double t) {
        try {
            return (Vector) positionXZ().invoke(null, piece.h(), piece.k(), piece.r(), t, 0.0, piece.straight());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Method positionXZ;

    private static synchronized Method positionXZ() {
        if (positionXZ == null) {
            try {
                Method method = RailMath.class.getDeclaredMethod("getPositionXZ", double.class, double.class, double.class, double.class, double.class,
                        boolean.class);
                method.setAccessible(true);
                positionXZ = method;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("The RailMath port has no getPositionXZ", e);
            }
        }
        return positionXZ;
    }

    private static double number(RailMath rail, int piece, int field) {
        try {
            return field(piece, field).getDouble(rail);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean flag(RailMath rail, int piece, int field) {
        try {
            return field(piece, field).getBoolean(rail);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static synchronized Field field(int piece, int field) {
        Field f = FIELDS[piece][field];
        if (f == null) {
            try {
                f = RailMath.class.getDeclaredField(PIECE_FIELDS[field] + piece);
                f.setAccessible(true);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("The RailMath port has no field " + PIECE_FIELDS[field] + piece, e);
            }
            FIELDS[piece][field] = f;
        }
        return f;
    }

    /**
     * A position {@link RailMath} returns (world coordinates, with MTR's +0.5) as a point in the block-centre frame:
     * x − 0.5 and z − 0.5. The height is dropped; vertical profiles handle it.
     */
    public static Vec2 horizontal(Vector position) {
        return new Vec2(position.x() - 0.5, position.z() - 0.5);
    }
}
