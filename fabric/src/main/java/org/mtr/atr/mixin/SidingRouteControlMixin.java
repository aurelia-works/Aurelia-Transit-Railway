package org.mtr.atr.mixin;

import org.mtr.aurelia.route.RouteControlPlan;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Siding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two points in MTR's siding path pipeline. Before the freshly generated depot path is copied into each siding's
 * trains, turn the throw-away waypoint segments into ordinary track (no dwell, no platform id, previous stop index).
 * While MTR later matches stop times by platform id, point the route stops at the chosen/fallback platforms so the
 * times, trips and durations land on the platform the path actually serves.
 */
@Mixin(value = Siding.class, remap = false)
public abstract class SidingRouteControlMixin {

	@Inject(method = "generateRoute", at = @At("HEAD"))
	private void atr$fixWaypointSegments(Platform firstPlatform, Platform lastPlatform, int stopIndex, long cruisingAltitude, CallbackInfo callback) {
		final Depot depot = ((Siding) (Object) this).area;
		if (depot != null) {
			RouteControlPlan.fixPath(depot);
		}
	}

	/** MTR matches stop times by platform id; use the chosen/fallback platform while it builds them. */
	@Inject(method = "generatePathDistancesAndTimeSegments", at = @At("HEAD"))
	private void atr$applyPlatformOverrides(CallbackInfo callback) {
		final Depot depot = ((Siding) (Object) this).area;
		if (depot != null) {
			RouteControlPlan.applyOverrides(depot);
		}
	}

	@Inject(method = "generatePathDistancesAndTimeSegments", at = @At("RETURN"))
	private void atr$restorePlatformOverrides(CallbackInfo callback) {
		RouteControlPlan.restoreOverrides();
	}
}
