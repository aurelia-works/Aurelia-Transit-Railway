package org.mtr.mod.resource;

import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.object.VertexArray;
import org.mtr.mapping.render.vertex.VertexAttributeMapping;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * MTR's {@code OptimizedModel.fromObjModels} uploads every OBJ group of a car on its own, so a car is drawn with one
 * call per group per material. Here all groups go into one {@link RawModel}, which keeps one mesh per material (MTR's
 * own {@code MaterialProperties} equality: shader, texture, colour, light), and the car is drawn with one call per
 * material. Same vertices, materials and shaders; only the number of buffers and draw calls changes.
 * Ported from Aurelia Transit Utilities 1.4.0 (MergedObjUpload).
 */
public final class MergedObjUpload {

	private MergedObjUpload() {
	}

	/** Render thread (uploads to the GPU). */
	public static <T> List<VertexArray> upload(Collection<T> objModels, Function<T, RawModel> rawModelOf, VertexAttributeMapping mapping) {
		return merge(objModels, rawModelOf).upload(mapping);
	}

	/** The merged model before upload. Steps per group are MTR's own: normals, then de-duplication. A group listed twice is merged once. */
	public static <T> RawModel merge(Collection<T> objModels, Function<T, RawModel> rawModelOf) {
		final Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		final RawModel merged = new RawModel();
		for (final T objModel : objModels) {
			if (!seen.add(objModel)) {
				continue;
			}
			final RawModel rawModel = rawModelOf.apply(objModel);
			rawModel.generateNormals();
			rawModel.distinct();
			merged.append(rawModel);
		}
		return merged;
	}
}
