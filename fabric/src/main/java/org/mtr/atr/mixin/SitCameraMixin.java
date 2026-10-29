package org.mtr.atr.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.mtr.aurelia.client.RideClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lowers the camera with the body while the local player sits on a train or bus.
 */
@Mixin(Camera.class)
public abstract class SitCameraMixin {

	@Shadow
	public abstract Vec3d getPos();

	@Shadow
	protected abstract void setPos(Vec3d pos);

	@Inject(method = "update", at = @At("TAIL"))
	private void atr$drop(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo callbackInfo) {
		if (focusedEntity == MinecraftClient.getInstance().player && RideClient.isSitting(focusedEntity.getUuid())) {
			setPos(getPos().add(0, -RideClient.sitDrop(), 0));
		}
	}
}
