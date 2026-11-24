package org.mtr.aurelia.client.map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.map.MapCamera;

/**
 * The full-screen map: drag to move, scroll to zoom about the cursor, C to come back to the player, right-click to set
 * home, add a landmark or remove a pin. The controls sit in the corners, Xaero's World Map style: the rail, station,
 * train and busy switches (the same as the panel's Map tab) top left, zoom and centre bottom right, the cursor's
 * coordinates bottom left, and a station list down the right side that a click on a name centres the map on, filtered by
 * the field above it. The world keeps running behind it.
 */
public final class WorldMapScreen extends Screen {

	private static final int MARGIN = 4;
	private static final int BUTTON = 18;
	private static final Text HINT = Text.translatable("gui.aurelia.map.hint");
	private static double lastScale = 1;
	private static String lastFilter = "";

	private final MapCamera camera;
	private final PinMenu pinMenu = new PinMenu();
	private final StationList stations = new StationList();
	private TextFieldWidget filter;
	private boolean dragging;
	private int cursorX = Integer.MIN_VALUE;
	private int cursorZ = Integer.MIN_VALUE;
	private Text cursor = Text.empty();

	public WorldMapScreen() {
		super(Text.translatable("gui.aurelia.map.title"));
		final var player = net.minecraft.client.MinecraftClient.getInstance().player;
		camera = new MapCamera(player == null ? 0 : player.getX(), player == null ? 0 : player.getZ(), lastScale);
	}

	@Override
	protected void init() {
		int y = MARGIN;
		y = toggle(y, "gui.aurelia.map.rails", MapSettings::rails, MapSettings::toggleRails);
		y = toggle(y, "gui.aurelia.map.stations", MapSettings::stations, MapSettings::toggleStations);
		y = toggle(y, "gui.aurelia.map.trains", MapSettings::trains, MapSettings::toggleTrains);
		toggle(y, "gui.aurelia.map.busy", MapSettings::busy, MapSettings::toggleBusy);

		final int bottom = height - MARGIN - BUTTON;
		final int centreX = width - MARGIN - 70;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.map.centre"), b -> centre()).dimensions(centreX, bottom, 70, BUTTON).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> zoom(1.25)).dimensions(centreX - 2 - BUTTON, bottom, BUTTON, BUTTON).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> zoom(0.8)).dimensions(centreX - 4 - BUTTON * 2, bottom, BUTTON, BUTTON).build());

		filter = addDrawableChild(new TextFieldWidget(textRenderer, listLeft() + MARGIN, MARGIN + MARGIN, StationList.WIDTH - MARGIN * 2, StationList.FIELD, Text.translatable("gui.aurelia.map.find_station")));
		filter.setPlaceholder(Text.translatable("gui.aurelia.map.find_station"));
		filter.setMaxLength(64);
		filter.setChangedListener(text -> {
			lastFilter = text;
			stations.setFilter(text);
		});
		filter.setText(lastFilter);
		stations.setFilter(lastFilter);
	}

	private int toggle(int y, String key, java.util.function.BooleanSupplier get, Runnable flip) {
		addDrawableChild(ButtonWidget.builder(label(key, get.getAsBoolean()), b -> {
			flip.run();
			b.setMessage(label(key, get.getAsBoolean()));
		}).dimensions(MARGIN, y, 80, BUTTON).build());
		return y + BUTTON + 2;
	}

	private static Text label(String key, boolean on) {
		return Text.translatable(key, ScreenTexts.onOrOff(on));
	}

	private void centre() {
		if (client != null && client.player != null) {
			camera.center(client.player.getX(), client.player.getZ());
		}
	}

	private void zoom(double factor) {
		camera.zoomAt(factor, width / 2.0, height / 2.0, 0, 0, width, height);
		lastScale = camera.scale();
	}

	private int listLeft() {
		return width - MARGIN - StationList.WIDTH;
	}

	/** The list runs from the top margin down to just above the bottom-right buttons. */
	private int listHeight() {
		return height - MARGIN * 3 - BUTTON;
	}

	private boolean overList(double mouseX, double mouseY) {
		return stations.contains(mouseX, mouseY, listLeft(), MARGIN, listHeight());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		MapView.draw(context, 0, 0, width, height, camera, true);
		stations.draw(context, listLeft(), MARGIN, listHeight(), mouseX, mouseY);
		super.render(context, mouseX, mouseY, delta);
		final int worldX = (int) Math.floor(camera.worldX(mouseX, 0, width));
		final int worldZ = (int) Math.floor(camera.worldZ(mouseY, 0, height));
		if (worldX != cursorX || worldZ != cursorZ) {
			cursorX = worldX;
			cursorZ = worldZ;
			cursor = Text.translatable("gui.aurelia.map.cursor", worldX, worldZ);
		}
		context.drawTextWithShadow(textRenderer, cursor, MARGIN + 2, height - MARGIN - 21, 0xDDDDDD);
		context.drawTextWithShadow(textRenderer, HINT, MARGIN + 2, height - MARGIN - 9, 0x888888);
		pinMenu.render(context, mouseX, mouseY, width, height);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (pinMenu.click(mouseX, mouseY)) {
			return true;
		}
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (overList(mouseX, mouseY)) {
			final SeenNetwork.Area station = stations.click(mouseX, mouseY, listLeft(), MARGIN, listHeight());
			if (station != null && button == 0) {
				centreOn(station);
			}
			return true;
		}
		if (hoveredElement(mouseX, mouseY).isPresent()) {
			return false;
		}
		setFocused(null);
		if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
			final int worldX = (int) Math.floor(camera.worldX(mouseX, 0, width));
			final int worldZ = (int) Math.floor(camera.worldZ(mouseY, 0, height));
			pinMenu.open((int) mouseX, (int) mouseY, worldX, worldZ, MapPinLayer.hit(mouseX, mouseY, 0, 0, width, height, camera.centerX(), camera.centerZ(), camera.scale()));
			return true;
		}
		dragging = button == 0;
		return dragging;
	}

	private void centreOn(SeenNetwork.Area station) {
		camera.center((station.minX() + station.maxX() + 1) / 2.0, (station.minZ() + station.maxZ() + 1) / 2.0);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging) {
			camera.drag(deltaX, deltaY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (overList(mouseX, mouseY)) {
			stations.scroll(amount, MARGIN, listHeight());
			return true;
		}
		camera.zoomAt(amount > 0 ? 1.25 : 0.8, mouseX, mouseY, 0, 0, width, height);
		lastScale = camera.scale();
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE && pinMenu.isOpen()) {
			pinMenu.close();
			return true;
		}
		if (filter.isFocused()) {
			// typing goes to the filter; Enter centres on the first match
			if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
				final SeenNetwork.Area first = stations.first();
				if (first != null) {
					centreOn(first);
				}
				return true;
			}
			return super.keyPressed(keyCode, scanCode, modifiers);
		}
		if (keyCode == GLFW.GLFW_KEY_C) {
			centre();
			return true;
		}
		if (MapClient.openKey().matchesKey(keyCode, scanCode)) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
