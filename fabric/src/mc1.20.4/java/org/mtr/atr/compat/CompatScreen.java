package org.mtr.atr.compat;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** The Minecraft 1.20.4 side of {@code src/mc1.20.1/java}'s {@code CompatScreen}: the vertical wheel amount goes on. */
public abstract class CompatScreen extends Screen {

	protected CompatScreen(Text title) {
		super(title);
	}

	@Override
	public final boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		return onMouseScrolled(mouseX, mouseY, verticalAmount);
	}

	/** Vanilla's handling: the hovered child gets the turn. */
	protected boolean onMouseScrolled(double mouseX, double mouseY, double amount) {
		return super.mouseScrolled(mouseX, mouseY, 0, amount);
	}
}
