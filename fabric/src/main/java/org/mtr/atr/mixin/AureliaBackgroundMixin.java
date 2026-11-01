package org.mtr.atr.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the dirt background on a restyled screen with one flat fill. The dashboard does not call this. */
@Mixin(Screen.class)
public abstract class AureliaBackgroundMixin {

	@Shadow
	public int width;

	@Shadow
	public int height;

	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true)
	private void atr$flatBackground(DrawContext context, CallbackInfo ci) {
		if (!AureliaUi.active()) {
			return;
		}
		context.fill(0, 0, this.width, this.height, AureliaPalette.BG);
		ci.cancel();
	}
}
