package com.aureliatransit.architecture.client.bridge;

import com.aureliatransit.architecture.block.entity.BridgeMemberBlockEntity;
import com.aureliatransit.architecture.bridge.span.BridgeMember;
import com.aureliatransit.architecture.bridge.world.BridgeStateCodec;
import com.aureliatransit.architecture.client.WrenchSettings;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a bridge member block: the block it stands in for, then each member as a straight square beam between its two ends,
 * lit from the air at the beam's middle (the anchor itself is usually buried in a pylon or a chord). Warning lights are drawn
 * full bright. Paint is a flat strip on the ground, shown and sized from the wrench's airport page.
 */
public final class BridgeMemberRenderer implements BlockEntityRenderer<BridgeMemberBlockEntity> {

	/** A lamp that is on: plain white, tinted {@link #LAMP_RED}. */
	private static final Identifier LAMP_ON = new Identifier("minecraft", "textures/block/white_concrete.png");
	/** A lit lamp's halo: white in the middle, fading to nothing at the edge. */
	private static final Identifier HALO_TEXTURE = new Identifier("aurelia_transit_architecture", "textures/misc/lamp_glow.png");
	private static final Map<BridgeMember.Material, Identifier> TEXTURES = new EnumMap<>(BridgeMember.Material.class);

	static {
		TEXTURES.put(BridgeMember.Material.CONCRETE, new Identifier("minecraft", "textures/block/light_gray_concrete.png"));
		TEXTURES.put(BridgeMember.Material.WHITE, new Identifier("minecraft", "textures/block/white_concrete.png"));
		TEXTURES.put(BridgeMember.Material.STEEL, new Identifier("minecraft", "textures/block/gray_concrete.png"));
		TEXTURES.put(BridgeMember.Material.CABLE, new Identifier("minecraft", "textures/block/black_concrete.png"));
		TEXTURES.put(BridgeMember.Material.RED, new Identifier("minecraft", "textures/block/red_terracotta.png"));
		TEXTURES.put(BridgeMember.Material.LIGHT, new Identifier("minecraft", "textures/block/red_concrete.png"));
		TEXTURES.put(BridgeMember.Material.STONE, new Identifier("minecraft", "textures/block/stone_bricks.png"));
		TEXTURES.put(BridgeMember.Material.PAINT, new Identifier("minecraft", "textures/block/yellow_concrete.png"));
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
		final World world = entity.getWorld();
		final BlockPos origin = entity.getPos();
		final String skin = entity.skin();
		if (!skin.isEmpty()) {
			final BlockState state = skin(skin);
			if (state != null) {
				// an anchor inside a tower is walled in; light it as its brightest open side is lit, or it draws black
				final int skinLight = world == null ? light : brightest(world, origin);
				MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(state, matrices, vertexConsumers, skinLight, OverlayTexture.DEFAULT_UV);
			}
		}
		final Vec3d eye = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
		final float pixel = pixelPerBlock();
		// one texture at a time: a buffer from the provider is only good until the next getBuffer call
		for (final BridgeMember.Material material : BridgeMember.Material.values()) {
			if (material == BridgeMember.Material.PAINT && !WrenchSettings.curvePaint() || material == BridgeMember.Material.LIGHT) {
				continue;
			}
			VertexConsumer buffer = null;
			for (final BridgeMember member : entity.members()) {
				if (member.material() != material) {
					continue;
				}
				if (buffer == null) {
					buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURES.get(material)));
				}
				final int lit = world == null ? LightmapTextureManager.MAX_LIGHT_COORDINATE : brightest(world, BlockPos.ofFloored(
						origin.getX() + (member.x0() + member.x1()) / 2, origin.getY() + (member.y0() + member.y1()) / 2, origin.getZ() + (member.z0() + member.z1()) / 2));
				if (material == BridgeMember.Material.PAINT) {
					strip(matrices, buffer, member, member.thickness() * WrenchSettings.paintWidth() / 100f, lit);
				} else {
					beam(matrices, buffer, member, visible(member, origin, eye, pixel), lit, PLAIN);
				}
			}
		}
		lights(entity, world, tickDelta, matrices, vertexConsumers, light, origin, eye, pixel);
	}

	/** Ticks in one flash of a tower's warning light, and how many of them it is lit: about a flash every two seconds. */
	private static final int FLASH_PERIOD = 40;
	private static final int FLASH_ON = 14;
	/** A lit lamp's halo is at least this many blocks across, and never under {@link #HALO_PIXELS} on screen. */
	private static final float HALO = 4.0f;
	private static final float HALO_PIXELS = 64;
	/** Lamps are drawn this much bigger than planned, so a tower top 60 blocks off still shows one. */
	private static final float LAMP_SCALE = 1.6f;
	/** No tint. */
	private static final int PLAIN = 0xFFFFFFFF;
	/** A warning lamp's red, as a tint over a white texture: brighter than any red block. */
	private static final int LAMP_RED = 0xFFFF2A1E;

	/**
	 * The red warning lamps on towers and pylons. By day they are plain red; at night they burn at full brightness, flash
	 * (unless the wrench's settings hold them steady) and carry a halo that always faces the camera, so a tower top far off
	 * still shows a point of light the way a real one does.
	 */
	private static void lights(BridgeMemberBlockEntity entity, World world, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			BlockPos origin, Vec3d eye, float pixel) {
		final int mode = WrenchSettings.towerLights();
		if (mode == 0) {
			return;
		}
		final boolean night = world != null && isNight(world.getSkyAngle(tickDelta));
		final boolean lit = night && (mode == 1 || Math.floorMod(world.getTime(), FLASH_PERIOD) < FLASH_ON);
		VertexConsumer buffer = null;
		for (final BridgeMember member : entity.members()) {
			if (member.material() != BridgeMember.Material.LIGHT) {
				continue;
			}
			if (buffer == null) {
				buffer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TEXTURES.get(BridgeMember.Material.LIGHT)));
			}
			beam(matrices, buffer, member, member.thickness() * LAMP_SCALE, light, PLAIN);
		}
		if (buffer == null || !lit) {
			return;
		}
		// the lit face is added on top of the red block (the eyes layer adds light and ignores the world's), so it burns brighter
		// than anything the world lights
		final VertexConsumer face = vertexConsumers.getBuffer(RenderLayer.getEyes(LAMP_ON));
		for (final BridgeMember member : entity.members()) {
			if (member.material() == BridgeMember.Material.LIGHT) {
				beam(matrices, face, member, member.thickness() * LAMP_SCALE * 1.05f, LightmapTextureManager.MAX_LIGHT_COORDINATE, LAMP_RED);
			}
		}
		final VertexConsumer halo = vertexConsumers.getBuffer(RenderLayer.getEyes(HALO_TEXTURE));
		final Quaternionf facing = MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();
		final Vector3f right = new Vector3f(1, 0, 0).rotate(facing);
		final Vector3f up = new Vector3f(0, 1, 0).rotate(facing);
		for (final BridgeMember member : entity.members()) {
			if (member.material() == BridgeMember.Material.LIGHT) {
				final float cx = (member.x0() + member.x1()) / 2;
				final float cy = (member.y0() + member.y1()) / 2;
				final float cz = (member.z0() + member.z1()) / 2;
				final Vec3d toEye = eye.subtract(origin.getX() + cx, origin.getY() + cy, origin.getZ() + cz);
				final float h = Math.max(HALO, (float) toEye.length() * pixel * HALO_PIXELS) / 2;
				final float rx = right.x() * h;
				final float ry = right.y() * h;
				final float rz = right.z() * h;
				final float ux = up.x() * h;
				final float uy = up.y() * h;
				final float uz = up.z() * h;
				quad(halo, matrices.peek().getPositionMatrix(), matrices.peek().getNormalMatrix(),
						new float[]{cx - rx - ux, cy - ry - uy, cz - rz - uz}, new float[]{cx + rx - ux, cy + ry - uy, cz + rz - uz},
						new float[]{cx + rx + ux, cy + ry + uy, cz + rz + uz}, new float[]{cx - rx + ux, cy - ry + uy, cz - rz + uz},
						(float) toEye.x, (float) toEye.y, (float) toEye.z, 1, LightmapTextureManager.MAX_LIGHT_COORDINATE, LAMP_RED);
			}
		}
	}

	/** True between dusk and dawn, from the sun's angle (0 at noon, 0.5 at midnight). */
	static boolean isNight(float skyAngle) {
		return skyAngle > 0.23f && skyAngle < 0.77f;
	}

	/** The lightmap value of the brightest of a block and its six neighbours, sky and block light taken apart. */
	private static int brightest(World world, BlockPos pos) {
		int sky = 0;
		int block = 0;
		for (int i = -1; i < 6; i++) {
			final int packed = WorldRenderer.getLightmapCoordinates(world, i < 0 ? pos : pos.offset(Direction.byId(i)));
			sky = Math.max(sky, LightmapTextureManager.getSkyLightCoordinates(packed));
			block = Math.max(block, LightmapTextureManager.getBlockLightCoordinates(packed));
		}
		return LightmapTextureManager.pack(block, sky);
	}

	/** The skin's block state, decoded once per text: most anchors on one bridge share it. */
	private BlockState skin(String text) {
		if (!text.equals(skinText)) {
			skinText = text;
			skinState = BridgeStateCodec.decode(text);
		}
		return skinState;
	}

	/** Seam cover at each end of a paint strip, so the outside of a bend has no gap between pieces. */
	private static final float STRIP_OVERLAP = 0.03f;

	/** A flat strip lying level from one end to the other, {@code width} across, facing up. */
	private static void strip(MatrixStack matrices, VertexConsumer buffer, BridgeMember m, float width, int light) {
		final float dx = m.x1() - m.x0();
		final float dz = m.z1() - m.z0();
		final float length = (float) Math.sqrt(dx * dx + dz * dz);
		if (length < 1e-4f) {
			return;
		}
		final float ux = dx / length;
		final float uz = dz / length;
		final float ax = -uz * width / 2;
		final float az = ux * width / 2;
		final float sx = m.x0() - ux * STRIP_OVERLAP;
		final float sz = m.z0() - uz * STRIP_OVERLAP;
		final float ex = m.x1() + ux * STRIP_OVERLAP;
		final float ez = m.z1() + uz * STRIP_OVERLAP;
		final float y = m.y0();
		quad(buffer, matrices.peek().getPositionMatrix(), matrices.peek().getNormalMatrix(), new float[]{sx - ax, y, sz - az}, new float[]{sx + ax, y, sz + az},
				new float[]{ex + ax, y, ez + az}, new float[]{ex - ax, y, ez - az}, 0, 1, 0, 1, light, PLAIN);
	}

	/** Under this many blocks thick a member is a wire or a stay, which a screen pixel can be wider than. */
	private static final float THIN = 0.2f;
	/** How many screen pixels a far wire keeps; under one it breaks into dashes. */
	private static final float MIN_PIXELS = 1.2f;

	/** Blocks of width per block of distance that one screen pixel covers, from the window's height and the field of view. */
	private static float pixelPerBlock() {
		final MinecraftClient client = MinecraftClient.getInstance();
		final int lines = Math.max(240, client.getWindow().getFramebufferHeight());
		final double fov = Math.toRadians(client.options.getFov().getValue());
		return (float) (2 * Math.tan(fov / 2) / lines);
	}

	/** The member's thickness, widened for a thin one far enough away that it would draw thinner than a pixel or so. */
	private static float visible(BridgeMember m, BlockPos origin, Vec3d eye, float pixel) {
		if (m.thickness() >= THIN) {
			return m.thickness();
		}
		final double far = Math.max(eye.distanceTo(new Vec3d(origin.getX() + m.x0(), origin.getY() + m.y0(), origin.getZ() + m.z0())),
				eye.distanceTo(new Vec3d(origin.getX() + m.x1(), origin.getY() + m.y1(), origin.getZ() + m.z1())));
		return Math.min(THIN, Math.max(m.thickness(), (float) far * pixel * MIN_PIXELS));
	}

	/** A square beam {@code thickness} across from one end of the member to the other, its sides square to the beam. */
	private static void beam(MatrixStack matrices, VertexConsumer buffer, BridgeMember m, float thickness, int light, int argb) {
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
		final float h = thickness / 2;
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
		final float v = Math.max(1, length / Math.max(thickness, 1));
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
			quad(buffer, position, normal, a0, a1, b1, b0, nx, ny, nz, v, light, argb);
		}
		quad(buffer, position, normal, corners[3], corners[2], corners[1], corners[0], -ux, -uy, -uz, 1, light, argb);
		quad(buffer, position, normal, corners[4], corners[5], corners[6], corners[7], ux, uy, uz, 1, light, argb);
	}

	/** One face; flips its winding when it would face away from its outward normal, so it survives back-face culling. */
	private static void quad(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float[] p0, float[] p1, float[] p2, float[] p3,
			float nx, float ny, float nz, float v, int light, int argb) {
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
			vertex(buffer, position, normal, p0, 0, 0, mx, my, mz, light, argb);
			vertex(buffer, position, normal, p1, 1, 0, mx, my, mz, light, argb);
			vertex(buffer, position, normal, p2, 1, v, mx, my, mz, light, argb);
			vertex(buffer, position, normal, p3, 0, v, mx, my, mz, light, argb);
		} else {
			vertex(buffer, position, normal, p3, 0, v, mx, my, mz, light, argb);
			vertex(buffer, position, normal, p2, 1, v, mx, my, mz, light, argb);
			vertex(buffer, position, normal, p1, 1, 0, mx, my, mz, light, argb);
			vertex(buffer, position, normal, p0, 0, 0, mx, my, mz, light, argb);
		}
	}

	private static void vertex(VertexConsumer buffer, Matrix4f position, Matrix3f normal, float[] p, float u, float v, float nx, float ny, float nz, int light, int argb) {
		buffer.vertex(position, p[0], p[1], p[2]).color(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, argb >>> 24).texture(u, Math.min(v, 1)).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, nx, ny, nz).next();
	}
}
