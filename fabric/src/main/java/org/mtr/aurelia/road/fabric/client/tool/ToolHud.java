/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.tool;

import net.minecraft.client.util.InputUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.entity.effect.StatusEffectInstance;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.tool.CurveTool;
import org.mtr.aurelia.road.road.tool.EditRoadsTool;
import org.mtr.aurelia.road.road.tool.RoadSettings;
import org.mtr.aurelia.road.road.tool.StraightTool;
import org.mtr.aurelia.road.tool.HudModel;
import org.mtr.aurelia.road.tool.PanelPlace;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.fabric.client.editor.StratosEditorClient;
import org.mtr.aurelia.road.fabric.client.ui.RoadPicture;
import org.mtr.aurelia.road.fabric.client.nodes.StratosNodes;
import org.mtr.aurelia.road.fabric.client.ui.RoadColours;
import org.mtr.aurelia.road.fabric.client.hub.HubScreen;
import org.mtr.aurelia.road.fabric.client.ui.HubIcon;
import org.mtr.aurelia.road.fabric.client.ui.UiText;

/**
 * The tool panel (task UI-1b; owner, 2026-10-15: mockups 02 and 03), 150 GUI pixels wide at the top right. Top left was
 * chosen first, but Xaero's minimap sits there by default in the pack.
 * <ol>
 *   <li><b>Header:</b> the tool's icon and name on a dark accent bar, and a dot for each step.</li>
 *   <li><b>State:</b> what to do now.</li>
 *   <li><b>Values:</b> each an icon and a short value, without labels: short ones two to a row, slope and shape (which
 *       run long) and the node aids' rows one to a row, wrapping if need be. Every row is always there, with a dash
 *       until it has a value, so a tool's panel keeps its size. The layout, palette and node aid of the road being drawn
 *       are left out, as the resting strip and the toolbar show them (owner, 2026-10-15).</li>
 *   <li><b>Mouse buttons:</b> a row each, saying what a click does now (dimmed when it does nothing).</li>
 *   <li><b>Keys:</b> the player's keys in chips, each group followed by its icon; a chip that does nothing now is
 *       dimmed. A tool has only the rows it uses ({@code Tool.keys()}).</li>
 * </ol>
 * The latest server answer, or else the tool's notice, shows in a strip just below the panel, with a bar and an icon:
 * the accent for news, red for a refusal, orange for a warning. Where there is no room below it (the toolbar is open, or
 * the text is long) the strip sits to the panel's left. While no tool is in hand the same panel, without mouse and key
 * rows, shows the node aids ("Lane nodes", {@link StratosNodes#panel()}). The panel sits below the potion-effect icons
 * when there are any, but moves up over them rather than reach the hotbar, the resting strip or the toolbar. It shows
 * beside the toolbar (G), except while the hub's Settings covers its place (UI-1c), and hides with F1 or while any other
 * screen is open. In ATR the panel wears ATR's colours and the game's font.
 */
public final class ToolHud {
    /** The panel's width, in GUI pixels. */
    static final int WIDTH = 150;
    static final int MARGIN = 6;
    private static final int PAD = 5;
    private static final int TOP_PAD = 3;
    private static final int HEADER = 16;
    private static final int ROW = 11;
    private static final int KEY_ROW = 12;
    /** Above the first key row. */
    private static final int KEYS_GAP = 2;
    /** A value's column, two to a row. */
    private static final int COLUMN = 70;
    private static final int ICON_GAP = 3;
    /** A divider and the room above and below it. */
    private static final int DIVIDER = 7;
    private static final int CHIP = 10;
    private static final int CHIP_GAP = 2;
    private static final int GROUP_GAP = 7;
    /** Between the panel and its message strip, and between either and what is below them. */
    private static final int GAP = 2;
    /** The hotbar's height, which the panel keeps clear of. */
    static final int HOTBAR = 23;
    private static final int STEP_DOT = 4;
    /** A layout's picture beside its name. */
    private static final int PICTURE_WIDTH = 12;
    private static final int PICTURE_HEIGHT = 8;
    /** Vanilla's effect icons: helpful ones in a row at the top right, harmful ones in a second row below (24 + 2 pixels each). */
    private static final int EFFECT_ROW = 26;
    /** {@code bg-mid}, nearly opaque. */
    private static final int PANEL = (0xF0 << 24) | (RoadColours.BG_MID & 0xFFFFFF);
    /** The header bar: {@code accent-dark}, nearly opaque. */
    private static final int HEADER_FILL = (0xF2 << 24) | (RoadColours.ACCENT_DARK & 0xFFFFFF);
    /** The panel's edge and dividers, and a chip's edge: white at 8%. */
    private static final int EDGE = 0x14FFFFFF;
    /** A chip: white at 6%. */
    private static final int CHIP_FILL = 0x0FFFFFFF;
    /** A dimmed chip or icon: {@code text-muted} at 60%. */
    private static final int OFF = (0x99 << 24) | (RoadColours.TEXT_MUTED & 0xFFFFFF);
    /** An icon in its own colours, a little dimmed beside the values. */
    private static final int PICTURE_TINT = 0xFFD8D8E0;

    private static final String JUNCTION_TOOL = "roads.junction";
    private static final String LAYOUT = "stratos.tool.line.layout";
    private static final String PALETTE = "stratos.tool.line.palette";
    private static final String NODES = "stratos.tool.line.nodes";
    private static final String BLOCKS = "stratos.tool.value.blocks";
    private static final String PROFILE = "stratos.road.profile.";
    /** A row without a label of its own: the node aids' spacers and their "and 2 more". */
    private static final String NO_LABEL = "stratos.tool.value.none";

    /** A group of keys, the icon after their chips, and optionally a short text after that (the radius's step). */
    private record Group(List<HudModel.Action> actions, HubIcon icon, String afterKey) {
        Group(HubIcon icon, HudModel.Action... actions) {
            this(List.of(actions), icon, null);
        }
    }

    /**
     * The key rows (mockup 02): the tool key, undo, redo and the node aid (whose icon is the node aid in force); the
     * height keys, the shape (R-4, owner 2026-10-06) and the crossing; the radius keys and the typed radius.
     */
    private static final List<List<Group>> KEY_ROWS = List.of(
            List.of(new Group(HubIcon.PUT_AWAY, HudModel.Action.TOOL_KEY), new Group(HubIcon.UNDO, HudModel.Action.UNDO),
                    new Group(HubIcon.REDO, HudModel.Action.REDO), new Group(HubIcon.TRACK, HudModel.Action.NODES)),
            List.of(new Group(HubIcon.HEIGHT, HudModel.Action.RAISE, HudModel.Action.LOWER), new Group(HubIcon.SHAPE, HudModel.Action.SHAPE),
                    new Group(HubIcon.CROSSING, HudModel.Action.CROSSINGS)),
            List.of(new Group(List.of(HudModel.Action.RADIUS_UP, HudModel.Action.RADIUS_DOWN), HubIcon.RADIUS, "stratos.tool.panel.radius_step"),
                    new Group(HubIcon.KEYBOARD, HudModel.Action.RADIUS_TYPE)));
    /** The mouse rows, left button first (mockup 02). */
    private static final List<HudModel.Action> MOUSE_ROWS = List.of(HudModel.Action.ATTACK, HudModel.Action.USE);

    /** A value as the panel shows it: its icon, the room the icon takes, its text, and whether it takes a whole row. */
    private record Value(int iconWidth, IconDrawer icon, Text text, boolean wide) {
    }

    @FunctionalInterface
    private interface IconDrawer {
        void draw(DrawContext graphics, int x, int y);
    }

    /** Where something was drawn, in GUI pixels (the harness checks the panel and its messages keep clear of the rest). */
    public record Box(int x, int y, int width, int height) {
        public int bottom() {
            return this.y + this.height;
        }

        public boolean overlaps(Box other) {
            return this.x < other.x + other.width && other.x < this.x + this.width && this.y < other.bottom() && other.y < bottom();
        }
    }

    private static long framesDrawn;
    private static long panelFramesDrawn;
    private static Box panelBox;
    private static PanelPlace place = PanelPlace.DEFAULT;
    private static Box messageBox;

    private ToolHud() {
    }

    /** How many frames have drawn the HUD (the harness checks it appears and goes). */
    public static long framesDrawn() {
        return framesDrawn;
    }

    /** How many frames have drawn the node aids' panel (R-6; the harness checks it shows). */
    public static long panelFramesDrawn() {
        return panelFramesDrawn;
    }

    /** Where the player dragged the panel to ({@link PanelMoveScreen}); the top right until moved. */
    public static PanelPlace place() {
        return place;
    }

    public static void setPlace(PanelPlace place) {
        ToolHud.place = place;
    }

    /** Where the last frame drew the panel, if it drew one. */
    public static Optional<Box> panelBox() {
        return Optional.ofNullable(panelBox);
    }

    /** Where the last frame drew the message strip, if it drew one. */
    public static Optional<Box> messageBox() {
        return Optional.ofNullable(messageBox);
    }

    static void render(DrawContext graphics, float tickDelta) {
        panelBox = null;
        messageBox = null;
        MinecraftClient minecraft = MinecraftClient.getInstance();
        boolean hub = minecraft.currentScreen instanceof HubScreen;
        boolean moving = minecraft.currentScreen instanceof PanelMoveScreen;
        if (minecraft.options.hudHidden || minecraft.currentScreen != null && !hub && !moving || StratosTools.session() == null) {
            return;
        }
        // UI-1c: while Settings covers the panel's place (GUI scales 3 and 4), the panel and its messages hide.
        int x = place.moved() ? place.left(graphics.getScaledWindowWidth(), WIDTH, MARGIN) : graphics.getScaledWindowWidth() - MARGIN - WIDTH;
        Box column = new Box(x, MARGIN, WIDTH,
                StrictMath.max(0, HubScreen.top(graphics.getScaledWindowHeight()) - MARGIN));
        if (minecraft.currentScreen instanceof HubScreen screen && screen.settingsArea().filter(column::overlaps).isPresent()) {
            return;
        }
        Optional<Tool> tool = StratosTools.session().active();
        Optional<HudModel> model = StratosTools.session().hud();
        if (model.isPresent() && tool.isPresent()) {
            framesDrawn++;
        } else {
            tool = Optional.empty();
            model = StratosNodes.panel();
            if (model.isPresent()) {
                panelFramesDrawn++;
            }
        }
        TextRenderer font = minecraft.textRenderer;
        // Clear of the hub's bar while it's open, else of the hotbar.
        int bottom = (hub ? HubScreen.top(graphics.getScaledWindowHeight()) : graphics.getScaledWindowHeight() - HOTBAR) - GAP;
        int y = MARGIN + effectRows(minecraft) * EFFECT_ROW;
        int stripX = x;
        int stripY = y;
        Optional<Strip> strip = strip(model);
        if (model.isPresent()) {
            HudModel hud = model.get();
            Set<HudModel.Action> keys = tool.map(Tool::keys).orElse(Set.of());
            List<Value> values = values(hud, tool);
            List<HudModel.Action> mouseRows = MOUSE_ROWS.stream().filter(keys::contains).toList();
            List<List<Group>> keyRows = KEY_ROWS.stream().filter(row -> row.stream().anyMatch(group -> group.actions().stream().anyMatch(keys::contains))).toList();
            List<OrderedText> state = font.wrapLines(UiText.of(text(hud.state()), RoadColours.TEXT_MAIN), WIDTH - 2 * PAD);
            int height = height(state.size(), rows(font, values), mouseRows.size(), keyRows.size());
            y = place.moved() ? place.top(graphics.getScaledWindowHeight(), height, MARGIN, bottom) : Math.max(MARGIN, Math.min(y, bottom - height));
            draw(graphics, minecraft, font, hud, tool, x, y, height, state, values, mouseRows, keyRows, keys);
            panelBox = new Box(x, y, WIDTH, height);
            stripY = y + height + GAP;
            if (strip.isPresent() && stripY + stripHeight(font, strip.get().text()) > bottom) {
                // beside the panel, on the side with room (a panel dragged to the left edge has none on its left)
                stripX = x - WIDTH - 3 >= 0 ? x - WIDTH - 3 : x + WIDTH + 3;
                stripY = y;
            }
        } else if (strip.isPresent()) {
            stripY = Math.max(MARGIN, Math.min(y, bottom - stripHeight(font, strip.get().text())));
        }
        if (strip.isPresent()) {
            messageBox = drawStrip(graphics, font, stripX, stripY, strip.get());
        }
    }

    /** What the message strip says, its icon and its bar's colour. */
    private record Strip(Text text, HubIcon icon, int colour) {
    }

    /**
     * The latest server answer, or else the tool's notice. A server answer shows while the toolbar is open even with no
     * tool in hand, so it doesn't land on the action bar behind the toolbar's tabs (UI-1b).
     */
    private static Optional<Strip> strip(Optional<HudModel> model) {
        Optional<StratosTools.Message> message = StratosTools.message();
        if (message.isPresent()) {
            boolean error = message.get().error();
            return Optional.of(new Strip(message.get().text(), error ? HubIcon.ERROR : HubIcon.JOINED, error ? RoadColours.STATUS_ERROR : RoadColours.ACCENT));
        }
        if (model.isEmpty() || model.get().notice().isEmpty()) {
            return Optional.empty();
        }
        HudModel.Notice notice = model.get().notice().get();
        Text text = notice.reason().map(reason -> StratosEditorClient.reason(reason.code(), reason.args()))
                .orElseGet(() -> text(notice.text().orElseThrow()));
        return Optional.of(switch (notice.level()) {
            case ERROR -> new Strip(text, HubIcon.ERROR, RoadColours.STATUS_ERROR);
            case WARNING -> new Strip(text, HubIcon.WARN, RoadColours.STATUS_WARNING);
            case INFO -> new Strip(text, HubIcon.JOINED, RoadColours.ACCENT);
        });
    }

    /** The panel's height for a state of {@code stateLines} lines, {@code valueRows} rows of values and the mouse and key rows. */
    private static int height(int stateLines, int valueRows, int mouseRows, int keyRows) {
        int height = HEADER + TOP_PAD + stateLines * ROW + DIVIDER + valueRows * ROW;
        if (mouseRows + keyRows > 0) {
            height += DIVIDER + mouseRows * ROW + (keyRows > 0 ? KEYS_GAP + keyRows * KEY_ROW : 0);
        }
        return height + PAD;
    }

    /** How many rows {@code values} take: the short ones two to a row, the wide ones a row each, or more where they wrap. */
    private static int rows(TextRenderer font, List<Value> values) {
        int shortOnes = (int) values.stream().filter(value -> !value.wide()).count();
        return (shortOnes + 1) / 2 + values.stream().filter(Value::wide).mapToInt(value -> wideLines(font, value).size()).sum();
    }

    /** A wide value's text, wrapped to the panel after its icon (a node's problem can run past one row). */
    private static List<OrderedText> wideLines(TextRenderer font, Value value) {
        List<OrderedText> lines = font.wrapLines(value.text(), WIDTH - 2 * PAD - textOffset(value));
        return lines.isEmpty() ? List.of(OrderedText.EMPTY) : lines;
    }

    private static int textOffset(Value value) {
        return value.iconWidth() > 0 ? value.iconWidth() + ICON_GAP : 0;
    }

    private static void draw(DrawContext graphics, MinecraftClient minecraft, TextRenderer font, HudModel hud, Optional<Tool> tool, int x, int y, int height,
            List<OrderedText> state, List<Value> values, List<HudModel.Action> mouseRows, List<List<Group>> keyRows, Set<HudModel.Action> keys) {
        // The panel and its header bar: the tool's icon and name, and a dot for each step (filled up to this one).
        graphics.fill(x, y, x + WIDTH, y + height, PANEL);
        graphics.drawBorder(x, y, WIDTH, height, EDGE);
        graphics.fill(x, y, x + WIDTH, y + HEADER, HEADER_FILL);
        graphics.fill(x, y, x + 2, y + HEADER, RoadColours.ACCENT);
        HubIcon icon = tool.map(Tool::id).map(HubIcon::forTool).orElse(HubIcon.LANE_NODES);
        icon.draw(graphics, x + 6, y + (HEADER - HubIcon.SMALL) / 2, HubIcon.SMALL, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
        graphics.drawText(font, Text.translatable(hud.titleKey()), x + 6 + HubIcon.SMALL + 4, y + (HEADER - font.fontHeight) / 2 + 1,
                RoadColours.TEXT_MAIN, false);
        for (int i = 0; i < hud.steps(); i++) {
            int dotX = x + WIDTH - PAD - (hud.steps() - i) * (STEP_DOT + 2) + 2;
            int dotY = y + (HEADER - STEP_DOT) / 2;
            if (i < hud.step()) {
                graphics.fill(dotX, dotY, dotX + STEP_DOT, dotY + STEP_DOT, RoadColours.ACCENT_SOFT);
            } else {
                graphics.drawBorder(dotX, dotY, STEP_DOT, STEP_DOT, RoadColours.TEXT_MUTED);
            }
        }

        // What to do now.
        int left = x + PAD;
        int cursor = y + HEADER + TOP_PAD;
        for (OrderedText line : state) {
            graphics.drawText(font, line, left, cursor + 1, 0xFFFFFFFF, false);
            cursor += ROW;
        }
        divider(graphics, x, cursor);
        cursor += DIVIDER;

        // The values: the short ones two to a row, then the wide ones.
        int column = 0;
        for (Value value : values) {
            if (!value.wide()) {
                value(graphics, font, value, left + column * COLUMN, cursor, COLUMN - 2);
                column++;
                if (column == 2) {
                    column = 0;
                    cursor += ROW;
                }
            }
        }
        if (column == 1) {
            cursor += ROW;
        }
        for (Value value : values) {
            if (value.wide()) {
                if (value.icon() != null) {
                    value.icon().draw(graphics, left, cursor);
                }
                for (OrderedText line : wideLines(font, value)) {
                    graphics.drawText(font, line, left + textOffset(value), cursor + 1, 0xFFFFFFFF, false);
                    cursor += ROW;
                }
            }
        }
        if (mouseRows.isEmpty() && keyRows.isEmpty()) {
            return;
        }
        divider(graphics, x, cursor);
        cursor += DIVIDER;

        // The mouse buttons: what a click does now, dimmed when it does nothing.
        for (HudModel.Action action : mouseRows) {
            Optional<HudModel.Hint> hint = hud.hint(action);
            int textX = left + mouseButton(graphics, minecraft, font, action, hint.isPresent(), left, cursor) + ICON_GAP;
            Text what = hint.map(live -> UiText.of(Text.translatable(live.textKey()), RoadColours.TEXT_MAIN))
                    .orElseGet(() -> UiText.of(Text.translatable(NO_LABEL), RoadColours.TEXT_MUTED));
            graphics.drawText(font, UiText.fit(font, what, x + WIDTH - PAD - textX), textX, cursor + 1, 0xFFFFFFFF, false);
            cursor += ROW;
        }

        // The keys: each group's chips (dimmed when they do nothing now), then its icon.
        cursor += KEYS_GAP;
        for (List<Group> row : keyRows) {
            int groupX = left;
            for (Group group : row) {
                List<HudModel.Action> used = group.actions().stream().filter(keys::contains).toList();
                if (used.isEmpty()) {
                    continue;
                }
                boolean any = false;
                for (HudModel.Action action : used) {
                    boolean live = hud.hint(action).isPresent();
                    any |= live;
                    groupX += chip(graphics, font, keyName(minecraft, action), live, groupX, cursor + 1) + CHIP_GAP;
                }
                HubIcon groupIcon = groupIcon(group, tool);
                groupIcon.draw(graphics, groupX, cursor + 2, HubIcon.TINY, any ? tint(groupIcon) : OFF);
                groupX += HubIcon.TINY;
                if (group.afterKey() != null && !junction(tool)) {
                    MutableText after = UiText.of(Text.translatable(group.afterKey()), RoadColours.TEXT_MUTED);
                    graphics.drawText(font, after, groupX + CHIP_GAP, cursor + 2, 0xFFFFFFFF, false);
                    groupX += CHIP_GAP + font.getWidth(after);
                }
                groupX += GROUP_GAP;
            }
            cursor += KEY_ROW;
        }
    }

    /** Whether {@code tool} is the junction tool, whose shape key picks the kind and whose radius keys pick the size. */
    private static boolean junction(Optional<Tool> tool) {
        return tool.map(Tool::id).filter(JUNCTION_TOOL::equals).isPresent();
    }

    /** A key group's icon: the node aid in force for the nodes key; the junction's kind and size for its keys; else the group's own. */
    private static HubIcon groupIcon(Group group, Optional<Tool> tool) {
        if (group.actions().contains(HudModel.Action.NODES)) {
            return HubIcon.forNodeAids(StratosTools.roadSettings().nodeAids());
        }
        if (junction(tool)) {
            if (group.actions().contains(HudModel.Action.SHAPE)) {
                return HubIcon.JUNCTION;
            }
            if (group.actions().contains(HudModel.Action.RADIUS_UP)) {
                return HubIcon.SIZE;
            }
        }
        return group.icon();
    }

    /** A short value at ({@code x}, {@code y}): its icon, then its text, shortened to fit {@code room}. */
    private static void value(DrawContext graphics, TextRenderer font, Value value, int x, int y, int room) {
        if (value.icon() != null) {
            value.icon().draw(graphics, x, y);
        }
        int textX = x + textOffset(value);
        graphics.drawText(font, UiText.fit(font, value.text(), x + room - textX), textX, y + 1, 0xFFFFFFFF, false);
    }

    /**
     * The values the panel shows for {@code hud}'s lines, in order. While the road being drawn has the layout, palette and
     * node aid in force, the panel leaves them to the resting strip and the toolbar (owner, 2026-10-15).
     */
    private static List<Value> values(HudModel hud, Optional<Tool> tool) {
        boolean choicesShown = tool.map(ToolHud::choicesShownElsewhere).orElse(false);
        List<Value> values = new ArrayList<>();
        for (HudModel.Line line : hud.lines()) {
            String label = line.labelKey();
            if (choicesShown && (label.equals(LAYOUT) || label.equals(PALETTE) || label.equals(NODES))) {
                continue;
            }
            values.add(value(line, tool));
        }
        return values;
    }

    /** Whether the road the tool draws has the layout, palette and node aid the resting strip and the toolbar show. */
    private static boolean choicesShownElsewhere(Tool tool) {
        return tool instanceof StraightTool || tool instanceof CurveTool || tool instanceof EditRoadsTool edit && edit.editor().isPresent();
    }

    private static Value value(HudModel.Line line, Optional<Tool> tool) {
        int colour = switch (line.tone()) {
            case WARNING -> RoadColours.STATUS_WARNING;
            case GOOD -> RoadColours.STATUS_SUCCESS;
            case NORMAL -> line.value().equals(HudModel.NONE) ? RoadColours.TEXT_MUTED : RoadColours.TEXT_MAIN;
        };
        Text text = UiText.of(text(shortValue(line)), colour);
        return switch (line.labelKey()) {
            case LAYOUT -> layout(line, tool, text);
            case PALETTE -> icon(HubIcon.PALETTE, text, false);
            case "stratos.tool.line.length" -> icon(HubIcon.LENGTH, text, false);
            case "stratos.tool.line.heading", "stratos.tool.line.headings" -> icon(HubIcon.HEADING, text, false);
            case "stratos.tool.line.radius" -> icon(HubIcon.RADIUS, text, false);
            case "stratos.tool.line.height" -> icon(HubIcon.HEIGHT, text, false);
            case "stratos.tool.line.slope" -> icon(HubIcon.SLOPE, text, true);
            case "stratos.tool.line.shape" -> icon(HubIcon.SHAPE, text, true);
            case NODES -> icon(nodeAids(line.value()), text, false);
            case "stratos.tool.line.blocks" -> icon(HubIcon.BLOCKS, text, false);
            case "stratos.tool.line.crossing" -> icon(HubIcon.CROSSING, text, false);
            case "stratos.tool.line.junction" -> icon(HubIcon.JUNCTION, text, false);
            case "stratos.tool.line.size" -> icon(HubIcon.SIZE, text, false);
            case "stratos.edit.line.joined" -> icon(HubIcon.LINK, text, false);
            case "stratos.nodes.line.road" -> icon(HubIcon.ROADS, text, true);
            case "stratos.nodes.line.nodes" -> icon(HubIcon.MTR_NODES, text, true);
            case "stratos.nodes.line.markers" -> icon(HubIcon.WOOL, text, true);
            case "stratos.nodes.line.aimed" -> icon(HubIcon.LANE_NODES, text, true);
            case "stratos.nodes.line.facing" -> icon(HubIcon.HEADING, text, true);
            // A node's problem keeps its lane and node number: "Lane 2 · 1: needs E–W, has SW–NE".
            case "stratos.nodes.line.problem" -> icon(line.tone() == HudModel.Tone.WARNING ? HubIcon.WARN : HubIcon.INFO,
                    UiText.of(Text.translatable("stratos.tool.panel.problem", text(line.label()), text(line.value())), colour), true);
            // In line with the texts above it, as their icons leave room.
            case NO_LABEL -> new Value(HubIcon.TINY, null, line.value().equals(HudModel.NONE) ? Text.empty() : text, true);
            default -> new Value(0, null, UiText.of(Text.translatable("stratos.tool.panel.labelled", text(line.label()), text(line.value())), colour), true);
        };
    }

    private static Value icon(HubIcon icon, Text text, boolean wide) {
        return new Value(HubIcon.TINY, (graphics, x, y) -> icon.draw(graphics, x, y + 1, HubIcon.TINY, tint(icon)), text, wide);
    }

    /** A road's layout: a little picture of it beside its name (the road aimed at's, or the one being drawn). */
    private static Value layout(HudModel.Line line, Optional<Tool> tool, Text text) {
        String key = line.value().key();
        Optional<RoadProfile> profile = key.startsWith(PROFILE) ? RoadProfiles.byId(key.substring(PROFILE.length())) : Optional.empty();
        if (profile.isEmpty()) {
            return new Value(PICTURE_WIDTH, (graphics, x, y) -> HubIcon.ROADS.draw(graphics, x + (PICTURE_WIDTH - HubIcon.TINY) / 2, y + 1,
                    HubIcon.TINY, PICTURE_TINT), text, false);
        }
        RoadSettings settings = StratosTools.roadSettings();
        Palette palette = tool.filter(EditRoadsTool.class::isInstance).map(EditRoadsTool.class::cast).flatMap(EditRoadsTool::shown)
                .flatMap(road -> settings.palettes().byId(road.params().paletteId())).orElse(settings.palette());
        return new Value(PICTURE_WIDTH, (graphics, x, y) -> RoadPicture.draw(graphics, profile.get(), palette, x, y + (ROW - PICTURE_HEIGHT) / 2,
                PICTURE_WIDTH, PICTURE_HEIGHT), text, false);
    }

    /** The node aid a "Nodes" row names. */
    private static HubIcon nodeAids(HudModel.Text value) {
        return switch (value.key()) {
            case "stratos.tool.value.nodes_track" -> HubIcon.TRACK;
            case "stratos.tool.value.nodes_wool" -> HubIcon.WOOL;
            case "stratos.tool.value.nodes_mtr" -> HubIcon.MTR_NODES;
            default -> HubIcon.NONE;
        };
    }

    /** A value as short as the panel words it: "23.6" for a length, "R 15.0" for a radius, "City 17" for a layout. */
    private static HudModel.Text shortValue(HudModel.Line line) {
        HudModel.Text value = line.value();
        return switch (line.labelKey()) {
            case "stratos.tool.line.length" -> value.key().equals(BLOCKS) ? new HudModel.Text("stratos.tool.panel.number", value.args()) : value;
            case "stratos.tool.line.radius" -> value.key().equals(BLOCKS) ? new HudModel.Text("stratos.tool.panel.radius", value.args()) : value;
            case LAYOUT -> value.key().startsWith(PROFILE) ? new HudModel.Text(PROFILE + "short." + value.key().substring(PROFILE.length()), value.args()) : value;
            default -> value;
        };
    }

    /** An icon's tint beside a value: a glyph in the muted text colour, a picture a little dimmed. */
    private static int tint(HubIcon icon) {
        return icon.picture() ? PICTURE_TINT : RoadColours.TEXT_MUTED;
    }

    /**
     * Draws a mouse row's button at ({@code x}, {@code y}): the left or right button's icon, or the player's key in a chip
     * if they moved the binding off the mouse. Returns its width.
     */
    private static int mouseButton(DrawContext graphics, MinecraftClient minecraft, TextRenderer font, HudModel.Action action, boolean live, int x, int y) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding(minecraft, action));
        if (key.getCategory() == InputUtil.Type.MOUSE && (key.getCode() == 0 || key.getCode() == 1)) {
            HubIcon icon = key.getCode() == 0 ? HubIcon.MOUSE_LEFT : HubIcon.MOUSE_RIGHT;
            icon.draw(graphics, x, y + 1, HubIcon.TINY, live ? 0xFFFFFFFF : OFF);
            return HubIcon.TINY;
        }
        return chip(graphics, font, keyName(minecraft, action), live, x, y);
    }

    /** Draws a key's chip at ({@code x}, {@code y}), in the accent when it does something now. Returns its width. */
    private static int chip(DrawContext graphics, TextRenderer font, Text key, boolean live, int x, int y) {
        MutableText name = UiText.of(key, live ? RoadColours.ACCENT_SOFT : OFF);
        int width = font.getWidth(name) + 6;
        graphics.fill(x, y, x + width, y + CHIP, CHIP_FILL);
        graphics.drawBorder(x, y, width, CHIP, EDGE);
        graphics.drawText(font, name, x + 3, y + 1, 0xFFFFFFFF, false);
        return width;
    }

    private static int stripHeight(TextRenderer font, Text text) {
        return stripLines(font, text).size() * ROW + 6;
    }

    private static List<OrderedText> stripLines(TextRenderer font, Text text) {
        return font.wrapLines(UiText.of(text, RoadColours.TEXT_MAIN), WIDTH - 2 - 4 - HubIcon.TINY - 4 - 4);
    }

    /** A message strip at ({@code x}, {@code y}): a coloured bar on the left, the icon, and the text wrapped to the panel's width. */
    private static Box drawStrip(DrawContext graphics, TextRenderer font, int x, int y, Strip strip) {
        List<OrderedText> lines = stripLines(font, strip.text());
        int height = lines.size() * ROW + 6;
        graphics.fill(x, y, x + WIDTH, y + height, PANEL);
        graphics.drawBorder(x, y, WIDTH, height, EDGE);
        graphics.fill(x, y, x + 2, y + height, strip.colour());
        strip.icon().draw(graphics, x + 6, y + 4, HubIcon.TINY, 0xFFFFFFFF);
        int lineY = y + 4;
        for (OrderedText line : lines) {
            graphics.drawText(font, line, x + 6 + HubIcon.TINY + 4, lineY, 0xFFFFFFFF, false);
            lineY += ROW;
        }
        return new Box(x, y, WIDTH, height);
    }

    private static void divider(DrawContext graphics, int x, int y) {
        graphics.fill(x + PAD, y + 3, x + WIDTH - PAD, y + 4, EDGE);
    }

    /**
     * The binding's key as the player reads it. The keys above the arrow keys get short names, so they fit their chips
     * (R-4).
     */
    private static MutableText keyName(MinecraftClient minecraft, HudModel.Action action) {
        KeyBinding mapping = binding(minecraft, action);
        String key = switch (KeyBindingHelper.getBoundKeyOf(mapping).getTranslationKey()) {
            case "key.keyboard.page.up" -> "stratos.tool.key.page_up";
            case "key.keyboard.page.down" -> "stratos.tool.key.page_down";
            case "key.keyboard.insert" -> "stratos.tool.key.insert";
            case "key.keyboard.delete" -> "stratos.tool.key.delete";
            default -> null;
        };
        return key != null ? Text.translatable(key) : mapping.getBoundKeyLocalizedText().copy();
    }

    /** How many rows of effect icons vanilla draws at the top right: 0, 1 (helpful only) or 2 (any harmful). */
    private static int effectRows(MinecraftClient minecraft) {
        if (minecraft.player == null) {
            return 0;
        }
        boolean helpful = false;
        for (StatusEffectInstance effect : minecraft.player.getStatusEffects()) {
            if (!effect.shouldShowIcon()) {
                continue;
            }
            if (!effect.getEffectType().isBeneficial()) {
                return 2;
            }
            helpful = true;
        }
        return helpful ? 1 : 0;
    }

    private static Text text(HudModel.Text text) {
        return Text.translatable(text.key(), text.args().toArray());
    }

    private static KeyBinding binding(MinecraftClient minecraft, HudModel.Action action) {
        return switch (action) {
            case USE -> minecraft.options.useKey;
            case ATTACK -> minecraft.options.attackKey;
            case TOOL_KEY -> StratosTools.TOOL_KEY;
            case UNDO -> StratosTools.UNDO_KEY;
            case REDO -> StratosTools.REDO_KEY;
            case RAISE -> StratosTools.RAISE_KEY;
            case LOWER -> StratosTools.LOWER_KEY;
            case SHAPE -> StratosTools.SHAPE_KEY;
            case RADIUS_UP -> StratosTools.RADIUS_UP_KEY;
            case RADIUS_DOWN -> StratosTools.RADIUS_DOWN_KEY;
            case RADIUS_TYPE -> StratosTools.RADIUS_TYPE_KEY;
            case NODES -> StratosTools.NODES_KEY;
            case CROSSINGS -> StratosTools.CROSSINGS_KEY;
        };
    }
}
