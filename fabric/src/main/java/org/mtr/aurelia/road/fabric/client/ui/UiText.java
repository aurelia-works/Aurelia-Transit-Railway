/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

/**
 * Body text for the road editor. Stratos drew its own font; in ATR the editor uses the game's font, as ATR's screens do.
 */
public final class UiText {
    /** {@code text} in an ARGB colour. */
    public static MutableText of(Text text, int argb) {
        return text.copy().styled(style -> style.withColor(TextColor.fromRgb(argb & 0xFFFFFF)));
    }

    /** The translation of {@code key} in an ARGB colour. */
    public static MutableText translatable(String key, int argb) {
        return of(Text.translatable(key), argb);
    }

    /** {@code text} cut to {@code room} GUI pixels, ending in an ellipsis where it was cut, in its own style. */
    public static Text fit(TextRenderer font, Text text, int room) {
        if (font.getWidth(text) <= room) {
            return text;
        }
        Style style = text.getStyle();
        String shown = text.getString();
        while (!shown.isEmpty() && font.getWidth(Text.literal(shown + "…").setStyle(style)) > room) {
            shown = shown.substring(0, shown.length() - 1);
        }
        return Text.literal(shown.stripTrailing() + "…").setStyle(style);
    }

    private UiText() {
    }
}
