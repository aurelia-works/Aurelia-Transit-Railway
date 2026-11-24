package org.mtr.aurelia.crossing.client;

import com.aureliatransit.architecture.util.Shapes;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.mtr.aurelia.crossing.LevelCrossingBarrierBlock;
import org.mtr.aurelia.crossing.LevelCrossingBarrierBlockEntity;

/**
 * A barrier's arm: red and white stripes half a block long, {@code length} blocks from the pivot on top of the post,
 * with a short black counterweight behind it. Drawn facing north and turned to the block's facing; it swings about the
 * pivot from flat over the road (down) to upright (raised).
 */
public final class LevelCrossingBarrierRenderer implements BlockEntityRenderer<LevelCrossingBarrierBlockEntity> {

	private static final Identifier WHITE = new Identifier("minecraft", "textures/block/white_concrete.png");
	private static final Identifier RED = new Identifier("minecraft", "textures/block/red_concrete.png");
	private static final Identifier BLACK = new Identifier("minecraft", "textures/block/black_concrete.png");
	private static final float STRIPE = 0.5F;
	private static final float COUNTERWEIGHT = 0.4F;

	public LevelCrossingBarrierRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public boolean rendersOutsideBoundingBox(LevelCrossingBarrierBlockEntity entity) {
		return true;
	}

	@Override
	public void render(LevelCrossingBarrierBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof LevelCrossingBarrierBlock)) {
			return;
		}
		final Direction facing = state.get(LevelCrossingBarrierBlock.FACING);
		final int length = state.get(LevelCrossingBarrierBlock.LENGTH);
		final float angle = entity.angle(state.get(LevelCrossingBarrierBlock.LOWERED), Util.getMeasuringTimeMs());
		final float half = LevelCrossingBarrierBlock.ARM / 2;

		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(facing)));
		matrices.translate(0, LevelCrossingBarrierBlock.PIVOT, 0);
		// the arm lies along -z (north); turning about x lifts its far end
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
		final int stripes = Math.round(length / STRIPE);
		// one texture at a time: a buffer from the provider is only good until the next getBuffer call
		for (final Identifier texture : new Identifier[]{WHITE, RED}) {
			final VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(texture));
			for (int i = texture == WHITE ? 0 : 1; i < stripes; i += 2) {
				box(matrices, buffer, -half, -half, -(i + 1) * STRIPE, half, half, -i * STRIPE, light);
			}
		}
		box(matrices, vertexConsumers.getBuffer(RenderLayer.getEntitySolid(BLACK)), -half * 1.6F, -half * 1.6F, 0.1F, half * 1.6F, half * 1.6F,
				0.1F + COUNTERWEIGHT, light);
		matrices.pop();
	}

	/** A box from (x0, y0, z0) to (x1, y1, z1), each face one quad with its texture stretched over it. */
	private static void box(MatrixStack matrices, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
		final Matrix4f position = matrices.peek().getPositionMatrix();
		final Matrix3f normal = matrices.peek().getNormalMatrix();
		// counter-clockwise seen from outside, so each face survives back-face culling
		quad(buffer, position, normal, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(buffer, position, normal, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		quad(buffer, position, normal, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		quad(buffer, position, normal, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(buffer, position, normal, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(buffer, position, normal, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
	}

	private static void quad(VertexConsumer buffer, Matrix4f position, Matrix3f normal, int light, float nx, float ny, float nz, float... corners) {
		final float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
		for (int i = 0; i < 4; i++) {
			buffer.vertex(position, corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2]).color(255, 255, 255, 255).texture(uv[i][0], uv[i][1])
					.overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, nx, ny, nz).next();
		}
	}
}
