package org.mtr.atr.mixin;

import org.mtr.core.data.Data;
import org.mtr.core.generated.data.NameColorDataBaseSchema;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The world data a depot belongs to, for the clock-face timetable. */
@Mixin(value = NameColorDataBaseSchema.class, remap = false)
public interface NameColorDataBaseSchemaAccessor {

	@Accessor("data")
	Data atr$data();
}
