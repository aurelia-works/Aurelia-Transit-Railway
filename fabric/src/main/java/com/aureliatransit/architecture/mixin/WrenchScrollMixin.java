package com.aureliatransit.architecture.mixin;

import com.aureliatransit.architecture.client.WrenchClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sneak + mouse wheel switches the one wrench's mode instead of scrolling the hotbar when a wrench is in hand. */
@Mixin(Mouse.class)
public abstract class WrenchScrollMixin {

	@Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
	private void atr$wrenchMode(long window, double horizontal, double vertical, CallbackInfo ci) {
		if (WrenchClient.onScroll(vertical)) {
			ci.cancel();
		}
	}
}
