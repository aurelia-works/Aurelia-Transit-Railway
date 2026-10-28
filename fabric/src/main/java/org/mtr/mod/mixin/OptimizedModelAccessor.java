package org.mtr.mod.mixin;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The vertex layout MTR uploads OBJ models with, so merged models upload the same way. */
@Mixin(value = OptimizedModel.class, remap = false)
public interface OptimizedModelAccessor {

	@Accessor("DEFAULT_MAPPING")
	static VertexAttributeMapping getDefaultMapping() {
		throw new AssertionError();
	}
}
