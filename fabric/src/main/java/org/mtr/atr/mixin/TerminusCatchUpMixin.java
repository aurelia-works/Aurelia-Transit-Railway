package org.mtr.atr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Vehicle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.engine.EngineFeature;
import org.mtr.mod.engine.EngineSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A late train at the last stop of its route uses the whole layover to get back on time, instead of only the share of
 * it the siding allows elsewhere. Earlier stops keep MTR's own catch-up.
 */
@Mixin(value = Vehicle.class, remap = false)
public abstract class TerminusCatchUpMixin {

	/** Copied from the engine: doors take this long to move, and start this long after the train stops. */
	@Unique
	private static final long DOOR_MOVE_TIME = 3200;
	@Unique
	private static final long DOOR_DELAY = 1000;

	@Unique
	private long atr$doorCloseTime;

	@Inject(method = "simulateStopped", at = @At("HEAD"))
	private void atr$readDwell(long millisElapsed, ObjectArrayList<?> vehiclePositions, int currentIndex, CallbackInfo callback) {
		final List<PathData> path = ((Vehicle) (Object) this).vehicleExtraData.immutablePath;
		final long totalDwell = currentIndex >= 1 && currentIndex - 1 < path.size() ? path.get(currentIndex - 1).getDwellTime() : 0;
		atr$doorCloseTime = Math.max(totalDwell / 2, totalDwell - DOOR_MOVE_TIME - DOOR_DELAY);
	}

	/**
	 * The first {@code Math.min(long, long)} in {@code simulateStopped}: how much of a late train's delay this stop takes back.
	 * A wrap, not a redirect: MAGIC (jme) redirects the same call, and two redirects on one call crash the game at launch.
	 * A wrap runs around another mod's redirect, so both apply.
	 */
	@WrapOperation(method = "simulateStopped", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(JJ)J", ordinal = 0))
	private long atr$catchUpAtTerminus(long deviation, long allowed, Operation<Long> original) {
		final Vehicle vehicle = (Vehicle) (Object) this;
		if (EngineSettings.enabled(EngineFeature.TIMETABLE) && vehicle.vehicleExtraData.getIsTerminating()) {
			final long elapsed = ((VehicleSchemaAccessor) vehicle).atr$elapsedDwellTime();
			return original.call(deviation, Math.max(allowed, atr$doorCloseTime - elapsed));
		}
		return original.call(deviation, allowed);
	}
}
