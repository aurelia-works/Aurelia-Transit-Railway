package org.mtr.atr.mixin;

import org.mtr.core.generated.data.VehicleSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** How far into its stop a train is, for the boarding hold. */
@Mixin(value = VehicleSchema.class, remap = false)
public interface VehicleSchemaAccessor {

	@Accessor("elapsedDwellTime")
	long atr$elapsedDwellTime();
}
