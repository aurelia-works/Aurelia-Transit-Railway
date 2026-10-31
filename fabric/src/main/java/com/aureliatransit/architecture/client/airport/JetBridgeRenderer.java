package com.aureliatransit.architecture.client.airport;

import com.aureliatransit.architecture.block.JetBridgeBlock;
import com.aureliatransit.architecture.block.entity.JetBridgeBlockEntity;
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
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The telescoping tunnel. The block itself is the retracted cab; this draws the walkway out of its north face, rotated
 * to the block's facing, by {@link #EXTEND_BLOCKS} blocks when the extension is 1.
 */
public final class JetBridgeRenderer implements BlockEntityRenderer<JetBridgeBlockEntity> {

	/** How far a fully extended tunnel reaches, in blocks. The nose stop is 3 blocks past the cab; the door sits behind the nose. */
	static final float EXTEND_BLOCKS = 8f;
	private static final Identifier STEEL = new Identifier("minecraft", "textures/block/iron_block.png");

	public JetBridgeRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public boolean rendersOutsideBoundingBox(JetBridgeBlockEntity entity) {
		return entity.extension() > 0.01f;
	}

	@Override
	public int getRenderDistance() {
		return 96;
	}

	@Override
	public void render(JetBridgeBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		if (entity.getWorld() != null) {
			entity.advance(entity.getWorld().getTime());
		}
		final float extension = entity.extension();
		if (extension <= 0.001f) {
			return;
		}
		final BlockState state = entity.getCachedState();
		if (!(state.getBlock() instanceof JetBridgeBlock)) {
			return;
		}
		final float length = extension * EXTEND_BLOCKS;
		final VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(STEEL));
		matrices.push();
		matrices.translate(0.5, 0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90F * Shapes.quarterTurns(state.get(JetBridgeBlock.FACING))));
		matrices.translate(-0.5, 0, -0.5);
		// Model north is -Z. The tunnel leaves through that face.
		cuboid(matrices, buffer, 0.15f, 0.0f, -length, 0.85f, 0.12f, 0.0f, light);
		cuboid(matrices, buffer, 0.15f, 0.12f, -length, 0.27f, 0.92f, 0.0f, light);
		cuboid(matrices, buffer, 0.73f, 0.12f, -length, 0.85f, 0.92f, 0.0f, light);
		cuboid(matrices, buffer, 0.15f, 0.88f, -length, 0.85f, 1.0f, 0.0f, light);
		matrices.pop();
	}

	private static void cuboid(MatrixStack matrices, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
		final Matrix4f position = matrices.peek().getPositionMatrix();
		final Matrix3f normal = matrices.peek().getNormalMatrix();
		quad(buffer, position, normal, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0, light);
		quad(buffer, position, normal, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0, light);
		quad(buffer, position, normal, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, light);
		quad(buffer, position, normal, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, light);
		quad(buffer, position, normal, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, light);
		quad(buffer, position, normal, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, light);
	}

	private static void quad(VertexConsumer buffer, Matrix4f position, Matrix3f normal,
			float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
			float nx, float ny, float nz, int light) {
		vertex(buffer, position, normal, x0, y0, z0, 0, 1, nx, ny, nz, light);
		vertex(buffer, position, normal, x1, y1, z1, 1, 1, nx, ny, nz, light);
		vertex(buffer, position, normal, x2, y2, z2, 1, 0, nx, ny, nz, light);
		vertex(buffer, position, normal, x3, y3, z3, 0, 0, nx, ny, nz, light);
	}

	private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
		buffer.vertex(position, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, nx, ny, nz).next();
	}
}
