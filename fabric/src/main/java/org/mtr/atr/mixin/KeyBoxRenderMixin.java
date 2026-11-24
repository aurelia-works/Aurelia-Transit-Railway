package org.mtr.atr.mixin;

import org.mtr.aurelia.client.KeyBoxWorldRenderer;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.block.BlockDriverKeyDispenser;
import org.mtr.mod.render.RenderDriverKeyDispenser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * With the key box Look switch on, a driver key box in a depot shows its keys hanging on the hooks inside the cabinet
 * instead of MTR's keys spinning in the middle. Outside a depot MTR's red cross stays.
 */
@Mixin(value = RenderDriverKeyDispenser.class, remap = false)
public abstract class KeyBoxRenderMixin {

	@Inject(method = "render(Lorg/mtr/mod/block/BlockDriverKeyDispenser$BlockEntity;FLorg/mtr/mapping/mapper/GraphicsHolder;II)V", at = @At("HEAD"), cancellable = true)
	private void atr$hangKeys(BlockDriverKeyDispenser.BlockEntity blockEntity, float tickDelta, GraphicsHolder graphicsHolder, int light, int overlay, CallbackInfo ci) {
		if (AureliaUiSettings.enabled(AureliaUiSettings.ScreenId.KEY_BOX) && KeyBoxWorldRenderer.render(blockEntity, light)) {
			ci.cancel();
		}
	}
}
