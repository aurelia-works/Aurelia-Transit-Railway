package org.mtr.aurelia.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.mtr.atr.compat.ClientCompat;
import org.mtr.atr.compat.CompatScreen;
import org.mtr.aurelia.client.ui.AureliaChrome;
import org.mtr.aurelia.keybox.KeysOut;
import org.mtr.aurelia.client.ui.AureliaMotion;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.core.data.Depot;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mod.InitClient;
import org.mtr.mod.Items;
import org.mtr.mod.block.BlockDriverKeyDispenser;
import org.mtr.mod.item.ItemDriverKey;
import org.mtr.mod.packet.PacketUpdateKeyDispenserConfig;

/**
 * The driver key box, opened by right-clicking MTR's driver key dispenser while its Look switch is on. The glass door
 * swings open and the keys the box hands out drop onto their hooks; tap a hook to add its key or take it off, then pick
 * how long the keys work. Saves the same values as MTR's own screen when it closes, so a box set here reads the same
 * with the switch off.
 */
public final class KeyBoxScreen extends CompatScreen {

	/** The upright key every hook shows, tinted with the key's own colour. Also drawn inside the block. */
	public static final Identifier KEY_SPRITE = new Identifier("aurelia", "textures/gui/key_box_key.png");
	public static final int KEY_SPRITE_WIDTH = 16;
	public static final int KEY_SPRITE_HEIGHT = 24;

	private static final int PANEL_WIDTH = 288;
	private static final int PANEL_HEIGHT = 206;
	private static final int PAD = 12;
	private static final int CABINET_TOP = 30;
	private static final int CABINET_HEIGHT = 104;
	private static final int ROD_Y = 12;
	private static final int KEY_SCALE = 2;
	private static final int STEP_BUTTON = 18;
	private static final int VALUE_WIDTH = 70;
	private static final int DONE_WIDTH = 56;
	private static final int ROW_HEIGHT = 18;

	private static final int CABINET = 0xFF0E1013;
	private static final int STEEL = 0xFFB8BEC6;
	private static final int STEEL_SHADE = 0xFF6B727A;
	private static final int GLASS = 0x2EB4D6F0;
	private static final int GLASS_EDGE = 0x66D6ECFA;

	private static final long OPEN_MILLIS = 200;
	private static final long DOOR_DELAY = 120;
	private static final long DOOR_MILLIS = 340;
	private static final long DROP_MILLIS = 300;
	private static final long HOVER_MILLIS = 120;
	private static final long[] STEPS = minutes(15, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440);

	/** The line under a key that is out with a driver. */
	private static final int OUT_TEXT = 0xFFE8B04A;

	private final BlockPos blockPos;
	private final Key[] keys;
	private final long opened = AureliaMotion.now();
	private long timeout;
	private boolean saved;
	private int pressed = -1;
	private long pressedAt;
	private long lastFrame = AureliaMotion.now();
	private float frameMillis;

	/** One hook: its key, whether the box hands it out, when it was last hung, and how lit it is under the mouse. */
	private static final class Key {
		final ItemDriverKey item;
		final Text name;
		boolean on;
		long hungAt;
		float hover;
		/** When the last key this box dropped comes back (epoch millis); until then its hook is empty. */
		final long backAt;

		Key(org.mtr.mapping.holder.Item item, String name, boolean on, long hungAt, long backAt) {
			this.item = (ItemDriverKey) item.data;
			this.name = Text.translatable("gui.aurelia.key_box.name." + name);
			this.on = on;
			this.hungAt = hungAt;
			this.backAt = backAt;
		}

		boolean out() {
			return on && backAt > System.currentTimeMillis();
		}
	}

	public KeyBoxScreen(BlockPos blockPos, BlockDriverKeyDispenser.BlockEntity blockEntity) {
		super(Text.translatable("gui.aurelia.key_box.title"));
		this.blockPos = blockPos;
		timeout = blockEntity.getTimeout();
		// once the door is open, the keys already in the box drop onto their hooks one after another
		final long settle = opened + DOOR_DELAY + DOOR_MILLIS * 2 / 3;
		final KeysOut out = (KeysOut) blockEntity;
		keys = new Key[]{
				new Key(Items.BASIC_DRIVER_KEY.get(), "basic", blockEntity.getDispenseBasicDriverKey(), settle, out.atr$keyBackAt(KeysOut.BASIC)),
				new Key(Items.ADVANCED_DRIVER_KEY.get(), "advanced", blockEntity.getDispenseAdvancedDriverKey(), settle + 80, out.atr$keyBackAt(KeysOut.ADVANCED)),
				new Key(Items.GUARD_KEY.get(), "guard", blockEntity.getDispenseGuardKey(), settle + 160, out.atr$keyBackAt(KeysOut.GUARD)),
		};
	}

	@Override
	protected void init() {
		play(SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 1.5F, 0.35F);
	}

	private int left() {
		return (width - PANEL_WIDTH) / 2;
	}

	private int top() {
		return (height - PANEL_HEIGHT) / 2;
	}

	private int cabinetLeft() {
		return left() + PAD;
	}

	private int cabinetWidth() {
		return PANEL_WIDTH - 2 * PAD;
	}

	private int slotWidth() {
		return cabinetWidth() / 3;
	}

	private int slotX(int index) {
		return cabinetLeft() + index * slotWidth();
	}

	private int cabinetTop() {
		return top() + CABINET_TOP;
	}

	private int stepY() {
		return cabinetTop() + CABINET_HEIGHT + 12;
	}

	private int plusX() {
		return left() + PANEL_WIDTH - PAD - STEP_BUTTON;
	}

	private int minusX() {
		return plusX() - VALUE_WIDTH - STEP_BUTTON;
	}

	private int footerY() {
		return top() + PANEL_HEIGHT - PAD - ROW_HEIGHT;
	}

	private int doneX() {
		return left() + PANEL_WIDTH - PAD - DONE_WIDTH;
	}

	private boolean ready() {
		return AureliaMotion.now() - opened >= OPEN_MILLIS;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		ClientCompat.renderBackground(this, context, mouseX, mouseY, delta);
		final float open = AureliaMotion.easeOut(AureliaMotion.progress(opened, OPEN_MILLIS));
		final float scale = 0.94F + 0.06F * open;
		context.getMatrices().push();
		context.getMatrices().translate(width / 2F, height / 2F + (1 - open) * 8, 0);
		context.getMatrices().scale(scale, scale, 1);
		context.getMatrices().translate(-width / 2F, -height / 2F, 0);
		final boolean live = ready();
		final long now = AureliaMotion.now();
		frameMillis = Math.min(100, now - lastFrame);
		lastFrame = now;

		final int left = left();
		final int top = top();
		AureliaChrome.shadow(context, left, top, PANEL_WIDTH, PANEL_HEIGHT, 8);
		AureliaChrome.rounded(context, left, top, PANEL_WIDTH, PANEL_HEIGHT, 8, AureliaPalette.SURFACE);
		AureliaChrome.roundedFrame(context, left, top, PANEL_WIDTH, PANEL_HEIGHT, 8, AureliaPalette.LINE);
		context.drawTextWithShadow(textRenderer, title, left + PAD, top + 11, AureliaPalette.TEXT);
		depotPill(context, left, top);

		cabinet(context, live ? mouseX : -1, live ? mouseY : -1);
		timeoutRow(context, live ? mouseX : -1, live ? mouseY : -1);
		footer(context, live ? mouseX : -1, live ? mouseY : -1);
		context.getMatrices().pop();
	}

	/** The depot the keys are for, as a quiet pill on the right of the title; red words when there is none. */
	private void depotPill(DrawContext context, int left, int top) {
		final Depot depot = InitClient.findDepot(blockPos);
		final Text where = depot == null ? Text.translatable("gui.aurelia.key_box.no_depot") : Text.literal(depot.getName());
		final int room = PANEL_WIDTH - 3 * PAD - textRenderer.getWidth(title) - 12;
		final String shown = textRenderer.trimToWidth(where.getString(), room);
		final int pillWidth = textRenderer.getWidth(shown) + 12;
		final int x = left + PANEL_WIDTH - PAD - pillWidth;
		AureliaChrome.rounded(context, x, top + 7, pillWidth, 15, 7, AureliaPalette.RAISED);
		context.drawText(textRenderer, shown, x + 6, top + 11, depot == null ? AureliaPalette.WARN : AureliaPalette.TEXT_DIM, false);
	}

	/** The inside of the box: a dark back, the rod, three hooks with their keys, and the glass door swinging open. */
	private void cabinet(DrawContext context, int mouseX, int mouseY) {
		final int x = cabinetLeft();
		final int y = cabinetTop();
		final int w = cabinetWidth();
		AureliaChrome.rounded(context, x, y, w, CABINET_HEIGHT, 6, CABINET);
		for (int i = 0; i < keys.length; i++) {
			slot(context, i, mouseX, mouseY);
		}
		// the rod runs the full width, over the slot fills and behind the keys' rings
		context.fill(x + 6, y + ROD_Y, x + w - 6, y + ROD_Y + 2, STEEL);
		context.fill(x + 6, y + ROD_Y + 2, x + w - 6, y + ROD_Y + 3, STEEL_SHADE);
		for (int i = 0; i < keys.length; i++) {
			hook(context, i);
		}
		door(context, x, y, w);
	}

	private void slot(DrawContext context, int index, int mouseX, int mouseY) {
		final Key key = keys[index];
		final int x = slotX(index);
		final int y = cabinetTop();
		final boolean hovered = inside(mouseX, mouseY, x, y, slotWidth(), CABINET_HEIGHT);
		key.hover += (hovered ? 1 : -1) * frameMillis / HOVER_MILLIS;
		key.hover = Math.max(0, Math.min(1, key.hover));
		final float press = pressed == index ? 1 - AureliaMotion.progress(pressedAt, 140) : 0;
		final int fill = AureliaChrome.mix(key.on ? 0xFF1A2330 : 0x00000000, key.on ? 0xFF22303F : 0xFF181B20, key.hover * 0.8F + press * 0.2F);
		AureliaChrome.rounded(context, x + 3, y + 3, slotWidth() - 6, CABINET_HEIGHT - 6, 5, fill);
		if (key.on) {
			AureliaChrome.roundedFrame(context, x + 3, y + 3, slotWidth() - 6, CABINET_HEIGHT - 6, 5, AureliaChrome.fade(AureliaPalette.ACCENT_LIGHT, 0.55F + 0.45F * key.hover));
		}
		final int centre = x + slotWidth() / 2;
		final int nameY = y + CABINET_HEIGHT - 28;
		drawCentred(context, key.name, centre, nameY, key.on ? 0xFFFFFFFF : AureliaPalette.TEXT_DIM, slotWidth() - 10);
		if (key.out()) {
			// a driver has this key: say when it comes back
			drawCentred(context, Text.translatable("gui.aurelia.key_box.out", shortDuration(key.backAt - System.currentTimeMillis())), centre, nameY + 11, OUT_TEXT, slotWidth() - 10);
			return;
		}
		final Text ability = Text.translatable(key.on || key.hover > 0.5F ? abilityKey(key.item) : "gui.aurelia.key_box.add");
		drawCentred(context, ability, centre, nameY + 11, key.on ? 0xFFB9C9DE : AureliaChrome.fade(AureliaPalette.TEXT_DIM, 0.8F), slotWidth() - 10);
	}

	/** The hook, and the key on it: dropping in with a small bounce and a swing that dies away, or a faint outline when empty. */
	private void hook(DrawContext context, int index) {
		final Key key = keys[index];
		final int centre = slotX(index) + slotWidth() / 2;
		final int rodY = cabinetTop() + ROD_Y;
		context.fill(centre - 1, rodY + 2, centre + 1, rodY + 8, STEEL);
		context.fill(centre - 1, rodY + 7, centre + 3, rodY + 9, STEEL);
		final int color = key.item.color;
		final long since = AureliaMotion.now() - key.hungAt;
		if (!key.on || key.out()) {
			drawKey(context, centre, rodY + 6, 0, color, 0.16F + 0.14F * key.hover);
		} else if (since >= 0) {
			final float drop = 1 - AureliaMotion.easeOutBack(AureliaMotion.progress(key.hungAt, DROP_MILLIS));
			final float swing = AureliaMotion.swing(key.hungAt, 14, 1.3F);
			drawKey(context, centre, rodY + 6 - Math.round(drop * 16), swing, color, Math.min(1, since / 90F));
		}
	}

	/** The key sprite hanging from ({@code hookX}, {@code hookY}) by its ring, turned {@code degrees} about the hook. */
	private void drawKey(DrawContext context, int hookX, int hookY, float degrees, int color, float alpha) {
		if (alpha <= 0) {
			return;
		}
		context.getMatrices().push();
		context.getMatrices().translate(hookX, hookY, 0);
		context.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(degrees));
		RenderSystem.enableBlend();
		RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, alpha);
		// the ring's hole is 5 px below the sprite's top, in its middle
		final int w = KEY_SPRITE_WIDTH * KEY_SCALE;
		final int h = KEY_SPRITE_HEIGHT * KEY_SCALE;
		context.drawTexture(KEY_SPRITE, -w / 2, -5 * KEY_SCALE, w, h, 0, 0, KEY_SPRITE_WIDTH, KEY_SPRITE_HEIGHT, KEY_SPRITE_WIDTH, KEY_SPRITE_HEIGHT);
		RenderSystem.setShaderColor(1, 1, 1, 1);
		RenderSystem.disableBlend();
		context.getMatrices().pop();
	}

	/** The glass door, hinged on the left: it folds away to its hinge as it swings open, catching less light as it turns. */
	private void door(DrawContext context, int x, int y, int w) {
		final float t = AureliaMotion.easeInOut(AureliaMotion.progress(opened + DOOR_DELAY, DOOR_MILLIS));
		if (t >= 1) {
			return;
		}
		final int doorWidth = Math.round(w * (1 - t));
		if (doorWidth <= 1) {
			return;
		}
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 200);
		AureliaChrome.rounded(context, x, y, doorWidth, CABINET_HEIGHT, 6, AureliaChrome.fade(GLASS, 1 - t * 0.5F));
		AureliaChrome.roundedFrame(context, x, y, doorWidth, CABINET_HEIGHT, 6, AureliaChrome.mix(STEEL, STEEL_SHADE, t));
		context.fill(x + 3, y + 3, x + 4, y + CABINET_HEIGHT - 3, AureliaChrome.mix(STEEL, STEEL_SHADE, t));
		context.fill(x + 6, y + 4, x + doorWidth - 6, y + 5, AureliaChrome.fade(GLASS_EDGE, 1 - t));
		if (doorWidth > 20) {
			context.fill(x + doorWidth - 9, y + CABINET_HEIGHT / 2 - 8, x + doorWidth - 6, y + CABINET_HEIGHT / 2 + 8, STEEL);
		}
		context.getMatrices().pop();
	}

	private void timeoutRow(DrawContext context, int mouseX, int mouseY) {
		final int y = stepY();
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.aurelia.key_box.lasts"), left() + PAD, y + 5, AureliaPalette.TEXT);
		final int groupX = minusX();
		final int groupWidth = 2 * STEP_BUTTON + VALUE_WIDTH;
		AureliaChrome.rounded(context, groupX, y, groupWidth, STEP_BUTTON, 6, AureliaPalette.RAISED);
		stepButton(context, minusX(), y, "-", 0, mouseX, mouseY, timeout > STEPS[0]);
		stepButton(context, plusX(), y, "+", 1, mouseX, mouseY, timeout < STEPS[STEPS.length - 1]);
		context.fill(minusX() + STEP_BUTTON, y + 4, minusX() + STEP_BUTTON + 1, y + STEP_BUTTON - 4, AureliaPalette.LINE);
		context.fill(plusX() - 1, y + 4, plusX(), y + STEP_BUTTON - 4, AureliaPalette.LINE);
		final Text value = duration(timeout);
		context.drawTextWithShadow(textRenderer, value, minusX() + STEP_BUTTON + (VALUE_WIDTH - textRenderer.getWidth(value)) / 2, y + 5, AureliaPalette.TEXT);
	}

	private void stepButton(DrawContext context, int x, int y, String label, int id, int mouseX, int mouseY, boolean active) {
		final boolean hovered = active && inside(mouseX, mouseY, x, y, STEP_BUTTON, STEP_BUTTON);
		final boolean down = pressed == 10 + id && AureliaMotion.progress(pressedAt, 120) < 1;
		if (hovered || down) {
			AureliaChrome.rounded(context, x + 1, y + 1, STEP_BUTTON - 2, STEP_BUTTON - 2, 5, down ? AureliaPalette.LINE : AureliaPalette.RAISED_HOVER);
		}
		context.drawTextWithShadow(textRenderer, label, x + (STEP_BUTTON - textRenderer.getWidth(label)) / 2 + 1, y + 5, AureliaChrome.labelColor(active));
	}

	/** The hint on the left and the one filled button, Done, on the right. */
	private void footer(DrawContext context, int mouseX, int mouseY) {
		final int y = footerY();
		context.fill(left() + PAD, y - 8, left() + PANEL_WIDTH - PAD, y - 7, AureliaPalette.LINE);
		final String hint = textRenderer.trimToWidth(Text.translatable("gui.aurelia.key_box.hint").getString(), PANEL_WIDTH - 3 * PAD - DONE_WIDTH);
		context.drawText(textRenderer, hint, left() + PAD, y + 5, AureliaPalette.TEXT_DIM, false);
		final boolean hovered = inside(mouseX, mouseY, doneX(), y, DONE_WIDTH, ROW_HEIGHT);
		AureliaChrome.rounded(context, doneX(), y, DONE_WIDTH, ROW_HEIGHT, 6, hovered ? AureliaChrome.mix(AureliaPalette.ACCENT, 0xFFFFFFFF, 0.12F) : AureliaPalette.ACCENT);
		final Text done = Text.translatable("gui.done");
		context.drawTextWithShadow(textRenderer, done, doneX() + (DONE_WIDTH - textRenderer.getWidth(done)) / 2, y + 5, AureliaPalette.TEXT);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || !ready()) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		for (int i = 0; i < keys.length; i++) {
			if (inside(mouseX, mouseY, slotX(i), cabinetTop(), slotWidth(), CABINET_HEIGHT)) {
				final Key key = keys[i];
				key.on = !key.on;
				key.hungAt = AureliaMotion.now();
				press(i);
				if (key.on) {
					play(SoundEvents.BLOCK_CHAIN_PLACE, 1.7F, 0.8F);
				} else {
					play(SoundEvents.BLOCK_CHAIN_HIT, 1.4F, 0.6F);
				}
				return true;
			}
		}
		if (inside(mouseX, mouseY, minusX(), stepY(), STEP_BUTTON, STEP_BUTTON) && timeout > STEPS[0]) {
			nudge(-1);
			return true;
		}
		if (inside(mouseX, mouseY, plusX(), stepY(), STEP_BUTTON, STEP_BUTTON) && timeout < STEPS[STEPS.length - 1]) {
			nudge(1);
			return true;
		}
		if (inside(mouseX, mouseY, doneX(), footerY(), DONE_WIDTH, ROW_HEIGHT)) {
			play(SoundEvents.UI_BUTTON_CLICK.value(), 1, 0.6F);
			close();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
		if (mouseY >= stepY() - 4 && mouseY < stepY() + STEP_BUTTON + 4) {
			nudge(amount > 0 ? 1 : -1);
			return true;
		}
		return super.onMouseScrolled(mouseX, mouseY, amount);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_3) {
			final Key key = keys[keyCode - GLFW.GLFW_KEY_1];
			key.on = !key.on;
			key.hungAt = AureliaMotion.now();
			play(key.on ? SoundEvents.BLOCK_CHAIN_PLACE : SoundEvents.BLOCK_CHAIN_HIT, key.on ? 1.7F : 1.4F, 0.7F);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD || keyCode == GLFW.GLFW_KEY_RIGHT) {
			nudge(1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT || keyCode == GLFW.GLFW_KEY_LEFT) {
			nudge(-1);
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	private void nudge(int direction) {
		final long next = step(timeout, direction);
		if (next != timeout) {
			timeout = next;
			press(direction > 0 ? 11 : 10);
			play(SoundEvents.UI_BUTTON_CLICK.value(), direction > 0 ? 1.15F : 0.95F, 0.35F);
		}
	}

	private void press(int id) {
		pressed = id;
		pressedAt = AureliaMotion.now();
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

	/** "1h 29m" or "29m", short enough for the line under a key. */
	private static Text shortDuration(long millis) {
		final long minutes = Math.max(1, (Math.max(0, millis) + 59_999) / 60000);
		return minutes < 60 ? Text.translatable("gui.aurelia.key_box.short_m", minutes)
				: Text.translatable("gui.aurelia.key_box.short_hm", minutes / 60, minutes % 60);
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
		final String shown = textRenderer.trimToWidth(text.getString(), maxWidth);
		context.drawText(textRenderer, shown, centreX - textRenderer.getWidth(shown) / 2, y, color, false);
	}

	private void play(SoundEvent sound, float pitch, float volume) {
		if (client != null) {
			client.getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
		}
	}

	private static boolean inside(double x, double y, int left, int top, int w, int h) {
		return x >= left && x < left + w && y >= top && y < top + h;
	}

	private static long[] minutes(long... values) {
		final long[] millis = new long[values.length];
		for (int i = 0; i < values.length; i++) {
			millis[i] = values[i] * 60000;
		}
		return millis;
	}
}
