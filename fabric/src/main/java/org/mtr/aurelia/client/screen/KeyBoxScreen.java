package org.mtr.aurelia.client.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import org.mtr.core.data.Depot;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.InitClient;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockDriverKeyDispenser;
import org.mtr.mod.item.ItemDriverKey;
import org.mtr.mod.packet.PacketUpdateKeyDispenserConfig;

/**
 * The driver key box, opened by right-clicking MTR's driver key dispenser while its Look switch is on. Three keys hang on
 * hooks: tap one to add it to the box or take it off, then pick how long the keys work. Saves the same values as MTR's own
 * screen when it closes, so a box set here reads the same with the switch off.
 */
public final class KeyBoxScreen extends Screen {

	private static final int PANEL_WIDTH = 252;
	private static final int PANEL_HEIGHT = 178;
	private static final int CARD_WIDTH = 76;
	private static final int CARD_HEIGHT = 96;
	private static final int CARD_GAP = 6;
	private static final int STEP_BUTTON = 16;
	private static final float KEY_SCALE = 2.6F;

	private static final int FRAME = 0xFF3A3F46;
	private static final int FRAME_LIGHT = 0xFF5C636C;
	private static final int BACK = 0xFF1B1E22;
	private static final int CARD = 0xFF24282E;
	private static final int CARD_HOVER = 0xFF2E333A;
	private static final int CARD_ON = 0xFF2B3A52;
	private static final int ACCENT = 0xFF4F93EA;
	private static final int ROD = 0xFFB8BEC6;
	private static final int ROD_SHADE = 0xFF7D848D;
	private static final int TEXT = 0xFFFFFF;
	private static final int DIM = 0x9AA3AD;
	private static final int WARN = 0xFF6B5E;

	private static final long OPEN_MILLIS = 220;
	private static final long DROP_MILLIS = 260;
	private static final long[] STEPS = minutes(15, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440);

	private final BlockPos blockPos;
	private final Key[] keys;
	private final long opened = Util.getMeasuringTimeMs();
	private long timeout;
	private boolean saved;

	/** One hook: the key it holds, whether the box hands it out, and when it was last hung, for the drop and swing. */
	private static final class Key {
		final ItemStack stack;
		final ItemDriverKey item;
		boolean on;
		long hungAt;

		Key(org.mtr.mapping.holder.Item item, boolean on, long hungAt) {
			this.item = (ItemDriverKey) item.data;
			this.stack = new ItemStack(item.data);
			this.on = on;
			this.hungAt = hungAt;
		}
	}

	public KeyBoxScreen(BlockPos blockPos, BlockDriverKeyDispenser.BlockEntity blockEntity) {
		super(Text.translatable("gui.aurelia.key_box.title"));
		this.blockPos = blockPos;
		timeout = blockEntity.getTimeout();
		final long now = Util.getMeasuringTimeMs();
		// the hung keys settle one after another as the box opens
		keys = new Key[]{
				new Key(Items.BASIC_DRIVER_KEY.get(), blockEntity.getDispenseBasicDriverKey(), now + OPEN_MILLIS / 2),
				new Key(Items.ADVANCED_DRIVER_KEY.get(), blockEntity.getDispenseAdvancedDriverKey(), now + OPEN_MILLIS / 2 + 70),
				new Key(Items.GUARD_KEY.get(), blockEntity.getDispenseGuardKey(), now + OPEN_MILLIS / 2 + 140),
		};
	}

	private int left() {
		return (width - PANEL_WIDTH) / 2;
	}

	private int top() {
		return (height - PANEL_HEIGHT) / 2;
	}

	private int cardX(int index) {
		return left() + (PANEL_WIDTH - 3 * CARD_WIDTH - 2 * CARD_GAP) / 2 + index * (CARD_WIDTH + CARD_GAP);
	}

	private int cardY() {
		return top() + 32;
	}

	private int stepY() {
		return cardY() + CARD_HEIGHT + 10;
	}

	private int minusX() {
		return left() + PANEL_WIDTH - 12 - 3 * STEP_BUTTON - 56;
	}

	private int plusX() {
		return left() + PANEL_WIDTH - 12 - STEP_BUTTON;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context);
		final long now = Util.getMeasuringTimeMs();
		final float open = easeOut(Math.min(1, (now - opened) / (float) OPEN_MILLIS));
		context.getMatrices().push();
		// the box rises a little and grows into place as it opens
		final float scale = 0.9F + 0.1F * open;
		context.getMatrices().translate(width / 2F, height / 2F + (1 - open) * 10, 0);
		context.getMatrices().scale(scale, scale, 1);
		context.getMatrices().translate(-width / 2F, -height / 2F, 0);
		final double mx = (mouseX - width / 2D) / scale + width / 2D;
		final double my = (mouseY - height / 2D - (1 - open) * 10) / scale + height / 2D;

		final int left = left();
		final int top = top();
		context.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + PANEL_HEIGHT + 3, FRAME);
		context.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top - 2, FRAME_LIGHT);
		context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACK);
		context.drawTextWithShadow(textRenderer, title, left + 10, top + 8, TEXT);
		final Depot depot = InitClient.findDepot(blockPos);
		final Text where = depot == null ? Text.translatable("gui.aurelia.key_box.no_depot") : Text.literal(depot.getName());
		context.drawTextWithShadow(textRenderer, where, left + PANEL_WIDTH - 10 - textRenderer.getWidth(where), top + 8, depot == null ? WARN : DIM);

		// the rod the hooks hang from
		final int rodY = cardY() + 12;
		context.fill(left + 8, rodY, left + PANEL_WIDTH - 8, rodY + 3, ROD);
		context.fill(left + 8, rodY + 3, left + PANEL_WIDTH - 8, rodY + 4, ROD_SHADE);

		for (int i = 0; i < keys.length; i++) {
			card(context, i, mx, my, rodY, now);
		}

		timeoutRow(context, mx, my);
		context.drawText(textRenderer, Text.translatable("gui.aurelia.key_box.hint"), left + 10, top + PANEL_HEIGHT - 14, DIM, false);
		context.getMatrices().pop();
	}

	private void card(DrawContext context, int index, double mx, double my, int rodY, long now) {
		final Key key = keys[index];
		final int x = cardX(index);
		final int y = cardY();
		final boolean hover = inside(mx, my, x, y, CARD_WIDTH, CARD_HEIGHT);
		context.fill(x, y, x + CARD_WIDTH, y + CARD_HEIGHT, key.on ? CARD_ON : hover ? CARD_HOVER : CARD);
		if (key.on) {
			context.fill(x, y + CARD_HEIGHT - 2, x + CARD_WIDTH, y + CARD_HEIGHT, ACCENT);
		}
		// re-draw the rod over the card so it reads as one bar behind the keys
		context.fill(x, rodY, x + CARD_WIDTH, rodY + 3, ROD);
		context.fill(x, rodY + 3, x + CARD_WIDTH, rodY + 4, ROD_SHADE);
		final int hookX = x + CARD_WIDTH / 2;
		context.fill(hookX - 1, rodY + 3, hookX + 1, rodY + 10, ROD);
		context.fill(hookX - 3, rodY + 9, hookX + 3, rodY + 11, ROD_SHADE);

		if (key.on) {
			hangKey(context, key, hookX, rodY + 10, now);
		} else {
			final Text add = Text.translatable("gui.aurelia.key_box.add");
			context.drawText(textRenderer, add, hookX - textRenderer.getWidth(add) / 2, rodY + 26, hover ? TEXT : DIM, false);
		}

		final Text name = key.stack.getName();
		drawCentred(context, name, hookX, y + CARD_HEIGHT - 26, key.on ? TEXT : DIM, CARD_WIDTH - 6);
		drawCentred(context, Text.translatable(abilityKey(key.item)), hookX, y + CARD_HEIGHT - 14, key.on ? 0xC4D7F2 : 0x6C747D, CARD_WIDTH - 6);
	}

	/** The key hangs by its ring from the hook: it drops onto the hook, then swings and settles. */
	private void hangKey(DrawContext context, Key key, int hookX, int hookY, long now) {
		final float since = Math.max(0, now - key.hungAt);
		final float drop = since < DROP_MILLIS ? 1 - easeOut(since / DROP_MILLIS) : 0;
		final float seconds = since / 1000F;
		final float swing = (float) (16 * Math.exp(-seconds / 0.55) * Math.sin(seconds * 2 * Math.PI * 1.4));
		context.getMatrices().push();
		context.getMatrices().translate(hookX, hookY - drop * 14, 100);
		context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(swing + 135));
		context.getMatrices().scale(KEY_SCALE, KEY_SCALE, 1);
		// the ring's hole sits at about (4, 12) of the 16 px icon
		context.drawItem(key.stack, -4, -12);
		context.getMatrices().pop();
	}

	private void timeoutRow(DrawContext context, double mx, double my) {
		final int y = stepY();
		final int left = left();
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia.key_box.lasts"), left + 12, y + 4, TEXT);
		stepButton(context, minusX(), y, "-", mx, my, timeout > STEPS[0]);
		final Text value = duration(timeout);
		final int valueLeft = minusX() + STEP_BUTTON;
		final int valueRight = plusX();
		context.fill(valueLeft + 2, y, valueRight - 2, y + STEP_BUTTON, CARD);
		context.drawTextWithShadow(textRenderer, value, (valueLeft + valueRight - textRenderer.getWidth(value)) / 2, y + 4, TEXT);
		stepButton(context, plusX(), y, "+", mx, my, timeout < STEPS[STEPS.length - 1]);
	}

	private void stepButton(DrawContext context, int x, int y, String label, double mx, double my, boolean active) {
		final boolean hover = active && inside(mx, my, x, y, STEP_BUTTON, STEP_BUTTON);
		context.fill(x, y, x + STEP_BUTTON, y + STEP_BUTTON, hover ? ACCENT : CARD_HOVER);
		context.drawTextWithShadow(textRenderer, label, x + (STEP_BUTTON - textRenderer.getWidth(label)) / 2 + 1, y + 4, active ? TEXT : 0x555B62);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			final double mx = mouseX;
			final double my = mouseY;
			for (int i = 0; i < keys.length; i++) {
				if (inside(mx, my, cardX(i), cardY(), CARD_WIDTH, CARD_HEIGHT)) {
					final Key key = keys[i];
					key.on = !key.on;
					key.hungAt = Util.getMeasuringTimeMs();
					playClick();
					return true;
				}
			}
			if (inside(mx, my, minusX(), stepY(), STEP_BUTTON, STEP_BUTTON) && timeout > STEPS[0]) {
				timeout = step(timeout, -1);
				playClick();
				return true;
			}
			if (inside(mx, my, plusX(), stepY(), STEP_BUTTON, STEP_BUTTON) && timeout < STEPS[STEPS.length - 1]) {
				timeout = step(timeout, 1);
				playClick();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseY >= stepY() - 4 && mouseY < stepY() + STEP_BUTTON + 4) {
			final long next = step(timeout, amount > 0 ? 1 : -1);
			if (next != timeout) {
				timeout = next;
				playClick();
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public void removed() {
		if (!saved) {
			saved = true;
			InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateKeyDispenserConfig(blockPos, keys[0].on, keys[1].on, keys[2].on, timeout));
		}
		super.removed();
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** The next stop up or down from {@code timeout}; a value set by hand between stops moves to its nearest stop. */
	static long step(long timeout, int direction) {
		if (direction > 0) {
			for (final long stop : STEPS) {
				if (stop > timeout) {
					return stop;
				}
			}
			return Math.max(timeout, STEPS[STEPS.length - 1]);
		}
		for (int i = STEPS.length - 1; i >= 0; i--) {
			if (STEPS[i] < timeout) {
				return STEPS[i];
			}
		}
		return Math.min(timeout, STEPS[0]);
	}

	private static Text duration(long millis) {
		final long minutes = Math.max(0, millis) / 60000;
		final long hours = minutes / 60;
		final long rest = minutes % 60;
		if (hours == 0) {
			return Text.translatable("gui.aurelia.key_box.minutes", rest);
		}
		return rest == 0 ? Text.translatable("gui.aurelia.key_box.hours", hours) : Text.translatable("gui.aurelia.key_box.hours_minutes", hours, rest);
	}

	private static String abilityKey(ItemDriverKey key) {
		if (key.canDrive && key.canOpenDoors) {
			return "gui.aurelia.key_box.drive_doors";
		}
		return key.canDrive ? "gui.aurelia.key_box.drive" : "gui.aurelia.key_box.doors";
	}

	private void drawCentred(DrawContext context, Text text, int centreX, int y, int color, int maxWidth) {
		final int textWidth = textRenderer.getWidth(text);
		if (textWidth <= maxWidth) {
			context.drawText(textRenderer, text, centreX - textWidth / 2, y, color, false);
		} else {
			final float shrink = maxWidth / (float) textWidth;
			context.getMatrices().push();
			context.getMatrices().translate(centreX - maxWidth / 2F, y + (1 - shrink) * 4, 0);
			context.getMatrices().scale(shrink, shrink, 1);
			context.drawText(textRenderer, text, 0, 0, color, false);
			context.getMatrices().pop();
		}
	}

	private void playClick() {
		if (client != null) {
			client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1));
		}
	}

	private static boolean inside(double x, double y, int left, int top, int w, int h) {
		return x >= left && x < left + w && y >= top && y < top + h;
	}

	private static float easeOut(float t) {
		final float u = 1 - t;
		return 1 - u * u * u;
	}

	private static long[] minutes(long... values) {
		final long[] millis = new long[values.length];
		for (int i = 0; i < values.length; i++) {
			millis[i] = values[i] * 60000;
		}
		return millis;
	}
}
