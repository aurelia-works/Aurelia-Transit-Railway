/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.road.road.tool.EditRoadsTool;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.Question;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.tool.ToolInput;
import org.mtr.aurelia.road.fabric.client.ui.HubIcon;
import org.mtr.aurelia.road.fabric.client.ui.RoadColours;
import org.mtr.aurelia.road.fabric.client.ui.RoadPanel;
import org.mtr.aurelia.road.fabric.client.ui.UiText;

/**
 * The box a tool's question shows in (task R-7; owner, 2026-10-09: "a small box"), such as the Edit roads tool's choices
 * and its hand-edit question, over the game, which doesn't pause. It has the tool panel's header (the tool's icon and
 * name) and the road it asks about. A question whose choices all have icons (Edit, Rebuild, Rebuild in …, Bake, Delete)
 * shows them as icon tiles, each explained on hover, under a short line ("City street · 17 wide · 48 blocks long"); any
 * other keeps its sentence and gets tiles of words (Overwrite them, Keep them). The first choice is drawn as chosen and
 * has the focus, so Enter takes it. A tile sends its choice to the tool in hand ({@link ToolInput.Choose}); Cancel, or Esc,
 * picks the last choice, the way out. In ATR's look.
 */
public final class QuestionScreen extends Screen {
    /** The box's width for a question in words. */
    private static final int WIDTH = 262;
    private static final int PAD = 8;
    private static final int TILE_WIDTH = 46;
    private static final int TILE_HEIGHT = 38;
    private static final int TILE_GAP = 4;
    private static final int BUTTON_HEIGHT = 16;
    private static final int GAP = 5;
    private static final int CHIP_FILL = 0x0FFFFFFF;

    private final Question question;
    private boolean answered;
    private int left;
    private int top;
    private int boxWidth;
    private int boxHeight;
    private Text road = Text.empty();
    private List<OrderedText> body = List.of();
    private HubIcon titleIcon = HubIcon.EDIT_ROADS;
    private Text toolTitle = Text.empty();

    public QuestionScreen(Question question) {
        super(text(question.title()));
        this.question = Objects.requireNonNull(question, "question");
    }

    public Question question() {
        return this.question;
    }

    @Override
    protected void init() {
        List<Question.Choice> choices = this.question.choices().subList(0, this.question.choices().size() - 1);
        String shortBody = this.question.body().key() + ".short";
        boolean tiles = I18n.hasTranslation(shortBody) && choices.stream().allMatch(choice -> icon(choice.id()).isPresent());

        // The road it asks about: its layout, and in the tiles' box how long it is.
        MutableText road = UiText.of(text(this.question.title()), RoadColours.TEXT_MAIN);
        if (tiles) {
            road.append(UiText.of(Text.translatable("stratos.question.joined", Text.translatable(shortBody, this.question.body().args().toArray())),
                    RoadColours.TEXT_MUTED));
        }
        this.road = road;
        this.boxWidth = tiles ? Math.max(2 * PAD + choices.size() * (TILE_WIDTH + TILE_GAP) - TILE_GAP, 2 * PAD + this.textRenderer.getWidth(road))
                : WIDTH;
        this.boxWidth = Math.min(this.boxWidth, this.width - 8);
        this.body = tiles ? List.of() : this.textRenderer.wrapLines(UiText.of(text(this.question.body()), RoadColours.TEXT_MUTED), this.boxWidth - 2 * PAD);
        Optional<Tool> tool = StratosTools.session() == null ? Optional.empty() : StratosTools.session().active();
        this.toolTitle = Text.translatable(tool.map(Tool::titleKey).orElse("stratos.tool.roads.edit"));
        this.titleIcon = tool.map(Tool::id).map(HubIcon::forTool).orElse(HubIcon.EDIT_ROADS);

        List<RoadPanel.Button> row = new ArrayList<>();
        boolean first = true;
        for (Question.Choice choice : choices) {
            row.add(tiles ? iconTile(choice, first) : wordTile(choice, first));
            first = false;
        }
        RoadPanel.Button cancel = cancel(this.question.wayOut());
        int rowHeight = tiles ? TILE_HEIGHT : BUTTON_HEIGHT;
        this.boxHeight = RoadPanel.HEADER + 5 + 10 + GAP + this.body.size() * 10 + (this.body.isEmpty() ? 0 : GAP) + rowHeight + GAP + BUTTON_HEIGHT + PAD;
        this.left = (this.width - this.boxWidth) / 2;
        this.top = (this.height - this.boxHeight) / 2;
        int x = this.left + PAD;
        int y = this.top + RoadPanel.HEADER + 5 + 10 + GAP + this.body.size() * 10 + (this.body.isEmpty() ? 0 : GAP);
        for (RoadPanel.Button button : row) {
            button.setPosition(x, y);
            addDrawableChild(button);
            x += button.getWidth() + TILE_GAP;
        }
        cancel.setPosition(this.left + PAD, y + rowHeight + GAP);
        addDrawableChild(cancel);
        if (!row.isEmpty()) {
            setInitialFocus(row.get(0));
        }
    }

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        RoadPanel.draw(graphics, this.textRenderer, this.left, this.top, this.boxWidth, this.boxHeight, this.titleIcon, this.toolTitle);
        int y = this.top + RoadPanel.HEADER + 5;
        graphics.drawText(this.textRenderer, UiText.fit(this.textRenderer, this.road, this.boxWidth - 2 * PAD), this.left + PAD, y + 1, 0xFFFFFFFF, false);
        y += 10 + GAP;
        for (OrderedText line : this.body) {
            graphics.drawText(this.textRenderer, line, this.left + PAD, y, 0xFFFFFFFF, false);
            y += 10;
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    private RoadPanel.Button iconTile(Question.Choice choice, boolean first) {
        HubIcon icon = icon(choice.id()).orElseThrow();
        Text label = UiText.fit(this.textRenderer, UiText.of(shortLabel(choice.label()), RoadColours.TEXT_MAIN), TILE_WIDTH - 4);
        RoadPanel.Button tile = new RoadPanel.Button(TILE_WIDTH, TILE_HEIGHT, text(choice.label()), first, (graphics, x, y, w, h) -> {
            icon.draw(graphics, x + (w - HubIcon.MEDIUM) / 2, y + 6, HubIcon.MEDIUM, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
            graphics.drawText(this.textRenderer, label, x + (w - this.textRenderer.getWidth(label)) / 2, y + 6 + HubIcon.MEDIUM + 4, 0xFFFFFFFF, false);
        }, () -> choose(choice.id()));
        MutableText tip = UiText.of(text(choice.label()), RoadColours.TEXT_MAIN);
        String tipKey = choice.label().key() + ".tip";
        if (I18n.hasTranslation(tipKey)) {
            tip.append("\n").append(UiText.of(Text.translatable(tipKey, choice.label().args().toArray()), RoadColours.TEXT_MUTED));
        }
        tile.setTooltip(Tooltip.of(tip));
        return tile;
    }

    private RoadPanel.Button wordTile(Question.Choice choice, boolean first) {
        return RoadPanel.Button.text(this.textRenderer, text(choice.label()), BUTTON_HEIGHT, first, () -> choose(choice.id()));
    }

    private RoadPanel.Button cancel(Question.Choice wayOut) {
        Text esc = UiText.of(Text.translatable("stratos.question.esc"), RoadColours.TEXT_MUTED);
        Text label = UiText.of(text(wayOut.label()), RoadColours.TEXT_MAIN);
        int chip = this.textRenderer.getWidth(esc) + 6;
        return new RoadPanel.Button(4 + chip + 4 + this.textRenderer.getWidth(label) + 6, BUTTON_HEIGHT, text(wayOut.label()), false,
                (graphics, x, y, w, h) -> {
                    graphics.fill(x + 4, y + 3, x + 4 + chip, y + 13, CHIP_FILL);
                    AureliaChrome.frame(graphics, x + 4, y + 3, chip, 10, AureliaPalette.LINE);
                    graphics.drawText(this.textRenderer, esc, x + 7, y + 4, 0xFFFFFFFF, false);
                    graphics.drawText(this.textRenderer, label, x + 4 + chip + 4, y + 4, 0xFFFFFFFF, false);
                }, () -> choose(wayOut.id()));
    }

    private static Optional<HubIcon> icon(String choiceId) {
        return Optional.ofNullable(switch (choiceId) {
            case EditRoadsTool.EDIT -> HubIcon.EDIT;
            case EditRoadsTool.REBUILD -> HubIcon.REBUILD;
            case EditRoadsTool.REBUILD_IN -> HubIcon.REBUILD_IN;
            case EditRoadsTool.BAKE -> HubIcon.BAKE;
            case EditRoadsTool.DELETE -> HubIcon.DELETE;
            default -> null;
        });
    }

    /** A tile's short name ({@code <key>.short}), or its full one. */
    private static Text shortLabel(HudModel.Text label) {
        String key = label.key() + ".short";
        return I18n.hasTranslation(key) ? Text.translatable(key, label.args().toArray()) : text(label);
    }

    /** Sends {@code choice} to the tool in hand and closes the box (a tile, or the harness). */
    public void choose(String choice) {
        if (this.answered) {
            return;
        }
        this.answered = true;
        StratosTools.session().input(new ToolInput.Choose(choice));
        close();
    }

    /** Esc picks the way out, so the question doesn't open again. */
    @Override
    public void close() {
        if (!this.answered) {
            this.answered = true;
            StratosTools.session().input(new ToolInput.Choose(this.question.wayOut().id()));
        }
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static Text text(HudModel.Text text) {
        return Text.translatable(text.key(), text.args().toArray());
    }
}
