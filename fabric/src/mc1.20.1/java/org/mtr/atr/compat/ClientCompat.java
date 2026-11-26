package org.mtr.atr.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Client-only {@link Compat}: the 1.20.1 side, making the calls ATR always made. Never load it on a dedicated server. */
public final class ClientCompat {

	private ClientCompat() {
	}

	public static void renderBackground(Screen screen, DrawContext context, int mouseX, int mouseY, float delta) {
		screen.renderBackground(context);
	}

	/** Turns the mouse wheel over an element; {@code amount} is the vertical turn. */
	public static boolean mouseScrolled(Element e, double mouseX, double mouseY, double amount) {
		return e.mouseScrolled(mouseX, mouseY, amount);
	}

	/** A button drawn from a texture holding its normal state above its hovered state, {@code hoveredVOffset} apart. */
	public static ButtonWidget texturedButton(int x, int y, int width, int height, int u, int v, int hoveredVOffset, Identifier texture, int textureWidth, int textureHeight, ButtonWidget.PressAction onPress, Text message) {
		return new TexturedButtonWidget(x, y, width, height, u, v, hoveredVOffset, texture, textureWidth, textureHeight, onPress, message);
	}

	public static String playerName() {
		return MinecraftClient.getInstance().getSession().getUsername();
	}

	public static String playerUuid() {
		return MinecraftClient.getInstance().getSession().getUuid();
	}
}
