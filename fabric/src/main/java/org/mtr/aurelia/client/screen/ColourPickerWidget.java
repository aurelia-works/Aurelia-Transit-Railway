package org.mtr.aurelia.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.IntConsumer;

/**
 * A colour picker: a saturation (across) by brightness (down) square for the current hue, and a hue strip beside it.
 * Dragging shows the colour through {@code preview}; letting go hands it to {@code commit} once, so a drag sends one
 * change, not one per pixel.
 */
public final class ColourPickerWidget extends ClickableWidget {

	private static final int HUE_WIDTH = 12;
	private static final int GAP = 4;

	private final IntConsumer preview;
	private final IntConsumer commit;
	private float hue;
	private float saturation;
	private float brightness;
	/** Which part the press started on, so a drag that wanders across the gap keeps moving the same thing. */
	private boolean draggingHue;

	public ColourPickerWidget(int x, int y, int width, int height, int colour, IntConsumer preview, IntConsumer commit) {
		super(x, y, width, height, Text.empty());
		this.preview = preview;
		this.commit = commit;
		final float red = (colour >> 16 & 0xFF) / 255F;
		final float green = (colour >> 8 & 0xFF) / 255F;
		final float blue = (colour & 0xFF) / 255F;
		final float max = Math.max(red, Math.max(green, blue));
		final float range = max - Math.min(red, Math.min(green, blue));
		brightness = max;
		saturation = max == 0 ? 0 : range / max;
		if (range == 0) {
			hue = 0;
		} else if (max == red) {
			hue = ((green - blue) / range / 6 + 1) % 1;
		} else if (max == green) {
			hue = ((blue - red) / range + 2) / 6;
		} else {
			hue = ((red - green) / range + 4) / 6;
		}
	}

	public int colour() {
		return MathHelper.hsvToRgb(hue, saturation, brightness) & 0xFFFFFF;
	}

	private int squareWidth() {
		return width - HUE_WIDTH - GAP;
	}

	@Override
	protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
		final int x = getX();
		final int y = getY();
		final int square = squareWidth();
		context.fill(x - 1, y - 1, x + square + 1, y + height + 1, active ? 0xFFFFFFFF : 0xFF555555);
		for (int column = 0; column < square; column++) {
			final int topColour = MathHelper.hsvToRgb(hue, column / (float) (square - 1), 1);
			context.fillGradient(x + column, y, x + column + 1, y + height, 0xFF000000 | topColour, 0xFF000000);
		}
		final int hueX = x + square + GAP;
		context.fill(hueX - 1, y - 1, hueX + HUE_WIDTH + 1, y + height + 1, active ? 0xFFFFFFFF : 0xFF555555);
		for (int row = 0; row < height; row++) {
			context.fill(hueX, y + row, hueX + HUE_WIDTH, y + row + 1, 0xFF000000 | MathHelper.hsvToRgb(row / (float) height, 1, 1));
		}
		final int markX = x + Math.round(saturation * (square - 1));
		final int markY = y + Math.round((1 - brightness) * (height - 1));
		final int ring = brightness > 0.5F ? 0xFF000000 : 0xFFFFFFFF;
		context.drawBorder(markX - 2, markY - 2, 5, 5, ring);
		final int hueY = y + Math.round(hue * (height - 1));
		context.fill(hueX - 2, hueY - 1, hueX + HUE_WIDTH + 2, hueY + 2, 0xFF000000);
		context.fill(hueX - 1, hueY, hueX + HUE_WIDTH + 1, hueY + 1, 0xFFFFFFFF);
		if (!active) {
			context.fill(x, y, hueX + HUE_WIDTH, y + height, 0x99000000);
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		draggingHue = mouseX >= getX() + squareWidth();
		pick(mouseX, mouseY);
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
		pick(mouseX, mouseY);
	}

	@Override
	public void onRelease(double mouseX, double mouseY) {
		commit.accept(colour());
	}

	private void pick(double mouseX, double mouseY) {
		final float down = MathHelper.clamp((float) (mouseY - getY()) / (height - 1), 0, 1);
		if (draggingHue) {
			hue = Math.min(down, 0.999F);
		} else {
			saturation = MathHelper.clamp((float) (mouseX - getX()) / (squareWidth() - 1), 0, 1);
			brightness = 1 - down;
		}
		preview.accept(colour());
	}

	@Override
	protected void appendClickableNarrations(NarrationMessageBuilder builder) {
		builder.put(NarrationPart.TITLE, Text.literal(String.format("#%06X", colour())));
	}
}
