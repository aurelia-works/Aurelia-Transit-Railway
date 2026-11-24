/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;

/**
 * The road tools' boxes (the radius box, a tool's question) in ATR's look: a dark rounded panel with a soft shadow, and
 * the tool panel's header, a dark accent bar with an icon and a title. Drawn with fills only, so it costs no texture.
 */
public final class RoadPanel {
    /** The header bar's height. */
    public static final int HEADER = 16;
    private static final int RADIUS = 4;

    private RoadPanel() {
    }

    /** Draws the panel at (x, y), w by h, with {@code icon} and {@code title} in its header. */
    public static void draw(DrawContext graphics, TextRenderer font, int x, int y, int w, int h, HubIcon icon, Text title) {
        AureliaChrome.shadow(graphics, x, y, w, h, RADIUS);
        AureliaChrome.rounded(graphics, x, y, w, h, RADIUS, AureliaPalette.SURFACE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + HEADER, RoadColours.ACCENT_DARK);
        graphics.fill(x + 1, y + 1, x + 3, y + HEADER, RoadColours.ACCENT);
        AureliaChrome.roundedFrame(graphics, x, y, w, h, RADIUS, AureliaPalette.LINE);
        icon.draw(graphics, x + 6, y + (HEADER - HubIcon.SMALL) / 2, HubIcon.SMALL, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
        graphics.drawText(font, UiText.fit(font, title, w - 6 - HubIcon.SMALL - 4 - 6), x + 6 + HubIcon.SMALL + 4,
                y + (HEADER - font.fontHeight) / 2 + 1, RoadColours.TEXT_MAIN, false);
    }

    /** A flat button in ATR's look, drawn by {@code painter}; {@code primary} fills it with the accent. */
    public static final class Button extends PressableWidget {
        /** Draws what's on the button. */
        public interface Painter {
            void paint(DrawContext graphics, int x, int y, int w, int h);
        }

        private final boolean primary;
        private final Painter painter;
        private final Runnable onPress;

        public Button(int width, int height, Text narration, boolean primary, Painter painter, Runnable onPress) {
            super(0, 0, width, height, narration);
            this.primary = primary;
            this.painter = painter;
            this.onPress = onPress;
        }

        /** A button with {@code label} centred on it. */
        public static Button text(TextRenderer font, Text label, int height, boolean primary, Runnable onPress) {
            return new Button(font.getWidth(label) + 16, height, label, primary, (graphics, x, y, w, h) ->
                    graphics.drawText(font, label, x + (w - font.getWidth(label)) / 2, y + (h - 8) / 2, primary ? RoadColours.ON_ACCENT : RoadColours.TEXT_MAIN,
                            false), onPress);
        }

        @Override
        public void onPress() {
            this.onPress.run();
        }

        @Override
        protected void renderButton(DrawContext graphics, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            boolean lit = isHovered() || isFocused();
            int fill = this.primary ? (lit ? RoadColours.ACCENT_STRONG : AureliaPalette.ACCENT) : (lit ? RoadColours.BG_HOVER_STRONG : RoadColours.BG_HOVER);
            graphics.fill(x, y, x + w, y + h, fill);
            AureliaChrome.frame(graphics, x, y, w, h, lit ? RoadColours.ACCENT : AureliaPalette.LINE);
            this.painter.paint(graphics, x, y, w, h);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
