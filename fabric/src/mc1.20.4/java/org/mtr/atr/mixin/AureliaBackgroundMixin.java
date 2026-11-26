package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the dirt background on a restyled screen with one flat fill. The dashboard does not call this. A screen
 * without a switch of its own keeps the world in view behind a dark scrim, as vanilla does, but flat and in the
 * Aurelia tone; with no world loaded it gets the opaque face.
 */
@Mixin(Screen.class)
public abstract class AureliaBackgroundMixin {

	@Shadow
	public int width;

	@Shadow
	public int height;

	@Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
	private void atr$flatBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!AureliaUi.active()) {
			return;
		}
		final MinecraftClient client = MinecraftClient.getInstance();
		final boolean world = client != null && client.world != null;
		context.fill(0, 0, this.width, this.height, world && AureliaUi.keepsWorldBehind((Screen) (Object) this) ? AureliaPalette.SCRIM : AureliaPalette.SURFACE);
		ci.cancel();
	}
}
