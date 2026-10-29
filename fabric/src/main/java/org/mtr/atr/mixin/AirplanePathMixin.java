package org.mtr.atr.mixin;

import org.mtr.core.data.Data;
import org.mtr.core.data.PathData;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectList;
import org.mtr.mod.data.AirplanePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts an airplane's platform and siding speed back after the cache has copied the runway's 300 km/h onto them.
 * The path list handed in here is often immutable, so this only writes the speed field on each segment.
 */
@Mixin(value = PathData.class, remap = false)
public abstract class AirplanePathMixin {

	@Inject(method = "writePathCache(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectList;Lorg/mtr/core/data/Data;Lorg/mtr/core/data/TransportMode;)V", at = @At("RETURN"))
	private static void atr$keepPlatformSpeed(ObjectList<PathData> path, Data data, TransportMode transportMode, CallbackInfo callback) {
		if (transportMode == TransportMode.AIRPLANE) {
			AirplanePath.restoreSavedRailSpeeds(path);
		}
	}
}
