package com.aureliatransit.architecture.client.airport;

import com.aureliatransit.architecture.airport.JetBridgeMotion;
import com.aureliatransit.architecture.block.JetBridgeBlock;
import com.aureliatransit.architecture.block.entity.JetBridgeBlockEntity;
import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The moving end of a boarding bridge. The block sits in the fixed tube's roof over its open end, on the side nearest the plane
 * ({@link JetBridgeMotion#ROOF_ABOVE_WALK} over the floor, and model y = 0 is put back on the floor); this draws a
 * telescoping section out of its north face (turned to the block's facing), a cab three blocks deep at the section's end with
 * a glazed front, a black canopy that closes onto the fuselage, and the drive leg with its wheels under the cab. The plane is
 * on the model's west (-x) side: the planner puts the bridge on the plane's left, and +v is left of +u in every local frame.
 *
 * <p>Model space: the block is x, y, z in 0..1; y = 0 is the bridge's floor, the apron is {@link #APRON} below it; the fixed
 * tube's walls are the blocks at x = -1 and x = {@link #WIDTH}, its roof the block at y = 2.
 */
public final class JetBridgeRenderer implements BlockEntityRenderer<JetBridgeBlockEntity> {

	/** The moving section's clear width: three blocks, the block's own column and two toward the tube's far side. */
	static final float WIDTH = 3f;
	/** The apron's top, below the bridge floor. */
	static final float APRON = -3f;
	private static final float CAB = 3f;
	private static final float TYRE_RADIUS = 0.42f;
	private static final float TYRE_WIDTH = 0.26f;
	/** Each tyre's inner x: a pair at the beam's near end, a pair at its far end, a small gap between the two of a pair. */
	private static final float[] TYRES = {-0.5f, -0.2f, WIDTH - 0.06f, WIDTH + 0.24f};
	private static final int TYRE_SIDES = 12;
	private static final Identifier PANEL = new Identifier("minecraft", "textures/block/light_gray_concrete.png");
	private static final Identifier TRIM = new Identifier("minecraft", "textures/block/gray_concrete.png");
	private static final Identifier RUBBER = new Identifier("minecraft", "textures/block/black_concrete.png");
	private static final Identifier STEEL = new Identifier("minecraft", "textures/block/iron_block.png");
	private static final Identifier GLASS = new Identifier("minecraft", "textures/block/gray_stained_glass.png");

	public JetBridgeRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public boolean rendersOutsideBoundingBox(JetBridgeBlockEntity entity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 160;
	}

	@Override
	public void render(JetBridgeBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (entity.getWorld() != null) {
			entity.advance(entity.getWorld().getTime());
		}
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof JetBridgeBlock)) {
			return;
		}
		final Direction facing = state.get(JetBridgeBlock.FACING);
		final float reach = entity.reach();
		// the block itself is solid, so its own light is dark: light the moving part from the air where the cab is
		final int lit = entity.getWorld() == null ? light
				: WorldRenderer.getLightmapCoordinates(entity.getWorld(), entity.getPos().offset(facing, MathHelper.ceil(reach) + 2).down(JetBridgeMotion.ROOF_ABOVE_WALK));
		final float docked = MathHelper.clamp((entity.extension() - 0.85f) / 0.15f, 0f, 1f);

		matrices.push();
		matrices.translate(0.5, -JetBridgeMotion.ROOF_ABOVE_WALK, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(facing)));
		matrices.translate(-0.5, 0, -0.5);

		final float front = -reach;
		final float cabFront = front - CAB;
		final float out = -1f - 0.35f * docked;
		final float legZ = front - CAB / 2;
		final boolean section = reach > 0.01f;
		// one texture at a time: a buffer from the provider is only good until the next getBuffer call

		// the telescoping section, a little inside the fixed tube's profile so it reads as sliding out of it; the cab's floor,
		// roof with a lip, sides below the window band and front sill; the frame beside the door opening
		VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(PANEL));
		if (section) {
			box(matrices, buffer, -0.85f, -0.85f, front, WIDTH + 0.85f, -0.6f, 0f, lit);
			box(matrices, buffer, -0.85f, 2.6f, front, WIDTH + 0.85f, 2.85f, 0f, lit);
			box(matrices, buffer, -0.85f, -0.6f, front, -0.6f, 2.0f, 0f, lit);
			box(matrices, buffer, WIDTH + 0.6f, -0.6f, front, WIDTH + 0.85f, 2.0f, 0f, lit);
		}
		box(matrices, buffer, -1f, -1f, cabFront, WIDTH + 1f, 0f, front, lit);
		box(matrices, buffer, -1.1f, 2.9f, cabFront - 0.1f, WIDTH + 1.1f, 3.1f, front, lit);
		box(matrices, buffer, WIDTH, 0f, cabFront + 0.3f, WIDTH + 1f, 0.9f, front, lit);
		box(matrices, buffer, -1f, 0f, cabFront, WIDTH + 1f, 0.9f, cabFront + 0.3f, lit);
		box(matrices, buffer, -1f, 0f, front - 0.3f, -0.8f, 2.2f, front, lit);
		box(matrices, buffer, -1f, 2.2f, cabFront, -0.8f, 2.9f, front, lit);

		// darker bands: the section's top rail, the cab's fascia over its windows, the bogie
		buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TRIM));
		if (section) {
			box(matrices, buffer, -0.85f, 2.0f, front, -0.6f, 2.6f, 0f, lit);
			box(matrices, buffer, WIDTH + 0.6f, 2.0f, front, WIDTH + 0.85f, 2.6f, 0f, lit);
		}
		box(matrices, buffer, -0.8f, 2.2f, cabFront, WIDTH + 1f, 2.9f, cabFront + 0.3f, lit);
		box(matrices, buffer, WIDTH, 2.2f, cabFront + 0.3f, WIDTH + 1f, 2.9f, front, lit);
		box(matrices, buffer, -0.45f, APRON + 0.3f, legZ - 0.25f, WIDTH + 0.45f, APRON + 0.55f, legZ + 0.25f, lit);
		box(matrices, buffer, 0.2f, APRON + 0.55f, legZ - 0.6f, WIDTH - 0.2f, APRON + 0.8f, legZ + 0.6f, lit);

		// the door opening on the plane's side, framed in rubber: the canopy that closes onto the fuselage once docked; the wheels
		buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(RUBBER));
		box(matrices, buffer, out, -0.1f, front - 2.7f, -0.8f, 0.1f, front - 0.3f, lit);
		box(matrices, buffer, out, 2.0f, front - 2.7f, -0.8f, 2.2f, front - 0.3f, lit);
		box(matrices, buffer, out, -0.1f, front - 2.9f, -0.8f, 2.2f, front - 2.7f, lit);
		box(matrices, buffer, out, -0.1f, front - 0.3f, -0.8f, 2.2f, front - 0.1f, lit);
		// twin tyres at each end of the axle beam, as on an apron-drive bridge's wheel bogie
		for (final float x : TYRES) {
			cylinderX(matrices, buffer, x, x + TYRE_WIDTH, APRON + TYRE_RADIUS, legZ, TYRE_RADIUS, lit);
		}

		// the drive leg down to the bogie
		buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(STEEL));
		box(matrices, buffer, WIDTH / 2 - 0.3f, APRON + 0.6f, legZ - 0.3f, WIDTH / 2 + 0.3f, -1f, legZ + 0.3f, lit);
		// the hubs, on the outer face of each pair
		cylinderX(matrices, buffer, TYRES[0] - 0.03f, TYRES[0], APRON + TYRE_RADIUS, legZ, TYRE_RADIUS * 0.5f, lit);
		cylinderX(matrices, buffer, TYRES[3] + TYRE_WIDTH, TYRES[3] + TYRE_WIDTH + 0.03f, APRON + TYRE_RADIUS, legZ, TYRE_RADIUS * 0.5f, lit);

		// glass last: the window band along the cab's far side and across its front
		buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(GLASS));
		box(matrices, buffer, WIDTH + 0.4f, 0.9f, cabFront + 0.3f, WIDTH + 0.6f, 2.2f, front, lit);
		box(matrices, buffer, -0.8f, 0.9f, cabFront + 0.1f, WIDTH + 1f, 2.2f, cabFront + 0.2f, lit);
		matrices.pop();
	}

	/** A cuboid whose faces are tiled one texture per block, so long walls are not one stretched texture. */
	private static void box(MatrixStack matrices, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
		final Matrix4f position = matrices.peek().getPositionMatrix();
		final Matrix3f normal = matrices.peek().getNormalMatrix();
		// -y, +y
		tiles(buffer, position, normal, light, x0, z1, x1, z0, (a, b) -> new float[]{a, y0, b}, 0, -1, 0);
		tiles(buffer, position, normal, light, x0, z0, x1, z1, (a, b) -> new float[]{a, y1, b}, 0, 1, 0);
		// -z, +z
		tiles(buffer, position, normal, light, x0, y0, x1, y1, (a, b) -> new float[]{a, b, z0}, 0, 0, -1);
		tiles(buffer, position, normal, light, x1, y0, x0, y1, (a, b) -> new float[]{a, b, z1}, 0, 0, 1);
		// -x, +x
		tiles(buffer, position, normal, light, z0, y0, z1, y1, (a, b) -> new float[]{x0, b, a}, -1, 0, 0);
		tiles(buffer, position, normal, light, z1, y0, z0, y1, (a, b) -> new float[]{x1, b, a}, 1, 0, 0);
	}

	/** A round wheel with its axle along x, from x0 to x1, centred at (cy, cz): {@link #TYRE_SIDES} tread faces and two flat sides. */
	private static void cylinderX(MatrixStack matrices, VertexConsumer buffer, float x0, float x1, float cy, float cz, float radius, int light) {
		final Matrix4f position = matrices.peek().getPositionMatrix();
		final Matrix3f normal = matrices.peek().getNormalMatrix();
		for (int k = 0; k < TYRE_SIDES; k++) {
			final double a0 = 2 * Math.PI * k / TYRE_SIDES;
			final double a1 = 2 * Math.PI * (k + 1) / TYRE_SIDES;
			final float y0 = cy + (float) (radius * Math.cos(a0));
			final float z0 = cz + (float) (radius * Math.sin(a0));
			final float y1 = cy + (float) (radius * Math.cos(a1));
			final float z1 = cz + (float) (radius * Math.sin(a1));
			final float ny = (float) Math.cos((a0 + a1) / 2);
			final float nz = (float) Math.sin((a0 + a1) / 2);
			// counter-clockwise from outside: the tread, then the two side wedges (a quad with its last corner doubled)
			vertex(buffer, position, normal, new float[]{x0, y0, z0}, 0, 0, 0, ny, nz, light);
			vertex(buffer, position, normal, new float[]{x0, y1, z1}, 1, 0, 0, ny, nz, light);
			vertex(buffer, position, normal, new float[]{x1, y1, z1}, 1, 1, 0, ny, nz, light);
			vertex(buffer, position, normal, new float[]{x1, y0, z0}, 0, 1, 0, ny, nz, light);
			vertex(buffer, position, normal, new float[]{x0, cy, cz}, 0.5f, 0.5f, -1, 0, 0, light);
			vertex(buffer, position, normal, new float[]{x0, y1, z1}, 1, 0, -1, 0, 0, light);
			vertex(buffer, position, normal, new float[]{x0, y0, z0}, 0, 0, -1, 0, 0, light);
			vertex(buffer, position, normal, new float[]{x0, y0, z0}, 0, 0, -1, 0, 0, light);
			vertex(buffer, position, normal, new float[]{x1, cy, cz}, 0.5f, 0.5f, 1, 0, 0, light);
			vertex(buffer, position, normal, new float[]{x1, y0, z0}, 0, 0, 1, 0, 0, light);
			vertex(buffer, position, normal, new float[]{x1, y1, z1}, 1, 0, 1, 0, 0, light);
			vertex(buffer, position, normal, new float[]{x1, y1, z1}, 1, 0, 1, 0, 0, light);
		}
	}

	private interface Plane {
		float[] at(float a, float b);
	}

	/**
	 * One face from (a0, b0) to (a1, b1) in its own two axes, cut into block-sized tiles. a may run either way; the corner order
	 * a0 then a1 is what makes the face point along its normal (emitted in reverse, so it is counter-clockwise from outside).
	 */
	private static void tiles(VertexConsumer buffer, Matrix4f position, Matrix3f normal, int light, float a0, float b0, float a1, float b1,
			Plane plane, float nx, float ny, float nz) {
		final float length = Math.abs(a1 - a0);
		final float height = Math.abs(b1 - b0);
		final int along = Math.max(1, MathHelper.ceil(length - 0.001f));
		final int up = Math.max(1, MathHelper.ceil(height - 0.001f));
		final float stepA = (a1 - a0) / along;
		final float stepB = (b1 - b0) / up;
		final float texA = Math.min(1f, length / along);
		final float texB = Math.min(1f, height / up);
		for (int i = 0; i < along; i++) {
			for (int j = 0; j < up; j++) {
				final float ta = a0 + stepA * i;
				final float tb = b0 + stepB * j;
				final float[] p0 = plane.at(ta, tb);
				final float[] p1 = plane.at(ta + stepA, tb);
				final float[] p2 = plane.at(ta + stepA, tb + stepB);
				final float[] p3 = plane.at(ta, tb + stepB);
				// counter-clockwise seen from outside, so the face survives back-face culling
				vertex(buffer, position, normal, p3, 0, 0, nx, ny, nz, light);
				vertex(buffer, position, normal, p2, texA, 0, nx, ny, nz, light);
				vertex(buffer, position, normal, p1, texA, texB, nx, ny, nz, light);
				vertex(buffer, position, normal, p0, 0, texB, nx, ny, nz, light);
			}
		}
	}

	private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float[] p, float u, float v, float nx, float ny, float nz, int light) {
		buffer.vertex(position, p[0], p[1], p[2]).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, nx, ny, nz).next();
	}

}
