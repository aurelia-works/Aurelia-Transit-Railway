/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.hub;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.road.fabric.client.StratosClient;
import org.mtr.aurelia.road.fabric.client.editor.StratosEditorClient;
import org.mtr.aurelia.road.fabric.client.tool.StratosTools;
import org.mtr.aurelia.road.fabric.client.tool.ToolHud;
import org.mtr.aurelia.road.fabric.client.ui.HubIcon;
import org.mtr.aurelia.road.fabric.client.ui.RoadColours;
import org.mtr.aurelia.road.fabric.client.ui.RoadPicture;
import org.mtr.aurelia.road.fabric.client.ui.UiText;
import org.mtr.aurelia.road.fabric.net.StratosPermissions;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadsModule;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.tool.RoadSettings;
import org.mtr.aurelia.road.tool.Tool;
import org.mtr.aurelia.road.tool.Toolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The road hub, a toolbar along the bottom of the screen in ATR's look. The world stays in view above it; it doesn't
 * pause the game, and the hub key or Esc closes it.
 * <ul>
 *   <li>Above the toolbar, the Roads tab; on the right, "Put away" while a tool is in hand, the globe (This world) and
 *       the gear (Settings).</li>
 *   <li>In the toolbar: the road tools (choosing one takes it in hand and closes the hub); the layouts as pictures of the
 *       road, paging between arrows when there are more than fit; Palette and Nodes as one tile each, whose pop-up lists
 *       the choices. A layout, palette or node aid keeps the hub open.</li>
 *   <li>The globe's pop-up is This world: the warning and two tiles, "Bake world…" and "Redraw markings…", each asked
 *       about first; confirming sends the world task and closes the hub. The globe's dot shows while the world has road
 *       blocks.</li>
 *   <li>The gear's pop-up is Settings: its pages down the left ({@link HubSettings}) and the chosen page's rows.</li>
 * </ul>
 * Drawn straight with fills each frame, so it costs no texture and no UI library; what can be clicked is collected while
 * drawing. Sizes are GUI pixels; {@link Toolbar} places the sections. A click outside an open pop-up closes it.
 */
public final class HubScreen extends Screen {
    private static final int TAB_HEIGHT = 14;
    private static final int PANEL_HEIGHT = 60;
    private static final int LABEL_Y = 3;
    private static final int TILE_Y = 13;
    private static final int CAPTION_Y = 47;
    /** A layout or palette tile's height, and a node aid tile's side. */
    private static final int SHORT_TILE = 22;
    /** A tab-row tile's width (globe, settings). */
    private static final int TAB_TILE = 19;
    private static final int DIVIDER_HEIGHT = 48;
    /** A layout picture inside its tile, and in a pop-up's row. */
    private static final int PICTURE_WIDTH = 28;
    private static final int PICTURE_HEIGHT = 20;
    private static final int RADIUS = 4;
    private static final int TILE_RADIUS = 2;
    private static final int POP_UP_PADDING = 5;
    private static final int POP_UP_ROW = 24;
    private static final int PALETTE_POP_UP_WIDTH = 180;
    private static final int NODES_POP_UP_WIDTH = 150;
    /** This world's pop-up: its width, padding and its tiles (Bake and Redraw side by side, Driving side under them). */
    private static final int WORLD_BOX_WIDTH = 174;
    private static final int WORLD_BOX_PADDING = 7;
    private static final int WORLD_TILE_WIDTH = 77;
    private static final int WORLD_TILE_HEIGHT = 30;
    private static final int WORLD_TILE_GAP = 4;
    private static final int BUTTON_HEIGHT = 14;
    /** The Settings pop-up: its size at most, padding, page list and rows. */
    private static final int SETTINGS_WIDTH = 468;
    private static final int SETTINGS_HEIGHT = 158;
    private static final int SETTINGS_PADDING = 7;
    private static final int SETTINGS_NAV_WIDTH = 100;
    private static final int SETTINGS_NAV_HEIGHT = 18;
    private static final int SETTINGS_TOP = 26;
    private static final int SETTINGS_ROW = 16;
    private static final int SETTINGS_CONTROL = 13;
    private static final int SWITCH_WIDTH = 18;
    private static final int SWITCH_HEIGHT = 9;
    /** A disabled tile's veil. */
    private static final int FADE = 0xA015181C;

    /** The pop-ups above the toolbar. */
    private enum PopUp { PALETTE, NODES, WORLD, SETTINGS }

    /** The Settings page shown last, so reopening Settings returns to it. */
    private static String lastSettingsPage = HubSettings.PREVIEW;

    private PopUp popUp;
    /** The world task This world is asking about (Protocol.TASK_*), or null. */
    private String confirming;
    /** The layout strip's first tile shown; -1 until the chosen layout has been brought into view. */
    private int stripFirst = -1;
    /** The layout strip as last drawn, and where it is, for the mouse wheel; null without one. */
    private Toolbar.Strip strip;
    private Box stripBox;
    /** The open pop-up as this frame and the last drew it: clicks and hovers inside it reach only its own parts. */
    private Box popUpBox;
    private Box lastPopUpBox;
    /** Where Settings is, while it is open (the tool panel keeps out of it). */
    private Box settingsArea;
    /** What can be clicked this frame, in drawing order, and where the pop-up's own parts begin. */
    private final List<Hot> hots = new ArrayList<>();
    private int popUpHots = Integer.MAX_VALUE;
    private boolean drawingPopUp;
    private int mouseX;
    private int mouseY;

    /** A rectangle in GUI pixels. */
    private record Box(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= this.x && px < this.x + this.w && py >= this.y && py < this.y + this.h;
        }
    }

    /** Something that can be clicked: where it is, what a click does (null: nothing, but the click stops there) and its tooltip. */
    private record Hot(Box box, Runnable press, List<Text> tip) {
    }

    public HubScreen() {
        super(Text.translatable("stratos.hub.title"));
    }

    /** Whether the hub is the screen open now. */
    public static boolean isOpen() {
        return MinecraftClient.getInstance().currentScreen instanceof HubScreen;
    }

    /** The toolbar's top edge, the tab included, on a screen {@code height} GUI pixels tall (the tool panel keeps above it). */
    public static int top(int height) {
        return height - Toolbar.MARGIN - PANEL_HEIGHT - TAB_HEIGHT - 1;
    }

    /** Where Settings is while it is open: the tool panel hides while it covers the panel's place. */
    public Optional<ToolHud.Box> settingsArea() {
        Box area = this.settingsArea;
        return this.popUp == PopUp.SETTINGS && area != null ? Optional.of(new ToolHud.Box(area.x(), area.y(), area.w(), area.h())) : Optional.empty();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---- Drawing ----

    @Override
    public void render(DrawContext graphics, int mouseX, int mouseY, float delta) {
        this.hots.clear();
        this.popUpHots = Integer.MAX_VALUE;
        this.strip = null;
        this.stripBox = null;
        this.lastPopUpBox = this.popUpBox;
        this.popUpBox = null;
        this.settingsArea = null;
        this.drawingPopUp = false;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        if (this.client == null || this.client.world == null) {
            graphics.fill(0, 0, this.width, this.height, AureliaPalette.BG);
        }
        int barWidth = Toolbar.width(this.width);
        int barX = (this.width - barWidth) / 2;
        int panelY = this.height - Toolbar.MARGIN - PANEL_HEIGHT;
        int tabY = panelY - TAB_HEIGHT;
        AureliaChrome.shadow(graphics, barX, panelY, barWidth, PANEL_HEIGHT, RADIUS);
        AureliaChrome.rounded(graphics, barX, panelY, barWidth, PANEL_HEIGHT, RADIUS, RoadColours.BG_MID);
        AureliaChrome.roundedFrame(graphics, barX, panelY, barWidth, PANEL_HEIGHT, RADIUS, AureliaPalette.LINE);
        int globeRight = tabRow(graphics, barX, tabY, barWidth);
        tools(graphics, barX, panelY, barWidth);
        if (this.popUp != null) {
            this.popUpHots = this.hots.size();
            this.drawingPopUp = true;
            switch (this.popUp) {
                case PALETTE -> palettePopUp(graphics, barX + barWidth, tabY - 2);
                case NODES -> nodesPopUp(graphics, barX + barWidth, tabY - 2);
                case WORLD -> {
                    if (StratosEditorClient.editingEnabled()) {
                        worldBox(graphics, globeRight, tabY - 2);
                    }
                }
                case SETTINGS -> settingsBox(graphics, barX + barWidth, tabY - 2);
            }
            this.drawingPopUp = false;
        }
        hovered().map(Hot::tip).filter(tip -> !tip.isEmpty()).ifPresent(tip -> graphics.drawOrderedTooltip(this.textRenderer, wrappedTip(tip), mouseX, mouseY));
    }

    /** The widest a tooltip line runs before it wraps; at most half the screen, so a long tooltip stays on it. */
    private static final int TIP_WIDTH = 200;

    /** A tooltip's lines, each wrapped to {@link #TIP_WIDTH} (or half the screen, when that's narrower). */
    private List<OrderedText> wrappedTip(List<Text> tip) {
        int width = StrictMath.min(TIP_WIDTH, this.width / 2);
        List<OrderedText> lines = new ArrayList<>();
        tip.forEach(line -> lines.addAll(wrap(line, width)));
        return lines;
    }

    /** The tab row; returns the globe's right edge, where This world lines up. */
    private int tabRow(DrawContext graphics, int barX, int tabY, int barWidth) {
        Text name = Text.translatable("stratos.module.roads");
        int tabX = barX + 4;
        int tabWidth = 4 + HubIcon.SMALL + 4 + this.textRenderer.getWidth(name) + 6;
        // The Roads tab joins the toolbar: no edge between them.
        AureliaChrome.rounded(graphics, tabX, tabY, tabWidth, TAB_HEIGHT + 4, 3, RoadColours.BG_MID);
        AureliaChrome.roundedFrame(graphics, tabX, tabY, tabWidth, TAB_HEIGHT + 4, 3, AureliaPalette.LINE);
        graphics.fill(tabX + 1, tabY + TAB_HEIGHT, tabX + tabWidth - 1, tabY + TAB_HEIGHT + 4, RoadColours.BG_MID);
        graphics.fill(tabX + 3, tabY, tabX + tabWidth - 3, tabY + 1, RoadColours.ACCENT);
        HubIcon icon = HubIcon.forModule(RoadsModule.ID);
        icon.draw(graphics, tabX + 4, tabY + 2, HubIcon.SMALL, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
        graphics.drawText(this.textRenderer, name, tabX + 4 + HubIcon.SMALL + 4, tabY + 4, RoadColours.TEXT_MAIN, false);
        hot(new Box(tabX, tabY, tabWidth, TAB_HEIGHT), null, List.of(name, muted(Text.translatable("stratos.module.roads.description"))));

        int right = barX + barWidth - 4 - TAB_TILE;
        tabTile(graphics, right, tabY, TAB_TILE, HubIcon.GEAR, null, this.popUp == PopUp.SETTINGS, false, () -> togglePopUp(PopUp.SETTINGS),
                named("stratos.hub.settings"));
        int globeRight = right - 2;
        if (StratosEditorClient.editingEnabled()) {
            right -= 2 + TAB_TILE;
            tabTile(graphics, right, tabY, TAB_TILE, HubIcon.GLOBE, null, this.popUp == PopUp.WORLD, StratosEditorClient.stratosBlocks(),
                    () -> togglePopUp(PopUp.WORLD), named("stratos.hub.world"));
        }
        if (StratosTools.toolActive()) {
            Text putAway = Text.translatable("stratos.hub.put_away_short");
            int width = 4 + HubIcon.SMALL + 4 + this.textRenderer.getWidth(putAway) + 6;
            right -= 2 + width;
            tabTile(graphics, right, tabY, width, HubIcon.PUT_AWAY, putAway, false, false, this::putToolAway,
                    List.of(Text.translatable("stratos.hub.put_away")));
        }
        return globeRight;
    }

    /** A tile in the tab row: an icon, and a word after it if {@code text} is given; a warning dot if {@code dot}. */
    private void tabTile(DrawContext graphics, int x, int y, int w, HubIcon icon, Text text, boolean chosen, boolean dot, Runnable press, List<Text> tip) {
        boolean lit = hovering(x, y, w, TAB_HEIGHT);
        int fill = chosen ? AureliaPalette.ACCENT : lit ? RoadColours.BG_HOVER_STRONG : AureliaChrome.fade(RoadColours.BG_MID, 0.9F);
        AureliaChrome.rounded(graphics, x, y, w, TAB_HEIGHT, 3, fill);
        AureliaChrome.roundedFrame(graphics, x, y, w, TAB_HEIGHT, 3, chosen ? RoadColours.ACCENT : AureliaPalette.LINE);
        int colour = icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN;
        if (text == null) {
            icon.draw(graphics, x + (w - HubIcon.SMALL) / 2, y + 1, HubIcon.SMALL, colour);
        } else {
            icon.draw(graphics, x + 4, y + 1, HubIcon.SMALL, colour);
            graphics.drawText(this.textRenderer, text, x + 4 + HubIcon.SMALL + 4, y + 3, RoadColours.TEXT_MAIN, false);
        }
        if (dot) {
            graphics.fill(x + w - 6, y + 1, x + w - 1, y + 6, AureliaPalette.BG);
            graphics.fill(x + w - 5, y + 2, x + w - 2, y + 5, RoadColours.STATUS_WARNING);
        }
        hot(new Box(x, y, w, TAB_HEIGHT), press, tip);
    }

    /** The road tools, then the layouts, the palette and the node aid. */
    private void tools(DrawContext graphics, int barX, int panelY, int barWidth) {
        List<Tool> tools = StratosTools.tools(RoadsModule.ID);
        Toolbar.Sections sections = Toolbar.sections(barWidth, tools.size());
        Tool active = StratosTools.session().active().orElse(null);
        int toolsX = barX + sections.toolsX();
        label(graphics, Text.translatable("stratos.hub.tools"), toolsX, panelY + LABEL_Y);
        for (int i = 0; i < tools.size(); i++) {
            Tool tool = tools.get(i);
            HubIcon icon = HubIcon.forTool(tool.id());
            boolean inHand = tool == active;
            int x = toolsX + i * (Toolbar.TILE + Toolbar.GAP);
            int y = panelY + TILE_Y;
            tileBack(graphics, x, y, Toolbar.TILE, Toolbar.TILE, inHand, true, false);
            int inset = (Toolbar.TILE - HubIcon.LARGE) / 2;
            icon.draw(graphics, x + inset, y + inset, HubIcon.LARGE, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
            String id = tool.id();
            hot(new Box(x, y, Toolbar.TILE, Toolbar.TILE), () -> chooseTool(id), named(tool.titleKey()));
        }
        Text inHand = active != null ? Text.translatable(active.titleKey()) : Text.translatable(tools.isEmpty() ? "stratos.hub.no_tools" : "stratos.hub.no_tool");
        caption(graphics, inHand, active != null ? RoadColours.TEXT_MAIN : RoadColours.TEXT_MUTED, toolsX, panelY + CAPTION_Y,
                StrictMath.max(sections.toolsWidth(), 60));
        divider(graphics, barX + sections.toolsDivider(), panelY);
        layouts(graphics, barX, panelY, sections);
        divider(graphics, barX + sections.paletteDivider(), panelY);
        palette(graphics, barX + sections.paletteX(), panelY);
        divider(graphics, barX + sections.nodesDivider(), panelY);
        nodes(graphics, barX + sections.nodesX(), panelY);
    }

    /** The layouts as pictures, the chosen one lit; between arrows when there are more than fit. */
    private void layouts(DrawContext graphics, int barX, int panelY, Toolbar.Sections sections) {
        RoadSettings settings = StratosTools.roadSettings();
        RoadProfile chosen = settings.profile();
        Palette palette = settings.palette();
        List<RoadProfile> layouts = StratosTools.layouts();
        int stripX = barX + sections.stripX();
        int y = panelY + TILE_Y;
        label(graphics, Text.translatable("stratos.hub.layout"), stripX, panelY + LABEL_Y);
        Toolbar.Strip shown = Toolbar.Strip.of(layouts.size(), sections.stripWidth(), StrictMath.max(0, this.stripFirst));
        if (this.stripFirst < 0 && layouts.contains(chosen)) {
            shown = shown.showing(layouts.indexOf(chosen));
        }
        this.stripFirst = shown.first();
        this.strip = shown;
        this.stripBox = new Box(stripX, y, sections.stripWidth(), SHORT_TILE);
        int tilesX = stripX + (shown.pages() ? Toolbar.ARROW : 0);
        for (int i = 0; i < shown.shown(); i++) {
            RoadProfile profile = layouts.get(shown.first() + i);
            int x = tilesX + i * (Toolbar.TILE + Toolbar.GAP);
            tileBack(graphics, x, y, Toolbar.TILE, SHORT_TILE, profile == chosen, true, false);
            RoadPicture.draw(graphics, profile, palette, x + 1, y + 1, PICTURE_WIDTH, PICTURE_HEIGHT);
            badge(graphics, Integer.toString(profile.width()), x, y, Toolbar.TILE, SHORT_TILE);
            String id = profile.id();
            hot(new Box(x, y, Toolbar.TILE, SHORT_TILE), () -> StratosTools.chooseLayout(id),
                    List.of(Text.translatable("stratos.road.profile." + id), muted(Text.translatable("stratos.hub.layout_tip", profile.width()))));
        }
        if (shown.pages()) {
            arrow(graphics, stripX, y, false, shown.canBack());
            arrow(graphics, tilesX + shown.shown() * (Toolbar.TILE + Toolbar.GAP) - Toolbar.GAP, y, true, shown.canForward());
        }
        caption(graphics, Text.translatable("stratos.road.profile." + chosen.id()), RoadColours.TEXT_MAIN, stripX, panelY + CAPTION_Y,
                sections.stripWidth());
    }

    /** A strip arrow, a small triangle pointing back or on; faded at the end it points to. */
    private void arrow(DrawContext graphics, int x, int y, boolean on, boolean enabled) {
        int w = Toolbar.ARROW - 2;
        int colour = !enabled ? AureliaChrome.fade(RoadColours.ACCENT_SOFT, 0.3F) : hovering(x, y, w, SHORT_TILE) ? RoadColours.TEXT_MAIN : RoadColours.ACCENT_SOFT;
        int middle = y + SHORT_TILE / 2;
        for (int i = 0; i < 4; i++) {
            int column = on ? x + 2 + i : x + w - 3 - i;
            graphics.fill(column, middle - 3 + i, column + 1, middle + 4 - i, colour);
        }
        hot(new Box(x, y, w, SHORT_TILE), enabled ? () -> scroll(on ? 1 : -1) : null, List.of(Text.translatable(on ? "stratos.hub.layout_next" : "stratos.hub.layout_back")));
    }

    private void scroll(int tiles) {
        if (this.strip != null) {
            this.stripFirst = this.strip.scroll(tiles).first();
        }
    }

    /** The palette in use, as the chosen layout in its colours; a click lists the server's palettes above. */
    private void palette(DrawContext graphics, int x, int panelY) {
        RoadSettings settings = StratosTools.roadSettings();
        Palette palette = settings.palette();
        int y = panelY + TILE_Y;
        label(graphics, Text.translatable("stratos.hub.palette"), x, panelY + LABEL_Y);
        tileBack(graphics, x, y, Toolbar.TILE, SHORT_TILE, this.popUp == PopUp.PALETTE, true, false);
        RoadPicture.draw(graphics, settings.profile(), palette, x + 1, y + 1, PICTURE_WIDTH, PICTURE_HEIGHT);
        chevron(graphics, x, y, Toolbar.TILE);
        hot(new Box(x, y, Toolbar.TILE, SHORT_TILE), () -> togglePopUp(PopUp.PALETTE),
                List.of(paletteName(palette), muted(Text.translatable("stratos.hub.palette_tip"))));
        caption(graphics, paletteName(palette), RoadColours.TEXT_MAIN, x, panelY + CAPTION_Y, Toolbar.PALETTE_WIDTH);
    }

    /** The node aid in use; a click lists the aids above. */
    private void nodes(DrawContext graphics, int x, int panelY) {
        RoadPlanParams.NodeAids aids = StratosTools.roadSettings().nodeAids();
        int y = panelY + TILE_Y;
        label(graphics, Text.translatable("stratos.hub.nodes"), x, panelY + LABEL_Y);
        tileBack(graphics, x, y, SHORT_TILE, SHORT_TILE, this.popUp == PopUp.NODES, true, false);
        glyph(graphics, HubIcon.forNodeAids(aids), x, y, SHORT_TILE, SHORT_TILE);
        chevron(graphics, x, y, SHORT_TILE);
        hot(new Box(x, y, SHORT_TILE, SHORT_TILE), () -> togglePopUp(PopUp.NODES),
                List.of(nodeAidsName(aids), muted(Text.translatable("stratos.hub.nodes_tip"))));
        caption(graphics, nodeAidsName(aids), RoadColours.TEXT_MAIN, x, panelY + CAPTION_Y, Toolbar.NODES_WIDTH);
    }

    // ---- The pop-ups ----

    /** A pop-up's panel, its bottom right corner at ({@code right}, {@code bottom}); returns its top left corner. */
    private Box popUpPanel(DrawContext graphics, int right, int bottom, int w, int h, HubIcon icon, Text title) {
        Box box = new Box(right - w, bottom - h, w, h);
        this.popUpBox = box;
        hot(box, null, List.of());
        AureliaChrome.shadow(graphics, box.x(), box.y(), w, h, RADIUS);
        AureliaChrome.rounded(graphics, box.x(), box.y(), w, h, RADIUS, RoadColours.BG_MID);
        graphics.fill(box.x() + 1, box.y() + 1, box.x() + w - 1, box.y() + HEADER, RoadColours.ACCENT_DARK);
        graphics.fill(box.x() + 1, box.y() + 1, box.x() + 3, box.y() + HEADER, RoadColours.ACCENT);
        AureliaChrome.roundedFrame(graphics, box.x(), box.y(), w, h, RADIUS, AureliaPalette.LINE);
        icon.draw(graphics, box.x() + 6, box.y() + (HEADER - HubIcon.SMALL) / 2, HubIcon.SMALL, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
        graphics.drawText(this.textRenderer, UiText.fit(this.textRenderer, title, w - 6 - HubIcon.SMALL - 4 - 6), box.x() + 6 + HubIcon.SMALL + 4,
                box.y() + (HEADER - this.textRenderer.fontHeight) / 2 + 1, RoadColours.TEXT_MAIN, false);
        return box;
    }

    /** The header bar of a pop-up. */
    private static final int HEADER = 16;

    /** The server's palettes, each as the chosen layout in its colours and its name; a player's own marked as theirs. */
    private void palettePopUp(DrawContext graphics, int right, int bottom) {
        RoadSettings settings = StratosTools.roadSettings();
        Palette chosen = settings.palette();
        RoadProfile profile = settings.profile();
        List<Palette> palettes = settings.palettes().all();
        int rowWidth = PALETTE_POP_UP_WIDTH - 2 * POP_UP_PADDING;
        List<OrderedText> hint = wrap(Text.translatable("stratos.hub.palette_own"), rowWidth - 4);
        int h = HEADER + POP_UP_PADDING + palettes.size() * (POP_UP_ROW + 1) + 3 + hint.size() * this.textRenderer.fontHeight + POP_UP_PADDING;
        Box box = popUpPanel(graphics, right, bottom, PALETTE_POP_UP_WIDTH, h, HubIcon.PALETTE, Text.translatable("stratos.hub.palette"));
        int x = box.x() + POP_UP_PADDING;
        int y = box.y() + HEADER + POP_UP_PADDING;
        Text yours = Text.translatable("stratos.hub.yours");
        for (Palette palette : palettes) {
            boolean builtIn = BuiltInPalettes.isBuiltIn(palette.id());
            int room = rowWidth - PICTURE_WIDTH - 12 - (builtIn ? 4 : this.textRenderer.getWidth(yours) + 10);
            tileBack(graphics, x, y, rowWidth, POP_UP_ROW, palette.id().equals(chosen.id()), true, true);
            graphics.fill(x + 2, y + 1, x + 4 + PICTURE_WIDTH, y + 3 + PICTURE_HEIGHT, AureliaPalette.LINE);
            RoadPicture.draw(graphics, profile, palette, x + 3, y + 2, PICTURE_WIDTH, PICTURE_HEIGHT);
            graphics.drawText(this.textRenderer, UiText.fit(this.textRenderer, paletteName(palette), room), x + PICTURE_WIDTH + 10, y + 8,
                    RoadColours.TEXT_MAIN, false);
            if (!builtIn) {
                graphics.drawText(this.textRenderer, yours, x + rowWidth - 4 - this.textRenderer.getWidth(yours), y + 8, RoadColours.TEXT_MUTED, false);
            }
            String id = palette.id();
            hot(new Box(x, y, rowWidth, POP_UP_ROW), () -> {
                StratosTools.choosePalette(id);
                this.popUp = null;
            }, List.of());
            y += POP_UP_ROW + 1;
        }
        drawLines(graphics, hint, x + 2, y + 3, RoadColours.TEXT_MUTED);
    }

    /** The node aids, in the order the nodes key steps through them; Track and MTR nodes faded where they can't be used. */
    private void nodesPopUp(DrawContext graphics, int right, int bottom) {
        RoadSettings settings = StratosTools.roadSettings();
        RoadPlanParams.NodeAids chosen = settings.nodeAids();
        List<RoadPlanParams.NodeAids> all = List.of(RoadPlanParams.NodeAids.TRACK, RoadPlanParams.NodeAids.WOOL, RoadPlanParams.NodeAids.PREORIENTED,
                RoadPlanParams.NodeAids.NONE);
        int rowWidth = NODES_POP_UP_WIDTH - 2 * POP_UP_PADDING;
        List<OrderedText> hint = wrap(Text.translatable("stratos.hub.nodes_key", StratosTools.NODES_KEY.getBoundKeyLocalizedText()), rowWidth - 4);
        int h = HEADER + POP_UP_PADDING + all.size() * (POP_UP_ROW + 1) + 3 + hint.size() * this.textRenderer.fontHeight + POP_UP_PADDING;
        Box box = popUpPanel(graphics, right, bottom, NODES_POP_UP_WIDTH, h, HubIcon.forNodeAids(chosen), Text.translatable("stratos.hub.nodes"));
        int x = box.x() + POP_UP_PADDING;
        int y = box.y() + HEADER + POP_UP_PADDING;
        for (RoadPlanParams.NodeAids aids : all) {
            boolean usable = switch (aids) {
                case TRACK -> settings.trackAvailable();
                case PREORIENTED -> settings.mtrNodesAvailable();
                case WOOL, NONE -> true;
            };
            tileBack(graphics, x, y, rowWidth, POP_UP_ROW, aids == chosen, usable, true);
            AureliaChrome.rounded(graphics, x + 2, y + 1, SHORT_TILE, SHORT_TILE, TILE_RADIUS, RoadColours.BG_HOVER_STRONG);
            glyph(graphics, HubIcon.forNodeAids(aids), x + 2, y + 1, SHORT_TILE, SHORT_TILE);
            graphics.drawText(this.textRenderer, nodeAidsName(aids), x + SHORT_TILE + 10, y + 8, RoadColours.TEXT_MAIN, false);
            if (!usable) {
                graphics.fill(x, y, x + rowWidth, y + POP_UP_ROW, FADE);
            }
            List<Text> tip = usable ? List.of() : List.of(Text.translatable(aids == RoadPlanParams.NodeAids.TRACK
                    ? "stratos.hub.nodes.track_unavailable" : "stratos.hub.nodes.mtr_unavailable"));
            hot(new Box(x, y, rowWidth, POP_UP_ROW), usable ? () -> {
                StratosTools.chooseNodeAids(aids);
                this.popUp = null;
            } : null, tip);
            y += POP_UP_ROW + 1;
        }
        drawLines(graphics, hint, x + 2, y + 3, RoadColours.TEXT_MUTED);
    }

    /** This world: the warning, or that there are no road blocks, and its tiles; or the question about one of them. */
    private void worldBox(DrawContext graphics, int right, int bottom) {
        int inner = WORLD_BOX_WIDTH - 2 * WORLD_BOX_PADDING;
        boolean blocks = StratosEditorClient.stratosBlocks();
        if (this.confirming == null) {
            List<OrderedText> message = wrap(Text.translatable(blocks ? "stratos.hub.world.warning" : "stratos.hub.world.none"), inner - HubIcon.SMALL - 5);
            int textHeight = StrictMath.max(HubIcon.SMALL, message.size() * this.textRenderer.fontHeight);
            int h = HEADER + WORLD_BOX_PADDING + textHeight + 6 + WORLD_TILE_HEIGHT + WORLD_TILE_GAP + WORLD_TILE_HEIGHT + WORLD_BOX_PADDING;
            Box box = popUpPanel(graphics, right, bottom, WORLD_BOX_WIDTH, h, HubIcon.GLOBE, Text.translatable("stratos.hub.world"));
            int x = box.x() + WORLD_BOX_PADDING;
            int y = box.y() + HEADER + WORLD_BOX_PADDING;
            HubIcon icon = blocks ? HubIcon.WARN : HubIcon.INFO;
            icon.draw(graphics, x, y, HubIcon.SMALL, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
            drawLines(graphics, message, x + HubIcon.SMALL + 5, y + 2, blocks ? RoadColours.TEXT_MAIN : RoadColours.TEXT_MUTED);
            int tilesY = y + textHeight + 6;
            worldTile(graphics, x, tilesY, WORLD_TILE_WIDTH, HubIcon.BAKE, "stratos.hub.world.bake", Protocol.TASK_BAKE_WORLD, blocks);
            worldTile(graphics, x + inner - WORLD_TILE_WIDTH, tilesY, WORLD_TILE_WIDTH, HubIcon.REDRAW, "stratos.hub.world.redraw", Protocol.TASK_REDRAW_MARKINGS,
                    blocks);
            // P9: the world's driving side; the tile offers the other one. It needs no Stratos blocks, so it is never greyed out.
            boolean keepsRight = StratosEditorClient.drivingSide() == RoadPlanParams.DrivingSide.RHT;
            worldTile(graphics, x, tilesY + WORLD_TILE_HEIGHT + WORLD_TILE_GAP, inner, HubIcon.REBUILD,
                    keepsRight ? "stratos.hub.world.drive_left" : "stratos.hub.world.drive_right", keepsRight ? Protocol.TASK_DRIVE_LEFT : Protocol.TASK_DRIVE_RIGHT, true);
            return;
        }
        String base = switch (this.confirming) {
            case Protocol.TASK_BAKE_WORLD -> "bake";
            case Protocol.TASK_REDRAW_MARKINGS -> "redraw";
            case Protocol.TASK_DRIVE_LEFT -> "drive_left";
            default -> "drive_right";
        };
        List<OrderedText> question = wrap(Text.translatable("stratos.hub.world." + base + "_confirm"), inner);
        // P9: the operator's override of a driving-side switch (every player's roads) is a second row of its own
        boolean drive = Protocol.isDriveTask(this.confirming) && mayOverride();
        int h = HEADER + WORLD_BOX_PADDING + question.size() * this.textRenderer.fontHeight + 8 + BUTTON_HEIGHT + (drive ? 4 + BUTTON_HEIGHT : 0)
                + WORLD_BOX_PADDING;
        Box box = popUpPanel(graphics, right, bottom, WORLD_BOX_WIDTH, h, HubIcon.GLOBE, Text.translatable("stratos.hub.world"));
        int x = box.x() + WORLD_BOX_PADDING;
        int y = box.y() + HEADER + WORLD_BOX_PADDING;
        drawLines(graphics, question, x, y, RoadColours.TEXT_MAIN);
        int buttonY = y + question.size() * this.textRenderer.fontHeight + 8;
        int goWidth = button(graphics, x, buttonY, Text.translatable("stratos.hub.world." + base + "_go"), true, this::confirm);
        button(graphics, x + goWidth + 4, buttonY, Text.translatable("stratos.hub.world.cancel"), false, () -> this.confirming = null);
        if (drive) {
            Text all = Text.translatable("stratos.hub.world.drive_all");
            int allY = buttonY + BUTTON_HEIGHT + 4;
            button(graphics, x, allY, all, false, this::confirmAll, List.of(all, muted(Text.translatable("stratos.hub.world.drive_all_tip"))));
        }
    }

    /** One of This world's tiles, {@code width} wide: its icon over its name; greyed out while the world has no road blocks. */
    private void worldTile(DrawContext graphics, int x, int y, int width, HubIcon icon, String key, String task, boolean enabled) {
        Text name = Text.translatable(key);
        tileBack(graphics, x, y, width, WORLD_TILE_HEIGHT, false, enabled, false);
        icon.draw(graphics, x + (width - HubIcon.MEDIUM) / 2, y + 3, HubIcon.MEDIUM, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
        Text shown = UiText.fit(this.textRenderer, name, width - 4);
        graphics.drawText(this.textRenderer, shown, x + (width - this.textRenderer.getWidth(shown)) / 2, y + 20, RoadColours.TEXT_MAIN, false);
        if (!enabled) {
            graphics.fill(x, y, x + width, y + WORLD_TILE_HEIGHT, FADE);
        }
        hot(new Box(x, y, width, WORLD_TILE_HEIGHT), enabled ? () -> this.confirming = task : null,
                List.of(name, muted(Text.translatable(enabled ? key + "_tip" : "stratos.hub.world.none"))));
    }

    /** Settings: the pages down the left, the chosen page's rows on the right. */
    private void settingsBox(DrawContext graphics, int right, int bottom) {
        int w = StrictMath.min(SETTINGS_WIDTH, right - Toolbar.MARGIN);
        int h = StrictMath.min(SETTINGS_HEIGHT, bottom - Toolbar.MARGIN);
        Box box = popUpPanel(graphics, right, bottom, w, h, HubIcon.GEAR, Text.translatable("stratos.hub.settings"));
        this.settingsArea = box;
        int x = box.x();
        int y = box.y();
        List<HubSettings.Page> pages = HubSettings.pages();
        HubSettings.Page page = pages.stream().filter(each -> each.id().equals(lastSettingsPage)).findFirst().orElse(pages.get(0));
        int navY = y + SETTINGS_TOP;
        for (HubSettings.Page each : pages) {
            boolean on = each.id().equals(page.id());
            int navX = x + SETTINGS_PADDING;
            tileBack(graphics, navX, navY, SETTINGS_NAV_WIDTH, SETTINGS_NAV_HEIGHT, on, true, true);
            each.icon().draw(graphics, navX + 5, navY + 3, HubIcon.SMALL, each.icon().picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
            graphics.drawText(this.textRenderer, UiText.fit(this.textRenderer, each.title(), SETTINGS_NAV_WIDTH - 5 - HubIcon.SMALL - 5 - 3),
                    navX + 5 + HubIcon.SMALL + 5, navY + 5, RoadColours.TEXT_MAIN, false);
            String id = each.id();
            hot(new Box(navX, navY, SETTINGS_NAV_WIDTH, SETTINGS_NAV_HEIGHT), () -> lastSettingsPage = id, List.of());
            navY += SETTINGS_NAV_HEIGHT + 3;
        }
        int lineX = x + SETTINGS_PADDING + SETTINGS_NAV_WIDTH + 7;
        graphics.fill(lineX, y + SETTINGS_TOP, lineX + 1, y + h - SETTINGS_PADDING, AureliaPalette.LINE);
        int contentX = lineX + 8;
        int contentWidth = x + w - SETTINGS_PADDING - contentX;
        graphics.drawText(this.textRenderer, UiText.fit(this.textRenderer, page.title(), contentWidth), contentX, y + HEADER + 2, RoadColours.ACCENT_SOFT, false);
        int rowY = y + SETTINGS_TOP;
        for (HubSettings.Row row : page.rows().get()) {
            rowY += settingsRow(graphics, row, contentX, rowY, contentWidth);
        }
        page.resetAll().ifPresent(reset -> {
            Text text = Text.translatable("stratos.hub.settings.reset_all");
            int tw = 4 + HubIcon.SMALL + 4 + this.textRenderer.getWidth(text) + 6;
            int tx = x + w - SETTINGS_PADDING - tw;
            int ty = y + h - SETTINGS_PADDING - TAB_HEIGHT;
            tileBack(graphics, tx, ty, tw, TAB_HEIGHT, false, true, false);
            HubIcon.REBUILD.draw(graphics, tx + 4, ty + 1, HubIcon.SMALL, RoadColours.TEXT_MAIN);
            graphics.drawText(this.textRenderer, text, tx + 4 + HubIcon.SMALL + 4, ty + 3, RoadColours.TEXT_MAIN, false);
            hot(new Box(tx, ty, tw, TAB_HEIGHT), reset, List.of(text, muted(Text.translatable("stratos.hub.settings.reset_all_tip"))));
        });
    }

    /** The width a Choice's options or a Toggle's switch take at the right of their row. */
    private int controlsWidth(HubSettings.Row row) {
        if (row instanceof HubSettings.Choice choice) {
            int width = -1;
            for (Text option : choice.options()) {
                width += this.textRenderer.getWidth(option) + 10 + 1;
            }
            return StrictMath.max(0, width);
        }
        return row instanceof HubSettings.Toggle ? SWITCH_WIDTH : 0;
    }

    /** Draws one row of a Settings page at ({@code x}, {@code y}), {@code width} wide; returns the height it took. */
    private int settingsRow(DrawContext graphics, HubSettings.Row row, int x, int y, int width) {
        if (row instanceof HubSettings.Note note) {
            List<OrderedText> lines = wrap(note.text(), width);
            drawLines(graphics, lines, x, y - 2, RoadColours.TEXT_MUTED);
            return lines.size() * this.textRenderer.fontHeight + 3;
        }
        Text label;
        Text tip;
        if (row instanceof HubSettings.Choice choice) {
            label = choice.label();
            tip = choice.tip();
        } else if (row instanceof HubSettings.Toggle toggle) {
            label = toggle.label();
            tip = toggle.tip();
        } else {
            HubSettings.Measure measure = (HubSettings.Measure) row;
            label = measure.label();
            tip = measure.tip();
        }
        // a long label is cut with "…" short of its controls; its tooltip still has all of it
        Text shown = row instanceof HubSettings.Measure ? label : UiText.fit(this.textRenderer, label, StrictMath.max(0, width - controlsWidth(row) - 6));
        int labelWidth = this.textRenderer.getWidth(shown) + 2;
        graphics.drawText(this.textRenderer, shown, x, y + 3, RoadColours.TEXT_MAIN, false);
        hot(new Box(x, y, labelWidth, SETTINGS_CONTROL), null, tipLines(label, tip));
        int right = x + width;
        if (row instanceof HubSettings.Choice choice) {
            int optionX = right;
            int chosen = choice.chosen().getAsInt();
            for (int i = choice.options().size() - 1; i >= 0; i--) {
                Text option = choice.options().get(i);
                boolean on = chosen == i;
                int ow = this.textRenderer.getWidth(option) + 10;
                optionX -= ow;
                tileBack(graphics, optionX, y, ow, SETTINGS_CONTROL, on, true, false);
                graphics.drawText(this.textRenderer, option, optionX + 5, y + 3, on ? RoadColours.ON_ACCENT : RoadColours.TEXT_MUTED, false);
                int index = i;
                List<Text> optionTip = i < choice.optionTips().size() ? List.of(option, muted(choice.optionTips().get(i))) : List.of();
                hot(new Box(optionX, y, ow, SETTINGS_CONTROL), () -> choice.choose().accept(index), optionTip);
                optionX -= 1;
            }
        } else if (row instanceof HubSettings.Toggle toggle) {
            int sx = right - SWITCH_WIDTH;
            int sy = y + (SETTINGS_CONTROL - SWITCH_HEIGHT) / 2;
            boolean on = toggle.on().getAsBoolean();
            AureliaChrome.rounded(graphics, sx, sy, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT / 2, on ? RoadColours.ACCENT_STRONG : RoadColours.BG_HOVER_STRONG);
            int knob = SWITCH_HEIGHT - 4;
            int knobX = on ? sx + SWITCH_WIDTH - 2 - knob : sx + 2;
            AureliaChrome.rounded(graphics, knobX, sy + 2, knob, knob, knob / 2, on ? RoadColours.ON_ACCENT : RoadColours.TEXT_MUTED);
            hot(new Box(sx, sy, SWITCH_WIDTH, SWITCH_HEIGHT), () -> toggle.set().accept(!toggle.on().getAsBoolean()), tipLines(label, tip));
        } else if (row instanceof HubSettings.Measure measure) {
            Text button = measure.button();
            int bw = this.textRenderer.getWidth(button) + 12;
            tileBack(graphics, right - bw, y, bw, SETTINGS_CONTROL, false, true, false);
            graphics.drawText(this.textRenderer, button, right - bw + 6, y + 3, RoadColours.TEXT_MAIN, false);
            hot(new Box(right - bw, y, bw, SETTINGS_CONTROL), measure.press(), List.of());
            int room = width - labelWidth - bw - 10;
            Text status = UiText.fit(this.textRenderer, measure.status().get(), StrictMath.max(0, room));
            graphics.drawText(this.textRenderer, status, right - bw - 6 - this.textRenderer.getWidth(status), y + 3, RoadColours.TEXT_MUTED, false);
        }
        return SETTINGS_ROW;
    }

    // ---- Input ----

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (StratosClient.OPEN_HUB.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.popUp != null) {
            closePopUp();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** A click outside the open pop-up closes it (and does nothing else); inside, only the pop-up's own parts answer. */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (this.popUp != null && this.popUpBox != null && !this.popUpBox.contains(mouseX, mouseY)) {
            closePopUp();
            return true;
        }
        Optional<Hot> hot = at(mouseX, mouseY);
        if (hot.isPresent()) {
            Runnable press = hot.get().press();
            if (press != null) {
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1F));
                press.run();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The mouse wheel over a paging layout strip moves it a tile. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        Box box = this.stripBox;
        if (this.popUp == null && this.strip != null && this.strip.pages() && box != null && box.contains(mouseX, mouseY)) {
            scroll(amount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    /** What the mouse is over: the topmost thing drawn there, the open pop-up's own parts only when it is over the pop-up. */
    private Optional<Hot> at(double x, double y) {
        int from = this.popUpBox != null && this.popUpBox.contains(x, y) ? StrictMath.min(this.popUpHots, this.hots.size()) : 0;
        for (int i = this.hots.size() - 1; i >= from; i--) {
            Hot hot = this.hots.get(i);
            if (hot.box().contains(x, y)) {
                return Optional.of(hot);
            }
        }
        return Optional.empty();
    }

    private Optional<Hot> hovered() {
        return at(this.mouseX, this.mouseY);
    }

    /** Whether the mouse is over this part: outside the open pop-up for the toolbar's parts, inside it for the pop-up's. */
    private boolean hovering(int x, int y, int w, int h) {
        if (!new Box(x, y, w, h).contains(this.mouseX, this.mouseY)) {
            return false;
        }
        Box popUpArea = this.drawingPopUp ? null : this.lastPopUpBox;
        return popUpArea == null || this.popUp == null || !popUpArea.contains(this.mouseX, this.mouseY);
    }

    private void hot(Box box, Runnable press, List<Text> tip) {
        this.hots.add(new Hot(box, press, tip));
    }

    // ---- What the tiles do ----

    private void togglePopUp(PopUp which) {
        this.popUp = this.popUp == which ? null : which;
        this.confirming = null;
    }

    private void closePopUp() {
        this.popUp = null;
        this.confirming = null;
    }

    /** This world's Bake, Redraw or Driving side button: sends the world task it asked about and closes the hub. */
    private void confirm() {
        String task = this.confirming;
        this.confirming = null;
        if (task != null) {
            StratosEditorClient.task(task);
            close();
        }
    }

    /**
     * Whether to offer the driving-side override: an operator (or the singleplayer world's owner, who holds every node) can use it.
     * Only what the hub shows: the server checks {@code stratos.admin} itself, so a client that sends it anyway is refused.
     */
    private boolean mayOverride() {
        return this.client != null && (this.client.isInSingleplayer() || this.client.player != null && this.client.player.hasPermissionLevel(StratosPermissions.ADMIN_LEVEL));
    }

    /**
     * The driving-side question's second button: the same switch for every player's roads (the operator's override). The server
     * checks the permission, as for the switch itself.
     */
    private void confirmAll() {
        String task = this.confirming;
        this.confirming = null;
        if (task != null && Protocol.isDriveTask(task)) {
            StratosEditorClient.task(Protocol.drivesRight(task) ? Protocol.TASK_DRIVE_RIGHT_ALL : Protocol.TASK_DRIVE_LEFT_ALL);
            close();
        }
    }

    /** Takes the tool in hand and closes the hub. */
    private void chooseTool(String toolId) {
        StratosTools.select(toolId);
        close();
    }

    private void putToolAway() {
        StratosTools.leave();
        close();
    }

    // ---- Small parts ----

    /** A tile's back: lit when chosen or hovered; {@code plain} draws nothing at rest; a veil when not {@code enabled}. */
    private void tileBack(DrawContext graphics, int x, int y, int w, int h, boolean chosen, boolean enabled, boolean plain) {
        boolean lit = enabled && hovering(x, y, w, h);
        if (chosen) {
            AureliaChrome.rounded(graphics, x, y, w, h, TILE_RADIUS, AureliaPalette.ACCENT);
            AureliaChrome.roundedFrame(graphics, x, y, w, h, TILE_RADIUS, RoadColours.ACCENT);
        } else if (lit) {
            AureliaChrome.rounded(graphics, x, y, w, h, TILE_RADIUS, RoadColours.BG_HOVER_STRONG);
            AureliaChrome.roundedFrame(graphics, x, y, w, h, TILE_RADIUS, RoadColours.BORDER_ACCENT_SUBTLE);
        } else if (!plain) {
            AureliaChrome.rounded(graphics, x, y, w, h, TILE_RADIUS, RoadColours.BG_HOVER);
            AureliaChrome.roundedFrame(graphics, x, y, w, h, TILE_RADIUS, AureliaPalette.LINE);
        }
    }

    /** A primary or plain button with {@code text}; returns its width. */
    private int button(DrawContext graphics, int x, int y, Text text, boolean primary, Runnable press) {
        return button(graphics, x, y, text, primary, press, List.of());
    }

    /** As {@link #button(DrawContext, int, int, Text, boolean, Runnable)}, with a tooltip. */
    private int button(DrawContext graphics, int x, int y, Text text, boolean primary, Runnable press, List<Text> tip) {
        int w = this.textRenderer.getWidth(text) + 12;
        boolean lit = hovering(x, y, w, BUTTON_HEIGHT);
        int fill = primary ? (lit ? RoadColours.ACCENT_STRONG : AureliaPalette.ACCENT) : (lit ? RoadColours.BG_HOVER_STRONG : RoadColours.BG_HOVER);
        AureliaChrome.rounded(graphics, x, y, w, BUTTON_HEIGHT, TILE_RADIUS, fill);
        AureliaChrome.roundedFrame(graphics, x, y, w, BUTTON_HEIGHT, TILE_RADIUS, lit ? RoadColours.ACCENT : AureliaPalette.LINE);
        graphics.drawText(this.textRenderer, text, x + 6, y + 3, primary ? RoadColours.ON_ACCENT : RoadColours.TEXT_MAIN, false);
        hot(new Box(x, y, w, BUTTON_HEIGHT), press, tip);
        return w;
    }

    /** A glyph centred in its tile; a picture icon in its own colours. */
    private static void glyph(DrawContext graphics, HubIcon icon, int x, int y, int w, int h) {
        icon.draw(graphics, x + (w - HubIcon.SMALL) / 2, y + (h - HubIcon.SMALL) / 2, HubIcon.SMALL, icon.picture() ? 0xFFFFFFFF : RoadColours.TEXT_MAIN);
    }

    /** A short text in a tile's bottom right corner (a layout's width). */
    private void badge(DrawContext graphics, String badge, int x, int y, int w, int h) {
        int textWidth = this.textRenderer.getWidth(badge);
        graphics.fill(x + w - textWidth - 3, y + h - 9, x + w - 1, y + h - 1, 0xD0101215);
        graphics.drawText(this.textRenderer, badge, x + w - textWidth - 2, y + h - 8, RoadColours.TEXT_MAIN, false);
    }

    /** A small triangle pointing up in a tile's top right corner: its pop-up opens above. */
    private static void chevron(DrawContext graphics, int x, int y, int w) {
        for (int row = 0; row < 3; row++) {
            graphics.fill(x + w - 5 - row, y + 2 + row, x + w - 4 + row, y + 3 + row, RoadColours.ACCENT_SOFT);
        }
    }

    private static void divider(DrawContext graphics, int x, int panelY) {
        graphics.fill(x, panelY + (PANEL_HEIGHT - DIVIDER_HEIGHT) / 2, x + 1, panelY + (PANEL_HEIGHT + DIVIDER_HEIGHT) / 2, AureliaPalette.LINE);
    }

    /** A section's label, muted. */
    private void label(DrawContext graphics, Text text, int x, int y) {
        graphics.drawText(this.textRenderer, text, x, y, RoadColours.TEXT_MUTED, false);
    }

    /** A caption under a section, cut with "…" to fit {@code width}. */
    private void caption(DrawContext graphics, Text text, int argb, int x, int y, int width) {
        graphics.drawText(this.textRenderer, UiText.fit(this.textRenderer, text, width), x, y, argb, false);
    }

    private List<OrderedText> wrap(Text text, int width) {
        return this.textRenderer.wrapLines(text, StrictMath.max(1, width));
    }

    private void drawLines(DrawContext graphics, List<OrderedText> lines, int x, int y, int argb) {
        for (OrderedText line : lines) {
            graphics.drawText(this.textRenderer, line, x, y, argb, false);
            y += this.textRenderer.fontHeight;
        }
    }

    /** A tooltip: the name under {@code key}, then its explanation under {@code key + ".tip"} when the lang file has one. */
    private static List<Text> named(String key) {
        Text name = Text.translatable(key);
        return I18n.hasTranslation(key + ".tip") ? List.of(name, muted(Text.translatable(key + ".tip"))) : List.of(name);
    }

    private static Text muted(Text text) {
        return UiText.of(text, RoadColours.TEXT_MUTED);
    }

    /** A row's tooltip: its name, then its explanation, a line for each of the explanation's lines. */
    private static List<Text> tipLines(Text name, Text tip) {
        List<Text> lines = new ArrayList<>();
        lines.add(name);
        for (String line : tip.getString().split("\n")) {
            lines.add(muted(Text.literal(line)));
        }
        return lines;
    }

    /** How the hub names a palette: a built-in one by its lang key, any other by its file's name, else its id. */
    static Text paletteName(Palette palette) {
        return BuiltInPalettes.isBuiltIn(palette.id()) ? Text.translatable("stratos.palette." + palette.id())
                : Text.literal(palette.name().orElse(palette.id()));
    }

    static Text nodeAidsName(RoadPlanParams.NodeAids aids) {
        return Text.translatable(switch (aids) {
            case TRACK -> "stratos.tool.value.nodes_track";
            case WOOL -> "stratos.tool.value.nodes_wool";
            case PREORIENTED -> "stratos.tool.value.nodes_mtr";
            case NONE -> "stratos.tool.value.nodes_none";
        });
    }
}
