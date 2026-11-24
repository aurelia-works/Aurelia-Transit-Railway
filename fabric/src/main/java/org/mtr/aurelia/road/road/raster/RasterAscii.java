/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.Map;
import org.mtr.aurelia.road.geom.BlockXZ;

/**
 * Renders rasterised columns as text, seen from above (rows are z, increasing downwards; columns are x), with the
 * letters of spec roads-v1 §2: {@code F} footway, {@code K} kerb, {@code E} edge, {@code L} lane, {@code *} a lane-centre
 * column, {@code |} a painted centre-line column, {@code :} an unpainted one, {@code .} outside the road. Used by the
 * ASCII snapshot tests.
 */
public final class RasterAscii {
    private RasterAscii() {
    }

    public static String render(Map<BlockXZ, Column> columns) {
        if (columns.isEmpty()) {
            return "";
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockXZ block : columns.keySet()) {
            minX = StrictMath.min(minX, block.x());
            minZ = StrictMath.min(minZ, block.z());
            maxX = StrictMath.max(maxX, block.x());
            maxZ = StrictMath.max(maxZ, block.z());
        }
        StringBuilder text = new StringBuilder();
        text.append("x ").append(minX).append("..").append(maxX).append(", z ").append(minZ).append("..").append(maxZ).append('\n');
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                text.append(symbol(columns.get(new BlockXZ(x, z))));
            }
            text.append('\n');
        }
        return text.toString();
    }

    public static char symbol(Column column) {
        if (column == null) {
            return '.';
        }
        return switch (column.band()) {
            case LANE -> column.laneCentre() ? '*' : 'L';
            case CENTRE_LINE -> column.painted() ? '|' : ':';
            case FOOTWAY -> 'F';
            case KERB -> 'K';
            case EDGE -> 'E';
            case VERGE -> 'V';
            case HARD_STRIP -> 'H';
            case HARD_SHOULDER -> 'S';
            case CENTRAL_RESERVATION -> 'R';
            case BARRIER -> 'B';
            case GUTTER -> 'G';
        };
    }
}
