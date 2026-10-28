package org.mtr.mod.mixin;

import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.shader.ShaderManager;
import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mod.render.ShaderBatchFastPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the start and end of each MTR material batch for {@link ShaderBatchFastPath}. The batching lives in the Minecraft-Mappings library. */
@Mixin(value = ShaderManager.class, remap = false)
public abstract class ShaderManagerMixin {

	@Inject(method = "setupShaderBatchState", at = @At("TAIL"))
	private void atr$beginBatch(MaterialProperties materialProperties, CallbackInfo ci) {
		// MTR's own shaders (no shader pack) never re-bind per draw, so there is nothing to speed up
		if (!Utilities.canUseCustomShader()) {
			ShaderBatchFastPath.beginBatch();
		}
	}

	@Inject(method = "cleanupShaderBatchState", at = @At("HEAD"))
	private void atr$endBatch(CallbackInfo ci) {
		ShaderBatchFastPath.endBatch();
	}
}
