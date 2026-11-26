package org.mtr.aurelia.road.fabric.client.tool;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.mtr.aurelia.road.fabric.client.ui.RoadColours;
import org.mtr.aurelia.road.fabric.client.ui.UiText;
import org.mtr.aurelia.road.tool.PanelPlace;

/**
 * Moving the tool panel (2026-11-26), like Xaero's HUD editor: the panel shows where it is, the player drags it with the
 * left button, a right click puts it back at the top right, and Esc or Done keeps the place. Opened from the hub's
 * Settings. With no tool in hand an outline of the panel's size stands in for it.
 */
public final class PanelMoveScreen extends Screen {
    /** The stand-in outline's height while no panel is drawn: about a road tool's panel. */
    private static final int STAND_IN_HEIGHT = 120;
    /** The edge drawn round the panel: ATR's accent. */
    private static final int OUTLINE = 0xFF000000 | RoadColours.ACCENT;

    private final Screen parent;
    /** Where the press landed inside the panel, while dragging. */
    private int grabX;
    private int grabY;
    private boolean dragging;

    public PanelMoveScreen(Screen parent) {
        super(Text.translatable("stratos.panel_move.title"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        ToolHud.Box box = panel();
        if (ToolHud.panelBox().isEmpty()) {
            graphics.fill(box.x(), box.y(), box.x() + box.width(), box.bottom(), 0x66000000);
        }
        graphics.drawBorder(box.x() - 1, box.y() - 1, box.width() + 2, box.height() + 2, OUTLINE);
        Text help = Text.translatable("stratos.panel_move.help");
        int textWidth = this.textRenderer.getWidth(help);
        int y = this.height / 2;
        graphics.fill((this.width - textWidth) / 2 - 4, y - 4, (this.width + textWidth) / 2 + 4, y + 12, 0xB0000000);
        graphics.drawTextWithShadow(this.textRenderer, UiText.of(help, RoadColours.TEXT_MAIN), (this.width - textWidth) / 2, y, -1);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ToolHud.Box box = panel();
        if (button == 1) {
            ToolHud.setPlace(PanelPlace.DEFAULT);
            return true;
        }
        if (button == 0 && mouseX >= box.x() && mouseX < box.x() + box.width() && mouseY >= box.y() && mouseY < box.bottom()) {
            this.grabX = (int) mouseX - box.x();
            this.grabY = (int) mouseY - box.y();
            this.dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging && button == 0) {
            ToolHud.setPlace(PanelPlace.at((int) mouseX - this.grabX, (int) mouseY - this.grabY, this.width, this.height));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** Where the panel is now: where the last frame drew it, or the stand-in's place. */
    private ToolHud.Box panel() {
        return ToolHud.panelBox().orElseGet(() -> {
            PanelPlace place = ToolHud.place();
            int x = place.moved() ? place.left(this.width, ToolHud.WIDTH, ToolHud.MARGIN) : this.width - ToolHud.MARGIN - ToolHud.WIDTH;
            int y = place.moved() ? place.top(this.height, STAND_IN_HEIGHT, ToolHud.MARGIN, this.height - ToolHud.HOTBAR) : ToolHud.MARGIN;
            return new ToolHud.Box(x, y, ToolHud.WIDTH, STAND_IN_HEIGHT);
        });
    }
}
