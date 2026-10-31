package org.mtr.atr.mixin;

import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.Siding;
import org.mtr.mod.data.DepotSchedules;
import org.mtr.mod.data.DepotSchedulesSync;
import org.mtr.mod.data.LiveDestination;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Arrivals take their destination from the route as it is now, not the copy the depot wrote into the timetable at its
 * last path generation (MTR #1010), and a cancelled run reads "Cancelled". The target is the lambda inside
 * {@code Siding.getArrivals}.
 */
@Mixin(value = Siding.class, remap = false)
public abstract class LiveDestinationMixin {

	@ModifyArg(method = "lambda$getArrivals$13", at = @At(value = "INVOKE", target = "Lorg/mtr/core/operation/ArrivalResponse;<init>(Ljava/lang/String;JJJZJILorg/mtr/core/data/Route;Lorg/mtr/core/data/Platform;)V"), index = 0)
	private String atr$liveDestination(String destination, long arrival, long departure, long deviation, boolean realtime, long departureIndex, int stopIndex, Route route, Platform platform) {
		// One handler for both rewrites, so a cancelled run reads "Cancelled" whatever order mixins apply in
		if (DepotSchedulesSync.isCancelled(((Siding) (Object) this).getId(), departureIndex, arrival)) {
			return DepotSchedules.CANCELLED;
		}
		return LiveDestination.forArrival(destination, route, stopIndex);
	}
}
