package org.mtr.atr.compat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * A screen that takes mouse-wheel turns through {@link #onMouseScrolled}. Minecraft 1.20.4 adds a horizontal amount to
 * {@code mouseScrolled}, which ATR's screens do not use; the 1.20.4 side of this class passes the vertical amount on.
 */
public abstract class CompatScreen extends Screen {

	protected CompatScreen(Text title) {
		super(title);
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		return onMouseScrolled(mouseX, mouseY, amount);
	}

	/** Vanilla's handling: the hovered child gets the turn. */
	protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
		return super.mouseScrolled(mouseX, mouseY, amount);
	}
}
