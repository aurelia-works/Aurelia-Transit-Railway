package com.aureliatransit.architecture.client.timetable;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.timetable.PocketSheet;
import com.aureliatransit.architecture.timetable.PocketTimetableItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * The printed sheet. Lines are drawn with the same text renderer the passenger terminal uses for its rows, on a paper
 * panel, so a timetable and a line map read like the signs and boards elsewhere in the mod.
 */
public final class PocketTimetableScreen extends Screen {

	private static final int PAPER = 0xFFF4EFE4;
	private static final int INK = 0xFF1C1A17;
	private static final int RULE = 0xFFB7A98A;

	private final PocketSheet.Sheet sheet;

	public PocketTimetableScreen(ItemStack stack) {
		super(Text.translatable("item." + AureliaTransitArchitecture.MOD_ID + ".pocket_timetable"));
		this.sheet = PocketTimetableItem.read(stack);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		final int width = 220;
		final int height = 16 + (sheet.lines().size() + 2) * 12;
		final int left = (this.width - width) / 2;
		final int top = Math.max(16, (this.height - height) / 2);
		context.fill(left, top, left + width, top + height, PAPER);
		context.fill(left, top, left + width, top + 2, RULE);
		final Text title = Text.literal(sheet.title().isEmpty() ? getTitle().getString() : sheet.title());
		context.drawText(textRenderer, title, left + 8, top + 8, INK, false);
		context.drawText(textRenderer, Text.translatable("screen." + AureliaTransitArchitecture.MOD_ID + ".pocket_" + sheet.kind().name().toLowerCase(java.util.Locale.ROOT)),
				left + 8, top + 20, RULE, false);
		int y = top + 36;
		for (final String line : sheet.lines()) {
			context.drawText(textRenderer, Text.literal(line), left + 8, y, INK, false);
			y += 12;
		}
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
