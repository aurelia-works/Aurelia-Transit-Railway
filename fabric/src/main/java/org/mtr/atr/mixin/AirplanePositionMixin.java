package org.mtr.atr.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.tool.Utilities;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import org.mtr.mod.data.AirplanePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the airplane height lock. That lock was meant to stop the body pitching down the glideslope.
 * It holds every later bogie at the nose height, so the airframe hangs below the flight path and the
 * model clips the ground. {@link AirplanePath#sampledPosition} is the real path position, and it also
 * keeps a vehicle that has stopped on an old, unanchored repeat sentinel on the platform instead of at the origin.
 */
@Mixin(value = Vehicle.class, remap = false)
public abstract class AirplanePositionMixin {

	@Shadow
	public VehicleExtraData vehicleExtraData;

	@Inject(method = "getPosition", at = @At("RETURN"), cancellable = true)
	private void atr$keepBogieOnThePath(double value, DoubleArrayList overrideY, CallbackInfoReturnable<Vector> callback) {
		if (callback.getReturnValue() == null) {
			return;
		}
		final int index = Utilities.getIndexFromConditionalList(vehicleExtraData.immutablePath, value);
		final PathData pathData = Utilities.getElement(vehicleExtraData.immutablePath, index);
		if (pathData == null) {
			return;
		}
		final Vector position = AirplanePath.sampledPosition(vehicleExtraData.immutablePath, index, value - pathData.getStartDistance());
		if (position != null) {
			callback.setReturnValue(position);
		}
	}
}
