package org.mtr.atr.mixin;

import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.mtr.aurelia.client.RideClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player sitting on a train or bus ({@link RideClient}) gets vanilla's seated legs. MTR riders are not on a vehicle
 * entity, so vanilla never sets {@code riding} for them.
 */
@Mixin(BipedEntityModel.class)
public abstract class SitWhileRidingMixin<T extends LivingEntity> {

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
	private void atr$sit(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch, CallbackInfo callbackInfo) {
		if (entity instanceof PlayerEntity && RideClient.isSitting(entity.getUuid())) {
			((BipedEntityModel<?>) (Object) this).riding = true;
		}
	}
}
