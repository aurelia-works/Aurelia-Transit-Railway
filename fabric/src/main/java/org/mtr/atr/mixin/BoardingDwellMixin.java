package org.mtr.atr.mixin;

import org.mtr.core.data.Vehicle;
import org.mtr.mod.engine.EngineFeature;
import org.mtr.mod.engine.EngineSettings;
import org.mtr.mod.passenger.BoardingHold;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A stopped train whose passengers are still getting on or off keeps its doors open: its dwell clock waits just short
 * of the moment the doors would start to close, for at most {@link BoardingHold#MAX_HOLD_MILLIS} a stop. A stop with
 * enough time already runs exactly as before. The train leaves a little late and MTR's own catch-up takes it from there.
 */
@Mixin(value = Vehicle.class, remap = false)
public abstract class BoardingDwellMixin {

	/** Copied from the engine: the doors take this long to move, and start this long after the train stops. */
	@Unique
	private static final long DOOR_MOVE_TIME = 3200;
	@Unique
	private static final long DOOR_DELAY = 1000;

	@Unique
	private long atr$heldMillis;

	/** The second {@code Math.min(long, long)} in {@code simulateStopped}: {@code elapsedDwellTime = min(elapsed + step, totalDwell)}. */
	@Redirect(method = "simulateStopped", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(JJ)J", ordinal = 1))
	private long atr$holdForBoarding(long next, long totalDwellMillis) {
		final long result = Math.min(next, totalDwellMillis);
		final Vehicle vehicle = (Vehicle) (Object) this;
		final long elapsed = ((VehicleSchemaAccessor) vehicle).atr$elapsedDwellTime();
		if (elapsed <= DOOR_DELAY) {
			atr$heldMillis = 0;
		}
		if (totalDwellMillis <= 0 || !EngineSettings.enabled(EngineFeature.BOARDING_DWELL) || !BoardingHold.stillBoarding(vehicle.getId())) {
			return result;
		}
		final long doorCloseTime = Math.max(totalDwellMillis / 2, totalDwellMillis - DOOR_MOVE_TIME - DOOR_DELAY);
		final long holdAt = doorCloseTime - 1;
		if (elapsed <= holdAt && result > holdAt && atr$heldMillis < BoardingHold.MAX_HOLD_MILLIS) {
			atr$heldMillis += result - holdAt;
			return holdAt;
		}
		return result;
	}
}
