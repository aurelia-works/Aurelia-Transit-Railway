package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.holder.Vector3f;
import org.mtr.mapping.render.batch.MaterialProperties;
import org.mtr.mapping.render.model.Face;
import org.mtr.mapping.render.model.RawMesh;
import org.mtr.mapping.render.model.RawModel;
import org.mtr.mapping.render.vertex.Vertex;
import org.mtr.mod.resource.MergedObjUpload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Groups merge into one mesh per material; the upload itself needs OpenGL and is checked in game. */
class MergedObjUploadTest {

	/** {@code triangles} separate triangles, so every vertex is distinct and used. */
	private static RawModel group(OptimizedModel.ShaderType shaderType, int triangles) {
		final RawMesh rawMesh = new RawMesh(new MaterialProperties(shaderType, null, null));
		for (int i = 0; i < triangles * 3; i++) {
			final Vertex vertex = new Vertex();
			vertex.position = new Vector3f(i, i % 3 == 1 ? 1 : 0, i % 3 == 2 ? 1 : 0);
			vertex.normal = new Vector3f(0, 0, 1);
			rawMesh.addVertex(vertex);
		}
		for (int i = 0; i < triangles; i++) {
			rawMesh.faces.add(new Face(new int[]{i * 3, i * 3 + 1, i * 3 + 2}));
		}
		final RawModel rawModel = new RawModel();
		rawModel.append(rawMesh);
		return rawModel;
	}

	private static List<RawMesh> meshes(RawModel rawModel) {
		final List<RawMesh> meshes = new ArrayList<>();
		rawModel.iterateRawMeshList(meshes::add);
		return meshes;
	}

	/** Faces and vertices MTR's own path uploads for one group: normals, then de-duplication. */
	private static int[] mtrCounts(RawModel group) {
		group.generateNormals();
		group.distinct();
		final RawMesh rawMesh = meshes(group).get(0);
		return new int[]{rawMesh.faces.size(), rawMesh.vertices.size()};
	}

	@Test
	void sameMaterialBecomesOneMeshWithEveryGroupsFaces() {
		final Map<String, RawModel> groups = Map.of("body", group(OptimizedModel.ShaderType.CUTOUT, 3), "roof", group(OptimizedModel.ShaderType.CUTOUT, 4));
		final int[] body = mtrCounts(group(OptimizedModel.ShaderType.CUTOUT, 3));
		final int[] roof = mtrCounts(group(OptimizedModel.ShaderType.CUTOUT, 4));
		final List<RawMesh> merged = meshes(MergedObjUpload.merge(List.of("body", "roof"), groups::get));
		assertEquals(1, merged.size());
		assertEquals(body[0] + roof[0], merged.get(0).faces.size());
		assertEquals(body[1] + roof[1], merged.get(0).vertices.size());
	}

	@Test
	void materialsStaySeparate() {
		final Map<String, RawModel> groups = Map.of("body", group(OptimizedModel.ShaderType.CUTOUT, 3), "seats", group(OptimizedModel.ShaderType.CUTOUT_BRIGHT, 3));
		final List<RawMesh> merged = meshes(MergedObjUpload.merge(List.of("body", "seats"), groups::get));
		assertEquals(2, merged.size());
		assertNotEquals(merged.get(0).materialProperties, merged.get(1).materialProperties);
	}

	@Test
	void groupListedTwiceIsMergedOnce() {
		final Map<String, RawModel> groups = Map.of("body", group(OptimizedModel.ShaderType.CUTOUT, 3));
		final int[] body = mtrCounts(group(OptimizedModel.ShaderType.CUTOUT, 3));
		final String name = "body";
		final List<RawMesh> merged = meshes(MergedObjUpload.merge(List.of(name, name), groups::get));
		assertEquals(body[0], merged.get(0).faces.size());
	}
}
