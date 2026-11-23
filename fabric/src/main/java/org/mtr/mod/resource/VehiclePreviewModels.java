package org.mtr.mod.resource;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.mapper.OptimizedModel;

import javax.annotation.Nullable;

/**
 * The uploaded models of one car as it stands in a depot with its doors shut, for ATR's train picker preview. Lives in
 * this package because {@link OptimizedModelWrapper#optimizedModel} is package-private.
 */
public final class VehiclePreviewModels {

	public final ObjectArrayList<OptimizedModel> body = new ObjectArrayList<>();
	public final ObjectArrayList<OptimizedModel> bogie1 = new ObjectArrayList<>();
	public final ObjectArrayList<OptimizedModel> bogie2 = new ObjectArrayList<>();

	private VehiclePreviewModels() {
	}

	/** Null while the models are still loading. */
	@Nullable
	public static VehiclePreviewModels of(VehicleResource vehicleResource, int carNumber, int totalCars) {
		final VehicleResourceCache cache = vehicleResource.getCachedVehicleResource(carNumber, totalCars, false);
		final VehicleResourceCache bogieCache = vehicleResource.getCachedVehicleResource(0, 1, false);
		if (cache == null || bogieCache == null) {
			return null;
		}
		final VehiclePreviewModels models = new VehiclePreviewModels();
		add(cache.optimizedModelsDoorsClosed, models.body);
		add(bogieCache.optimizedModelsBogie1, models.bogie1);
		add(bogieCache.optimizedModelsBogie2, models.bogie2);
		return models;
	}

	private static void add(Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper> models, ObjectArrayList<OptimizedModel> into) {
		for (final PartCondition partCondition : new PartCondition[]{PartCondition.NORMAL, PartCondition.AT_DEPOT, PartCondition.DOORS_CLOSED}) {
			final OptimizedModelWrapper wrapper = models.get(partCondition);
			if (wrapper != null && wrapper.optimizedModel != null) {
				into.add(wrapper.optimizedModel);
			}
		}
	}
}
