package org.mtr.atr.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.UUID;

/** The Minecraft 1.20.4 side of {@code src/mc1.20.1/java}'s {@code ClientCompat}. Never load it on a dedicated server. */
public final class ClientCompat {

	private ClientCompat() {
	}

	public static void renderBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
		screen.renderBackground(context, mouseX, mouseY, delta);
	}

	/** Turns the mouse wheel over an element; {@code amount} is the vertical turn. */
	public static boolean mouseScrolled(Element e, double mouseX, double mouseY, double amount) {
		return e.mouseScrolled(mouseX, mouseY, 0, amount);
	}

	/**
	 * A button drawn from a texture holding its normal state above its hovered state, {@code hoveredVOffset} apart. 1.20.4's
	 * textured button reads GUI atlas sprites instead, so this draws the texture the way 1.20.1's did.
	 */
	public static ButtonWidget texturedButton(int x, int y, int width, int height, int u, int v, int hoveredVOffset, Identifier texture, int textureWidth, int textureHeight, ButtonWidget.PressAction onPress, Text message) {
		return new TextureButton(x, y, width, height, u, v, hoveredVOffset, texture, textureWidth, textureHeight, onPress, message);
	}

	public static String playerName() {
		return MinecraftClient.getInstance().getSession().getUsername();
	}

	public static String playerUuid() {
		final UUID uuid = MinecraftClient.getInstance().getSession().getUuidOrNull();
		return uuid == null ? "" : uuid.toString().replace("-", "");
	}

	private static final class TextureButton extends ButtonWidget {

		private final int u;
		private final int v;
		private final int hoveredVOffset;
		private final Identifier texture;
		private final int textureWidth;
		private final int textureHeight;

		private TextureButton(int x, int y, int width, int height, int u, int v, int hoveredVOffset, Identifier texture, int textureWidth, int textureHeight, PressAction onPress, Text message) {
			super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
			this.u = u;
			this.v = v;
			this.hoveredVOffset = hoveredVOffset;
			this.texture = texture;
			this.textureWidth = textureWidth;
			this.textureHeight = textureHeight;
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			final int state = !active ? 2 : isSelected() ? 1 : 0;
			context.drawTexture(texture, getX(), getY(), u, v + state * hoveredVOffset, width, height, textureWidth, textureHeight);
		}
	}
}
