package org.mtr.atr.mixin;

import org.mtr.aurelia.route.RouteControlPlan;
import org.mtr.core.data.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/**
 * MTR rejects any intermediate path with fewer than two rails. A waypoint hop is exactly one rail (the chosen
 * waypoint rail), so when the pending finder chain includes one of those throw-away platforms this takes over the
 * same five-millisecond tick loop and accepts a single-rail hop. Everything else behaves like the core method.
 */
@Mixin(value = SidingPathFinder.class, remap = false)
public abstract class SidingPathFinderRouteControlMixin {

	private static final long TICK_BUDGET_MILLIS = 5;

	@Inject(method = "findPathTick", at = @At("HEAD"), cancellable = true)
	private static void atr$findPathTick(ObjectArrayList path, ObjectArrayList sidingPathFinders, long cruisingAltitude, Runnable callbackSuccess, BiConsumer callbackFail, CallbackInfo callback) {
		if (sidingPathFinders.isEmpty()) {
			return;
		}

		boolean hasWaypointHop = false;
		for (int i = 0; i < sidingPathFinders.size(); i++) {
			final SidingPathFinder<?, ?, ?, ?> finder = (SidingPathFinder<?, ?, ?, ?>) sidingPathFinders.get(i);
			if (isWaypointHop(finder)) {
				hasWaypointHop = true;
				break;
			}
		}
		if (!hasWaypointHop) {
			return;
		}
		callback.cancel();

		final long startMillis = System.currentTimeMillis();
		while (System.currentTimeMillis() - startMillis < TICK_BUDGET_MILLIS) {
			final SidingPathFinder<?, ?, ?, ?> finder = (SidingPathFinder<?, ?, ?, ?>) sidingPathFinders.get(0);
			final ObjectArrayList<PathData> tempPath = ((SidingPathFinderAccessor) (Object) finder).atr$tick(cruisingAltitude);

			if (tempPath != null) {
				final boolean waypointHop = isWaypointHop(finder);
				if (tempPath.size() < (waypointHop ? 1 : 2)) {
					sidingPathFinders.clear();
					path.clear();
					RouteControlPlan.failed(finder.startSavedRail.getId());
					RouteControlPlan.failed(finder.endSavedRail.getId());
					callbackFail.accept(finder.startSavedRail, finder.endSavedRail);
					return;
				}

				if (SidingPathFinder.overlappingPaths(path, tempPath)) {
					tempPath.remove(0);
				}
				path.addAll(tempPath);
				sidingPathFinders.remove(0);
				if (sidingPathFinders.isEmpty()) {
					callbackSuccess.run();
					return;
				}
			}
		}
	}

	private static boolean isWaypointHop(SidingPathFinder<?, ?, ?, ?> finder) {
		return RouteControlPlan.isWaypointPlatform(finder.startSavedRail.getId()) || RouteControlPlan.isWaypointPlatform(finder.endSavedRail.getId());
	}
}
