package org.mtr.aurelia.client.map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.mtr.aurelia.map.MapCamera;

/**
 * The full-screen map: drag to move, scroll to zoom about the cursor, C to come back to the player. Buttons along the
 * top switch rails, stations and trains (the same switches as the panel's Map tab). The world keeps running behind it.
 */
public final class WorldMapScreen extends Screen {

	private static final int BAR = 22;
	private static final int BAR_BG = 0xE0101418;
	private static double lastScale = 1;

	private final MapCamera camera;
	private boolean dragging;

	public WorldMapScreen() {
		super(Text.translatable("gui.aurelia.map.title"));
		final var player = net.minecraft.client.MinecraftClient.getInstance().player;
		camera = new MapCamera(player == null ? 0 : player.getX(), player == null ? 0 : player.getZ(), lastScale);
	}

	@Override
	protected void init() {
		int x = 6;
		x = toggle(x, "gui.aurelia.map.rails", MapSettings::rails, MapSettings::toggleRails);
		x = toggle(x, "gui.aurelia.map.stations", MapSettings::stations, MapSettings::toggleStations);
		x = toggle(x, "gui.aurelia.map.trains", MapSettings::trains, MapSettings::toggleTrains);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.map.centre"), b -> centre()).dimensions(x, 2, 70, 18).build());
	}

	private int toggle(int x, String key, java.util.function.BooleanSupplier get, Runnable flip) {
		addDrawableChild(ButtonWidget.builder(label(key, get.getAsBoolean()), b -> {
			flip.run();
			b.setMessage(label(key, get.getAsBoolean()));
		}).dimensions(x, 2, 80, 18).build());
		return x + 84;
	}

	private static Text label(String key, boolean on) {
		return Text.translatable(key, ScreenTexts.onOrOff(on));
	}

	private void centre() {
		if (client != null && client.player != null) {
			camera.center(client.player.getX(), client.player.getZ());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		MapView.draw(context, 0, BAR, width, height - BAR, camera, true);
		context.fill(0, 0, width, BAR, BAR_BG);
		super.render(context, mouseX, mouseY, delta);
		final int worldX = (int) Math.floor(camera.worldX(mouseX, 0, width));
		final int worldZ = (int) Math.floor(camera.worldZ(mouseY, BAR, height - BAR));
		final Text where = mouseY >= BAR ? Text.translatable("gui.aurelia.map.cursor", worldX, worldZ) : Text.empty();
		context.drawTextWithShadow(textRenderer, where, width - 6 - textRenderer.getWidth(where), 7, 0xDDDDDD);
		final Text hint = Text.translatable("gui.aurelia.map.hint");
		context.drawTextWithShadow(textRenderer, hint, 6, height - 12, 0x888888);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		dragging = button == 0 && mouseY >= BAR;
		return dragging;
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
		camera.zoomAt(amount > 0 ? 1.25 : 0.8, mouseX, mouseY, 0, BAR, width, height - BAR);
		lastScale = camera.scale();
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
