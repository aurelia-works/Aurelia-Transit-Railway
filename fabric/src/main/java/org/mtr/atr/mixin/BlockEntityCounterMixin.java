package org.mtr.atr.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.mtr.mod.render.BlockEntityCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts block entities drawn per frame for the Aurelia panel's live counter. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityCounterMixin {

	@Inject(method = "render", at = @At("HEAD"))
	private void atr$countBlockEntity(BlockEntity blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo callbackInfo) {
		BlockEntityCounter.draw();
	}
}
