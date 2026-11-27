package org.mtr.atr.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.mtr.core.data.Data;
import org.mtr.core.data.Depot;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.mod.engine.ClockFace;
import org.mtr.mod.engine.EngineFeature;
import org.mtr.mod.engine.EngineSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * A depot running on in-game frequencies gets departures at the same minutes past every hour (see {@link ClockFace}).
 * The list MTR built is swapped just before MTR sorts it and hands it to the sidings. Real-time departures typed in by
 * hand, and lifts and cable cars, are left as they are.
 */
@Mixin(value = Depot.class, remap = false)
public abstract class ClockFaceTimetableMixin {

	/**
	 * The second {@code Collections.sort} in the method sorts the departures (the first sorts the sidings).
	 * A wrap, not a redirect: MAGIC (jme) changes the argument of the same call, which fails at launch once a redirect has
	 * replaced the call. A wrap runs after MAGIC's change and gets its list.
	 */
	@WrapOperation(method = "generatePlatformDirectionsAndWriteDeparturesToSidings", at = @At(value = "INVOKE", target = "Ljava/util/Collections;sort(Ljava/util/List;)V", ordinal = 1))
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void atr$clockFace(List list, Operation<Void> original) {
		final Depot depot = (Depot) (Object) this;
		final Data data = ((NameColorDataBaseSchemaAccessor) (Object) depot).atr$data();
		if (list instanceof LongArrayList departures && data instanceof Simulator simulator && simulator.getGameMillisPerDay() > 0
				&& !depot.getUseRealTime() && !depot.getTransportMode().continuousMovement && EngineSettings.enabled(EngineFeature.TIMETABLE)) {
			// A plain loop, not a lambda: lambdas merged into an engine class renumber its own.
			final long[] frequencies = new long[24];
			for (int hour = 0; hour < frequencies.length; hour++) {
				frequencies[hour] = depot.getFrequency(simulator.isTimeMoving() ? hour : simulator.getHour());
			}
			departures.clear();
			departures.addAll(LongArrayList.wrap(ClockFace.departures(frequencies, simulator.getMillisOfGameMidnight(), simulator.getGameMillisPerDay())));
		}
		original.call(list);
	}
}
