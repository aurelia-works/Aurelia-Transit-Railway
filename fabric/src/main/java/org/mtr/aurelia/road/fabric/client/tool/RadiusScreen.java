/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.tool;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.road.road.tool.RoadSettings;
import org.mtr.aurelia.road.fabric.client.ui.HubIcon;
import org.mtr.aurelia.road.fabric.client.ui.RoadColours;
import org.mtr.aurelia.road.fabric.client.ui.RoadPanel;
import org.mtr.aurelia.road.fabric.client.ui.UiText;

/**
 * The box the End key opens (R-4): type Two radii's radius, in blocks, and Enter sets it. A value outside
 * {@link RoadSettings#MIN_RADIUS}..{@link RoadSettings#MAX_RADIUS} says so and keeps the box open. In ATR's look.
 */
public final class RadiusScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int PAD = 8;
    private static final int FIELD_HEIGHT = 16;
    private static final int BUTTON_HEIGHT = 16;
    private static final int GAP = 6;

    private TextFieldWidget field;
    private Text problem = Text.empty();
    private int left;
    private int top;
    private int panelHeight;
    private List<OrderedText> help = List.of();

    public RadiusScreen() {
        super(Text.translatable("stratos.radius.title"));
    }

    @Override
    protected void init() {
        String typed = this.field == null ? RoadSettings.format(StratosTools.roadSettings().radius()) : this.field.getText();
        this.help = this.textRenderer.wrapLines(UiText.of(Text.translatable("stratos.radius.help", format(RoadSettings.MIN_RADIUS),
                format(RoadSettings.MAX_RADIUS)), RoadColours.TEXT_MUTED), WIDTH - 2 * PAD);
        this.panelHeight = RoadPanel.HEADER + PAD + this.help.size() * 10 + GAP + FIELD_HEIGHT + GAP + 10 + GAP + BUTTON_HEIGHT + PAD;
        this.left = (this.width - WIDTH) / 2;
        this.top = (this.height - this.panelHeight) / 2;
        int y = this.top + RoadPanel.HEADER + PAD + this.help.size() * 10 + GAP;
        // The field draws no box of its own: the panel draws ATR's, and the text sits inside it.
        this.field = new TextFieldWidget(this.textRenderer, this.left + PAD + 4, y + (FIELD_HEIGHT - 8) / 2, WIDTH - 2 * PAD - 8, 9,
                Text.translatable("stratos.radius.title"));
        this.field.setDrawsBackground(false);
        this.field.setTextPredicate(text -> text.matches("[0-9]{0,6}(\\.[0-9]?)?"));
        this.field.setText(typed);
        addDrawableChild(this.field);
        setInitialFocus(this.field);
        int buttonsY = y + FIELD_HEIGHT + GAP + 10 + GAP;
        RoadPanel.Button set = RoadPanel.Button.text(this.textRenderer, Text.translatable("stratos.radius.set"), BUTTON_HEIGHT, true, this::set);
        set.setPosition(this.left + PAD, buttonsY);
        addDrawableChild(set);
        RoadPanel.Button cancel = RoadPanel.Button.text(this.textRenderer, Text.translatable("stratos.radius.cancel"), BUTTON_HEIGHT, false, this::close);
        cancel.setPosition(this.left + PAD + set.getWidth() + 4, buttonsY);
        addDrawableChild(cancel);
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        RoadPanel.draw(graphics, this.textRenderer, this.left, this.top, WIDTH, this.panelHeight, HubIcon.RADIUS, this.title);
        int y = this.top + RoadPanel.HEADER + PAD;
        for (OrderedText line : this.help) {
            graphics.drawText(this.textRenderer, line, this.left + PAD, y, 0xFFFFFFFF, false);
            y += 10;
        }
        y += GAP;
        graphics.fill(this.left + PAD, y, this.left + WIDTH - PAD, y + FIELD_HEIGHT, AureliaPalette.FIELD);
        AureliaChrome.frame(graphics, this.left + PAD, y, WIDTH - 2 * PAD, FIELD_HEIGHT, this.field.isFocused() ? RoadColours.ACCENT : AureliaPalette.LINE);
        y += FIELD_HEIGHT + GAP;
        graphics.drawText(this.textRenderer, UiText.fit(this.textRenderer, this.problem, WIDTH - 2 * PAD), this.left + PAD, y + 1, RoadColours.STATUS_ERROR,
                false);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            set();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    void set() {
        double value;
        try {
            value = Double.parseDouble(this.field.getText());
        } catch (NumberFormatException e) {
            value = Double.NaN;
        }
        if (!StratosTools.roadSettings().setRadius(value)) {
            this.problem = Text.translatable("stratos.radius.invalid", format(RoadSettings.MIN_RADIUS), format(RoadSettings.MAX_RADIUS));
            return;
        }
        StratosTools.session().settingsChanged();
        close();
    }

    /** The radius field (tests type into it). */
    public TextFieldWidget field() {
        return this.field;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String format(double value) {
        return value >= 1 ? String.format(Locale.ROOT, "%,.0f", value) : RoadSettings.format(value);
    }
}
