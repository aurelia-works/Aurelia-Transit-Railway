package org.mtr.atr.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.mtr.aurelia.driving.DriverClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops MTR's "Speed: x m/s" action-bar line while ATR's cab HUD is up: the HUD already shows the speed, and the
 * action-bar row sits over the HUD's desk panel. Every other action-bar message, and the line when not driving, is kept.
 */
@Mixin(InGameHud.class)
public abstract class DriverSpeedMessageMixin {

	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void atr$dropSpeedWhileDriving(Text message, boolean tinted, CallbackInfo callback) {
		if (DriverClient.isDriving() && message.getContent() instanceof TranslatableTextContent content && "gui.mtr.vehicle_speed".equals(content.getKey())) {
			callback.cancel();
		}
	}
}
