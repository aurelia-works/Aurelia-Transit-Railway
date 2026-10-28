package org.mtr.atr.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import org.mtr.mod.client.RainInsideVehicle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla weather treats a spot as covered only if a block is above it (Heightmap MOTION_BLOCKING). Vehicles are not blocks, so skip the rain, splashes and rain sound while riding.
 */
@Mixin(WorldRenderer.class)
public abstract class WeatherInsideVehicleMixin {

	@Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
	private void atr$hideRain(LightmapTextureManager manager, float tickDelta, double cameraX, double cameraY, double cameraZ, CallbackInfo callbackInfo) {
		if (RainInsideVehicle.shouldHideRainNow()) {
			callbackInfo.cancel();
		}
	}

	@Inject(method = "tickRainSplashing", at = @At("HEAD"), cancellable = true)
	private void atr$hideRainSplashing(Camera camera, CallbackInfo callbackInfo) {
		if (RainInsideVehicle.shouldHideRainNow()) {
			callbackInfo.cancel();
		}
	}
}
