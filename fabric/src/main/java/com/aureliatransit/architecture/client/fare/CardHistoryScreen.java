package com.aureliatransit.architecture.client.fare;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.fare.CardHistory;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Tap-in / tap-out lines stored on the card, newest at the bottom. */
public final class CardHistoryScreen extends Screen {

	private final ItemStack stack;

	public CardHistoryScreen(ItemStack stack) {
		super(Text.translatable("screen." + AureliaTransitArchitecture.MOD_ID + ".card_history"));
		this.stack = stack;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		final var taps = CardHistory.read(stack);
		final int width = 240;
		final int height = 28 + Math.max(1, taps.size()) * 12;
		final int left = (this.width - width) / 2;
		final int top = Math.max(16, (this.height - height) / 2);
		context.fill(left, top, left + width, top + height, 0xFF16191D);
		context.drawText(textRenderer, getTitle(), left + 8, top + 8, 0xFFF2F2EE, false);
		if (taps.isEmpty()) {
			context.drawText(textRenderer, Text.translatable("screen." + AureliaTransitArchitecture.MOD_ID + ".card_history.empty"), left + 8, top + 24, 0xFF9AA0A6, false);
		}
		int y = top + 24;
		for (final CardHistory.Tap tap : taps) {
			final String kind = tap.entry() ? "in" : "out";
			context.drawText(textRenderer, Text.translatable("screen." + AureliaTransitArchitecture.MOD_ID + ".card_history.line", kind, tap.station(), tap.fare()),
					left + 8, y, tap.entry() ? 0xFF9AA0A6 : 0xFFF2F2EE, false);
			y += 12;
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
