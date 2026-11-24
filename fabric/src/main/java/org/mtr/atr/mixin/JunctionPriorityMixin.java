package org.mtr.atr.mixin;

import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Rail;
import org.mtr.core.data.Vehicle;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntIterator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.mod.engine.EngineFeature;
import org.mtr.mod.engine.EngineSettings;
import org.mtr.mod.engine.GiveWay;
import org.mtr.mod.engine.JunctionPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Express trains go first at junctions (see {@link JunctionPriority}). Plain loops only: lambdas merged into an engine
 * class renumber its own.
 */
@Mixin(value = Vehicle.class, remap = false)
public abstract class JunctionPriorityMixin {

	@Unique
	private final GiveWay atr$giveWay = new GiveWay();

	/** An express train claims the track it will cover soon. */
	@Inject(method = "simulate", at = @At("HEAD"))
	private void atr$claimTrackAhead(long millisElapsed, ObjectArrayList<?> vehiclePositions, Long2LongAVLTreeMap vehicleTimesAlongRoute, CallbackInfo callback) {
		final Vehicle vehicle = (Vehicle) (Object) this;
		final Data data = ((NameColorDataBaseSchemaAccessor) (Object) vehicle).atr$data();
		if (!(data instanceof Simulator)) {
			return;
		}
		if (!EngineSettings.enabled(EngineFeature.JUNCTION_PRIORITY) || !vehicle.getIsOnRoute() || !atr$isPriority(vehicle, data)) {
			JunctionPriority.release(vehicle.getId());
			return;
		}
		final List<PathData> path = vehicle.vehicleExtraData.immutablePath;
		final VehicleSchemaAccessor accessor = (VehicleSchemaAccessor) vehicle;
		final double railProgress = accessor.atr$railProgress();
		final double speed = accessor.atr$speed();
		final int index = Utilities.getIndexFromConditionalList(path, railProgress);
		if (speed <= 0 && index >= 1 && index - 1 < path.size() && path.get(index - 1).getDwellTime() - accessor.atr$elapsedDwellTime() > JunctionPriority.DWELL_CLAIM_MILLIS) {
			// still a long wait at the platform: let other trains use the junction meanwhile
			JunctionPriority.release(vehicle.getId());
			return;
		}
		final double end = railProgress + JunctionPriority.lookAheadMeters(speed);
		final ObjectOpenHashSet<Rail> rails = new ObjectOpenHashSet<>();
		for (int i = Math.max(0, index); i < path.size() && path.get(i).getStartDistance() < end; i++) {
			rails.add(path.get(i).getRail());
		}
		JunctionPriority.claim(vehicle.getId(), rails, System.currentTimeMillis());
	}

	/** A slower train treats a signal block an express has claimed as red, unless it can no longer stop for it. */
	@Inject(method = "checkAndBlockSignal", at = @At("HEAD"), cancellable = true)
	private void atr$giveWayToExpress(int currentIndex, ObjectArrayList<?> vehiclePositions, boolean reserveRail, boolean secondPass, CallbackInfoReturnable<Boolean> callback) {
		if (secondPass || !EngineSettings.enabled(EngineFeature.JUNCTION_PRIORITY)) {
			return;
		}
		final Vehicle vehicle = (Vehicle) (Object) this;
		final Data data = ((NameColorDataBaseSchemaAccessor) (Object) vehicle).atr$data();
		final List<PathData> path = vehicle.vehicleExtraData.immutablePath;
		if (!(data instanceof Simulator) || currentIndex < 0 || currentIndex >= path.size()) {
			return;
		}
		final IntAVLTreeSet signalColors = path.get(currentIndex).getSignalColors();
		if (signalColors.isEmpty() || atr$isPriority(vehicle, data)) {
			return;
		}
		final VehicleSchemaAccessor accessor = (VehicleSchemaAccessor) vehicle;
		final double railProgress = accessor.atr$railProgress();
		final double speed = accessor.atr$speed();
		final double stoppingDistance = 0.5 * speed * speed / vehicle.vehicleExtraData.getDeceleration();
		final ObjectArrayList<Rail> blockRails = new ObjectArrayList<>();
		for (int i = currentIndex; i < path.size() && atr$overlaps(path.get(i).getSignalColors(), signalColors); i++) {
			blockRails.add(path.get(i).getRail());
		}
		final int occupied = Utilities.getIndexFromConditionalList(path, GiveWay.occupiedProgress(railProgress));
		final Rail ownRail = occupied >= 0 && occupied < path.size() ? path.get(occupied).getRail() : null;
		final long now = System.currentTimeMillis();
		final boolean claimed = JunctionPriority.claimedByOther(vehicle.getId(), blockRails, ownRail, now);
		if (atr$giveWay.hold(currentIndex, path.get(currentIndex).getStartDistance() - railProgress, stoppingDistance, claimed, now)) {
			callback.setReturnValue(true);
		}
	}

	@Unique
	private static boolean atr$isPriority(Vehicle vehicle, Data data) {
		return JunctionPriority.isPriority(data.routeIdMap.get(vehicle.vehicleExtraData.getThisRouteId()));
	}

	@Unique
	private static boolean atr$overlaps(IntAVLTreeSet colors, IntAVLTreeSet other) {
		final IntIterator iterator = colors.iterator();
		while (iterator.hasNext()) {
			if (other.contains(iterator.nextInt())) {
				return true;
			}
		}
		return false;
	}
}
