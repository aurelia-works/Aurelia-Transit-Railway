package org.mtr.aurelia.client.screen;

import com.aureliatransit.architecture.client.AirportWrenchScreen;
import com.aureliatransit.architecture.client.StationStudioClient;
import com.aureliatransit.architecture.client.StationWrenchScreen;
import com.aureliatransit.architecture.registry.ModItems;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.item.Item;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.mtr.aurelia.client.Announcer;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.client.RideClient;
import org.mtr.aurelia.client.ScriptingHandshake;
import org.mtr.mod.InitClient;
import org.mtr.mod.Items;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.mtr.mod.item.ItemRailBuilder;
import org.mtr.mod.packet.PacketUpdateRailBuilder;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

/**
 * The Aurelia panel: one screen with tabs for ATR's own switches and tools. Rendering rows read and write ATR's client
 * config ({@code config/mtr.json}); Tools are ATU's hub; Sound holds the Announcer switches; Riding holds sitting while
 * riding; Builder sets the Rail Builder in the player's hand.
 */
public final class AureliaPanelScreen extends Screen {

	public enum Tab {RENDERING, TOOLS, SOUND, ARCHITECTURE, RIDING, BUILDER}

	private static final int TAB_WIDTH = 90;
	private static final int BUTTON_WIDTH = 200;
	private static final int VALUE_WIDTH = 80;
	private static final int ROW = 24;
	private static final int TOP = 56;

	private sealed interface Row permits Toggle, Distance {
		String key();
	}

	private record Toggle(String key, BooleanSupplier get, Runnable toggle) implements Row {
	}

	private record Distance(String key, int max, IntSupplier get, IntConsumer set) implements Row {
	}

	/** {@code needsEdit}: greyed out without MTR edit permission. Depot health only reads until Regenerate is pressed. */
	private record Tool(String key, boolean needsEdit, Function<Screen, Screen> open) {
	}

	private static final List<Tool> TOOLS = List.of(
			new Tool("presets", true, TrainPresetScreen::new),
			new Tool("stops", true, RouteStopsScreen::new),
			new Tool("return", true, ReturnRouteScreen::new),
			new Tool("dwell", true, PlatformDwellScreen::new),
			new Tool("departures", true, DepartureEditorScreen::new),
			new Tool("overlap", true, StationOverlapScreen::new),
			new Tool("height", true, ZoneHeightScreen::new),
			new Tool("codes", true, StationCodesScreen::new),
			new Tool("depots", false, DepotHealthScreen::new),
			new Tool("speed", false, SpeedAuditScreen::new),
			new Tool("trains", false, TrainInspectorScreen::new),
			// /tp permission is checked by the server; the screen says so when the player lacks it.
			new Tool("jump", false, JumpScreen::new)
	);

	@Nullable
	private final Screen parent;
	private final Client config = Config.getClient();
	private final List<Row> renderingRows = List.of(
			new Toggle("options.mtr.hide_rain_inside_vehicles", config::getHideRainInsideVehicles, config::toggleHideRainInsideVehicles),
			new Toggle("options.mtr.faster_door_checks", config::getFasterDoorChecks, config::toggleFasterDoorChecks),
			new Toggle("options.mtr.cheap_shadows", config::getCheapShadows, config::toggleCheapShadows),
			new Toggle("options.mtr.rail_lod", config::getRailLod, config::toggleRailLod),
			new Distance("options.mtr.rail_simple_chunks", Client.RAIL_SIMPLE_CHUNKS_MAX, config::getRailSimpleChunks, config::setRailSimpleChunks),
			new Distance("options.mtr.rail_far_chunks", Client.RAIL_FAR_CHUNKS_MAX, config::getRailFarChunks, config::setRailFarChunks),
			new Toggle("options.mtr.shader_pack_fast_path", config::getShaderPackFastPath, config::toggleShaderPackFastPath),
			new Toggle("options.mtr.perf_on_f3", config::getPerfOnF3, config::togglePerfOnF3),
			new Toggle("options.mtr.merge_train_parts", config::getMergeTrainParts, config::toggleMergeTrainParts),
			new Toggle("options.mtr.train_lod", config::getTrainLod, config::toggleTrainLod),
			new Distance("options.mtr.train_interior_chunks", Client.TRAIN_INTERIOR_CHUNKS_MAX, config::getTrainInteriorChunks, config::setTrainInteriorChunks),
			new Distance("options.mtr.train_box_chunks", Client.TRAIN_BOX_CHUNKS_MAX, config::getTrainBoxChunks, config::setTrainBoxChunks),
			new Toggle("options.mtr.faster_model_loading", config::getFasterModelLoading, config::toggleFasterModelLoading),
			new Toggle("options.mtr.background_model_loading", config::getBackgroundModelLoading, config::toggleBackgroundModelLoading),
			new Toggle("options.mtr.free_unused_models", config::getFreeUnusedModels, config::toggleFreeUnusedModels)
	);

	private Tab tab;
	private int firstRow;

	public AureliaPanelScreen(@Nullable Screen parent, Tab tab) {
		super(Text.translatable("gui.aurelia.panel.title"));
		this.parent = parent;
		this.tab = tab;
	}

	@Override
	protected void init() {
		final Tab[] tabs = Tab.values();
		final int tabWidth = Math.min(TAB_WIDTH, (width - 16 - (tabs.length - 1) * 4) / tabs.length);
		final int tabsLeft = (width - tabs.length * tabWidth - (tabs.length - 1) * 4) / 2;
		for (int i = 0; i < tabs.length; i++) {
			final Tab target = tabs[i];
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.panel.tab." + target.name().toLowerCase()), b -> {
				tab = target;
				firstRow = 0;
				clearAndInit();
			}).dimensions(tabsLeft + i * (tabWidth + 4), 28, tabWidth, 20).build());
			button.active = target != tab;
		}
		switch (tab) {
			case RENDERING -> initRendering();
			case TOOLS -> initTools();
			case SOUND -> initSound();
			case ARCHITECTURE -> initArchitecture();
			case RIDING -> initRiding();
			case BUILDER -> initBuilder();
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions((width - BUTTON_WIDTH) / 2, height - 28, BUTTON_WIDTH, 20).build());
	}

	private int visibleRows() {
		return Math.max(1, (height - 36 - TOP) / ROW);
	}

	private void initRendering() {
		firstRow = Math.max(0, Math.min(firstRow, renderingRows.size() - visibleRows()));
		final int valueX = width - 16 - VALUE_WIDTH;
		for (int i = firstRow; i < Math.min(renderingRows.size(), firstRow + visibleRows()); i++) {
			final int y = TOP + (i - firstRow) * ROW;
			if (renderingRows.get(i) instanceof Toggle toggle) {
				addDrawableChild(ButtonWidget.builder(ScreenTexts.onOrOff(toggle.get().getAsBoolean()), b -> {
					toggle.toggle().run();
					b.setMessage(ScreenTexts.onOrOff(toggle.get().getAsBoolean()));
				}).dimensions(valueX, y, VALUE_WIDTH, 20).build());
			} else if (renderingRows.get(i) instanceof Distance distance) {
				addDrawableChild(new DistanceSlider(valueX, y, distance));
			}
		}
	}

	private void initTools() {
		final int columns = Math.min(3, (TOOLS.size() + visibleRows() - 1) / visibleRows());
		final int buttonWidth = columns == 1 ? BUTTON_WIDTH : Math.min(BUTTON_WIDTH, (width - 16 - (columns - 1) * 8) / columns);
		final int rows = (TOOLS.size() + columns - 1) / columns;
		final int left = (width - columns * buttonWidth - (columns - 1) * 8) / 2;
		final boolean canEdit = MtrBridge.canEdit();
		for (int i = 0; i < TOOLS.size(); i++) {
			final Tool tool = TOOLS.get(i);
			final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.tools." + tool.key()), b -> client.setScreen(tool.open().apply(this)))
					.dimensions(left + (i / rows) * (buttonWidth + 8), TOP + (i % rows) * ROW, buttonWidth, 20).build());
			button.active = canEdit || !tool.needsEdit();
		}
	}

	private void initSound() {
		final int left = (width - BUTTON_WIDTH) / 2;
		final ButtonWidget announcer = addDrawableChild(ButtonWidget.builder(announcerLabel(), b -> {
			Announcer.setEnabled(!Announcer.enabled());
			b.setMessage(announcerLabel());
		}).dimensions(left, TOP, BUTTON_WIDTH, 20).build());
		announcer.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.announcer.tooltip")));
		final ButtonWidget platform = addDrawableChild(ButtonWidget.builder(platformLabel(), b -> {
			Announcer.setPlatformEnabled(!Announcer.platformEnabled());
			b.setMessage(platformLabel());
		}).dimensions(left, TOP + ROW, BUTTON_WIDTH, 20).build());
		platform.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.platform.tooltip")));
		final ButtonWidget melodies = addDrawableChild(ButtonWidget.builder(melodiesLabel(), b -> {
			ScriptingHandshake.setMelodies(!ScriptingHandshake.melodiesEnabled());
			b.setMessage(melodiesLabel());
		}).dimensions(left, TOP + ROW * 2, BUTTON_WIDTH, 20).build());
		melodies.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.tools.melodies.tooltip")));
	}

	/**
	 * Whether each ride starts sitting (the sit key flips it on the way), and how far a sitting rider drops onto the seat.
	 */
	private void initRiding() {
		final int left = (width - BUTTON_WIDTH) / 2;
		addDrawableChild(ButtonWidget.builder(sitLabel(), b -> {
			RideClient.setSitWhenBoarding(!RideClient.sitWhenBoarding());
			b.setMessage(sitLabel());
		}).dimensions(left, TOP, BUTTON_WIDTH, 20).build()).setTooltip(Tooltip.of(Text.translatable("gui.aurelia.riding.sit.tooltip")));
		addDrawableChild(new SliderWidget(left, TOP + ROW, BUTTON_WIDTH, 20, Text.empty(), RideClient.sitDrop() / RideClient.MAX_SIT_DROP) {
			{
				updateMessage();
			}

			@Override
			protected void updateMessage() {
				setMessage(Text.translatable("gui.aurelia.riding.drop", String.format("%.2f", value * RideClient.MAX_SIT_DROP)));
			}

			@Override
			protected void applyValue() {
				RideClient.setSitDrop(value * RideClient.MAX_SIT_DROP);
			}

			@Override
			public void onRelease(double mouseX, double mouseY) {
				super.onRelease(mouseX, mouseY);
				RideClient.saveSitDrop();
			}
		});
	}

	private static Text sitLabel() {
		return Text.translatable("gui.aurelia.riding.sit", ScreenTexts.onOrOff(RideClient.sitWhenBoarding()));
	}

	/**
	 * Width and height sliders and the three part switches. Each change is written to the stack here and sent to the server.
	 */
	private void initBuilder() {
		final boolean offHand = !isRailBuilder(client.player.getMainHandStack()) && isRailBuilder(client.player.getOffHandStack());
		final net.minecraft.item.ItemStack itemStack = offHand ? client.player.getOffHandStack() : client.player.getMainHandStack();
		if (!isRailBuilder(itemStack)) {
			return;
		}
		final int left = (width - BUTTON_WIDTH) / 2;
		final UnaryOperator<UnaryOperator<ItemRailBuilder.Settings>> update = change -> settings -> {
			final ItemRailBuilder.Settings newSettings = change.apply(settings);
			ItemRailBuilder.write(itemStack, newSettings);
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateRailBuilder(offHand, newSettings));
			return newSettings;
		};
		addDrawableChild(new BuilderSlider(left, TOP, "gui.aurelia.builder.width", ItemRailBuilder.MIN_WIDTH, ItemRailBuilder.MAX_WIDTH, 2, ItemRailBuilder.read(itemStack).width(),
				value -> update.apply(settings -> new ItemRailBuilder.Settings(value, settings.height(), settings.deck(), settings.clear(), settings.walls())).apply(ItemRailBuilder.read(itemStack))));
		addDrawableChild(new BuilderSlider(left, TOP + ROW, "gui.aurelia.builder.height", ItemRailBuilder.MIN_HEIGHT, ItemRailBuilder.MAX_HEIGHT, 1, ItemRailBuilder.read(itemStack).height(),
				value -> update.apply(settings -> new ItemRailBuilder.Settings(settings.width(), value, settings.deck(), settings.clear(), settings.walls())).apply(ItemRailBuilder.read(itemStack))));
		addBuilderToggle(left, TOP + ROW * 2, "gui.aurelia.builder.deck", itemStack, ItemRailBuilder.Settings::deck,
				settings -> new ItemRailBuilder.Settings(settings.width(), settings.height(), !settings.deck(), settings.clear(), settings.walls()), update);
		addBuilderToggle(left, TOP + ROW * 3, "gui.aurelia.builder.clear", itemStack, ItemRailBuilder.Settings::clear,
				settings -> new ItemRailBuilder.Settings(settings.width(), settings.height(), settings.deck(), !settings.clear(), settings.walls()), update);
		addBuilderToggle(left, TOP + ROW * 4, "gui.aurelia.builder.walls", itemStack, ItemRailBuilder.Settings::walls,
				settings -> new ItemRailBuilder.Settings(settings.width(), settings.height(), settings.deck(), settings.clear(), !settings.walls()), update);
	}

	private void addBuilderToggle(int x, int y, String key, net.minecraft.item.ItemStack itemStack, Function<ItemRailBuilder.Settings, Boolean> get, UnaryOperator<ItemRailBuilder.Settings> flip, UnaryOperator<UnaryOperator<ItemRailBuilder.Settings>> update) {
		addDrawableChild(ButtonWidget.builder(Text.translatable(key, ScreenTexts.onOrOff(get.apply(ItemRailBuilder.read(itemStack)))), b -> {
			final ItemRailBuilder.Settings settings = update.apply(flip).apply(ItemRailBuilder.read(itemStack));
			b.setMessage(Text.translatable(key, ScreenTexts.onOrOff(get.apply(settings))));
		}).dimensions(x, y, BUTTON_WIDTH, 20).build());
	}

	private static boolean isRailBuilder(net.minecraft.item.ItemStack itemStack) {
		return itemStack.getItem() == Items.RAIL_BUILDER.get().data;
	}

	/** ATA's tools: Station Studio opens in the browser; each wrench screen edits the wrench in the player's hand. */
	private void initArchitecture() {
		final int left = (width - BUTTON_WIDTH) / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.architecture.studio"), b -> StationStudioClient.open())
				.dimensions(left, TOP, BUTTON_WIDTH, 20).build())
				.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.architecture.studio.tooltip")));
		addWrenchButton("station", ModItems.STATION_WRENCH, StationWrenchScreen::open, TOP + ROW);
		addWrenchButton("airport", ModItems.AIRPORT_WRENCH, AirportWrenchScreen::open, TOP + ROW * 2);
	}

	private void addWrenchButton(String key, Item wrench, Consumer<Hand> open, int y) {
		final Hand hand = heldHand(wrench);
		final ButtonWidget button = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.architecture." + key), b -> open.accept(hand))
				.dimensions((width - BUTTON_WIDTH) / 2, y, BUTTON_WIDTH, 20).build());
		button.active = hand != null;
		button.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.architecture." + key + (hand == null ? ".hold" : ".tooltip"))));
	}

	@Nullable
	private Hand heldHand(Item item) {
		if (client == null || client.player == null) {
			return null;
		}
		for (final Hand hand : Hand.values()) {
			if (client.player.getStackInHand(hand).isOf(item)) {
				return hand;
			}
		}
		return null;
	}

	private static Text platformLabel() {
		return Text.translatable("gui.aurelia.tools.platform", ScreenTexts.onOrOff(Announcer.platformEnabled()));
	}

	private static Text melodiesLabel() {
		return Text.translatable("gui.aurelia.tools.melodies", ScreenTexts.onOrOff(ScriptingHandshake.melodiesEnabled()));
	}

	private static Text announcerLabel() {
		return Text.translatable("gui.aurelia.tools.announcer", ScreenTexts.onOrOff(Announcer.enabled()));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (tab == Tab.RENDERING) {
			final int newFirstRow = Math.max(0, Math.min(renderingRows.size() - visibleRows(), firstRow - (int) Math.signum(amount)));
			if (newFirstRow != firstRow) {
				firstRow = newFirstRow;
				clearAndInit();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		if (tab == Tab.RENDERING) {
			for (int i = firstRow; i < Math.min(renderingRows.size(), firstRow + visibleRows()); i++) {
				context.drawTextWithShadow(textRenderer, Text.translatable(renderingRows.get(i).key()), 16, TOP + (i - firstRow) * ROW + 6, 0xFFFFFF);
			}
			if (renderingRows.size() > visibleRows()) {
				context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.panel.scroll", firstRow + 1, Math.min(renderingRows.size(), firstRow + visibleRows()), renderingRows.size()), width / 2, height - 44, 0xAAAAAA);
			}
		} else if (tab == Tab.TOOLS && !MtrBridge.canEdit()) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.no_permission"), width / 2, TOP - 10, 0xFF5555);
		} else if (tab == Tab.BUILDER && !isRailBuilder(client.player.getMainHandStack()) && !isRailBuilder(client.player.getOffHandStack())) {
			context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.aurelia.builder.hold"), width / 2, TOP + 6, 0xAAAAAA);
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		Config.save();
		client.setScreen(parent);
	}

	/** A Rail Builder number from {@code min} to {@code max} in steps of {@code step}. */
	private static final class BuilderSlider extends SliderWidget {

		private final String key;
		private final int min;
		private final int steps;
		private final int step;
		private final IntConsumer set;

		private BuilderSlider(int x, int y, String key, int min, int max, int step, int current, IntConsumer set) {
			super(x, y, BUTTON_WIDTH, 20, Text.empty(), (double) (current - min) / step / ((max - min) / step));
			this.key = key;
			this.min = min;
			this.steps = (max - min) / step;
			this.step = step;
			this.set = set;
			updateMessage();
		}

		private int current() {
			return min + (int) Math.round(value * steps) * step;
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable(key, current()));
		}

		@Override
		protected void applyValue() {
			set.accept(current());
		}
	}

	/** Whole chunks from 0 (off, shown as "-") to the row's maximum. */
	private static final class DistanceSlider extends SliderWidget {

		private final Distance row;

		private DistanceSlider(int x, int y, Distance row) {
			super(x, y, VALUE_WIDTH, 20, Text.empty(), (double) row.get().getAsInt() / row.max());
			this.row = row;
			updateMessage();
		}

		private int chunks() {
			return (int) Math.round(value * row.max());
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.literal(chunks() == 0 ? "-" : String.valueOf(chunks())));
		}

		@Override
		protected void applyValue() {
			row.set().accept(chunks());
		}
	}
}
