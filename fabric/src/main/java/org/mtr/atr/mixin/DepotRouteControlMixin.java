package org.mtr.atr.mixin;

import org.mtr.aurelia.route.RouteControlPlan;
import org.mtr.core.data.Depot;
import org.mtr.core.data.Route;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds route control into MTR's own depot path generation without touching the core jar. The platform list is
 * rebuilt after MTR fills it (chosen platform or a free one when the planned platform is taken), and the per-pair
 * finders MTR builds are replaced with the same chain plus waypoint hops. With no route control the rebuilt list and
 * finders are equivalent to MTR's own, so old worlds behave as before.
 */
@Mixin(value = Depot.class, remap = false)
public abstract class DepotRouteControlMixin {

	@Shadow
	@Final
	private ObjectArrayList platformsInRoute;

	@Shadow
	@Final
	private ObjectArrayList sidingPathFinders;

	@Shadow
	@Final
	private LongAVLTreeSet generatingSidingIds;

	@Inject(method = "writeRouteCache", at = @At("RETURN"))
	private void atr$applyRouteControl(Long2ObjectOpenHashMap<Route> routeIdMap, CallbackInfo callback) {
		// While a generation is in flight (main path or siding paths), leave its plan and throw-away ids alone;
		// it is replaced at the next explicit generation, and a sync in between must not make the running finders
		// unrecognisable or point the stop times at different platforms.
		if (sidingPathFinders.isEmpty() && generatingSidingIds.isEmpty()) {
			RouteControlPlan.refresh((Depot) (Object) this, platformsInRoute, false);
		}
	}

	/**
	 * Runs on the {@code sidingPathFinders.clear()} call, just before the loop that builds the plain platform pairs.
	 * Intercepting the call keeps the private {@code OnGenerationComplete} parameter out of the mixin signature; the
	 * receiver check sorts it from MTR's earlier {@code path.clear()}.
	 */
	@Redirect(method = "generateMainRoute", at = @At(value = "INVOKE", target = "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;clear()V"))
	private void atr$refreshBeforeGeneration(ObjectArrayList list) {
		if (list == sidingPathFinders) {
			RouteControlPlan.refresh((Depot) (Object) this, platformsInRoute, true);
		}
		list.clear();
	}

	/** The last plain finder added means the pairs are complete; swap in the waypoint chain. */
	@Redirect(method = "generateMainRoute", at = @At(value = "INVOKE", target = "Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;add(Ljava/lang/Object;)Z"))
	private boolean atr$addWaypointFinders(ObjectArrayList list, Object finder) {
		final boolean added = list.add(finder);
		if (list == sidingPathFinders && sidingPathFinders.size() == platformsInRoute.size() - 1) {
			RouteControlPlan.applyWaypoints((Depot) (Object) this, sidingPathFinders);
		}
		return added;
	}
}
