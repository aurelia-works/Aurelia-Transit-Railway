package org.mtr.mod.resource;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.annotation.MappedMethod;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.ModelPartExtension;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mod.Init;
import org.mtr.mod.config.Config;
import org.mtr.mod.mixin.ObjModelAccessor;
import org.mtr.mod.mixin.OptimizedModelAccessor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
		// MTR's own upload stays first here: add-ons hook its lambda (lambda$fromObjModels$1)
		final OptimizedModel mergedModel = OptimizedRenderer.hasOptimizedRendering() && objModels != null ? upload(objModels) : null;
		final OptimizedModelWrapper wrapper = new OptimizedModelWrapper(mergedModel != null ? mergedModel : OptimizedRenderer.hasOptimizedRendering() && objModels != null ? OptimizedModel.fromObjModels(objModels.stream().map(objModel -> objModel.objModel).filter(Objects::nonNull).collect(Collectors.toList())) : null);
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

	/**
	 * One buffer per material per car ({@link MergedObjUpload}) when on, with the parts both door states use uploaded
	 * once ({@link DoorShare}); null (MTR's own upload) when off or after a failure.
	 */
	@Nullable
	private static OptimizedModel upload(List<ObjModelWrapper> allObjModels) {
		if (!mergeFailed && Config.getClient().getMergeTrainParts()) {
			final List<ObjModelWrapper> objModels = allObjModels.stream().filter(objModel -> objModel.objModel != null).collect(Collectors.toList());
			try {
				final DoorShare.Split<ObjModelWrapper> split = DoorShare.split(objModels, objModel -> objModel.doorShare);
				if (split.share == null) {
					return new OptimizedModel(uploadMerged(split.unique));
				}
				List<VertexArray> commonArrays = split.share.uploaded();
				if (commonArrays == null) {
					commonArrays = uploadMerged(split.common);
					split.share.store(commonArrays);
				}
				final List<VertexArray> vertexArrays = new ArrayList<>(commonArrays);
				if (!split.rest.isEmpty()) {
					vertexArrays.addAll(uploadMerged(split.rest));
				}
				return new OptimizedModel(vertexArrays);
			} catch (RuntimeException e) {
				mergeFailed = true;
				Init.LOGGER.error("Merging train parts failed; using MTR's own model upload for the rest of this session", e);
			}
		}
		return null;
	}

	private static List<VertexArray> uploadMerged(List<ObjModelWrapper> objModels) {
		return MergedObjUpload.upload(objModels, objModel -> ((ObjModelAccessor) (Object) objModel.objModel).getRawModel(), OptimizedModelAccessor.getDefaultMapping());
	}

	/** Links the parts both door states use ({@link DoorShare}), when merging is on. Call once per car build. */
	public static void linkDoorStates(Map<?, ? extends Collection<ObjModelWrapper>> objModelsDoorsOpen, Map<?, ? extends Collection<ObjModelWrapper>> objModelsDoorsClosed) {
		if (mergeFailed || !Config.getClient().getMergeTrainParts()) {
			return;
		}
		try {
			DoorShare.link(objModelsDoorsOpen, objModelsDoorsClosed).forEach((objModel, share) -> objModel.doorShare = share);
		} catch (RuntimeException e) {
			Init.LOGGER.warn("Could not link a train's door states; its parts are uploaded for each", e);
		}
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
		/** Set when this part is one both door states of a car use ({@link DoorShare}). */
		@Nullable
		private DoorShare<ObjModelWrapper> doorShare;

		public ObjModelWrapper(OptimizedModel.ObjModel objModel) {
			this.objModel = objModel;
		}

		public void addTransformation(OptimizedModel.ShaderType shaderType, double x, double y, double z, boolean flipped) {
			objModel.addTransformation(shaderType, x, y, z, flipped);
		}
	}
}
