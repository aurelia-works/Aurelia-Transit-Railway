package org.mtr.aurelia.client.map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * The small menu a right-click opens on a map view: set home here, add a landmark here (asks its name), or remove the
 * pin under the cursor. Each view keeps one; it closes on any click.
 */
public final class PinMenu {

	private static final int ROW = 12;
	private static final int PAD = 4;
	private static final int BACKGROUND = 0xF0101418;
	private static final int BORDER = 0xFF3A4048;
	private static final int HOVER = 0xFF2A3440;
	private static final int SET_HOME = 0;
	private static final int ADD_LANDMARK = 1;
	private static final int REMOVE = 2;
	private static final Text SET_HOME_TEXT = Text.translatable("gui.aurelia.map.pin.set_home");
	private static final Text ADD_LANDMARK_TEXT = Text.translatable("gui.aurelia.map.pin.add_landmark");

	private final int[] actions = new int[3];
	private final Text[] labels = new Text[3];
	private int count;
	private boolean open;
	private int menuX;
	private int menuY;
	private int menuWidth;
	private int worldX;
	private int worldZ;
	private int pin;

	public boolean isOpen() {
		return open;
	}

	/** Opens at a screen point over the block {@code (worldX, worldZ)}; {@code pin} is what {@link MapPinLayer#hit} found there. */
	public void open(int screenX, int screenY, int worldX, int worldZ, int pin) {
		this.worldX = worldX;
		this.worldZ = worldZ;
		this.pin = pin;
		count = 0;
		add(SET_HOME, SET_HOME_TEXT);
		add(ADD_LANDMARK, ADD_LANDMARK_TEXT);
		if (pin == MapPinLayer.HOME) {
			add(REMOVE, Text.translatable("gui.aurelia.map.pin.remove", MapPinLayer.HOME_LABEL));
		} else if (pin >= 0 && pin < MapPins.landmarks().size()) {
			add(REMOVE, Text.translatable("gui.aurelia.map.pin.remove", MapPins.landmarks().get(pin).name()));
		}
		final TextRenderer text = MinecraftClient.getInstance().textRenderer;
		menuWidth = 0;
		for (int i = 0; i < count; i++) {
			menuWidth = Math.max(menuWidth, text.getWidth(labels[i]) + PAD * 2);
		}
		menuX = screenX;
		menuY = screenY;
		open = true;
	}

	public void close() {
		open = false;
	}

	/** Draws the menu inside a screen of this size, moved in from the edges. */
	public void render(DrawContext context, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		if (!open) {
			return;
		}
		menuX = Math.max(0, Math.min(menuX, screenWidth - menuWidth));
		menuY = Math.max(0, Math.min(menuY, screenHeight - count * ROW - 2));
		final TextRenderer text = MinecraftClient.getInstance().textRenderer;
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 400);
		context.fill(menuX - 1, menuY - 1, menuX + menuWidth + 1, menuY + count * ROW + 1, BORDER);
		context.fill(menuX, menuY, menuX + menuWidth, menuY + count * ROW, BACKGROUND);
		for (int i = 0; i < count; i++) {
			final int rowY = menuY + i * ROW;
			if (mouseX >= menuX && mouseX < menuX + menuWidth && mouseY >= rowY && mouseY < rowY + ROW) {
				context.fill(menuX, rowY, menuX + menuWidth, rowY + ROW, HOVER);
			}
			context.drawTextWithShadow(text, labels[i], menuX + PAD, rowY + 2, 0xFFFFFFFF);
		}
		context.getMatrices().pop();
	}

	/** Handles a click while the menu is open: runs the row under it, then closes. Returns false when the menu was closed already. */
	public boolean click(double mouseX, double mouseY) {
		if (!open) {
			return false;
		}
		open = false;
		if (mouseX < menuX || mouseX >= menuX + menuWidth || mouseY < menuY || mouseY >= menuY + count * ROW) {
			return true;
		}
		switch (actions[(int) (mouseY - menuY) / ROW]) {
			case SET_HOME -> MapPins.setHome(worldX, worldZ);
			case ADD_LANDMARK -> {
				final MinecraftClient client = MinecraftClient.getInstance();
				client.setScreen(new PinNameScreen(client.currentScreen, worldX, worldZ));
			}
			case REMOVE -> {
				if (pin == MapPinLayer.HOME) {
					MapPins.removeHome();
				} else {
					MapPins.removeLandmark(pin);
				}
			}
			default -> {
			}
		}
		return true;
	}

	private void add(int action, Text label) {
		actions[count] = action;
		labels[count] = label;
		count++;
	}
}
