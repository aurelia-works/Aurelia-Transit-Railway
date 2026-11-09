package org.mtr.atr.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private per-tick search step so the route-control path-tick mixin can drive it. */
@Mixin(value = SidingPathFinder.class, remap = false)
public interface SidingPathFinderAccessor {

	@Invoker("tick")
	ObjectArrayList<PathData> atr$tick(long cruisingAltitude);
}
