package com.aureliatransit.architecture.client.bridge;

import com.aureliatransit.architecture.block.entity.BridgeMemberBlockEntity;
import com.aureliatransit.architecture.bridge.span.BridgeMember;
import com.aureliatransit.architecture.bridge.world.BridgeStateCodec;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a bridge member block: the block it stands in for, then each member as a straight square beam between its two ends,
 * lit from the air at the beam's middle (the anchor itself is usually buried in a pylon or a chord). Warning lights are drawn
 * full bright.
 */
public final class BridgeMemberRenderer implements BlockEntityRenderer<BridgeMemberBlockEntity> {

	private static final Map<BridgeMember.Material, Identifier> TEXTURES = new EnumMap<>(BridgeMember.Material.class);

	static {
		TEXTURES.put(BridgeMember.Material.CONCRETE, new Identifier("minecraft", "textures/block/light_gray_concrete.png"));
		TEXTURES.put(BridgeMember.Material.WHITE, new Identifier("minecraft", "textures/block/white_concrete.png"));
		TEXTURES.put(BridgeMember.Material.STEEL, new Identifier("minecraft", "textures/block/gray_concrete.png"));
		TEXTURES.put(BridgeMember.Material.CABLE, new Identifier("minecraft", "textures/block/black_concrete.png"));
		TEXTURES.put(BridgeMember.Material.RED, new Identifier("minecraft", "textures/block/red_terracotta.png"));
		TEXTURES.put(BridgeMember.Material.LIGHT, new Identifier("minecraft", "textures/block/red_concrete.png"));
		TEXTURES.put(BridgeMember.Material.STONE, new Identifier("minecraft", "textures/block/stone_bricks.png"));
	}

	private String skinText;
	private BlockState skinState;

	public BridgeMemberRenderer(BlockEntityRendererFactory.Context context) {
	}

	@Override
	public boolean rendersOutsideBoundingBox(BridgeMemberBlockEntity entity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 256;
	}

	@Override
	public void render(BridgeMemberBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
		final String skin = entity.skin();
		if (!skin.isEmpty()) {
			final BlockState state = skin(skin);
			if (state != null) {
				final BlockPos pos = entity.getPos();
				final int skinLight = entity.getWorld() == null ? light : WorldRenderer.getLightmapCoordinates(entity.getWorld(), pos.up());
				MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, vertexConsumers, skinLight, OverlayTexture.DEFAULT_UV);
			}
		}
		final BlockPos origin = entity.getPos();
		// one texture at a time: a buffer from the provider is only good until the next getBuffer call
		for (final BridgeMember.Material material : BridgeMember.Material.values()) {
			VertexConsumer buffer = null;
			for (final BridgeMember member : entity.members()) {
				if (member.material() != material) {
					continue;
				}
				if (buffer == null) {
					buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURES.get(material)));
				}
				final int lit = material == BridgeMember.Material.LIGHT || entity.getWorld() == null ? LightmapTextureManager.MAX_LIGHT_COORDINATE
						: WorldRenderer.getLightmapCoordinates(entity.getWorld(), BlockPos.ofFloored(
						origin.getX() + (member.x0() + member.x1()) / 2, origin.getY() + (member.y0() + member.y1()) / 2, origin.getZ() + (member.z0() + member.z1()) / 2));
				beam(matrices, buffer, member, lit);
			}
		}
	}

	/** The skin's block state, decoded once per text: most anchors on one bridge share it. */
	private BlockState skin(String text) {
		if (!text.equals(skinText)) {
			skinText = text;
			skinState = BridgeStateCodec.decode(text);
		}
		return skinState;
	}

	/** A square beam of the member's thickness from one end to the other, its sides square to the beam. */
	private static void beam(MatrixStack matrices, VertexConsumer buffer, BridgeMember m, int light) {
		final float dx = m.x1() - m.x0();
		final float dy = m.y1() - m.y0();
		final float dz = m.z1() - m.z0();
		final float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length < 1e-4f) {
			return;
		}
		final float ux = dx / length;
		final float uy = dy / length;
		final float uz = dz / length;
		// two directions across the beam: away from up (or x for a vertical beam), then round it
		float rx = 0;
		float ry = 1;
		float rz = 0;
		if (Math.abs(uy) > 0.9f) {
			rx = 1;
			ry = 0;
		}
		float ax = uy * rz - uz * ry;
		float ay = uz * rx - ux * rz;
		float az = ux * ry - uy * rx;
		final float an = (float) Math.sqrt(ax * ax + ay * ay + az * az);
		final float h = m.thickness() / 2;
		ax = ax / an * h;
		ay = ay / an * h;
		az = az / an * h;
		float bx = uy * az - uz * ay;
		float by = uz * ax - ux * az;
		float bz = ux * ay - uy * ax;
		final float bn = (float) Math.sqrt(bx * bx + by * by + bz * bz);
		bx = bx / bn * h;
		by = by / bn * h;
		bz = bz / bn * h;
		final float[][] corners = new float[8][];
		final float[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		for (int end = 0; end < 2; end++) {
			final float px = end == 0 ? m.x0() : m.x1();
			final float py = end == 0 ? m.y0() : m.y1();
			final float pz = end == 0 ? m.z0() : m.z1();
			for (int k = 0; k < 4; k++) {
				corners[end * 4 + k] = new float[]{px + signs[k][0] * ax + signs[k][1] * bx, py + signs[k][0] * ay + signs[k][1] * by, pz + signs[k][0] * az + signs[k][1] * bz};
			}
		}
		final Matrix4f position = matrices.peek().getPositionMatrix();
		final Matrix3f normal = matrices.peek().getNormalMatrix();
		final float v = Math.max(1, length / Math.max(m.thickness(), 1));
		// the four sides, then the two ends; each counter-clockwise seen from outside
		for (int k = 0; k < 4; k++) {
			final int n = (k + 1) % 4;
			final float[] a0 = corners[k];
			final float[] a1 = corners[n];
			final float[] b1 = corners[4 + n];
			final float[] b0 = corners[4 + k];
			final float nx = (a0[0] + a1[0]) / 2 - m.x0();
			final float ny = (a0[1] + a1[1]) / 2 - m.y0();
			final float nz = (a0[2] + a1[2]) / 2 - m.z0();
			quad(buffer, position, normal, a0, a1, b1, b0, nx, ny, nz, v, light);
		}
		quad(buffer, position, normal, corners[3], corners[2], corners[1], corners[0], -ux, -uy, -uz, 1, light);
		quad(buffer, position, normal, corners[4], corners[5], corners[6], corners[7], ux, uy, uz, 1, light);
	}

	/** One face; flips its winding when it would face away from its outward normal, so it survives back-face culling. */
	private static void quad(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float[] p0, float[] p1, float[] p2, float[] p3,
			float nx, float ny, float nz, float v, int light) {
		final float ex = p1[0] - p0[0];
		final float ey = p1[1] - p0[1];
		final float ez = p1[2] - p0[2];
		final float fx = p2[0] - p0[0];
		final float fy = p2[1] - p0[1];
		final float fz = p2[2] - p0[2];
		final float cx = ey * fz - ez * fy;
		final float cy = ez * fx - ex * fz;
		final float cz = ex * fy - ey * fx;
		final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		final float mx = length > 0 ? nx / length : 0;
		final float my = length > 0 ? ny / length : 1;
		final float mz = length > 0 ? nz / length : 0;
		if (cx * mx + cy * my + cz * mz >= 0) {
			vertex(buffer, position, normal, p0, 0, 0, mx, my, mz, light);
			vertex(buffer, position, normal, p1, 1, 0, mx, my, mz, light);
			vertex(buffer, position, normal, p2, 1, v, mx, my, mz, light);
			vertex(buffer, position, normal, p3, 0, v, mx, my, mz, light);
		} else {
			vertex(buffer, position, normal, p3, 0, v, mx, my, mz, light);
			vertex(buffer, position, normal, p2, 1, v, mx, my, mz, light);
			vertex(buffer, position, normal, p1, 1, 0, mx, my, mz, light);
			vertex(buffer, position, normal, p0, 0, 0, mx, my, mz, light);
		}
	}

	private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float[] p, float u, float v, float nx, float ny, float nz, int light) {
		buffer.vertex(position, p[0], p[1], p[2]).color(255, 255, 255, 255).texture(u, Math.min(v, 1)).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, nx, ny, nz).next();
	}
}
