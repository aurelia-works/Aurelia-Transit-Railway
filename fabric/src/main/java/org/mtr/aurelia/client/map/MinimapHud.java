package org.mtr.aurelia.client.map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.mtr.aurelia.map.MapCamera;

/**
 * The minimap: a north-up square in the corner the Map tab picks, centred on the player, with the block coordinates
 * under it. Hidden with F1, on the debug screen, while the big map is open, and when the Map tab turns it off.
 */
public final class MinimapHud {

	private static final int MARGIN = 4;
	private static final int BORDER = 0xFF3A3A3E;

	private MinimapHud() {
	}

	public static void render(DrawContext context) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (!MapSettings.minimap() || client.player == null || MapClient.map() == null || client.options.hudHidden
				|| client.options.debugEnabled || client.currentScreen instanceof WorldMapScreen) {
			return;
		}
		final int size = MapSettings.size();
		final int screenWidth = context.getScaledWindowWidth();
		final int screenHeight = context.getScaledWindowHeight();
		final boolean right = MapSettings.corner() == MapSettings.Corner.TOP_RIGHT || MapSettings.corner() == MapSettings.Corner.BOTTOM_RIGHT;
		final boolean bottom = MapSettings.corner() == MapSettings.Corner.BOTTOM_RIGHT || MapSettings.corner() == MapSettings.Corner.BOTTOM_LEFT;
		final int left = right ? screenWidth - MARGIN - size : MARGIN;
		// the coordinates line sits under the map, so a bottom map leaves room for it and the hotbar
		final int top = bottom ? screenHeight - MARGIN - size - 12 - 24 : MARGIN;
		final MapCamera camera = new MapCamera(client.player.getX(), client.player.getZ(), MapSettings.zoom() / 100.0);
		context.fill(left - 1, top - 1, left + size + 1, top + size + 1, BORDER);
		MapView.draw(context, left, top, size, size, camera, false);
		final Text where = Text.literal(client.player.getBlockX() + ", " + client.player.getBlockY() + ", " + client.player.getBlockZ());
		context.drawCenteredTextWithShadow(client.textRenderer, where, left + size / 2, top + size + 3, 0xDDDDDD);
	}
}
