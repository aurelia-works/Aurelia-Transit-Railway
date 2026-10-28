package org.mtr.mod.mixin;

import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mod.render.MtrFrameStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts MTR's draw calls for the F3 lines. The draw lives in the Minecraft-Mappings library. */
@Mixin(value = VertexArray.class, remap = false)
public abstract class VertexArrayMixin {

	@Inject(method = "draw", at = @At("HEAD"))
	private void atr$countDraw(CallbackInfo ci) {
		MtrFrameStats.INSTANCE.draw();
	}
}
