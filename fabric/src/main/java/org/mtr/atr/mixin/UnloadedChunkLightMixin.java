/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.atr.mixin;

import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ClientWorld;
import org.mtr.mapping.holder.LightType;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Light in chunks the client has not loaded. MTR reads the light for rails, vehicle cars and gangways through this one
 * method ({@code RenderRails}, {@code PositionAndRotation}, {@code RenderVehicles}). Past Minecraft's render distance
 * there is no light data, so a far train box (a bus on a road's hidden lane track, say) came out dark at noon. With the
 * far view on, an unloaded chunk answers as open sky instead. Ported from Stratos Transit's
 * {@code ClientWorldHolderMixin}; ATR's own far rail pass already falls back to the camera's light and is unaffected.
 */
@Mixin(value = ClientWorld.class, remap = false)
public abstract class UnloadedChunkLightMixin {

	@Inject(method = "getLightLevel", at = @At("HEAD"), cancellable = true)
	private void atr$lightInUnloadedChunks(LightType lightType, BlockPos blockPos, CallbackInfoReturnable<Integer> callbackInfoReturnable) {
		final Client client = Config.getClient();
		if (!client.getFarDetail() || client.getTrainViewChunks() <= 0 && client.getRailViewChunks() <= 0) {
			return;
		}
		final net.minecraft.client.world.ClientWorld world = ((ClientWorld) (Object) this).data;
		final net.minecraft.util.math.BlockPos pos = blockPos.data;
		if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
			callbackInfoReturnable.setReturnValue(lightType == LightType.SKY && world.getDimension().hasSkyLight() ? 15 : 0);
		}
	}
}
