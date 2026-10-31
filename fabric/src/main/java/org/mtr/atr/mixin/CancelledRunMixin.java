package org.mtr.atr.mixin;

import org.mtr.core.data.Siding;
import org.mtr.mod.data.DepotSchedulesSync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A cancelled run (one departure of one siding, until a set time) does not leave the depot. Its arrivals show
 * "Cancelled" through {@link LiveDestinationMixin}, which owns the one rewrite of the arrival destination.
 */
@Mixin(value = Siding.class, remap = false)
public abstract class CancelledRunMixin {

	@Inject(method = "matchDeparture", at = @At("RETURN"), cancellable = true)
	private void atr$skipCancelledRun(CallbackInfoReturnable<Integer> callback) {
		final int departureIndex = callback.getReturnValueI();
		if (departureIndex >= 0 && DepotSchedulesSync.isCancelled(((Siding) (Object) this).getId(), departureIndex, System.currentTimeMillis())) {
			callback.setReturnValue(-1);
		}
	}
}
