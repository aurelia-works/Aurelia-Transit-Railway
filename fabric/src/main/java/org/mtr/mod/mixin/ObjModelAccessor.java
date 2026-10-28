package org.mtr.mod.mixin;

import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.render.model.RawModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The parsed groups of an OBJ model, so {@link org.mtr.mod.resource.MergedObjUpload} can merge them before upload. */
@Mixin(value = OptimizedModel.ObjModel.class, remap = false)
public interface ObjModelAccessor {

	@Accessor("rawModel")
	RawModel getRawModel();
}
