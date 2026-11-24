package com.aureliatransit.architecture.client.fare;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.fare.CardHistory;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaMotion;
import org.mtr.aurelia.client.ui.AureliaPalette;

import java.util.ArrayList;
import java.util.List;

/**
 * The transit card in your hand, how much it spent today, and its journeys newest first: each tap in paired with the tap
 * out after it. The card slides in and catches the light; the journeys follow one after another. Scroll for older ones.
 */
public final class CardHistoryScreen extends Screen {

	private static final String KEY = "screen." + AureliaTransitArchitecture.MOD_ID + ".card_history";
	private static final int WIDTH = 300;
	private static final int MAX_HEIGHT = 236;
	private static final int PAD = 12;
	private static final int CARD_WIDTH = 128;
	private static final int CARD_HEIGHT = 80;
	private static final int ROW = 26;
	private static final int GAP = 4;
	private static final int CARD_TOP = 0xFF0B3D91;
	private static final int CARD_BOTTOM = 0xFF00A3A1;
	private static final long PANEL_MILLIS = 200;
	private static final long CARD_MILLIS = 620;
	private static final long ROWS_START = 360;
	private static final long ROW_STAGGER = 45;
	private static final long ROW_MILLIS = 240;
	private static final long SHINE_START = 700;
	private static final long SHINE_MILLIS = 650;
	/** After this long without a tap out, a journey shows as not tapped out instead of travelling. */
	private static final long TRAVELLING_MILLIS = 3 * 3_600_000L;
	/** Below this the balance shows red: MTR's base fare is $2 plus $1 a zone, so a longer trip may not fit. */
	private static final int LOW_BALANCE = 5;

	private final ItemStack stack;
	private final long opened = AureliaMotion.now();
	private float scroll;

	/** One trip: where it started (null when the tap in is no longer on the card), where it ended (null while travelling). */
	private record Journey(CardHistory.Tap in, CardHistory.Tap out) {

		long at() {
			return out != null ? out.epochMillis() : in.epochMillis();
		}
	}

	public CardHistoryScreen(ItemStack stack) {
		super(Text.translatable(KEY));
		this.stack = stack;
	}

	/** Pairs each tap in with the tap out after it, newest journey first. */
	private static List<Journey> journeys(List<CardHistory.Tap> taps) {
		final List<Journey> journeys = new ArrayList<>();
		CardHistory.Tap open = null;
		for (final CardHistory.Tap tap : taps) {
			if (tap.entry()) {
				if (open != null) {
					journeys.add(new Journey(open, null));
				}
				open = tap;
			} else {
				journeys.add(new Journey(open, tap));
				open = null;
			}
		}
		if (open != null) {
			journeys.add(new Journey(open, null));
		}
		java.util.Collections.reverse(journeys);
		return journeys;
	}

	private int panelHeight() {
		return Math.min(MAX_HEIGHT, height - 16);
	}

	private int listTop(int top) {
		return top + 30 + CARD_HEIGHT + 12;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		final List<CardHistory.Tap> taps = CardHistory.read(stack);
		final List<Journey> journeys = journeys(taps);
		final long now = System.currentTimeMillis();
		final int h = panelHeight();
		final int left = (width - WIDTH) / 2;
		final int top = (height - h) / 2;

		final float panel = AureliaMotion.easeOut(AureliaMotion.progress(opened, PANEL_MILLIS));
		context.getMatrices().push();
		final float scale = 0.96F + 0.04F * panel;
		context.getMatrices().translate(width / 2F, height / 2F, 0);
		context.getMatrices().scale(scale, scale, 1);
		context.getMatrices().translate(-width / 2F, -height / 2F, 0);
		AureliaChrome.shadow(context, left, top, WIDTH, h, 8);
		AureliaChrome.rounded(context, left, top, WIDTH, h, 8, AureliaPalette.SURFACE);
		AureliaChrome.roundedFrame(context, left, top, WIDTH, h, 8, AureliaPalette.LINE);

		context.drawText(textRenderer, getTitle().copy().formatted(net.minecraft.util.Formatting.BOLD), left + PAD, top + PAD, AureliaPalette.FG, false);
		final Text kept = Text.translatable(KEY + ".kept", CardHistory.MAX);
		context.drawText(textRenderer, kept, left + WIDTH - PAD - textRenderer.getWidth(kept), top + PAD, AureliaPalette.TEXT_DIM, false);

		final int listTop = listTop(top);
		// the divider goes under the card, which passes over it as it slides in
		context.fill(left + PAD, listTop - 6, left + WIDTH - PAD, listTop - 5, AureliaPalette.LINE);
		card(context, left + PAD, top + 30, journeys.isEmpty());
		stats(context, left + PAD + CARD_WIDTH + 16, top + 34, taps, journeys, now);

		final int listBottom = top + h - PAD;
		if (journeys.isEmpty()) {
			empty(context, left, listTop, listBottom);
		} else {
			final int content = journeys.size() * (ROW + GAP) - GAP;
			final int room = listBottom - listTop;
			scroll = Math.max(0, Math.min(scroll, Math.max(0, content - room)));
			context.enableScissor(left + 1, listTop, left + WIDTH - 1, listBottom);
			for (int i = 0; i < journeys.size(); i++) {
				final int y = listTop + i * (ROW + GAP) - Math.round(scroll);
				if (y + ROW < listTop || y > listBottom) {
					continue;
				}
				final float shown = AureliaMotion.easeOut(AureliaMotion.progress(opened + ROWS_START + i * ROW_STAGGER, ROW_MILLIS));
				row(context, journeys.get(i), left + PAD + Math.round((1 - shown) * 10), y, WIDTH - 2 * PAD, shown, now);
			}
			context.disableScissor();
			if (content > room) {
				// a thin bar on the right that says how far down the list is
				final int bar = Math.max(12, room * room / content);
				final int barY = listTop + Math.round((room - bar) * scroll / (content - room));
				AureliaChrome.rounded(context, left + WIDTH - 6, barY, 2, bar, 1, AureliaPalette.LINE);
			}
		}
		context.getMatrices().pop();
		super.render(context, mouseX, mouseY, delta);
	}

	/** The card itself: a blue-to-teal wave, a chip, the contactless mark, the name and a card number of its own. */
	private void card(DrawContext context, int x, int y, boolean blank) {
		final float in = AureliaMotion.easeOutBack(AureliaMotion.progress(opened + 120, CARD_MILLIS));
		final var matrices = context.getMatrices();
		matrices.push();
		matrices.translate(x + CARD_WIDTH / 2F, y + CARD_HEIGHT / 2F + (1 - in) * 28, 0);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((1 - in) * -7));
		matrices.translate(-CARD_WIDTH / 2F, -CARD_HEIGHT / 2F, 0);

		AureliaChrome.shadow(context, 0, 0, CARD_WIDTH, CARD_HEIGHT, 7);
		// the face, shaded corner to corner a few pixels at a time
		final int r = 7;
		for (int row = 0; row < CARD_HEIGHT; row++) {
			final int edge = Math.min(row, CARD_HEIGHT - 1 - row);
			int inset = 0;
			if (edge < r) {
				final double dy = r - edge - 0.5;
				inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
			}
			for (int col = inset; col < CARD_WIDTH - inset; col += 4) {
				final float t = (col + row * 1.6F) / (CARD_WIDTH + CARD_HEIGHT * 1.6F);
				context.fill(col, row, Math.min(col + 4, CARD_WIDTH - inset), row + 1, AureliaChrome.mix(CARD_TOP, CARD_BOTTOM, t));
			}
		}
		// two soft bands across the face, like the waves printed on a real card
		for (int i = 0; i < CARD_WIDTH; i += 2) {
			final int wave = (int) Math.round(Math.sin(i / 18.0) * 5);
			context.fill(i, 46 + wave, i + 2, 52 + wave, 0x14FFFFFF);
			context.fill(i, 56 + wave, i + 2, 59 + wave, 0x0EFFFFFF);
		}
		context.drawText(textRenderer, "AURELIA", 9, 8, 0xFFFFFFFF, false);
		context.drawText(textRenderer, "TRANSIT", 9 + textRenderer.getWidth("AURELIA "), 8, 0xB3FFFFFF, false);
		chip(context, 10, 24);
		contactless(context, CARD_WIDTH - 16, 30);
		final String name = client == null || client.player == null ? "" : client.player.getName().getString();
		context.drawText(textRenderer, name.toUpperCase(java.util.Locale.ROOT), 10, CARD_HEIGHT - 22, 0xE6FFFFFF, false);
		context.drawText(textRenderer, "•••• " + number(), 10, CARD_HEIGHT - 12, 0x99FFFFFF, false);
		if (blank) {
			context.drawText(textRenderer, "NEW", CARD_WIDTH - 10 - textRenderer.getWidth("NEW"), CARD_HEIGHT - 12, 0x99FFFFFF, false);
		}
		shine(context);
		matrices.pop();
	}

	/** A band of light that passes over the card once it has landed. */
	private void shine(DrawContext context) {
		final float t = AureliaMotion.progress(opened + SHINE_START, SHINE_MILLIS);
		if (t <= 0 || t >= 1) {
			return;
		}
		final float centre = -30 + (CARD_WIDTH + 60) * AureliaMotion.easeInOut(t);
		for (int row = 2; row < CARD_HEIGHT - 2; row++) {
			final int x = Math.round(centre - row * 0.5F);
			final int x1 = Math.max(2, x - 9);
			final int x2 = Math.min(CARD_WIDTH - 2, x + 9);
			if (x2 > x1) {
				context.fill(x1, row, x2, row + 1, 0x1CFFFFFF);
				context.fill(Math.max(x1, x - 3), row, Math.min(x2, x + 3), row + 1, 0x18FFFFFF);
			}
		}
	}

	private static void chip(DrawContext context, int x, int y) {
		AureliaChrome.rounded(context, x, y, 18, 14, 3, 0xFFE3C27A);
		context.fill(x + 1, y + 4, x + 17, y + 5, 0xFFB8954D);
		context.fill(x + 1, y + 9, x + 17, y + 10, 0xFFB8954D);
		context.fill(x + 6, y + 1, x + 7, y + 13, 0xFFB8954D);
		context.fill(x + 11, y + 1, x + 12, y + 13, 0xFFB8954D);
	}

	/** Three arcs opening to the right. */
	private static void contactless(DrawContext context, int cx, int cy) {
		for (int r = 3; r <= 9; r += 3) {
			for (int deg = -50; deg <= 50; deg += 6) {
				final double a = Math.toRadians(deg);
				final int px = cx + (int) Math.round(r * Math.cos(a));
				final int py = cy + (int) Math.round(r * Math.sin(a));
				context.fill(px, py, px + 1, py + 1, 0xCCFFFFFF);
			}
		}
	}

	/** Four digits that stay the same for one player's cards. */
	private String number() {
		final int hash = client == null || client.player == null ? 0 : client.player.getUuid().hashCode();
		return String.format("%04d", Math.floorMod(hash, 10_000));
	}

	/** Balance big, then today's spend and the journey count side by side; a low balance turns red with a nudge to top up. */
	private void stats(DrawContext context, int x, int y, List<CardHistory.Tap> taps, List<Journey> journeys, long now) {
		final Integer balance = CardBalanceClient.recent();
		final boolean low = balance != null && balance < LOW_BALANCE;
		context.drawText(textRenderer, Text.translatable(KEY + ".balance"), x, y, AureliaPalette.TEXT_DIM, false);
		final Text shown = balance == null ? Text.literal("-") : Text.literal((balance < 0 ? "-" : "") + Text.translatable(KEY + ".fare", Math.abs(balance)).getString());
		final var matrices = context.getMatrices();
		matrices.push();
		matrices.translate(x, y + 12, 0);
		matrices.scale(2, 2, 1);
		context.drawText(textRenderer, shown, 0, 0, low ? AureliaPalette.WARN : AureliaPalette.FG, false);
		matrices.pop();
		if (low) {
			final Text topUp = Text.translatable(KEY + ".top_up");
			final int px = x + 2 * textRenderer.getWidth(shown) + 6;
			final int pw = textRenderer.getWidth(topUp) + 8;
			AureliaChrome.rounded(context, px, y + 14, pw, 12, 6, AureliaChrome.fade(AureliaPalette.WARN, 0.18F));
			context.drawText(textRenderer, topUp, px + 4, y + 16, AureliaPalette.WARN, false);
		}
		context.drawText(textRenderer, Text.translatable(KEY + ".spent_today"), x, y + 40, AureliaPalette.TEXT_DIM, false);
		context.drawText(textRenderer, Text.translatable(KEY + ".fare", CardHistory.spentOnDay(taps, now)), x, y + 52, AureliaPalette.FG, false);
		context.drawText(textRenderer, Text.translatable(KEY + ".journeys"), x + 76, y + 40, AureliaPalette.TEXT_DIM, false);
		context.drawText(textRenderer, Integer.toString(journeys.size()), x + 76, y + 52, AureliaPalette.FG, false);
	}

	private void empty(DrawContext context, int left, int top, int bottom) {
		final int mid = top + (bottom - top) / 2 - 10;
		final Text title = Text.translatable(KEY + ".empty");
		final Text hint = Text.translatable(KEY + ".empty_hint");
		context.drawText(textRenderer, title, left + (WIDTH - textRenderer.getWidth(title)) / 2, mid, AureliaPalette.FG, false);
		context.drawText(textRenderer, hint, left + (WIDTH - textRenderer.getWidth(hint)) / 2, mid + 13, AureliaPalette.TEXT_DIM, false);
	}

	/** One journey: a route mark, where it started and ended, its fare and how long ago. */
	private void row(DrawContext context, Journey journey, int x, int y, int w, float shown, long now) {
		if (shown <= 0.03F) {
			return;
		}
		AureliaChrome.rounded(context, x, y, w, ROW, 5, AureliaChrome.fade(AureliaPalette.RAISED, shown));
		final boolean travelling = journey.out() == null && now - journey.in().epochMillis() < TRAVELLING_MILLIS;
		final int accent = travelling ? AureliaPalette.ACCENT_LIGHT : 0xFF00A3A1;
		// the route mark: a ring for the start, a dot for the end, a line between
		AureliaChrome.roundedFrame(context, x + 9, y + 5, 5, 5, 2, AureliaChrome.fade(accent, shown));
		context.fill(x + 11, y + 10, x + 12, y + 16, AureliaChrome.fade(AureliaPalette.LINE, shown));
		AureliaChrome.rounded(context, x + 9, y + 16, 5, 5, 2, AureliaChrome.fade(journey.out() == null ? AureliaPalette.LINE : accent, shown));

		final int text = AureliaChrome.fade(AureliaPalette.FG, shown);
		final int dim = AureliaChrome.fade(AureliaPalette.TEXT_DIM, shown);
		final String from = journey.in() == null ? Text.translatable(KEY + ".unknown_start").getString() : journey.in().station();
		final Text to = journey.out() != null ? Text.literal(journey.out().station())
				: Text.translatable(KEY + (travelling ? ".travelling" : ".no_tap_out"));
		final int textX = x + 22;
		final Text fare = journey.out() == null ? Text.literal("") : Text.translatable(KEY + ".fare", journey.out().fare());
		final Text ago = ago(now - journey.at());
		final int right = x + w - 8;
		final int room = right - Math.max(textRenderer.getWidth(fare), textRenderer.getWidth(ago)) - 8 - textX;
		context.drawText(textRenderer, trim(from, room), textX, y + 4, journey.in() == null ? dim : text, false);
		context.drawText(textRenderer, trim(to.getString(), room), textX, y + 15, journey.out() == null ? AureliaChrome.fade(accent, shown) : text, false);
		context.drawText(textRenderer, fare, right - textRenderer.getWidth(fare), y + 4, text, false);
		context.drawText(textRenderer, ago, right - textRenderer.getWidth(ago), y + 15, dim, false);
	}

	private String trim(String text, int room) {
		return textRenderer.getWidth(text) <= room ? text : textRenderer.trimToWidth(text, Math.max(0, room - textRenderer.getWidth("…"))) + "…";
	}

	private static Text ago(long millis) {
		final long minutes = Math.max(0, millis) / 60_000L;
		if (minutes < 1) {
			return Text.translatable(KEY + ".just_now");
		}
		if (minutes < 60) {
			return Text.translatable(KEY + ".minutes_ago", minutes);
		}
		if (minutes < 48 * 60) {
			return Text.translatable(KEY + ".hours_ago", minutes / 60);
		}
		return Text.translatable(KEY + ".days_ago", minutes / (24 * 60));
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		scroll -= (float) amount * (ROW + GAP);
		return true;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
