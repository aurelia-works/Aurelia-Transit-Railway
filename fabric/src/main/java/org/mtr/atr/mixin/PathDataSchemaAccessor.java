package org.mtr.atr.mixin;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.generated.data.PathDataSchema;
import org.mtr.core.tool.Angle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets ATR copy a path segment with a different dwell time, keeping its shape and speed limit. */
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

	@Accessor("shape")
	Rail.Shape atr$shape();

	@Accessor("shape")
	void atr$setShape(Rail.Shape shape);

	@Accessor("verticalRadius")
	double atr$verticalRadius();

	@Accessor("verticalRadius")
	void atr$setVerticalRadius(double verticalRadius);

	@Accessor("speedLimit")
	long atr$speedLimit();

	@Accessor("speedLimit")
	void atr$setSpeedLimit(long speedLimit);
}
