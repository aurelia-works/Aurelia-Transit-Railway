package org.mtr.atr.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.data.VehicleExtraData;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.AirplanePath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Anchors the repeat sentinel while the path list is still mutable: once when a vehicle is dispatched,
 * and again when a saved vehicle is loaded, before that list is copied into {@code immutablePath}.
 */
@Mixin(value = VehicleExtraData.class, remap = false)
public abstract class AirplaneRepeatMixin {

	@Inject(method = "createPathData", at = @At("RETURN"))
	private static void atr$anchorSentinel(ObjectArrayList<PathData> pathSidingToMainRoute, ObjectArrayList<PathData> pathMainRoute, ObjectArrayList<PathData> pathMainRouteToSiding, boolean repeatInfinitely, PathData defaultPathData, CallbackInfoReturnable<ObjectArrayList<PathData>> callback) {
		AirplanePath.anchorRepeatSentinel(callback.getReturnValue());
	}

	/**
	 * {@code updateData} has filled {@code path}. {@code immutablePath} is built from it on the next instruction.
	 * The list lives on the generated schema, so it is read by name rather than shadowed.
	 */
	@Inject(method = "<init>(Lorg/mtr/core/serializer/ReaderBase;)V", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/VehicleExtraData;updateData(Lorg/mtr/core/serializer/ReaderBase;)V", shift = At.Shift.AFTER))
	private void atr$anchorLoadedPath(ReaderBase readerBase, CallbackInfo callback) {
		AirplanePath.anchorRepeatSentinel(loadedPath(this));
	}

	@SuppressWarnings("unchecked")
	private static List<PathData> loadedPath(Object vehicleExtraData) {
		Class<?> type = vehicleExtraData.getClass();
		while (type != null) {
			try {
				final Field field = type.getDeclaredField("path");
				field.setAccessible(true);
				return (List<PathData>) field.get(vehicleExtraData);
			} catch (NoSuchFieldException ignored) {
				type = type.getSuperclass();
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalStateException("VehicleExtraData.path");
	}
}
