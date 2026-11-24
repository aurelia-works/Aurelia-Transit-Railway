package org.mtr.aurelia.dashboard;

import net.minecraft.client.gui.screen.Screen;

import javax.annotation.Nullable;

/**
 * Exposes the parent screen of panel screens ({@code AureliaPanelScreen}, {@code JumpScreen})
 * so the unified Transit Dashboard can return directly to an already-open dashboard ancestor or capture camera state
 * and dispose {@code WorldMap} tile textures when replacing it.
 */
public interface DashboardParentAccess {

	@Nullable
	Screen atr$parentScreen();
}
