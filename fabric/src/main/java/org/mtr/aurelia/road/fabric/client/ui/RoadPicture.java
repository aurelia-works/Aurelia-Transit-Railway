/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.ui;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.Section;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteRole;

/**
 * A layout as a little picture (task UI-1a; owner, 2026-10-15): the road running left to right, seen from above, its
 * columns as the profile gives them (footway, kerb, lanes, centre line) in a palette's map colours, on grass. Drawn in
 * the screen's own pixels, so a one-block kerb still shows at GUI scale 1. Every layout gets one from its profile. From UI-1b the tool panel draws one beside a road's layout and palette.
 */
public final class RoadPicture {
    private static final int GRASS = 0x4D7A34;
    /** How much lighter a kerb is drawn than its block's map colour, so the edge shows against the footway. */
    private static final double KERB_LIGHTEN = 0.25;

    private RoadPicture() {
    }

    /** Draws {@code profile} in {@code palette}'s colours, filling {@code w} by {@code h} GUI pixels at ({@code x}, {@code y}). */
    public static void draw(DrawContext graphics, RoadProfile profile, Palette palette, int x, int y, int w, int h) {
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        MatrixStack pose = graphics.getMatrices();
        pose.push();
        pose.translate(x, y, 0);
        pose.scale((float) (1 / scale), (float) (1 / scale), 1F);
        int width = (int) StrictMath.round(w * scale);
        int height = (int) StrictMath.round(h * scale);
        graphics.fill(0, 0, width, height, 0xFF000000 | GRASS);
        // A block's height in screen pixels: the widest built-in layout (17) fills the picture less a GUI pixel each side.
        double block = (height - 2 * scale) / StrictMath.max(17, profile.width());
        double at = (height - profile.width() * block) / 2;
        for (Section section : profile.sections()) {
            int top = (int) StrictMath.round(at);
            int bottom = (int) StrictMath.round(at + section.width() * block);
            graphics.fill(0, top, width, bottom, 0xFF000000 | colour(palette, section.band()));
            section.marking().ifPresent(marking -> line(graphics, palette, marking, width, (top + bottom) / 2, block));
            at += section.width() * block;
        }
        pose.pop();
    }

    /** A line marking along the picture: solid, or dashed in its pattern; at least a pixel thick. */
    private static void line(DrawContext graphics, Palette palette, LineMarking marking, int width, int middle, double block) {
        int colour = 0xFF000000 | PaletteRole.byId(marking.colour().paletteRole()).flatMap(palette::family)
                .map(family -> BlockColours.map(family.full())).orElse(0xFFFFFF);
        int thickness = StrictMath.max(1, (int) StrictMath.round(block * 0.4));
        int top = middle - thickness / 2;
        if (marking.isSolid()) {
            graphics.fill(0, top, width, top + thickness, colour);
            return;
        }
        double mark = marking.mark() * block;
        double period = (marking.mark() + marking.gap()) * block;
        for (double from = block; from < width; from += period) {
            graphics.fill((int) StrictMath.round(from), top, (int) StrictMath.round(StrictMath.min(width, from + mark)), top + thickness, colour);
        }
    }

    /** A band's colour: its palette role's block, a kerb a little lighter; grass where the palette has no block for it. */
    private static int colour(Palette palette, Band band) {
        int rgb = PaletteRole.byId(band.paletteRole()).flatMap(palette::family).map(family -> BlockColours.map(family.full()))
                .orElse(band == Band.VERGE || band == Band.CENTRAL_RESERVATION ? GRASS : BlockColours.NO_COLOUR);
        return band == Band.KERB ? lighter(rgb) : rgb;
    }

    private static int lighter(int rgb) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        return (int) (r + (255 - r) * KERB_LIGHTEN) << 16 | (int) (g + (255 - g) * KERB_LIGHTEN) << 8 | (int) (b + (255 - b) * KERB_LIGHTEN);
    }
}
