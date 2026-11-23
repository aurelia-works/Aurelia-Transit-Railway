package org.mtr.atr.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.generated.data.PathDataSchema;
import org.mtr.core.tool.Angle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets ATR copy a path segment with a different dwell time. */
@Mixin(value = PathDataSchema.class, remap = false)
public interface PathDataSchemaAccessor {

	@Accessor("startPosition")
	Position atr$startPosition();

	@Accessor("startAngle")
	Angle atr$startAngle();

	@Accessor("endPosition")
	Position atr$endPosition();

	@Accessor("endAngle")
	Angle atr$endAngle();
}
