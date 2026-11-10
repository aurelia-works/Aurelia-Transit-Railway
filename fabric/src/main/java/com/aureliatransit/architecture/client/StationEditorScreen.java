package com.aureliatransit.architecture.client;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.PlanTopView;
import com.aureliatransit.architecture.station.StationRecipe;
import com.aureliatransit.architecture.station.StationSpec;
import com.aureliatransit.architecture.station.studio.StationFiles;
import com.aureliatransit.architecture.station.studio.StudioApi;
import com.aureliatransit.architecture.station.world.StationWrenchItem;
import com.aureliatransit.architecture.station.world.WrenchMode;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The station editor, opened from the station wrench: the station's settings on the left, its plan from above (one pixel per
 * block) and the 3D preview on the right, and the station files it shares with Station Studio in the browser
 * ({@link StationFiles}, {@code config/aurelia/stations}). It edits the same form the Studio page sends, so a station saved
 * here opens there unchanged and back: fields this screen has no control for are carried along as they are.
 */
public final class StationEditorScreen extends Screen {

	private static final String KEY = "screen.aurelia_transit_architecture.station_editor.";
	private static final Identifier PLAN_TEXTURE = new Identifier(AureliaTransitArchitecture.MOD_ID, "dynamic/station_editor_plan");
	private static final long PREVIEW_DELAY_MS = 350;
	private static final int PANEL = 160;
	private static final int GAP = 8;
	private static final int LENGTH_STEP = 8;

	private final Hand hand;
	/** The Studio form being edited (lower-case field names, as the Studio server reads them). */
	private final Map<String, String> form;
	private final StationPreview preview = new StationPreview();
	private final StationFiles files = StationStudioClient.files();
	private List<StationFiles.Entry> entries = List.of();
	/** The file shown in the file row; -1 for none. */
	private int fileIndex = -1;
	/** The file the form came from or was last saved to; null for a new station. */
	private String file;
	private String message = "";
	private boolean messageBad;
	private long editedAt;
	private boolean dragging;
	private PlanTopView uploaded;
	private boolean textureLive;
	/** Whether the file list was read; it is read once when the screen opens and again after a save, not on every relayout. */
	private boolean listed;

	/** Opens the editor on {@code description}, the station the wrench holds now (as typed words). */
	public StationEditorScreen(Hand hand, String description) {
		super(Text.translatable(KEY + "title"));
		this.hand = hand;
		this.form = new TreeMap<>();
		if (description != null && !description.isBlank()) {
			form.put("text", description.strip());
		}
	}

	@Override
	protected void init() {
		if (!listed) {
			refreshFiles();
			listed = true;
		}
		final int x = GAP;
		// fits a 427 x 240 GUI (854 x 480 at scale 2): the last row ends at 206, the message line sits under it
		int y = 22;
		final TextFieldWidget name = new TextFieldWidget(textRenderer, x, y, PANEL, 18, Text.translatable(KEY + "name"));
		name.setMaxLength(StationSpec.MAX_NAME);
		name.setText(form.getOrDefault("name", ""));
		name.setPlaceholder(Text.translatable(KEY + "name").formatted(Formatting.DARK_GRAY));
		name.setChangedListener(text -> set("name", text.strip(), false));
		addDrawableChild(name);
		y += 22;
		final TextFieldWidget words = new TextFieldWidget(textRenderer, x, y, PANEL, 18, Text.translatable(KEY + "words"));
		words.setMaxLength(StudioApi.MAX_TYPED);
		words.setText(form.getOrDefault("text", ""));
		words.setPlaceholder(Text.translatable(KEY + "words").formatted(Formatting.DARK_GRAY));
		words.setChangedListener(text -> set("text", text, false));
		addDrawableChild(words);
		y += 24;
		final StationRecipe recipe = StudioApi.recipe(form);
		final int third = (PANEL - 8) / 3;
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> tracks(-1)).dimensions(x, y, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "tracks", recipe.tracks()), b -> tracks(0))
				.dimensions(x + 22, y, PANEL - 44, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> tracks(1)).dimensions(x + PANEL - 20, y, 20, 20).build());
		y += 22;
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> length(-LENGTH_STEP)).dimensions(x, y, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "length", recipe.length()), b -> length(0))
				.dimensions(x + 22, y, PANEL - 44, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> length(LENGTH_STEP)).dimensions(x + PANEL - 20, y, 20, 20).build());
		y += 22;
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "look", recipe.look() == StationRecipe.Look.AUTO
				? Text.translatable(KEY + "look.custom") : Text.literal(recipe.look().label())), b -> nextLook())
				.dimensions(x, y, PANEL, 20).tooltip(Tooltip.of(Text.translatable(KEY + "look.tip"))).build());
		y += 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> pickFile(-1)).dimensions(x, y, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(fileLabel(), b -> openFile()).dimensions(x + 22, y, PANEL - 44, 20)
				.tooltip(Tooltip.of(Text.translatable(KEY + "open.tip", files.dir().toString()))).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> pickFile(1)).dimensions(x + PANEL - 20, y, 20, 20).build());
		y += 22;
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "save"), b -> save()).dimensions(x, y, third, 20)
				.tooltip(Tooltip.of(Text.translatable(KEY + "save.tip"))).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "new"), b -> {
			form.clear();
			file = null;
			fileIndex = -1;
			edited(true);
		}).dimensions(x + third + 4, y, third, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "studio"), b -> openStudio()).dimensions(x + 2 * (third + 4), y, third, 20)
				.tooltip(Tooltip.of(Text.translatable(KEY + "studio.tip"))).build());
		y += 26;
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "plan"), b -> send(StationWrenchItem.Action.PLAN)).dimensions(x, y, third, 20)
				.tooltip(Tooltip.of(Text.translatable(KEY + "plan.tip"))).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "build"), b -> send(StationWrenchItem.Action.CONFIRM))
				.dimensions(x + third + 4, y, third, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable(KEY + "wrench"), b -> send(StationWrenchItem.Action.SAVE))
				.dimensions(x + 2 * (third + 4), y, third, 20).tooltip(Tooltip.of(Text.translatable(KEY + "wrench.tip"))).build());
		final int[] planBox = boxes()[0];
		final Text roofLabel = Text.translatable(KEY + "roof", net.minecraft.screen.ScreenTexts.onOrOff(WrenchSettings.planRoof()));
		final int roofWidth = textRenderer.getWidth(roofLabel) + 12;
		addDrawableChild(ButtonWidget.builder(roofLabel, b -> {
			WrenchSettings.togglePlanRoof();
			uploaded = null;
			clearAndInit();
		}).dimensions(planBox[2] - roofWidth, 4, roofWidth, 16).tooltip(Tooltip.of(Text.translatable(KEY + "roof.tip"))).build());
		setInitialFocus(name);
		preview.show(description());
	}

	// ---- editing ----------------------------------------------------------------------------------------------------

	/** What the planner reads: the form's description, as the Studio page plans it. */
	private String description() {
		return StudioApi.description(form);
	}

	/** Sets a field; a control (not the words or the name) is pinned, so typed words no longer override it. */
	private void set(String key, String value, boolean pin) {
		if (value.isEmpty()) {
			form.remove(key);
		} else {
			form.put(key, value);
		}
		if (pin) {
			final Set<String> pinned = new LinkedHashSet<>();
			for (final String part : form.getOrDefault("pinned", "").split(",")) {
				if (!part.isBlank()) {
					pinned.add(part.strip());
				}
			}
			pinned.add(key);
			form.put("pinned", String.join(",", pinned));
		}
		edited(false);
	}

	private void edited(boolean relayout) {
		editedAt = System.currentTimeMillis();
		if (relayout) {
			clearAndInit();
		}
	}

	private void tracks(int step) {
		final int tracks = Math.max(StationRecipe.MIN_TRACKS, Math.min(StationRecipe.MAX_TRACKS, StudioApi.recipe(form).tracks() + step));
		set("tracks", Integer.toString(tracks), true);
		clearAndInit();
	}

	private void length(int step) {
		final int length = Math.max(StationRecipe.MIN_LENGTH, Math.min(StationRecipe.MAX_LENGTH, StudioApi.recipe(form).length() + step));
		set("length", Integer.toString(length), true);
		clearAndInit();
	}

	private void nextLook() {
		final StationRecipe.Look[] looks = StationRecipe.Look.values();
		final StationRecipe.Look next = looks[(StudioApi.recipe(form).look().ordinal() + 1) % looks.length];
		set("look", next.name(), true);
		// a second look only goes with a city look
		if (next == StationRecipe.Look.AUTO) {
			form.remove("mix");
		}
		clearAndInit();
	}

	// ---- files ------------------------------------------------------------------------------------------------------

	private void refreshFiles() {
		try {
			entries = files.list();
		} catch (IOException e) {
			entries = List.of();
			say(Text.translatable(KEY + "list_failed").getString(), true);
		}
		fileIndex = -1;
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).file().equals(file)) {
				fileIndex = i;
			}
		}
		if (fileIndex < 0 && !entries.isEmpty() && file == null) {
			fileIndex = 0;
		}
	}

	private Text fileLabel() {
		if (fileIndex < 0 || fileIndex >= entries.size()) {
			return Text.translatable(KEY + (entries.isEmpty() ? "no_files" : "pick_file"));
		}
		final StationFiles.Entry entry = entries.get(fileIndex);
		return Text.translatable(KEY + "open", entry.name().isBlank() ? entry.file() : entry.name());
	}

	private void pickFile(int step) {
		if (entries.isEmpty()) {
			return;
		}
		fileIndex = Math.floorMod((fileIndex < 0 ? 0 : fileIndex + step), entries.size());
		clearAndInit();
	}

	private void openFile() {
		if (fileIndex < 0 || fileIndex >= entries.size()) {
			return;
		}
		final String id = entries.get(fileIndex).file();
		try {
			final Map<String, String> read = files.read(id);
			form.clear();
			form.putAll(read);
			file = id;
			say(Text.translatable(KEY + "opened", id + ".json").getString(), false);
		} catch (IOException | IllegalArgumentException e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not open station file {}", id, e);
			say(Text.translatable(KEY + "open_failed", id).getString(), true);
		}
		edited(true);
	}

	private void save() {
		if (form.getOrDefault("name", "").isBlank()) {
			say(Text.translatable(KEY + "save_name").getString(), true);
			return;
		}
		try {
			file = files.write(form);
			say(Text.translatable(KEY + "saved", file + ".json").getString(), false);
		} catch (IOException e) {
			AureliaTransitArchitecture.LOGGER.warn("Could not save station file", e);
			say(Text.translatable(KEY + "save_failed").getString(), true);
		}
		refreshFiles();
		clearAndInit();
	}

	/** Saves (when the station has a name) and opens the same file in Station Studio in the browser. */
	private void openStudio() {
		if (!form.getOrDefault("name", "").isBlank()) {
			try {
				file = files.write(form);
			} catch (IOException e) {
				AureliaTransitArchitecture.LOGGER.warn("Could not save station file", e);
			}
		}
		StationStudioClient.open(file);
	}

	private void say(String text, boolean bad) {
		message = text;
		messageBad = bad;
	}

	/** Plans, builds or keeps the station on the wrench, as the wrench's own buttons do, then closes. */
	private void send(StationWrenchItem.Action action) {
		if (!ClientPlayNetworking.canSend(ModPackets.STATION_WRENCH)) {
			say(Text.translatable(KEY + "no_server").getString(), true);
			return;
		}
		ClientPlayNetworking.send(ModPackets.STATION_WRENCH, ModPackets.writeStationWrench(hand, action, description(),
				StudioApi.recipe(form).encode(), WrenchMode.STATION));
		close();
	}

	// ---- drawing ----------------------------------------------------------------------------------------------------

	/** The 2D plan's box and the 3D preview's box, right of the controls: {x0, y0, x1, y1} each. */
	private int[][] boxes() {
		final int x0 = GAP + PANEL + GAP;
		final int x1 = width - GAP;
		final int y0 = 22;
		final int y1 = height - GAP - 12;
		final PlanTopView top = preview.topView();
		final double aspect = top == null ? 4 : Math.max(1, (double) top.width() / top.height());
		final int planHeight = (int) Math.max(40, Math.min((y1 - y0) * 0.4, (x1 - x0) / aspect + 4));
		return new int[][]{{x0, y0, x1, y0 + planHeight}, {x0, y0 + planHeight + GAP, x1, y1}};
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		final int[][] boxes = boxes();
		drawPlan(context, boxes[0]);
		preview.render(context, boxes[1][0], boxes[1][1], boxes[1][2], boxes[1][3]);
		super.render(context, mouseX, mouseY, delta);
		context.drawTextWithShadow(textRenderer, getTitle(), GAP, 8, 0xFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable(KEY + "plan_view"), boxes[0][0], 8, 0xE0C060);
		if (!message.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.literal(message), GAP, height - GAP - 9, messageBad ? 0xFF7070 : 0x9AE6A0);
		}
	}

	private void drawPlan(DrawContext context, int[] box) {
		context.fill(box[0], box[1], box[2], box[3], PlanTopView.BACKGROUND);
		context.drawBorder(box[0], box[1], box[2] - box[0], box[3] - box[1], 0xFF2A2F36);
		final PlanTopView top = preview.topView();
		if (top == null || client == null) {
			return;
		}
		if (top != uploaded) {
			upload(top);
		}
		final int innerW = box[2] - box[0] - 4;
		final int innerH = box[3] - box[1] - 4;
		final double scale = Math.min((double) innerW / top.width(), (double) innerH / top.height());
		final int w = Math.max(1, (int) (top.width() * scale));
		final int h = Math.max(1, (int) (top.height() * scale));
		final int x = box[0] + 2 + (innerW - w) / 2;
		final int y = box[1] + 2 + (innerH - h) / 2;
		context.drawTexture(PLAN_TEXTURE, x, y, w, h, 0, 0, top.width(), top.height(), top.width(), top.height());
		// track numbers at the near end, where they fit
		if (scale >= 2) {
			for (final PlanTopView.Label label : top.labels()) {
				final int lx = x + (int) (label.x() * scale) + 1;
				final int ly = y + (int) (label.y() * scale + scale / 2) - 4;
				context.drawTextWithShadow(textRenderer, Text.literal(label.text()), lx, ly, 0xFFFFFF);
			}
		}
	}

	private void upload(PlanTopView top) {
		final NativeImage image = new NativeImage(top.width(), top.height(), false);
		for (int y = 0; y < top.height(); y++) {
			for (int x = 0; x < top.width(); x++) {
				final int argb = top.at(x, y, WrenchSettings.planRoof());
				// NativeImage stores ABGR
				image.setColor(x, y, (argb & 0xFF00FF00) | (argb & 0xFF) << 16 | (argb >> 16) & 0xFF);
			}
		}
		// registering under the same id closes the texture it replaces
		client.getTextureManager().registerTexture(PLAN_TEXTURE, new NativeImageBackedTexture(image));
		textureLive = true;
		uploaded = top;
	}

	@Override
	public void tick() {
		super.tick();
		if (editedAt > 0 && System.currentTimeMillis() - editedAt >= PREVIEW_DELAY_MS) {
			editedAt = 0;
			preview.show(description());
		}
	}

	private boolean inPreview(double x, double y) {
		final int[] box = boxes()[1];
		return x >= box[0] && x < box[2] && y >= box[1] && y < box[3];
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
		if (inPreview(mouseX, mouseY)) {
			preview.zoom(amount);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		preview.close();
		if (textureLive && client != null) {
			client.getTextureManager().destroyTexture(PLAN_TEXTURE);
			textureLive = false;
		}
	}
}
