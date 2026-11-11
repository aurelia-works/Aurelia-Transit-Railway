package org.mtr.aurelia.vehicle.client;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.mtr.aurelia.vehicle.RoadVehicles;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Blockbench {@code .bbmodel} (cubes and meshes in groups) read straight from the resource pack and drawn as quads in
 * Blockbench units (16 to a block). Group names give the parts their job: {@code body} (and {@code body_*}) takes the paint
 * colour, {@code glass} draws translucent, {@code wheel_f*} and {@code *steer} (a bicycle's fork and bars) turn with the
 * steering about their pivot, and every {@code wheel_*} rolls about its pivot.
 */
final class BlockbenchMesh {

	private static final String[] FACES = {"north", "south", "east", "west", "up", "down"};
	/** Cube corners per face as {x, y, z} picks (0 = from, 1 = to), in the order of the UV corners (u1 v1, u2 v1, u2 v2, u1 v2). */
	private static final int[][][] CORNERS = {
			{{1, 1, 0}, {0, 1, 0}, {0, 0, 0}, {1, 0, 0}},
			{{0, 1, 1}, {1, 1, 1}, {1, 0, 1}, {0, 0, 1}},
			{{1, 1, 1}, {1, 1, 0}, {1, 0, 0}, {1, 0, 1}},
			{{0, 1, 0}, {0, 1, 1}, {0, 0, 1}, {0, 0, 0}},
			{{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}},
			{{0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {0, 0, 0}},
	};
	private static final float[][] NORMALS = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};

	private final List<Part> roots;

	private BlockbenchMesh(List<Part> roots) {
		this.roots = roots;
	}

	@Nullable
	static BlockbenchMesh load(ResourceManager resourceManager, Identifier id) {
		final Optional<Resource> resource = resourceManager.getResource(id);
		if (resource.isEmpty()) {
			RoadVehicles.LOGGER.error("[ATR] Road vehicle model {} is missing", id);
			return null;
		}
		try (final Reader reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
			return parse(JsonParser.parseReader(reader).getAsJsonObject());
		} catch (Exception e) {
			RoadVehicles.LOGGER.error("[ATR] Road vehicle model {} could not be read", id, e);
			return null;
		}
	}

	private static BlockbenchMesh parse(JsonObject json) {
		float uvWidth = 16;
		float uvHeight = 16;
		if (json.has("resolution")) {
			uvWidth = json.getAsJsonObject("resolution").get("width").getAsFloat();
			uvHeight = json.getAsJsonObject("resolution").get("height").getAsFloat();
		}
		final JsonArray textures = json.has("textures") ? json.getAsJsonArray("textures") : new JsonArray();
		if (!textures.isEmpty() && textures.get(0).getAsJsonObject().has("uv_width")) {
			uvWidth = textures.get(0).getAsJsonObject().get("uv_width").getAsFloat();
			uvHeight = textures.get(0).getAsJsonObject().get("uv_height").getAsFloat();
		}
		final Map<String, List<Quad>> elements = new HashMap<>();
		for (final JsonElement element : json.getAsJsonArray("elements")) {
			final JsonObject object = element.getAsJsonObject();
			final List<Quad> quads = new ArrayList<>();
			if ("mesh".equals(object.has("type") ? object.get("type").getAsString() : "cube")) {
				readMesh(object, uvWidth, uvHeight, quads);
			} else {
				readCube(object, uvWidth, uvHeight, quads);
			}
			elements.put(object.get("uuid").getAsString(), quads);
		}
		// Blockbench 5 keeps group settings in "groups" and only the tree in "outliner".
		final Map<String, JsonObject> groups = new HashMap<>();
		if (json.has("groups")) {
			json.getAsJsonArray("groups").forEach(group -> groups.put(group.getAsJsonObject().get("uuid").getAsString(), group.getAsJsonObject()));
		}
		final Part root = new Part("", new float[3], new float[3], Role.SOLID);
		readChildren(json.getAsJsonArray("outliner"), root, elements, groups, true);
		return new BlockbenchMesh(List.of(root));
	}

	private static void readChildren(JsonArray children, Part parent, Map<String, List<Quad>> elements, Map<String, JsonObject> groups, boolean top) {
		for (final JsonElement child : children) {
			if (child.isJsonPrimitive()) {
				final List<Quad> quads = elements.get(child.getAsString());
				if (quads != null) {
					parent.quads.addAll(quads);
				}
			} else {
				final JsonObject node = child.getAsJsonObject();
				final JsonObject settings = groups.getOrDefault(node.has("uuid") ? node.get("uuid").getAsString() : "", node);
				final String name = settings.has("name") ? settings.get("name").getAsString() : "";
				final Role role = top ? Role.of(name) : parent.role;
				final Part part = new Part(name, vector(settings, "origin"), vector(settings, "rotation"), role);
				readChildren(node.getAsJsonArray("children"), part, elements, groups, false);
				parent.children.add(part);
			}
		}
	}

	private static void readCube(JsonObject cube, float uvWidth, float uvHeight, List<Quad> quads) {
		final float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
		final float[] from = vector(cube, "from");
		final float[] to = vector(cube, "to");
		for (int i = 0; i < 3; i++) {
			from[i] -= inflate;
			to[i] += inflate;
		}
		final float[][] box = {from, to};
		final float[] origin = vector(cube, "origin");
		final Matrix3f rotation = rotation(vector(cube, "rotation"));
		final JsonObject faces = cube.getAsJsonObject("faces");
		for (int face = 0; face < FACES.length; face++) {
			if (!faces.has(FACES[face]) || !faces.getAsJsonObject(FACES[face]).has("texture") || faces.getAsJsonObject(FACES[face]).get("texture").isJsonNull()) {
				continue;
			}
			final JsonObject faceJson = faces.getAsJsonObject(FACES[face]);
			final JsonArray uv = faceJson.getAsJsonArray("uv");
			final float u1 = uv.get(0).getAsFloat() / uvWidth;
			final float v1 = uv.get(1).getAsFloat() / uvHeight;
			final float u2 = uv.get(2).getAsFloat() / uvWidth;
			final float v2 = uv.get(3).getAsFloat() / uvHeight;
			final float[][] uvCorners = {{u1, v1}, {u2, v1}, {u2, v2}, {u1, v2}};
			final int turns = faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() / 90 : 0;
			final Quad quad = new Quad();
			for (int corner = 0; corner < 4; corner++) {
				final int[] pick = CORNERS[face][corner];
				final Vector3f point = new Vector3f(box[pick[0]][0] - origin[0], box[pick[1]][1] - origin[1], box[pick[2]][2] - origin[2]);
				rotation.transform(point);
				quad.set(corner, point.x + origin[0], point.y + origin[1], point.z + origin[2], uvCorners[(corner + turns) & 3]);
			}
			final Vector3f normal = rotation.transform(new Vector3f(NORMALS[face]));
			quad.normal(normal.x, normal.y, normal.z);
			quads.add(quad);
		}
	}

	private static void readMesh(JsonObject mesh, float uvWidth, float uvHeight, List<Quad> quads) {
		final float[] origin = vector(mesh, "origin");
		final Matrix3f rotation = rotation(vector(mesh, "rotation"));
		final Map<String, Vector3f> vertices = new HashMap<>();
		mesh.getAsJsonObject("vertices").entrySet().forEach(entry -> {
			final JsonArray xyz = entry.getValue().getAsJsonArray();
			final Vector3f point = rotation.transform(new Vector3f(xyz.get(0).getAsFloat(), xyz.get(1).getAsFloat(), xyz.get(2).getAsFloat()));
			vertices.put(entry.getKey(), point.add(origin[0], origin[1], origin[2]));
		});
		for (final Map.Entry<String, JsonElement> entry : mesh.getAsJsonObject("faces").entrySet()) {
			final JsonObject face = entry.getValue().getAsJsonObject();
			if (!face.has("texture") || face.get("texture").isJsonNull()) {
				continue;
			}
			final JsonArray keys = face.getAsJsonArray("vertices");
			if (keys.size() < 3 || keys.size() > 4) {
				continue;
			}
			final String[] order = perimeter(keys, vertices);
			final JsonObject uv = face.getAsJsonObject("uv");
			final Quad quad = new Quad();
			for (int corner = 0; corner < 4; corner++) {
				final String key = order[Math.min(corner, order.length - 1)];
				final Vector3f point = vertices.get(key);
				final JsonArray uvPoint = uv.getAsJsonArray(key);
				quad.set(corner, point.x, point.y, point.z, new float[]{uvPoint.get(0).getAsFloat() / uvWidth, uvPoint.get(1).getAsFloat() / uvHeight});
			}
			final Vector3f a = vertices.get(order[0]);
			final Vector3f normal = new Vector3f(vertices.get(order[1])).sub(a).cross(new Vector3f(vertices.get(order[2])).sub(a));
			if (normal.lengthSquared() > 0) {
				normal.normalize();
			}
			quad.normal(normal.x, normal.y, normal.z);
			quads.add(quad);
		}
	}

	/** Four corners in an order that walks round the face, so the quad does not fold over itself. */
	private static String[] perimeter(JsonArray keys, Map<String, Vector3f> vertices) {
		final String[] order = new String[keys.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = keys.get(i).getAsString();
		}
		if (order.length == 4) {
			for (final int[] tryOrder : new int[][]{{0, 1, 2, 3}, {0, 1, 3, 2}, {0, 2, 1, 3}}) {
				final Vector3f a = vertices.get(order[tryOrder[0]]);
				final Vector3f b = vertices.get(order[tryOrder[1]]);
				final Vector3f c = vertices.get(order[tryOrder[2]]);
				final Vector3f d = vertices.get(order[tryOrder[3]]);
				final Vector3f first = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
				final Vector3f second = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(a));
				if (first.dot(second) > 0) {
					return new String[]{order[tryOrder[0]], order[tryOrder[1]], order[tryOrder[2]], order[tryOrder[3]]};
				}
			}
		}
		return order;
	}

	void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Identifier texture, int light, int overlay, int colour, float steer, float spin) {
		final VertexConsumer solid = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
		for (final Part root : roots) {
			draw(root, matrices, solid, false, light, overlay, colour, steer, spin);
		}
		final VertexConsumer glass = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
		for (final Part root : roots) {
			draw(root, matrices, glass, true, light, overlay, colour, steer, spin);
		}
	}

	private static void draw(Part part, MatrixStack matrices, VertexConsumer consumer, boolean glassPass, int light, int overlay, int colour, float steer, float spin) {
		matrices.push();
		matrices.translate(part.origin[0], part.origin[1], part.origin[2]);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(part.rotation[2]));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(part.rotation[1]));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(part.rotation[0]));
		if (part.name.startsWith("wheel_f") || part.name.endsWith("steer")) {
			matrices.multiply(RotationAxis.POSITIVE_Y.rotation(steer));
		}
		if (part.name.startsWith("wheel_")) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(spin));
		}
		matrices.translate(-part.origin[0], -part.origin[1], -part.origin[2]);
		if ((part.role == Role.GLASS) == glassPass) {
			final MatrixStack.Entry entry = matrices.peek();
			final Matrix4f position = entry.getPositionMatrix();
			final Matrix3f normalMatrix = entry.getNormalMatrix();
			final int tint = part.role == Role.BODY ? colour : 0xFFFFFF;
			final int red = tint >> 16 & 0xFF;
			final int green = tint >> 8 & 0xFF;
			final int blue = tint & 0xFF;
			// Glass is a real pane: the texture stays, the body behind it shows through. Cutout would drop it.
			final int alpha = part.role == Role.GLASS ? 120 : 255;
			for (final Quad quad : part.quads) {
				for (int corner = 0; corner < 4; corner++) {
					consumer.vertex(position, quad.xyz[corner * 3], quad.xyz[corner * 3 + 1], quad.xyz[corner * 3 + 2])
							.color(red, green, blue, alpha)
							.texture(quad.uv[corner * 2], quad.uv[corner * 2 + 1])
							.overlay(overlay)
							.light(light)
							.normal(normalMatrix, quad.normal[0], quad.normal[1], quad.normal[2])
							.next();
				}
			}
		}
		for (final Part child : part.children) {
			draw(child, matrices, consumer, glassPass, light, overlay, colour, steer, spin);
		}
		matrices.pop();
	}

	private static float[] vector(JsonObject object, String key) {
		if (!object.has(key)) {
			return new float[3];
		}
		final JsonArray array = object.getAsJsonArray(key);
		return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
	}

	/** Blockbench turns about Z, then Y, then X (three.js order "ZYX"), in degrees. */
	private static Matrix3f rotation(float[] degrees) {
		return new Matrix3f().rotateZ((float) Math.toRadians(degrees[2])).rotateY((float) Math.toRadians(degrees[1])).rotateX((float) Math.toRadians(degrees[0]));
	}

	private enum Role {
		BODY, GLASS, SOLID;

		static Role of(String groupName) {
			if (groupName.equals("body") || groupName.startsWith("body_")) {
				return BODY;
			}
			return groupName.equals("glass") ? GLASS : SOLID;
		}
	}

	private static final class Part {

		private final String name;
		private final float[] origin;
		private final float[] rotation;
		private final Role role;
		private final List<Quad> quads = new ArrayList<>();
		private final List<Part> children = new ArrayList<>();

		private Part(String name, float[] origin, float[] rotation, Role role) {
			this.name = name;
			this.origin = origin;
			this.rotation = rotation;
			this.role = role;
		}
	}

	private static final class Quad {

		private final float[] xyz = new float[12];
		private final float[] uv = new float[8];
		private final float[] normal = new float[3];

		private void set(int corner, float x, float y, float z, float[] uvPoint) {
			xyz[corner * 3] = x;
			xyz[corner * 3 + 1] = y;
			xyz[corner * 3 + 2] = z;
			uv[corner * 2] = uvPoint[0];
			uv[corner * 2 + 1] = uvPoint[1];
		}

		private void normal(float x, float y, float z) {
			normal[0] = x;
			normal[1] = y;
			normal[2] = z;
		}
	}
}
