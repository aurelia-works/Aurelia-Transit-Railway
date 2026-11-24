/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.nodes;

import java.util.OptionalInt;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.geom.mtr.MtrRails;

/**
 * MTR's node block as Stratos sees it: by runtime id and state strings only, as {@code stratos} never depends on MTR
 * (ADR-0007). Facts from MTR 4.0.5, checked in game in spike S6a (roads-v1 §9):
 * <ul>
 *   <li>The node block is {@value #BLOCK}. Its heading, modulo 180° (0 east, 4 south, as {@code LaneNode.mtrFacing()}),
 *       is stored as {@code facing} (0° if true, else 90°) + {@code is_22_5} (22.5°) + {@code is_45} (45°): for heading
 *       {@code i}, {@code facing = i < 4}, {@code is_22_5 = i odd}, {@code is_45 = i mod 4 ≥ 2}.</li>
 *   <li><b>Every property is named</b> when Stratos writes one ({@link #state}): MTR never sets the block's default
 *       state, so a property left out would take Minecraft's default, {@code true}. A node no rail has joined yet has
 *       {@code is_connected=false}, as a player's does.</li>
 *   <li><b>The yaw rule:</b> a player facing yaw {@code yaw} places a node with heading {@link #facingFor}{@code (yaw)}
 *       ({@code Angle.getQuadrant}, ported in G-2: the nearest of the 16, a tie going clockwise, modulo 180°).</li>
 * </ul>
 */
public final class MtrNodes {
    /** MTR's node block (and the item that places it, "Rail Node"). */
    public static final String BLOCK = "mtr:rail";
    /** The start of MTR's rail connector items' ids ({@code mtr:rail_connector_40} and so on). */
    public static final String CONNECTOR_PREFIX = "mtr:rail_connector_";
    /** How many headings a node can have (modulo 180°). */
    public static final int FACINGS = 8;

    private MtrNodes() {
    }

    /** The state of a node with heading {@code facing} (0–7), no rail joined to it yet; every property named (S6a). */
    public static String state(int facing) {
        return state(facing, false);
    }

    /**
     * The state of a node with heading {@code facing} (0–7), joined to a rail ({@code connected}, as MTR's connector
     * leaves it; T-2's Track) or not; every property named (S6a).
     */
    public static String state(int facing, boolean connected) {
        checkFacing(facing);
        return BLOCK + "[facing=" + (facing < 4) + ",is_22_5=" + (facing % 2 == 1) + ",is_45=" + (facing % 4 >= 2)
                + ",is_connected=" + connected + ",waterlogged=false]";
    }

    /** Whether {@code state} is an MTR node, whatever its properties. */
    public static boolean isNode(String state) {
        return state.equals(BLOCK) || state.startsWith(BLOCK + "[");
    }

    /**
     * The heading (0–7) a node in {@code state} has, read from {@code facing}, {@code is_22_5} and {@code is_45} in any
     * order; empty if it isn't a node or doesn't name all three.
     */
    public static OptionalInt facing(String state) {
        if (!state.startsWith(BLOCK + "[") || !state.endsWith("]")) {
            return OptionalInt.empty();
        }
        Boolean facing = null;
        Boolean is225 = null;
        Boolean is45 = null;
        for (String property : state.substring(BLOCK.length() + 1, state.length() - 1).split(",")) {
            int equals = property.indexOf('=');
            if (equals < 0) {
                return OptionalInt.empty();
            }
            String name = property.substring(0, equals);
            String value = property.substring(equals + 1);
            if (!value.equals("true") && !value.equals("false")) {
                continue;
            }
            boolean on = value.equals("true");
            switch (name) {
                case "facing" -> facing = on;
                case "is_22_5" -> is225 = on;
                case "is_45" -> is45 = on;
                default -> {
                }
            }
        }
        if (facing == null || is225 == null || is45 == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((facing ? 0 : 4) + (is45 ? 2 : 0) + (is225 ? 1 : 0));
    }

    /** The heading (0–7) MTR gives a node placed by a player facing Minecraft yaw {@code yaw} (S6a's yaw rule). */
    public static int facingFor(float yaw) {
        return MtrRails.facing(yaw).index() % FACINGS;
    }

    /** The two names of heading {@code facing} (0–7), such as {@code E} and {@code W}: a node faces both ways along it. */
    public static String[] names(int facing) {
        checkFacing(facing);
        return new String[] {Heading.of(facing).name(), Heading.of(facing + FACINGS).name()};
    }

    /** Whether {@code itemId} (a runtime id) is MTR's Rail Node or one of its rail connectors: the overlay shows for them. */
    public static boolean isNodeItem(String itemId) {
        return itemId.equals(BLOCK) || itemId.startsWith(CONNECTOR_PREFIX);
    }

    private static void checkFacing(int facing) {
        if (facing < 0 || facing >= FACINGS) {
            throw new IllegalArgumentException("A node's heading is 0 to 7, not " + facing);
        }
    }
}
