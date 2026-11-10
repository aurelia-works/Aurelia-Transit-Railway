package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.block.elevated.StyleCycle;
import com.aureliatransit.architecture.client.interactive.EditorWidgets;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.AirportPresets;
import com.aureliatransit.architecture.station.StationDescriptionParser;
import com.aureliatransit.architecture.station.StationPresets;
import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import com.aureliatransit.architecture.station.world.WrenchMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.function.Supplier;

/**
 * The station wrench screen. The simple page builds a station from buttons: a city look (and a second one to mix in), through
 * station or terminus, tracks, platforms, length, roof, building and name, then Place / Build it / Undo. The advanced page is
 * the typed description with the preset buttons and every generator action. Choices only change the screen; each action
 * button sends one packet (hand, action, description, choices) and closes the screen so the chat summary and the outline are
 * visible. Closing without an action saves changed choices. The server re-checks the operator level and bounds everything it
 * reads. Where the window has room, a live 3D preview of the station stands beside the controls ({@link StationPreview}).
 */
public class StationWrenchScreen extends Screen {

	/** The control column on a wide screen; narrower ones shrink it (down to {@link #MIN_COLUMN}) to keep the 3D preview. */
	private static final int WIDTH = 320;
	private static final int MIN_COLUMN = 260;
	/** The preview width a shrunk column aims to leave. */
	private static final int FIT_PREVIEW = 180;
	private static final int ROW = 22;
	/** The look and preset grids: shorter buttons, so sixteen cities fit on a small screen. */
	private static final int GRID_ROW = 18;
	private static final int GRID_BUTTON = 16;
	/** From the last choice row to the action buttons: the station's one-line summary and the next-step hint. */
	private static final int SUMMARY = 24;
	private static final String KEY = EditorWidgets.KEY + "station_wrench.";
	private static final int GAP = 10;
	/** Space left of the controls and right of the preview. */
	private static final int MARGIN = 16;
	/** The size a fresh station gets when it becomes a terminus (as the parser gives a bare "terminus"). */
	private static final int TERMINUS_TRACKS = 8;
	private static final int TERMINUS_LENGTH = 160;
	/** The narrowest preview worth showing; below it the controls stay centred on their own. */
	private static final int MIN_PREVIEW = 150;
	private static final int MAX_PREVIEW = 480;
	/** Typing waits this long before the preview replans. */
	private static final long PREVIEW_DELAY_MS = 350;
	private static final int MODE_ROW = 22;
	private static final int AIRPORT_COLUMNS = 3;
	private static final int AIRPORT_GRID_ROW = 20;

	private final Hand hand;
	private final String initial;
	private final StationRecipe initialRecipe;
	private StationRecipe recipe;
	private WrenchMode mode;
	private boolean advanced;
	/** The advanced page's text; {@code custom} once the player typed or picked a preset, else it follows the choices. */
	private String typed;
	private boolean custom;
	private boolean sent;
	private final StationPreview preview = new StationPreview();
	private long editedAt;
	private boolean dragging;
	/** Bridge and airport: whether the secondary tools are open under More. */
	private boolean more;
	/** Station: the city look grid or the preset grid is open on its own page instead of the choices. */
	private Picker picker = Picker.NONE;
	/** The control column's width, its half and third, set by {@link #fitColumn()} on each layout. */
	private int column = WIDTH;
	private int half = (WIDTH - 4) / 2;
	private int third = (WIDTH - 8) / 3;

	private enum Picker {NONE, LOOK, PRESET}

	public StationWrenchScreen(Hand hand, String description, StationRecipe recipe) {
		this(hand, description, recipe, modeOf(hand));
	}

	public StationWrenchScreen(Hand hand, String description, StationRecipe recipe, WrenchMode mode) {
		super(Text.translatable(KEY + "title"));
		this.hand = hand;
		this.initialRecipe = recipe;
		this.recipe = recipe;
		this.typed = description;
		this.mode = mode;
		// a description typed on the advanced page (not the one the choices make) reopens there
		this.custom = mode == WrenchMode.STATION && !description.isEmpty() && !description.equals(recipe.description());
		this.advanced = custom;
		this.initial = description();
	}

	/** The stack's own mode, or Station when the screen is opened without a wrench in hand (the dev screenshot hook). */
	private static WrenchMode modeOf(Hand hand) {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client.player == null ? WrenchMode.STATION : StationWrenchItem.mode(client.player.getStackInHand(hand));
	}

	/** Opens the screen for the wrench in {@code hand} of the local player, in the mode saved on that stack. */
	public static void open(Hand hand) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.player.getStackInHand(hand).getItem() instanceof StationWrenchItem) {
			final ItemStack stack = client.player.getStackInHand(hand);
			client.setScreen(new StationWrenchScreen(hand, StationWrenchItem.description(stack), StationWrenchItem.recipe(stack),
					StationWrenchItem.mode(stack)));
		}
	}

	/** Opens the wrench in a named mode, saving it on the stack and telling the server; used by the old ids and the ATR menu. */
	public static void openMode(Hand hand, WrenchMode mode) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player != null && client.player.getStackInHand(hand).getItem() instanceof StationWrenchItem) {
			final ItemStack stack = client.player.getStackInHand(hand);
			StationWrenchItem.setMode(stack, mode);
			if (ClientPlayNetworking.canSend(ModPackets.STATION_WRENCH)) {
				ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, StationWrenchItem.Action.MODE,
						StationWrenchItem.description(stack), StationWrenchItem.recipe(stack).encode(), mode));
			}
		}
		open(hand);
	}

	public static void openStation(Hand hand) {
		openMode(hand, WrenchMode.STATION);
	}

	public static void openAirport(Hand hand) {
		openMode(hand, WrenchMode.AIRPORT);
	}

	public static void openBridge(Hand hand) {
		openMode(hand, WrenchMode.BRIDGE);
	}

	public static void openEarthworks(Hand hand) {
		openMode(hand, WrenchMode.EARTHWORKS);
	}

	@Override
	protected void init() {
		fitColumn();
		switch (mode) {
			case AIRPORT -> initAirport();
			case BRIDGE -> initBridge();
			case EARTHWORKS -> initEarthworks();
			case STATION -> {
				// no title: the lit Station tab names the page, and the rows start right under it
				if (picker != Picker.NONE) {
					initPicker();
				} else if (advanced) {
					initAdvanced();
				} else {
					initSimple();
				}
			}
		}
		initModes();
		if (mode == WrenchMode.STATION && previewWidth() > 0) {
			preview.show(description());
		}
	}

	/** The four modes along the top, centred on the screen so they stay put between pages; the active one is lit. */
	private void initModes() {
		final int left = (width - WIDTH) / 2;
		final int button = (WIDTH - 9) / 4;
		final WrenchMode[] modes = WrenchMode.values();
		for (int i = 0; i < modes.length; i++) {
			final WrenchMode target = modes[i];
			final ButtonWidget widget = ButtonWidget.builder(target.label(), b -> switchMode(target))
					.dimensions(left + i * (button + 3), 8, button, 18).build();
			widget.active = target != mode;
			addDrawableChild(widget);
		}
	}

	private void switchMode(WrenchMode target) {
		if (target == mode) {
			return;
		}
		mode = target;
		advanced = false;
		more = false;
		picker = Picker.NONE;
		if (client != null && client.player != null) {
			final ItemStack stack = client.player.getStackInHand(hand);
			StationWrenchItem.setMode(stack, target);
			if (ClientPlayNetworking.canSend(ModPackets.STATION_WRENCH)) {
				ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, StationWrenchItem.Action.MODE,
						description(), recipe.encode(), target));
			}
		}
		clearAndInit();
	}

	/** Cycles the mode from the mouse wheel while sneaking, or the V key, and tells the server so its next click uses it. */
	private void cycleMode(int step) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		mode = mode.next(step);
		advanced = false;
		more = false;
		picker = Picker.NONE;
		StationWrenchItem.setMode(client.player.getStackInHand(hand), mode);
		if (ClientPlayNetworking.canSend(ModPackets.STATION_WRENCH)) {
			ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, StationWrenchItem.Action.MODE,
					description(), recipe.encode(), mode));
		}
		if (WrenchSettings.actionBar()) {
			client.player.sendMessage(mode.label(), true);
		}
		clearAndInit();
	}

	// ---- preview --------------------------------------------------------------------------------------------------

	private int previewWidth() {
		if (mode != WrenchMode.STATION) {
			return 0;
		}
		final int room = width - column - GAP - 2 * MARGIN;
		return room >= MIN_PREVIEW ? Math.min(room, MAX_PREVIEW) : 0;
	}

	/**
	 * Station mode on a screen too narrow for the full column and a preview (1920x1080 on automatic GUI scale is 480 wide):
	 * the column shrinks so the 3D preview still fits beside it. Below {@link #MIN_COLUMN} the labels would be cut, so the
	 * column stays full width and the preview is left out. The look and preset grids keep the full width: their names need it.
	 */
	private void fitColumn() {
		int fitted = WIDTH;
		if (mode == WrenchMode.STATION && picker == Picker.NONE && width - WIDTH - GAP - 2 * MARGIN < MIN_PREVIEW) {
			final int shrunk = Math.max(MIN_COLUMN, width - GAP - 2 * MARGIN - FIT_PREVIEW);
			if (width - shrunk - GAP - 2 * MARGIN >= MIN_PREVIEW) {
				fitted = shrunk;
			}
		}
		column = fitted;
		half = (fitted - 4) / 2;
		third = (fitted - 8) / 3;
	}

	/** The controls' left edge: centred alone, or centred together with the preview. */
	private int left() {
		final int preview = previewWidth();
		return preview == 0 ? (width - column) / 2 : (width - column - GAP - preview) / 2;
	}

	/** x0, y0, x1, y1 of the preview box, beside the controls and as tall as the page. */
	private int[] previewBox() {
		final int total = stationHeight();
		final int top = top(total);
		final int x0 = left() + column + GAP;
		return new int[]{x0, top, x0 + previewWidth(), top + total - 12};
	}

	private boolean inPreview(double x, double y) {
		if (previewWidth() == 0) {
			return false;
		}
		final int[] box = previewBox();
		return x >= box[0] && x < box[2] && y >= box[1] && y < box[3];
	}

	@Override
	public void tick() {
		super.tick();
		if (mode == WrenchMode.STATION && editedAt > 0 && System.currentTimeMillis() - editedAt >= PREVIEW_DELAY_MS && previewWidth() > 0) {
			editedAt = 0;
			preview.show(description());
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (inPreview(mouseX, mouseY)) {
			dragging = true;
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging) {
			preview.drag(button, deltaX, deltaY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (client != null && client.player != null && client.player.isSneaking()) {
			cycleMode(amount > 0 ? 1 : -1);
			return true;
		}
		if (inPreview(mouseX, mouseY)) {
			preview.zoom(amount);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		// R resets the preview's camera when no text field is being typed in
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R && !(getFocused() instanceof TextFieldWidget) && mode == WrenchMode.STATION
				&& previewWidth() > 0) {
			preview.reset();
			return true;
		}
		// Esc in a look or preset grid goes back to the page it came from instead of closing the wrench
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && picker != Picker.NONE) {
			picker = Picker.NONE;
			clearAndInit();
			return true;
		}
		// V cycles the mode, like the keybinding while no screen is open
		if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V && !(getFocused() instanceof TextFieldWidget)) {
			cycleMode(1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	// ---- simple page ----------------------------------------------------------------------------------------------

	/**
	 * The step line, five rows of two choices, the summary, then Place / Build it and Undo / Cancel / More. The city look is
	 * one button that opens its own grid, so the page fits a 427x240 GUI (an 854x480 window, or 1920x1080 on GUI scale 4).
	 */
	private static int simpleHeight() {
		return summaryOffset() + SUMMARY + ROW + 20;
	}

	/** From the page top to the summary line: the step line, then five rows of choices. */
	private static int summaryOffset() {
		return 12 + 5 * ROW;
	}

	private void initSimple() {
		final int left = left();
		int y = top(simpleHeight()) + 12;
		final Text lookName = Text.literal(recipe.look() == StationRecipe.Look.AUTO ? Text.translatable(KEY + "look.auto").getString() : recipe.look().label());
		// the look opens its own grid rather than cycling: twenty-odd cities are too many to step through
		addDrawableChild(opener(left, y, half, "look", lookName, Picker.LOOK, "look.pick.tip"));
		final ButtonWidget mix = cycle(left + half + 4, y, half, "mix",
				recipe.mix() == null ? Text.translatable(KEY + "none") : Text.literal(recipe.mix().label()), () -> recipe.withMix(recipe.nextMix()));
		mix.setTooltip(Tooltip.of(Text.translatable(KEY + "mix.tip")));
		addDrawableChild(mix);
		y += ROW;
		final ButtonWidget type = cycle(left, y, half, "type", Text.translatable(KEY + (recipe.hub() ? "hub" : recipe.terminus() ? "terminus" : "through")),
				this::nextType);
		type.setTooltip(Tooltip.of(Text.translatable(KEY + "type.tip")));
		addDrawableChild(type);
		final int trackStep = recipe.trackStep();
		final int tracksX = left + half + 4;
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> change(recipe.withTracks(recipe.tracks() - trackStep))).dimensions(tracksX, y, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "tracks", recipe.tracks()), b -> change(recipe.withTracks(
				recipe.tracks() % StationRecipe.MAX_TRACKS + trackStep))).dimensions(tracksX + 22, y, half - 44, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> change(recipe.withTracks(recipe.tracks() + trackStep)))
				.dimensions(tracksX + half - 20, y, 20, 20).build());
		y += ROW;
		addDrawableChild(cycle(left, y, half, "platforms", Text.literal(recipe.platforms().label()),
				() -> recipe.withPlatforms(StyleCycle.next(recipe.platforms()))));
		addDrawableChild(cycle(left + half + 4, y, half, "length", Text.literal(Integer.toString(recipe.length())),
				() -> recipe.withLength(nextLength(recipe.length()))));
		y += ROW;
		final ButtonWidget roof = cycle(left, y, half, "roof", Text.literal(recipe.roof().label()), () -> recipe.withRoof(StyleCycle.next(recipe.roof())));
		roof.setTooltip(Tooltip.of(Text.translatable(KEY + "roof.tip")));
		addDrawableChild(roof);
		final WidthSlider width = new WidthSlider(left + half + 4, y, half);
		width.setTooltip(Tooltip.of(Text.translatable(KEY + "width.tip")));
		addDrawableChild(width);
		y += ROW;
		final Text buildingLabel = !recipe.building() ? ScreenTexts.NO
				: recipe.size() == StationRecipe.Size.AUTO ? ScreenTexts.YES : Text.literal(recipe.size().label());
		final ButtonWidget building = cycle(left, y, half, "building", buildingLabel, this::nextBuilding);
		building.setTooltip(Tooltip.of(Text.translatable(KEY + "building.tip")));
		if (recipe.terminus()) {
			building.setTooltip(Tooltip.of(Text.translatable(KEY + "building.terminus")));
		}
		addDrawableChild(building);
		final TextFieldWidget name = new TextFieldWidget(textRenderer, left + half + 5, y + 1, half - 2, 18, Text.translatable(KEY + "name"));
		name.setMaxLength(32);
		name.setText(recipe.name());
		name.setPlaceholder(Text.translatable(KEY + "name").formatted(Formatting.DARK_GRAY));
		name.setChangedListener(text -> {
			recipe = recipe.withName(text);
			editedAt = System.currentTimeMillis();
		});
		addDrawableChild(name);
		y += ROW + SUMMARY;
		final ButtonWidget place = action(left, y, half, "place", StationWrenchItem.Action.PLAN);
		place.setTooltip(Tooltip.of(Text.translatable(KEY + "place.tip")));
		addDrawableChild(place);
		final ButtonWidget build = action(left + half + 4, y, half, "build_it", StationWrenchItem.Action.CONFIRM);
		build.setTooltip(Tooltip.of(Text.translatable(KEY + "build_it.tip")));
		addDrawableChild(build);
		y += ROW;
		addDrawableChild(action(left, y, third, "undo", StationWrenchItem.Action.UNDO));
		addDrawableChild(action(left + third + 4, y, third, "cancel_plan", StationWrenchItem.Action.CANCEL));
		final ButtonWidget more = ButtonWidget.builder(Text.translatable(KEY + "advanced"), b -> {
			advanced = true;
			if (!custom) {
				typed = recipe.description();
			}
			clearAndInit();
		}).dimensions(left + 2 * (third + 4), y, third, 20).build();
		more.setTooltip(Tooltip.of(Text.translatable(KEY + "advanced.tip")));
		addDrawableChild(more);
	}

	// ---- look and preset pickers -------------------------------------------------------------------------------------

	/** The step line, the grid, then Back; the grid takes as few columns as the window's height allows. */
	private int pickerHeight() {
		return 12 + pickerRows() * GRID_ROW + 6 + 20;
	}

	private int pickerCount() {
		return picker == Picker.LOOK ? StationRecipe.Look.values().length : StationPresets.ALL.size();
	}

	/** Three columns when they fit under the mode row, else four, five or six. */
	private int pickerColumns() {
		final int room = height - MODE_ROW - 26;
		for (int columns = 3; columns < 6; columns++) {
			if (12 + (pickerCount() + columns - 1) / columns * GRID_ROW + 26 <= room) {
				return columns;
			}
		}
		return 6;
	}

	private int pickerRows() {
		final int columns = pickerColumns();
		return (pickerCount() + columns - 1) / columns;
	}

	private void initPicker() {
		final int left = left();
		int y = top(pickerHeight()) + 12;
		final int columns = pickerColumns();
		final int buttonWidth = (column - (columns - 1) * 4) / columns;
		if (picker == Picker.LOOK) {
			final StationRecipe.Look[] looks = StationRecipe.Look.values();
			for (int i = 0; i < looks.length; i++) {
				final StationRecipe.Look look = looks[i];
				final String name = look == StationRecipe.Look.AUTO ? Text.translatable(KEY + "look.auto").getString() : look.label();
				addDrawableChild(ButtonWidget.builder(lookLabel(name, recipe.look() == look, buttonWidth - 8), b -> {
							recipe = recipe.withLook(look);
							picker = Picker.NONE;
							clearAndInit();
						}).dimensions(left + i % columns * (buttonWidth + 4), y + i / columns * GRID_ROW, buttonWidth, GRID_BUTTON)
						.tooltip(Tooltip.of(Text.literal(name).append("\n").append(Text.translatable(KEY + (look == StationRecipe.Look.AUTO ? "look.auto.tip" : "look.tip"))))).build());
			}
		} else {
			for (int i = 0; i < StationPresets.ALL.size(); i++) {
				final StationPresets.Preset preset = StationPresets.ALL.get(i);
				final String name = Text.translatable(KEY + "preset." + preset.key()).getString();
				addDrawableChild(ButtonWidget.builder(lookLabel(name, false, buttonWidth - 8), b -> {
							typed = preset.description();
							custom = true;
							editedAt = System.currentTimeMillis();
							picker = Picker.NONE;
							clearAndInit();
						}).dimensions(left + i % columns * (buttonWidth + 4), y + i / columns * GRID_ROW, buttonWidth, GRID_BUTTON)
						.tooltip(Tooltip.of(Text.literal(name).append("\n").append(Text.literal(preset.description()).formatted(Formatting.GRAY)))).build());
			}
		}
		y += pickerRows() * GRID_ROW + 6;
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "back"), b -> {
			picker = Picker.NONE;
			clearAndInit();
		}).dimensions(left, y, column, 20).build());
	}

	// ---- airport page ----------------------------------------------------------------------------------------------

	private int airportHeight() {
		return 12 + (more ? 46 : 0) + 30 + ROW + ROW + 26;
	}

	private void initAirport() {
		final int left = (width - column) / 2;
		int y = top(airportHeight()) + 12;
		if (more) {
			final int presetWidth = (column - (AIRPORT_COLUMNS - 1) * 4) / AIRPORT_COLUMNS;
			for (int i = 0; i < AirportPresets.ALL.size(); i++) {
				final AirportPresets.Preset preset = AirportPresets.ALL.get(i);
				addDrawableChild(ButtonWidget.builder(Text.translatable("screen.aurelia_transit_architecture.airport_wrench.preset." + preset.key()),
								b -> airportText(preset.description()))
						.dimensions(left + i % AIRPORT_COLUMNS * (presetWidth + 4), y + i / AIRPORT_COLUMNS * AIRPORT_GRID_ROW, presetWidth, 18)
						.tooltip(Tooltip.of(Text.literal(preset.description()))).build());
			}
			y += ((AirportPresets.ALL.size() + AIRPORT_COLUMNS - 1) / AIRPORT_COLUMNS) * AIRPORT_GRID_ROW + 6;
		}
		final TextFieldWidget field = new TextFieldWidget(textRenderer, left, y, column, 18,
				Text.translatable("screen.aurelia_transit_architecture.airport_wrench.description"));
		field.setMaxLength(StationDescriptionParser.MAX_INPUT);
		field.setText(typed);
		field.setPlaceholder(Text.translatable("screen.aurelia_transit_architecture.airport_wrench.example").formatted(Formatting.DARK_GRAY));
		field.setChangedListener(text -> {
			typed = text;
			editedAt = System.currentTimeMillis();
		});
		addDrawableChild(field);
		y += 30;
		addDrawableChild(airportAction(left, y, column, "plan", StationWrenchItem.Action.PLAN));
		y += ROW;
		addDrawableChild(airportAction(left, y, third, "build", StationWrenchItem.Action.CONFIRM));
		addDrawableChild(airportAction(left + third + 4, y, third, "undo", StationWrenchItem.Action.UNDO));
		addDrawableChild(airportAction(left + 2 * (third + 4), y, third, "cancel", StationWrenchItem.Action.CANCEL));
		y += ROW + 4;
		addDrawableChild(ButtonWidget.builder(moreLabel(), b -> {
			more = !more;
			clearAndInit();
		}).dimensions(left, y, column, 20).build());
	}

	private void airportText(String text) {
		typed = text;
		more = false;
		clearAndInit();
	}

	// ---- bridge page -----------------------------------------------------------------------------------------------

	/** Three steps in plain words (save one, lay it along a rail, undo); the rest under More. */
	private int bridgeHeight() {
		return 12 + 3 * ROW + (more ? 4 * ROW + 4 : 0) + 24;
	}

	private void initBridge() {
		final int left = (width - column) / 2;
		int y = top(bridgeHeight()) + 12;
		commandButton(left, y, column, "bridge.capture", "atr_bridge capture ");
		y += ROW;
		commandButton(left, y, column, "bridge.build", "atr_bridge build ");
		y += ROW;
		runButton(left, y, column, "bridge.undo", "atr_bridge undo");
		if (more) {
			y += ROW + 4;
			commandButton(left, y, half, "bridge.preview", "atr_bridge preview ");
			runButton(left + half + 4, y, half, "bridge.list", "atr_bridge list");
			y += ROW;
			commandButton(left, y, half, "bridge.delete", "atr_bridge delete ");
			runButton(left + half + 4, y, half, "bridge.help", "atr_bridge help");
		}
		y = top(bridgeHeight()) + bridgeHeight() - 20;
		addDrawableChild(ButtonWidget.builder(moreLabel(), b -> {
			more = !more;
			clearAndInit();
		}).dimensions(left, y, column, 20).build());
	}

	/** Puts a command in the chat box so the player can fill in its arguments. */
	private void commandButton(int x, int y, int w, String key, String command) {
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + key), b -> {
			sent = true;
			close();
			client.setScreen(new ChatScreen("/" + command));
		}).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable(KEY + key + ".tip"))).build());
	}

	/** Runs a command with no arguments. */
	private void runButton(int x, int y, int w, String key, String command) {
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + key), b -> {
			sent = true;
			close();
			if (client != null && client.player != null) {
				client.player.networkHandler.sendChatCommand(command);
			}
		}).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable(KEY + key + ".tip"))).build());
	}

	// ---- earthworks page -------------------------------------------------------------------------------------------

	private static int earthworksHeight() {
		return 12 + 24 + ROW + 16;
	}

	private void initEarthworks() {
		final int left = (width - column) / 2;
		final int top = top(earthworksHeight());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "earthworks.guide"), b -> {
			close();
			CodexScreen.openChapter("Getting started");
		}).dimensions(left, top + 12 + 24, column, 20).build());
	}

	private Text moreLabel() {
		return Text.translatable(KEY + (more ? "less" : "more"));
	}

	/** Forgets everything the wrench remembers (choices, typed description, Studio design) and starts again from the defaults. */
	private ButtonWidget clearButton(int x, int y, int w) {
		return ButtonWidget.builder(Text.translatable(KEY + "clear"), b -> {
			recipe = StationRecipe.defaults();
			typed = "";
			custom = false;
			advanced = false;
			editedAt = System.currentTimeMillis();
			clearAndInit();
		}).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable(KEY + "clear.tip"))).build();
	}

	/**
	 * A grid button's text within {@code room} pixels: "> name <" when chosen, without the arrows when that is too wide, and
	 * cut with an ellipsis when even the name is too wide (the tooltip has it in full).
	 */
	private Text lookLabel(String name, boolean chosen, int room) {
		final String marked = "> " + name + " <";
		final String fitted = textRenderer.getWidth(name) <= room ? name : textRenderer.trimToWidth(name, room - textRenderer.getWidth("…")) + "…";
		final Text text = Text.literal(chosen && textRenderer.getWidth(marked) <= room ? marked : fitted);
		return chosen ? text.copy().formatted(Formatting.YELLOW) : text;
	}

	/** A "Label: value" button that opens a picker page. */
	private ButtonWidget opener(int x, int y, int w, String key, Text value, Picker target, String tip) {
		final Text label = Text.translatable(KEY + key).append(": ").append(value.copy().formatted(Formatting.AQUA));
		return ButtonWidget.builder(label, b -> {
			picker = target;
			clearAndInit();
		}).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable(KEY + tip))).build();
	}

	/** A "Label: value" button that replaces the choices with {@code next} when pressed. */
	private ButtonWidget cycle(int x, int y, int w, String key, Text value, Supplier<StationRecipe> next) {
		final Text label = Text.translatable(KEY + key).append(": ").append(value.copy().formatted(Formatting.AQUA));
		return ButtonWidget.builder(label, b -> change(next.get())).dimensions(x, y, w, 20).build();
	}

	private void change(StationRecipe value) {
		recipe = value;
		clearAndInit();
	}

	/**
	 * Platform width: Auto (the parser's default for the platform kind), then every width from {@link StationRecipe#MIN_WIDTH} to
	 * {@link StationRecipe#MAX_WIDTH}. Dragging changes the choice without rebuilding the page, so the drag carries on; the
	 * preview replans once the slider rests.
	 */
	private final class WidthSlider extends SliderWidget {

		private static final int STEPS = StationRecipe.MAX_WIDTH - StationRecipe.MIN_WIDTH + 1;

		WidthSlider(int x, int y, int w) {
			super(x, y, w, 20, Text.empty(), position(recipe.width()));
			updateMessage();
		}

		private static double position(int width) {
			return width <= 0 ? 0 : (width - StationRecipe.MIN_WIDTH + 1) / (double) STEPS;
		}

		private int width() {
			final int step = (int) Math.round(value * STEPS);
			return step == 0 ? 0 : StationRecipe.MIN_WIDTH + step - 1;
		}

		@Override
		protected void updateMessage() {
			final int width = width();
			setMessage(Text.translatable(KEY + "width").append(": ").append((width == 0 ? Text.translatable(KEY + "width.auto")
					: Text.translatable(KEY + "width.blocks", width)).copy().formatted(Formatting.AQUA)));
		}

		@Override
		protected void applyValue() {
			if (width() != recipe.width()) {
				recipe = recipe.withWidth(width());
				editedAt = System.currentTimeMillis();
			}
		}
	}

	/** Platform lengths the length button steps through, then back to the first. */
	private static final int[] LENGTHS = {32, 48, 64, 96, 128, 160, 200, 250, 300};
	private static int nextLength(int length) {
		for (final int option : LENGTHS) {
			if (option > length) {
				return option;
			}
		}
		return LENGTHS[0];
	}

	/**
	 * Through station, then terminus, then hub, then through again. A terminus (or hub) is a city's main station, so switching a
	 * station still at the default size makes it {@link #TERMINUS_TRACKS} tracks and {@link #TERMINUS_LENGTH} long; any size the
	 * player already chose is kept.
	 */
	private StationRecipe nextType() {
		final StationRecipe value = recipe.hub() ? recipe.withTerminus(false) : recipe.terminus() ? recipe.withHub(true) : recipe.withTerminus(true);
		final StationRecipe fresh = StationRecipe.defaults();
		if (value.terminus() && recipe.tracks() == fresh.tracks() && recipe.length() == fresh.length()) {
			return value.withTracks(TERMINUS_TRACKS).withLength(TERMINUS_LENGTH);
		}
		return value;
	}

	/** No building, then a building sized by the station, then compact, city and grand, then no building again. */
	private StationRecipe nextBuilding() {
		if (recipe.terminus()) {
			// a terminus always has its head building: only its size changes
			return recipe.withSize(StyleCycle.next(recipe.size()));
		}
		if (!recipe.building()) {
			return recipe.withBuilding(true).withSize(StationRecipe.Size.AUTO);
		}
		if (recipe.size() == StationRecipe.Size.GRAND) {
			return recipe.withBuilding(false).withSize(StationRecipe.Size.AUTO);
		}
		return recipe.withSize(StyleCycle.next(recipe.size()));
	}

	// ---- advanced page --------------------------------------------------------------------------------------------

	private static int advancedHeight() {
		return 46 + ROW + 24 + 24 + 28 + 20;
	}

	private void initAdvanced() {
		final int left = left();
		int y = top(advancedHeight());
		final TextFieldWidget field = new TextFieldWidget(textRenderer, left, y, column, 18, Text.translatable(KEY + "description"));
		field.setMaxLength(StationDescriptionParser.MAX_INPUT);
		field.setText(typed);
		field.setPlaceholder(Text.translatable(KEY + "description").formatted(Formatting.DARK_GRAY));
		field.setChangedListener(text -> {
			typed = text;
			custom = true;
			editedAt = System.currentTimeMillis();
		});
		addDrawableChild(field);
		y += 46;
		addDrawableChild(opener(left, y, column, "presets", Text.literal(Integer.toString(StationPresets.ALL.size())), Picker.PRESET, "presets.tip"));
		y += ROW;
		addDrawableChild(action(left, y, half, "plan", StationWrenchItem.Action.PLAN));
		addDrawableChild(action(left + half + 4, y, half, "preview", StationWrenchItem.Action.PREVIEW));
		y += 24;
		addDrawableChild(action(left, y, column, "build", StationWrenchItem.Action.CONFIRM));
		y += 24;
		addDrawableChild(action(left, y, half, "cancel", StationWrenchItem.Action.CANCEL));
		addDrawableChild(action(left + half + 4, y, half, "undo", StationWrenchItem.Action.UNDO));
		y += 28;
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "simple"), b -> {
			advanced = false;
			clearAndInit();
		}).dimensions(left, y, third, 20).tooltip(Tooltip.of(Text.translatable(KEY + "simple.tip"))).build());
		addDrawableChild(clearButton(left + third + 4, y, third));
		addDrawableChild(studioButton(left + 2 * (third + 4), y, third));
		setInitialFocus(field);
	}

	/**
	 * Opens the station editor on this station ({@link StationEditorScreen}: plan from above, 3D view, station files shared with
	 * Station Studio, which it opens in the browser).
	 */
	private ButtonWidget studioButton(int x, int y, int w) {
		return ButtonWidget.builder(Text.translatable(KEY + "editor"), b -> {
			if (client != null) {
				client.setScreen(new StationEditorScreen(hand, description()));
			}
		}).dimensions(x, y, w, 20).tooltip(Tooltip.of(Text.translatable(KEY + "editor.tip"))).build();
	}

	// ---- shared ---------------------------------------------------------------------------------------------------

	/** What the server plans: the typed text on the advanced and airport pages, the choices' description on the simple one. */
	private String description() {
		if (mode == WrenchMode.AIRPORT) {
			return typed.strip();
		}
		return mode == WrenchMode.STATION && advanced ? typed.strip() : recipe.description();
	}

	private ButtonWidget action(int x, int y, int buttonWidth, String key, StationWrenchItem.Action action) {
		return ButtonWidget.builder(Text.translatable(KEY + key), button -> send(action)).dimensions(x, y, buttonWidth, 20).build();
	}

	private ButtonWidget airportAction(int x, int y, int buttonWidth, String key, StationWrenchItem.Action action) {
		return ButtonWidget.builder(Text.translatable("screen.aurelia_transit_architecture.airport_wrench." + key), button -> send(action))
				.dimensions(x, y, buttonWidth, 20).build();
	}

	private void send(StationWrenchItem.Action action) {
		sent = true;
		ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, action, description(), recipe.encode(), mode));
		close();
	}

	@Override
	public Text getTitle() {
		return mode.label();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		if (mode == WrenchMode.STATION && previewWidth() > 0) {
			final int[] box = previewBox();
			preview.render(context, box[0], box[1], box[2], box[3]);
		}
		super.render(context, mouseX, mouseY, delta);
		final int left = left();
		switch (mode) {
			case AIRPORT -> {
				final int top = top(airportHeight());
				context.drawCenteredTextWithShadow(textRenderer, getTitle(), left + column / 2, top - 16, 0xFFFFFF);
				context.drawTextWithShadow(textRenderer, Text.translatable("screen.aurelia_transit_architecture.airport_wrench.hint"), left, top, 0x9AA0A6);
			}
			case BRIDGE -> {
				final int top = top(bridgeHeight());
				context.drawCenteredTextWithShadow(textRenderer, getTitle(), left + column / 2, top - 16, 0xFFFFFF);
				context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "bridge.hint"), left, top, 0x9AA0A6);
			}
			case EARTHWORKS -> {
				final int top = top(earthworksHeight());
				context.drawCenteredTextWithShadow(textRenderer, getTitle(), left + column / 2, top - 16, 0xFFFFFF);
				context.drawCenteredTextWithShadow(textRenderer, Text.translatable(KEY + "earthworks.soon"), left + column / 2, top + 12, 0xE0C060);
				context.drawCenteredTextWithShadow(textRenderer, Text.translatable(KEY + "earthworks.body"), left + column / 2, top + 12 + 12, 0x9AA0A6);
			}
			case STATION -> {
				if (picker != Picker.NONE) {
					final int top = top(pickerHeight());
					context.drawTextWithShadow(textRenderer, Text.translatable(KEY + (picker == Picker.LOOK ? "look.pick" : "presets.pick")), left, top, 0xE0C060);
				} else if (advanced) {
					final int top = top(advancedHeight());
					context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "example"), left, top + 22, 0x9AA0A6);
					context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "hint"), left, top + 32, 0x9AA0A6);
				} else {
					final int top = top(simpleHeight());
					context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "step_look"), left, top, 0xE0C060);
					final int summary = top + summaryOffset() + 1;
					context.drawCenteredTextWithShadow(textRenderer, Text.literal(recipe.title()), left + column / 2, summary, 0xFFFFFF);
					context.drawCenteredTextWithShadow(textRenderer, Text.translatable(KEY + "step_place"), left + column / 2, summary + 11, 0x9AA0A6);
				}
			}
		}
	}

	private int stationHeight() {
		return picker != Picker.NONE ? pickerHeight() : advanced ? advancedHeight() : simpleHeight();
	}

	/** The first row's y; the whole page is centred vertically under the mode row and its title. */
	private int top(int total) {
		return Math.max(MODE_ROW + 22, (height - total) / 2);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		preview.close();
		if (mode != WrenchMode.STATION && mode != WrenchMode.AIRPORT) {
			return;
		}
		if (!sent && (!description().equals(initial) || !recipe.equals(initialRecipe))) {
			sent = true;
			ClientPlayNetworking.send(ModPackets.STATION_WRENCH,
					ModPackets.writeStationWrench(hand, StationWrenchItem.Action.SAVE, description(), recipe.encode(), mode));
		}
	}
}
