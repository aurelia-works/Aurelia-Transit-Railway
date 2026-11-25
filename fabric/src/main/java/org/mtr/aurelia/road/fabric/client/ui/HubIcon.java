/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.road.RoadPlanParams;

/**
 * The hub's flat icons (task UI-1a; owner, 2026-10-15: flat, from the approved mockups). Drawn from icon sheets made by
 * {@code icons/make_icons.py}: one per GUI scale and size, so each icon is pixel for pixel at every scale, as the body
 * font is (spike S11). The order is the sheets' (icons.html). Glyphs are white and tinted when drawn; pictures (the
 * road tools, Roads, Blue wool, MTR nodes) keep their own colours under a white tint. From UI-1b the sheets also hold the
 * tool panel's values and keys, the Edit roads box's choices and the message strip's icons, in two more sizes.
 */
public enum HubIcon {
    ROADS, TERRAIN, FOREST, BLUEPRINTS, TRANSIT, ROAD_STRAIGHT, ROAD_CURVE, LANE_NODES,
    EDIT_ROADS, TRACK, WOOL, MTR_NODES, NONE, PUT_AWAY, GLOBE, GEAR,
    WARN, BAKE, REDRAW, INFO, UNDO, REDO, HEIGHT, SHAPE,
    RADIUS, KEYBOARD, LENGTH, HEADING, SLOPE, BLOCKS, PALETTE, REBUILD,
    REBUILD_IN, DELETE, EDIT, CANCEL, MOUSE_LEFT, MOUSE_RIGHT, ERROR, JOINED,
    // UI-1c: Settings' Preview page.
    EYE,
    // Polish pass: the junction tool, the crossing option, a junction's size and a road joined to another. Drawn at the end so
    // the order of the sheets above stays as it was.
    JUNCTION, CROSSING, SIZE, LINK;

    /** A tool or module tile's icon, in GUI pixels. */
    public static final int LARGE = 20;
    /** An Edit roads tile's icon, in GUI pixels (UI-1b). */
    public static final int MEDIUM = 16;
    /** A small tile's or a tab's icon, in GUI pixels. */
    public static final int SMALL = 12;
    /** A tool panel value's or key's icon, in GUI pixels (UI-1b). */
    public static final int TINY = 8;
    private static final int COLUMNS = 8;
    private static final int ROWS = 6;

    /**
     * Draws the icon at ({@code x}, {@code y}), {@link #LARGE}, {@link #MEDIUM}, {@link #SMALL} or {@link #TINY} GUI
     * pixels square, multiplied by {@code argb} (white for its own colours; the alpha fades it).
     */
    public void draw(DrawContext graphics, int x, int y, int size, int argb) {
        int scale = StrictMath.max(1, StrictMath.min(4, (int) MinecraftClient.getInstance().getWindow().getScaleFactor()));
        int cell = size * scale;
        Identifier sheet = new Identifier(StratosMod.MOD_ID,
                "textures/gui/icons_" + sheet(size) + "_" + scale + ".png");
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setShaderColor((argb >> 16 & 0xFF) / 255F, (argb >> 8 & 0xFF) / 255F, (argb & 0xFF) / 255F, (argb >>> 24) / 255F);
        graphics.drawTexture(sheet, x, y, size, size, (ordinal() % COLUMNS) * cell, (ordinal() / COLUMNS) * cell, cell, cell,
                COLUMNS * cell, ROWS * cell);
        graphics.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    private static String sheet(int size) {
        return switch (size) {
            case LARGE -> "large";
            case MEDIUM -> "medium";
            case SMALL -> "small";
            case TINY -> "tiny";
            default -> throw new IllegalArgumentException("No icon sheet for " + size + " pixels");
        };
    }

    /** A module's icon, by its id ({@code StratosModule.id()}); another module's gets the info glyph. */
    public static HubIcon forModule(String moduleId) {
        return switch (moduleId) {
            case "roads" -> ROADS;
            case "terrain" -> TERRAIN;
            case "forest" -> FOREST;
            case "blueprints" -> BLUEPRINTS;
            case "transit" -> TRANSIT;
            default -> INFO;
        };
    }

    /** A tool's icon, by its id ({@code Tool.id()}); a tool without one gets the info glyph. */
    public static HubIcon forTool(String toolId) {
        return switch (toolId) {
            case "roads.straight" -> ROAD_STRAIGHT;
            case "roads.curve" -> ROAD_CURVE;
            case "roads.nodes" -> LANE_NODES;
            case "roads.edit" -> EDIT_ROADS;
            case "roads.junction" -> JUNCTION;
            default -> INFO;
        };
    }

    /** A node aid's icon. */
    public static HubIcon forNodeAids(RoadPlanParams.NodeAids aids) {
        return switch (aids) {
            case TRACK -> TRACK;
            case WOOL -> WOOL;
            case PREORIENTED -> MTR_NODES;
            case NONE -> NONE;
        };
    }

    /** Whether the icon is a picture in its own colours rather than a glyph to tint. */
    public boolean picture() {
        return switch (this) {
            case ROADS, ROAD_STRAIGHT, ROAD_CURVE, LANE_NODES, EDIT_ROADS, WOOL, MTR_NODES, WARN, BAKE, INFO, SHAPE, RADIUS, KEYBOARD,
                    LENGTH, HEADING, SLOPE, BLOCKS, PALETTE, REBUILD_IN, EDIT, MOUSE_LEFT, MOUSE_RIGHT, ERROR, JOINED, JUNCTION, CROSSING -> true;
            default -> false;
        };
    }
}
