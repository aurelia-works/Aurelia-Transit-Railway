package org.mtr.atr.mixin;

import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.mod.screen.DashboardScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The dashboard's left panel uses AureliaMap's background instead of MTR's {@code #121212}. */
@Mixin(value = DashboardScreen.class, remap = false)
public abstract class AureliaDashboardMixin {

	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GuiDrawing;drawRectangle(DDDDI)V"), index = 4)
	private int atr$panelColour(int color) {
		if (color == 0xFF121212 && AureliaUiSettings.enabled(AureliaUiSettings.ScreenId.DASHBOARD)) {
			return AureliaPalette.BG;
		}
		return color;
	}
}
