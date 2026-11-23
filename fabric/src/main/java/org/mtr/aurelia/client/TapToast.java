package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.client.ui.AureliaMotion;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUiSettings;

/**
 * A small card that pops up above the hotbar when you tap at a gate or card reader: green with the station, the fare and
 * your balance, red when there is not enough money. It reads MTR's own tap messages, so the numbers are MTR's, and takes
 * their place above the hotbar. The "Tap pop-up at gates" Look switch turns it off and brings the plain line back.
 */
public final class TapToast {

	private enum Kind {
		IN("gui.aurelia.tap.in", GREEN),
		OUT("gui.aurelia.tap.out", GREEN),
		LOW("gui.aurelia.tap.low", AureliaPalette.WARN),
		ALREADY_IN("gui.aurelia.tap.already_in", AMBER),
		NO_RECORD("gui.aurelia.tap.no_record", AMBER);

		final String key;
		final int color;

		Kind(String key, int color) {
			this.key = key;
			this.color = color;
		}

		boolean ok() {
			return this == IN || this == OUT;
		}
	}

	private static final int GREEN = 0xFF34C77B;
	private static final int AMBER = 0xFFE8B04A;
	private static final int CARD_TOP = 0xFF0B3D91;
	private static final int CARD_BOTTOM = 0xFF00A3A1;
	private static final int HEIGHT = 34;
	private static final long IN_MILLIS = 220;
	private static final long SHOW_MILLIS = 2800;
	private static final long OUT_MILLIS = 240;

	private static Kind kind;
	private static String station;
	private static Text detail;
	private static long shownAt;

	private TapToast() {
	}

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
				!overlay || !AureliaUiSettings.enabled(AureliaUiSettings.ScreenId.TAP_POPUP) || !accept(message));
		HudRenderCallback.EVENT.register((context, tickDelta) -> render(context));
	}

	/** Shows the pop-up for one of MTR's tap messages and returns true, or returns false for any other message. */
	private static boolean accept(Text message) {
		if (!(message.getContent() instanceof TranslatableTextContent content)) {
			return false;
		}
		final Object[] args = content.getArgs();
		switch (content.getKey()) {
			case "gui.mtr.enter_barrier" -> show(Kind.IN, station(arg(args, 0)), Text.translatable("gui.aurelia.tap.balance", money(arg(args, 1))));
			case "gui.mtr.exit_barrier" -> show(Kind.OUT, station(arg(args, 0)), Text.translatable("gui.aurelia.tap.fare_balance", money(arg(args, 1)), money(arg(args, 2))));
			case "gui.mtr.insufficient_balance" -> show(Kind.LOW, "", Text.translatable("gui.aurelia.tap.low_hint", money(arg(args, 0))));
			case "gui.mtr.already_entered" -> show(Kind.ALREADY_IN, "", Text.translatable("gui.aurelia.tap.already_in_hint"));
			case "gui.mtr.already_exited" -> show(Kind.NO_RECORD, "", Text.translatable("gui.aurelia.tap.no_record_hint"));
			default -> {
				return false;
			}
		}
		return true;
	}

	private static String arg(Object[] args, int index) {
		if (index >= args.length) {
			return "";
		}
		return args[index] instanceof Text text ? text.getString() : String.valueOf(args[index]);
	}

	/** MTR adds the station's zones, as in "Alpha (0/0/0)"; the pop-up shows the name alone. */
	private static String station(String shown) {
		return shown.replaceFirst("\\s*\\(-?\\d+/-?\\d+/-?\\d+\\)$", "");
	}

	/** "$4", or "-$4" when MTR's balance has gone below zero. */
	static String money(String amount) {
		return amount.startsWith("-") ? "-$" + amount.substring(1) : "$" + amount;
	}

	private static void show(Kind newKind, String newStation, Text newDetail) {
		kind = newKind;
		station = newStation;
		detail = newDetail;
		shownAt = AureliaMotion.now();
	}

	private static void render(DrawContext context) {
		if (kind == null) {
			return;
		}
		final long since = AureliaMotion.now() - shownAt;
		if (since > SHOW_MILLIS + OUT_MILLIS) {
			kind = null;
			return;
		}
		final float in = AureliaMotion.easeOutBack(AureliaMotion.progress(shownAt, IN_MILLIS));
		final float out = AureliaMotion.easeInOut(AureliaMotion.progress(shownAt + SHOW_MILLIS, OUT_MILLIS));
		final float alpha = Math.min(1, AureliaMotion.progress(shownAt, IN_MILLIS / 2)) * (1 - out);
		if (alpha <= 0.04F) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		final TextRenderer font = client.textRenderer;
		final Text title = Text.translatable(kind.key).formatted(Formatting.BOLD);
		final int textWidth = Math.max(font.getWidth(title) + (station.isEmpty() ? 0 : 6 + font.getWidth(station)), font.getWidth(detail));
		final int width = Math.max(150, textWidth + 50);
		final int x = (context.getScaledWindowWidth() - width) / 2;
		final int y = context.getScaledWindowHeight() - 66 - HEIGHT + Math.round((1 - in) * 10 + out * 8);

		AureliaChrome.rounded(context, x - 1, y + 1, width + 2, HEIGHT + 1, 7, AureliaChrome.fade(0x66000000, alpha));
		AureliaChrome.rounded(context, x, y, width, HEIGHT, 7, AureliaChrome.fade(AureliaPalette.SURFACE, alpha * 0.96F));
		AureliaChrome.roundedFrame(context, x, y, width, HEIGHT, 7, AureliaChrome.fade(AureliaChrome.mix(AureliaPalette.LINE, kind.color, 0.35F), alpha));
		card(context, x + 9, y + 9, alpha);
		badge(context, x + 26, y + 19, alpha);

		final int textX = x + 42;
		context.drawText(font, title, textX, y + 8, AureliaChrome.fade(AureliaPalette.FG, alpha), false);
		if (!station.isEmpty()) {
			final int room = x + width - 8 - (textX + font.getWidth(title) + 6);
			context.drawText(font, font.trimToWidth(station, Math.max(0, room)), textX + font.getWidth(title) + 6, y + 8, AureliaChrome.fade(AureliaPalette.TEXT_DIM, alpha), false);
		}
		context.drawText(font, detail, textX, y + 20, AureliaChrome.fade(kind.ok() ? 0xFFB9C9DE : kind.color, alpha), false);
	}

	/** A tiny transit card: blue to teal with a gold chip. */
	private static void card(DrawContext context, int x, int y, float alpha) {
		context.fillGradient(x + 1, y, x + 23, y + 16, AureliaChrome.fade(CARD_TOP, alpha), AureliaChrome.fade(CARD_BOTTOM, alpha));
		context.fill(x, y + 1, x + 1, y + 15, AureliaChrome.fade(CARD_TOP, alpha));
		context.fill(x + 23, y + 1, x + 24, y + 15, AureliaChrome.fade(CARD_BOTTOM, alpha));
		context.fill(x + 3, y + 5, x + 8, y + 9, AureliaChrome.fade(0xFFE3C27A, alpha));
	}

	/** A round badge on the card's corner: a tick when the gate let you through, a cross when it did not. */
	private static void badge(DrawContext context, int cx, int cy, float alpha) {
		AureliaChrome.rounded(context, cx - 6, cy - 6, 12, 12, 6, AureliaChrome.fade(AureliaPalette.SURFACE, alpha));
		AureliaChrome.rounded(context, cx - 5, cy - 5, 10, 10, 5, AureliaChrome.fade(kind.color, alpha));
		final int mark = AureliaChrome.fade(0xFFFFFFFF, alpha);
		if (kind.ok()) {
			// down from the left to the bottom of the tick, then up to the right
			for (int i = -3; i <= 3; i++) {
				final int dy = i <= -1 ? i + 3 : 1 - i;
				context.fill(cx + i, cy + dy - 1, cx + i + 1, cy + dy + 1, mark);
			}
		} else {
			for (int i = -2; i <= 2; i++) {
				context.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, mark);
				context.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, mark);
			}
		}
	}
}
