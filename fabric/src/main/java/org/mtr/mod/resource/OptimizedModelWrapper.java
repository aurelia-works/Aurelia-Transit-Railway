package org.mtr.mod.resource;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.annotation.MappedMethod;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.ModelPartExtension;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.Init;
import org.mtr.mod.config.Config;
import org.mtr.mod.mixin.ObjModelAccessor;
import org.mtr.mod.mixin.OptimizedModelAccessor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class OptimizedModelWrapper {
	/**
	 * Used as a fallback texture if a face does not have a texture
	 */
	public static final Identifier WHITE_TEXTURE = new Identifier(Init.MOD_ID, "textures/block/white.png");

	@Nullable
	final OptimizedModel optimizedModel;
	/**
	 * Vertical extent of the OBJ model in blocks, NaN when unknown (used by rail LOD)
	 */
	private float height = Float.NaN;

	private static boolean mergeFailed;

	public static OptimizedModelWrapper fromMaterialGroups(@Nullable ObjectArrayList<MaterialGroupWrapper> materialGroupList) {
		return new OptimizedModelWrapper(OptimizedRenderer.hasOptimizedRendering() && materialGroupList != null ? OptimizedModel.fromMaterialGroups(materialGroupList.stream().map(materialGroup -> materialGroup.materialGroup).filter(Objects::nonNull).collect(Collectors.toList())) : null);
	}

	public static OptimizedModelWrapper fromObjModels(@Nullable ObjectArrayList<ObjModelWrapper> objModels) {
		final OptimizedModelWrapper wrapper = new OptimizedModelWrapper(OptimizedRenderer.hasOptimizedRendering() && objModels != null ? upload(objModels.stream().map(objModel -> objModel.objModel).filter(Objects::nonNull).collect(Collectors.toList())) : null);
		if (objModels != null) {
			float min = Float.POSITIVE_INFINITY;
			float max = Float.NEGATIVE_INFINITY;
			for (final ObjModelWrapper objModel : objModels) {
				if (objModel.objModel != null) {
					min = Math.min(min, objModel.objModel.getMinY());
					max = Math.max(max, objModel.objModel.getMaxY());
				}
			}
			wrapper.height = max >= min ? max - min : Float.NaN;
		}
		return wrapper;
	}

	/** One buffer per material per car ({@link MergedObjUpload}) when on; MTR's own upload when off or after a failure. */
	private static OptimizedModel upload(List<OptimizedModel.ObjModel> objModels) {
		if (!mergeFailed && Config.getClient().getMergeTrainParts()) {
			try {
				return new OptimizedModel(MergedObjUpload.upload(objModels, objModel -> ((ObjModelAccessor) (Object) objModel).getRawModel(), OptimizedModelAccessor.getDefaultMapping()));
			} catch (RuntimeException e) {
				mergeFailed = true;
				Init.LOGGER.error("Merging train parts failed; using MTR's own model upload for the rest of this session", e);
			}
		}
		return OptimizedModel.fromObjModels(objModels);
	}

	public float getHeight() {
		return height;
	}

	private OptimizedModelWrapper(@Nullable OptimizedModel optimizedModel) {
		this.optimizedModel = optimizedModel;
	}

	public OptimizedModelWrapper(@Nullable OptimizedModelWrapper optimizedModel1, @Nullable OptimizedModelWrapper optimizedModel2) {
		final boolean nonNull1 = optimizedModel1 != null && optimizedModel1.optimizedModel != null;
		final boolean nonNull2 = optimizedModel2 != null && optimizedModel2.optimizedModel != null;
		if (OptimizedRenderer.hasOptimizedRendering()) {
			if (nonNull1 && nonNull2) {
				optimizedModel = new OptimizedModel(optimizedModel1.optimizedModel, optimizedModel2.optimizedModel);
			} else if (nonNull1) {
				optimizedModel = optimizedModel1.optimizedModel;
			} else if (nonNull2) {
				optimizedModel = optimizedModel2.optimizedModel;
			} else {
				optimizedModel = null;
			}
		} else {
			optimizedModel = null;
		}
	}

	public static final class MaterialGroupWrapper {

		@Nullable
		private final OptimizedModel.MaterialGroup materialGroup;

		@MappedMethod
		public MaterialGroupWrapper(OptimizedModel.ShaderType shaderType, Identifier texture) {
			materialGroup = OptimizedRenderer.hasOptimizedRendering() ? new OptimizedModel.MaterialGroup(shaderType, texture) : null;
		}

		@MappedMethod
		public void addCube(ModelPartExtension modelPart, double x, double y, double z, boolean flipped, int light) {
			if (materialGroup != null) {
				materialGroup.addCube(modelPart, x, y, z, flipped, light);
			}
		}
	}

	public static final class ObjModelWrapper {

		public final OptimizedModel.ObjModel objModel;

		public ObjModelWrapper(OptimizedModel.ObjModel objModel) {
			this.objModel = objModel;
		}

		public void addTransformation(OptimizedModel.ShaderType shaderType, double x, double y, double z, boolean flipped) {
			objModel.addTransformation(shaderType, x, y, z, flipped);
		}
	}
}
