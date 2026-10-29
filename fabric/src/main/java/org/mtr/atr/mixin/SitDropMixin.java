package org.mtr.atr.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.math.Vec3d;
import org.mtr.aurelia.client.RideClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lowers a sitting rider's body by the panel's sit height, so the seated pose sits on a seat instead of in the air.
 */
@Mixin(PlayerEntityRenderer.class)
public abstract class SitDropMixin {

	@Inject(method = "getPositionOffset(Lnet/minecraft/client/network/AbstractClientPlayerEntity;F)Lnet/minecraft/util/math/Vec3d;", at = @At("RETURN"), cancellable = true)
	private void atr$drop(AbstractClientPlayerEntity player, float tickDelta, CallbackInfoReturnable<Vec3d> callbackInfo) {
		if (RideClient.isSitting(player.getUuid())) {
			callbackInfo.setReturnValue(callbackInfo.getReturnValue().add(0, -RideClient.sitDrop(), 0));
		}
	}
}
