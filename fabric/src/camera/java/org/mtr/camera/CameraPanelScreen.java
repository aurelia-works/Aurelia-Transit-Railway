package org.mtr.camera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Dev-only trailer camera panel (the {@code atr_camera} mod, {@code src/camera}, never in the
 * release jar). Left: the frames on the current path, the play toggle, save/delete by name, the
 * playback speed slider and the hide-HUD toggle. Right: the saved paths in
 * {@code <run dir>/camera-paths/}; clicking one loads it.
 */
public final class CameraPanelScreen extends Screen {

	private static final int BUTTON_WIDTH = 200;
	private static final int ROW = 24;
	private static final int TOP = 44;

	@Nullable
	private final Screen parent;
	private TextFieldWidget nameField;
	private ButtonWidget playButton;
	private List<String> saved = List.of();
	private int listFirst;
	private String nameText = "shot1";

	public CameraPanelScreen(@Nullable Screen parent) {
		super(Text.translatable("gui.atr_camera.panel.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		saved = CameraRecorderMod.savedPaths();
		final int left = width / 2 - 210;
		final int right = width / 2 + 10;
		nameField = addDrawableChild(new TextFieldWidget(textRenderer, left, TOP, BUTTON_WIDTH, 20, Text.translatable("gui.atr_camera.panel.name")));
		nameField.setMaxLength(32);
		nameField.setText(nameText);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.atr_camera.panel.save"), button -> {
			nameText = nameField.getText();
			try {
				CameraRecorderMod.save(nameField.getText());
			} catch (RuntimeException | IOException e) {
				CameraRecorderMod.fail("save failed: " + e.getMessage());
			}
			clearAndInit();
		}).dimensions(left, TOP + ROW, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.atr_camera.panel.delete"), button -> {
			nameText = nameField.getText();
			try {
				CameraRecorderMod.delete(nameField.getText());
			} catch (RuntimeException | IOException e) {
				CameraRecorderMod.fail("delete failed: " + e.getMessage());
			}
			clearAndInit();
		}).dimensions(left, TOP + ROW * 2, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.atr_camera.panel.add"), button -> {
			if (client != null) {
				CameraRecorderMod.addFrameFromPlayer(client);
			}
			clearAndInit();
		}).dimensions(left, TOP + ROW * 3, BUTTON_WIDTH, 20).build());
		playButton = addDrawableChild(ButtonWidget.builder(playLabel(), button -> {
			CameraRecorderMod.togglePlay();
			button.setMessage(playLabel());
		}).dimensions(left, TOP + ROW * 4, BUTTON_WIDTH, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.atr_camera.panel.clear"), button -> {
			CameraRecorderMod.clearFrames();
			clearAndInit();
		}).dimensions(left, TOP + ROW * 5, BUTTON_WIDTH, 20).build());
		addDrawableChild(new SpeedSlider(left, TOP + ROW * 6));
		addDrawableChild(ButtonWidget.builder(hideHudLabel(), button -> {
			CameraRecorderMod.setHideHud(!CameraRecorderMod.hideHud());
			button.setMessage(hideHudLabel());
		}).dimensions(left, TOP + ROW * 7, BUTTON_WIDTH, 20).build());
		listFirst = Math.max(0, Math.min(listFirst, Math.max(0, saved.size() - visibleRows())));
		for (int i = listFirst; i < Math.min(saved.size(), listFirst + visibleRows()); i++) {
			final String name = saved.get(i);
			final int frames = CameraRecorderMod.savedFrameCount(name);
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.atr_camera.panel.path", name, frames < 0 ? "?" : String.valueOf(frames)), button -> {
				try {
					CameraRecorderMod.load(name);
					nameText = name;
				} catch (RuntimeException | IOException e) {
					CameraRecorderMod.fail("load failed: " + e.getMessage());
				}
				clearAndInit();
			}).dimensions(right, TOP + ROW + (i - listFirst) * ROW, BUTTON_WIDTH, 20).build());
		}
		addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions((width - BUTTON_WIDTH) / 2, height - 28, BUTTON_WIDTH, 20).build());
	}

	@Override
	public void tick() {
		if (playButton != null) {
			playButton.setMessage(playLabel());
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		final int newFirst = Math.max(0, Math.min(Math.max(0, saved.size() - visibleRows()), listFirst - (int) Math.signum(amount)));
		if (newFirst != listFirst) {
			listFirst = newFirst;
			clearAndInit();
		}
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.atr_camera.panel.frames", CameraRecorderMod.frameCount()), width / 2 - 210, TOP + 6, 0xFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.atr_camera.panel.saved"), width / 2 + 10, TOP + 6, 0xFFFFFF);
		if (saved.size() > visibleRows()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.atr_camera.panel.scroll", listFirst + 1, Math.min(saved.size(), listFirst + visibleRows()), saved.size()), width / 2 + 10, height - 44, 0xAAAAAA);
		} else if (saved.isEmpty()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.atr_camera.panel.empty"), width / 2 + 10, TOP + ROW + 6, 0xAAAAAA);
		}
		final String progress = CameraRecorderMod.progress();
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.atr_camera.panel.status", CameraRecorderMod.status() + (progress.isEmpty() ? "" : " " + progress)), width / 2, height - 58, 0xAAAAAA);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	private int visibleRows() {
		return Math.max(1, (height - 28 - 16 - (TOP + ROW)) / ROW);
	}

	private static Text playLabel() {
		return Text.translatable(CameraRecorderMod.playing() ? "gui.atr_camera.panel.stop" : "gui.atr_camera.panel.play");
	}

	private static Text hideHudLabel() {
		return Text.translatable("gui.atr_camera.panel.hide_hud", ScreenTexts.onOrOff(CameraRecorderMod.hideHud()));
	}

	/** Playback speed in blocks per second, 1 to 30 in halves. */
	private static final class SpeedSlider extends SliderWidget {

		private static final double MIN = 1;
		private static final double MAX = 30;
		private static final double STEP = 0.5;
		private static final int STEPS = (int) ((MAX - MIN) / STEP);

		private SpeedSlider(int x, int y) {
			super(x, y, BUTTON_WIDTH, 20, Text.empty(), (CameraRecorderMod.speed() - MIN) / (MAX - MIN));
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("gui.atr_camera.panel.speed", String.format(java.util.Locale.ROOT, "%.1f", MIN + Math.round(value * STEPS) * STEP)));
		}

		@Override
		protected void applyValue() {
			CameraRecorderMod.setSpeed(MIN + Math.round(value * STEPS) * STEP);
		}
	}
}
