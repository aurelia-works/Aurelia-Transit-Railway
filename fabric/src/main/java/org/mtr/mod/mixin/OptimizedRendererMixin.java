package org.mtr.mod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mapping.render.batch.BatchManager;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeState;
import org.mtr.mod.render.TrainLod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Middle train LOD tier: a far car's batched model is queued without its interior parts (MTR render stages INTERIOR
 * and INTERIOR_TRANSLUCENT, i.e. shader types CUTOUT_BRIGHT and TRANSLUCENT_BRIGHT). The batching lives in the
 * Minecraft-Mappings library, so this is a hook into it; {@link TrainLod#skipInterior()} is set around one car's queue.
 */
@Mixin(value = OptimizedRenderer.class, remap = false)
public abstract class OptimizedRendererMixin {

	/** Exterior-only part lists per uploaded model; weak so rebuilt models drop out. */
	@Unique
	private static final Map<OptimizedModel, List<VertexArray>> ATR$EXTERIOR_PARTS = new WeakHashMap<>();

	@WrapOperation(method = "queue", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/render/batch/BatchManager;queue(Ljava/util/List;Lorg/mtr/mapping/render/vertex/VertexAttributeState;)V"))
	private void atr$trainInteriorLod(BatchManager batchManager, List<VertexArray> parts, VertexAttributeState state, Operation<Void> original, @Local(argsOnly = true) OptimizedModel model) {
		original.call(batchManager, TrainLod.skipInterior() ? ATR$EXTERIOR_PARTS.computeIfAbsent(model, key -> TrainLod.withoutInterior(parts, part -> {
			final OptimizedModel.ShaderType shaderType = part.materialProperties.shaderType;
			return shaderType == OptimizedModel.ShaderType.CUTOUT_BRIGHT || shaderType == OptimizedModel.ShaderType.TRANSLUCENT_BRIGHT;
		})) : parts, state);
	}
}
