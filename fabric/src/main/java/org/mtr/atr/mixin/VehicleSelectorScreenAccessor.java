package org.mtr.atr.mixin;

import org.mtr.core.data.Siding;
import org.mtr.mod.screen.VehicleSelectorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hands the siding MTR's train selector was opened for to ATR's train picker. */
@Mixin(value = VehicleSelectorScreen.class, remap = false)
public interface VehicleSelectorScreenAccessor {

	@Accessor("siding")
	Siding atr$siding();
}
