package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.registry.ModItems;
import com.aureliatransit.architecture.station.world.WrenchMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.mtr.aurelia.client.screen.AureliaPanelScreen;
import org.mtr.aurelia.client.ui.AureliaPalette;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The one ATR menu: Build, Guide, Settings and Dev in one screen. Build holds the wrench modes, Station Studio and
 * wayfinding; Guide opens the Station Codex; Settings holds the Aurelia look switches and the wrench's action bar; Dev
 * opens the old dev panel on any of its tabs. The ATR dashboard button and the M key open this screen.
 */
public final class AtrMenuScreen extends Screen {

	public enum Tab {BUILD, GUIDE, SETTINGS, DEV}

	private static final int TAB_WIDTH = 110;
	private static final int BUTTON_WIDTH = 190;
	private static final int ROW = 24;
	private static final int TAB_ROW = 22;
	private static final int COLUMN_GAP = 8;

	private final Tab tab;
	@Nullable
	private final Screen parent;
	private int top = 60;

	public AtrMenuScreen(@Nullable Screen parent, Tab tab) {
		super(Text.translatable("gui.aurelia.menu.title"));
		this.parent = parent;
		this.tab = tab;
	}

	public static void open(@Nullable Screen parent, Tab tab) {
		MinecraftClient.getInstance().setScreen(new AtrMenuScreen(parent, tab));
	}

	@Override
	protected void init() {
		// the four tabs always share one row; the subtitle sits under them, then the tab's buttons
		final Tab[] tabs = Tab.values();
		final int tabWidth = Math.max(1, Math.min(TAB_WIDTH, (width - 16 - (tabs.length - 1) * 4) / tabs.length));
		final int left = (width - tabs.length * tabWidth - (tabs.length - 1) * 4) / 2;
		top = 28 + TAB_ROW + 18;
		for (int i = 0; i < tabs.length; i++) {
			final Tab target = tabs[i];
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.tab." + target.name().toLowerCase()),
					b -> MinecraftClient.getInstance().setScreen(new AtrMenuScreen(parent, target)))
					.dimensions(left + i * (tabWidth + 4), 28, tabWidth, 18).build());
			button.active = target != tab;
		}
		switch (tab) {
			case BUILD -> initBuild();
			case GUIDE -> initGuide();
			case SETTINGS -> initSettings();
			case DEV -> initDev();
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.close"), b -> close())
				.dimensions((width - BUTTON_WIDTH) / 2, height - 28, BUTTON_WIDTH, 20).build());
	}

	private void initBuild() {
		final int left = (width - (BUTTON_WIDTH * 2 + COLUMN_GAP)) / 2;
		final Hand hand = WrenchClient.wrenchHand(client);
		wrenchButton(left, top, "station", WrenchMode.STATION, hand);
		wrenchButton(left + BUTTON_WIDTH + COLUMN_GAP, top, "airport", WrenchMode.AIRPORT, hand);
		wrenchButton(left, top + ROW, "bridge", WrenchMode.BRIDGE, hand);
		wrenchButton(left + BUTTON_WIDTH + COLUMN_GAP, top + ROW, "earthworks", WrenchMode.EARTHWORKS, hand);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.build.studio"), b -> StationStudioClient.open())
				.dimensions(left, top + ROW * 2, BUTTON_WIDTH, 20).build())
				.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.build.studio.tooltip")));
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.build.wayfinding"), b -> {
			close();
			CodexScreen.openChapter("Finding your way");
		}).dimensions(left + BUTTON_WIDTH + COLUMN_GAP, top + ROW * 2, BUTTON_WIDTH, 20).build())
				.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.build.wayfinding.tooltip")));
		final ButtonWidget give = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.build.give"), b -> {
			if (client.player != null) {
				client.player.networkHandler.sendChatCommand("give @s " + Registries.ITEM.getId(ModItems.WRENCH));
			}
		}).dimensions(left, top + ROW * 3, BUTTON_WIDTH * 2 + COLUMN_GAP, 20).build());
		give.active = client.player != null && client.player.hasPermissionLevel(2);
		give.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.build.give.tooltip")));
	}

	private void wrenchButton(int x, int y, String key, WrenchMode mode, @Nullable Hand hand) {
		final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.build." + key), b -> {
			if (hand == null) {
				if (client.player != null) {
					client.player.sendMessage(Text.translatable("gui.aurelia.menu.build.hold"), true);
				}
				return;
			}
			close();
			StationWrenchScreen.openMode(hand, mode);
		}).dimensions(x, y, BUTTON_WIDTH, 20).build());
		button.active = hand != null;
		button.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.build." + key + "." + (hand == null ? "hold" : "tooltip"))));
	}

	private void initGuide() {
		final int left = (width - BUTTON_WIDTH) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.guide.codex"), b -> {
			close();
			CodexScreen.open();
		}).dimensions(left, top, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.guide.wayfinding"), b -> {
			close();
			CodexScreen.openChapter("Finding your way");
		}).dimensions(left, top + ROW, BUTTON_WIDTH, 20).build());
		final ButtonWidget item = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.guide.item"), b -> {
			if (client.player != null) {
				client.player.networkHandler.sendChatCommand("ata_guide");
			}
		}).dimensions(left, top + ROW * 2, BUTTON_WIDTH, 20).build());
		item.active = client.player != null && client.player.hasPermissionLevel(2);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.guide.start"), b -> {
			close();
			CodexScreen.openChapter("Getting started");
		}).dimensions(left, top + ROW * 3, BUTTON_WIDTH, 20).build());
	}

	/** The wrench's two switches, then links to the panel tabs a player sets once; two columns so it fits a 427x240 GUI. */
	private void initSettings() {
		int cell = 0;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.settings.action_bar",
						net.minecraft.screen.ScreenTexts.onOrOff(WrenchSettings.actionBar())), b -> {
					WrenchSettings.toggleActionBar();
					b.setMessage(Text.translatable("gui.aurelia.menu.settings.action_bar",
							net.minecraft.screen.ScreenTexts.onOrOff(WrenchSettings.actionBar())));
				}).dimensions(cellX(cell), cellY(cell), BUTTON_WIDTH, 20).build())
				.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.menu.settings.action_bar.tooltip")));
		cell++;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.settings.menu_look",
						net.minecraft.screen.ScreenTexts.onOrOff(WrenchSettings.menuLook())), b -> {
					WrenchSettings.toggleMenuLook();
					b.setMessage(Text.translatable("gui.aurelia.menu.settings.menu_look",
							net.minecraft.screen.ScreenTexts.onOrOff(WrenchSettings.menuLook())));
				}).dimensions(cellX(cell), cellY(cell), BUTTON_WIDTH, 20).build());
		cell++;
		final List<AureliaPanelScreen.Tab> panels = List.of(AureliaPanelScreen.Tab.SOUND, AureliaPanelScreen.Tab.RIDING,
				AureliaPanelScreen.Tab.LOOK, AureliaPanelScreen.Tab.PACKS);
		for (final AureliaPanelScreen.Tab panel : panels) {
			closeOnOpen(cellX(cell), cellY(cell), panel, "settings");
			cell++;
		}
	}

	/** Two columns of buttons, filled row by row. */
	private int cellX(int cell) {
		return (width - (BUTTON_WIDTH * 2 + COLUMN_GAP)) / 2 + cell % 2 * (BUTTON_WIDTH + COLUMN_GAP);
	}

	private int cellY(int cell) {
		return top + cell / 2 * ROW;
	}

	private void closeOnOpen(int x, int y, AureliaPanelScreen.Tab panel, String prefix) {
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu." + prefix + "." + panel.name().toLowerCase()), b ->
				MinecraftClient.getInstance().setScreen(new AureliaPanelScreen(this, panel))).dimensions(x, y, BUTTON_WIDTH, 20).build());
	}

	/** The panel itself, then a link to each of its build and test tabs, in two columns. */
	private void initDev() {
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.menu.dev.open"), b ->
				MinecraftClient.getInstance().setScreen(new AureliaPanelScreen(this, AureliaPanelScreen.Tab.RENDERING)))
				.dimensions(cellX(0), top, BUTTON_WIDTH * 2 + COLUMN_GAP, 20).build());
		final AureliaPanelScreen.Tab[] tabs = {AureliaPanelScreen.Tab.RENDERING, AureliaPanelScreen.Tab.TOOLS,
				AureliaPanelScreen.Tab.ARCHITECTURE, AureliaPanelScreen.Tab.BUILD, AureliaPanelScreen.Tab.PASSENGERS,
				AureliaPanelScreen.Tab.DRIVING, AureliaPanelScreen.Tab.PACKS};
		for (int i = 0; i < tabs.length; i++) {
			closeOnOpen(cellX(i + 2), cellY(i + 2), tabs[i], "dev");
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (WrenchSettings.menuLook()) {
			context.fill(0, 0, width, height, AureliaPalette.BG);
			context.fill(12, 22, width - 12, height - 22, AureliaPalette.FIELD);
		} else {
			renderBackground(context);
		}
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.menu.subtitle." + tab.name().toLowerCase(java.util.Locale.ROOT)), width / 2, top - 12, 0xAAAAAA);
		super.render(context, mouseX, mouseY, delta);
		if (tab == Tab.BUILD && client != null && client.player != null && WrenchClient.wrenchHand(client) == null) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.menu.build.no_wrench"),
					width / 2, height - 48, 0xFFAA00);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		MinecraftClient.getInstance().setScreen(parent);
	}

	/** Dev screenshot hook: open on a tab by name. */
	public static boolean openNamed(String name) {
		for (final Tab tab : Tab.values()) {
			if (tab.name().equalsIgnoreCase(name)) {
				open(MinecraftClient.getInstance().currentScreen, tab);
				return true;
			}
		}
		return false;
	}
}
