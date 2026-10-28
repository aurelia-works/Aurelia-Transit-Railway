package org.mtr.mod.client;

import org.mtr.mod.config.Config;

public final class RainInsideVehicle {

	private RainInsideVehicle() {
	}

	/**
	 * Vanilla decides what is "under cover" from the block heightmap, which a vehicle is not part of.
	 *
	 * @param ridingAnyVehicle whether the client player is currently riding inside an MTR vehicle
	 * @param switchEnabled    the "Hide rain inside vehicles" client config switch
	 */
	public static boolean shouldHideRain(boolean ridingAnyVehicle, boolean switchEnabled) {
		return ridingAnyVehicle && switchEnabled;
	}

	/**
	 * Called from the weather mixin on the render thread.
	 */
	public static boolean shouldHideRainNow() {
		return shouldHideRain(VehicleRidingMovement.isRidingAnyVehicle(), Config.getClient().getHideRainInsideVehicles());
	}
}
