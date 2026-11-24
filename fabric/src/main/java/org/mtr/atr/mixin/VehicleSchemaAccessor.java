package org.mtr.atr.mixin;

import org.mtr.core.generated.data.VehicleSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far into its stop a train is (boarding hold), and where and how fast it is (junction priority). */
@Mixin(value = VehicleSchema.class, remap = false)
public interface VehicleSchemaAccessor {

	@Accessor("elapsedDwellTime")
	long atr$elapsedDwellTime();

	@Accessor("railProgress")
	double atr$railProgress();

	@Accessor("speed")
	double atr$speed();
}
