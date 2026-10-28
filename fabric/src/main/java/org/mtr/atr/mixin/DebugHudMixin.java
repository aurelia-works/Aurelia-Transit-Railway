package org.mtr.atr.mixin;

import net.minecraft.client.gui.hud.DebugHud;
import org.mtr.mod.render.MtrFrameStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** MTR's draw calls and CPU time on the right side of the F3 screen. */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

	@Inject(method = "getRightText", at = @At("RETURN"))
	private void atr$addPerfLines(CallbackInfoReturnable<List<String>> cir) {
		MtrFrameStats.INSTANCE.addLines(cir.getReturnValue());
	}
}
