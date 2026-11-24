package org.mtr.aurelia.client.map;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/** Asks the name of a new landmark, then goes back to the map it was opened from. */
public final class PinNameScreen extends Screen {

	@Nullable
	private final Screen parent;
	private final int worldX;
	private final int worldZ;
	private TextFieldWidget name;

	public PinNameScreen(@Nullable Screen parent, int worldX, int worldZ) {
		super(Text.translatable("gui.aurelia.map.pin.name_title"));
		this.parent = parent;
		this.worldX = worldX;
		this.worldZ = worldZ;
	}

	@Override
	protected void init() {
		name = new TextFieldWidget(textRenderer, width / 2 - 100, height / 2 - 20, 200, 20, title);
		name.setMaxLength(MapPins.MAX_NAME);
		addDrawableChild(name);
		setInitialFocus(name);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.aurelia.map.pin.add"), button -> done()).dimensions(width / 2 - 100, height / 2 + 6, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close()).dimensions(width / 2 + 2, height / 2 + 6, 98, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 36, 0xFFFFFF);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			done();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		if (client != null) {
			client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void done() {
		MapPins.addLandmark(name.getText(), worldX, worldZ);
		close();
	}
}
