package org.mtr.atr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.PathData;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.Vehicle;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.engine.EngineFeature;
import org.mtr.mod.engine.EngineSettings;
import org.mtr.mod.engine.HillGrade;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trains climbing a hill go a little slower and pull away more gently (see {@link HillGrade}). The grade of the piece
 * of track the train is on is worked out at the start of each moving step, on the server and the client alike, so both
 * agree. Only trains: planes and boats keep MTR's speeds.
 */
@Mixin(value = Vehicle.class, remap = false)
public abstract class HillSlowdownMixin {

	@Unique
	private double atr$grade;

	@Inject(method = "simulateMoving", at = @At("HEAD"))
	private void atr$readGrade(long millisElapsed, ObjectArrayList<?> vehiclePositions, int currentIndex, CallbackInfo callback) {
		atr$grade = 0;
		final Vehicle vehicle = (Vehicle) (Object) this;
		if (vehicle.getTransportMode() != TransportMode.TRAIN || !EngineSettings.enabled(EngineFeature.HILL_SLOWDOWN)) {
			return;
		}
		final java.util.List<PathData> path = vehicle.vehicleExtraData.immutablePath;
		if (currentIndex >= 0 && currentIndex < path.size()) {
			final PathData pathData = path.get(currentIndex);
			final PathDataSchemaAccessor accessor = (PathDataSchemaAccessor) pathData;
			if (accessor.atr$startPosition() != null && accessor.atr$endPosition() != null) {
				atr$grade = HillGrade.grade(accessor.atr$startPosition().getY(), accessor.atr$endPosition().getY(), pathData.getRailLength());
			}
		}
	}

	/**
	 * A wrap, not a redirect: add-ons such as MAGIC redirect this same call, and two redirects on one call crash the
	 * game. MixinExtras applies a wrap after the redirects, so it multiplies whatever value they return.
	 */
	@WrapOperation(method = "simulateMoving", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/PathData;getSpeedLimitMetersPerMillisecond()D"))
	private double atr$slowerUphill(PathData pathData, Operation<Double> original) {
		return original.call(pathData) * HillGrade.speedFactor(atr$grade);
	}

	@WrapOperation(method = "simulateMoving", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/VehicleExtraData;getAcceleration()D"))
	private double atr$gentlerUphill(VehicleExtraData vehicleExtraData, Operation<Double> original) {
		return original.call(vehicleExtraData) * HillGrade.accelerationFactor(atr$grade);
	}
}
