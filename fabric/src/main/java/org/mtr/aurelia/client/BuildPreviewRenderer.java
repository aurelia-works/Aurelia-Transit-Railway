package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.mtr.mod.building.BuildMath;
import org.mtr.mod.building.BuildSettings;

/**
 * Station footprint, tunnel box and rail curve. One reused point and one reused aim buffer. Skipped past 96 blocks.
 */
public final class BuildPreviewRenderer {

	private static final double FAR = 96;
	private static final double[] POINT = new double[3];
	private static final int[] AIM = new int[2];

	private BuildPreviewRenderer() {
	}

	public static void register() {
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(BuildPreviewRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		if (context.consumers() == null || context.camera() == null || context.matrixStack() == null) {
			return;
		}
		final BuildSettings settings = BuildClient.settings();
		final Vec3d camera = context.camera().getPos();
		final MatrixStack matrices = context.matrixStack();
		final VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
		final Matrix4f matrix = matrices.peek().getPositionMatrix();
		if (settings.stationEnabled() && BuildClient.stationA()) {
			final int x1 = BuildClient.stationB() ? BuildClient.sbx() : BuildClient.sax();
			final int z1 = BuildClient.stationB() ? BuildClient.sbz() : BuildClient.saz();
			final double y = BuildClient.say() + 1.02;
			if (near(camera, BuildClient.sax(), y, BuildClient.saz())) {
				rect(matrix, lines, camera, BuildClient.sax(), x1, BuildClient.saz(), z1, y, 61, 126, 255);
			}
		}
		if (settings.tunnelEnabled() && BuildClient.tunnelA()) {
			final int x1 = BuildClient.tunnelB() ? BuildClient.tbx() : BuildClient.tax();
			final int y1 = BuildClient.tunnelB() ? BuildClient.tby() : BuildClient.tay();
			final int z1 = BuildClient.tunnelB() ? BuildClient.tbz() : BuildClient.taz();
			if (near(camera, BuildClient.tax(), BuildClient.tay(), BuildClient.taz())) {
				box(matrix, lines, camera, BuildClient.tax(), BuildClient.tay(), BuildClient.taz(), x1, y1, z1);
			}
		}
		if (settings.railEnabled() && BuildClient.rail()) {
			final MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || !near(camera, BuildClient.rx(), BuildClient.ry(), BuildClient.rz())) {
				return;
			}
			int distance = Math.min(8, settings.railReach());
			int y = BuildClient.ry();
			if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK && client.crosshairTarget instanceof BlockHitResult hit) {
				final double dx = hit.getBlockPos().getX() - BuildClient.rx();
				final double dz = hit.getBlockPos().getZ() - BuildClient.rz();
				distance = Math.max(1, Math.min(settings.railReach(), (int) Math.round(Math.sqrt(dx * dx + dz * dz))));
				final int dy = hit.getBlockPos().getY() - BuildClient.ry();
				if (dy > 8) {
					y = BuildClient.ry() + 8;
				} else if (dy < -8) {
					y = BuildClient.ry() - 8;
				} else {
					y = hit.getBlockPos().getY();
				}
			}
			final int yaw = BuildMath.snapYaw(client.player.getYaw(), settings.snapDegrees());
			BuildMath.aim(distance, yaw, AIM);
			final double x0 = BuildClient.rx() + 0.5;
			final double y0 = BuildClient.ry() + 0.2;
			final double z0 = BuildClient.rz() + 0.5;
			final double x1 = BuildClient.rx() + AIM[0] + 0.5;
			final double y1 = y + 0.2;
			final double z1 = BuildClient.rz() + AIM[1] + 0.5;
			final int endYaw = yaw;
			BuildMath.curve(0, x0, y0, z0, x1, y1, z1, Math.round(BuildClient.railYaw()), endYaw, POINT);
			double px = POINT[0];
			double py = POINT[1];
			double pz = POINT[2];
			for (int i = 1; i <= BuildMath.CURVE_SAMPLES; i++) {
				BuildMath.curve(i / (double) BuildMath.CURVE_SAMPLES, x0, y0, z0, x1, y1, z1, Math.round(BuildClient.railYaw()), endYaw, POINT);
				line(matrix, lines, camera, px, py, pz, POINT[0], POINT[1], POINT[2], 80, 200, 120);
				px = POINT[0];
				py = POINT[1];
				pz = POINT[2];
			}
		}
	}

	private static void rect(Matrix4f matrix, VertexConsumer lines, Vec3d camera, int x0, int x1, int z0, int z1, double y, int r, int g, int b) {
		final double minX = Math.min(x0, x1);
		final double maxX = Math.max(x0, x1) + 1;
		final double minZ = Math.min(z0, z1);
		final double maxZ = Math.max(z0, z1) + 1;
		line(matrix, lines, camera, minX, y, minZ, maxX, y, minZ, r, g, b);
		line(matrix, lines, camera, maxX, y, minZ, maxX, y, maxZ, r, g, b);
		line(matrix, lines, camera, maxX, y, maxZ, minX, y, maxZ, r, g, b);
		line(matrix, lines, camera, minX, y, maxZ, minX, y, minZ, r, g, b);
	}

	private static void box(Matrix4f matrix, VertexConsumer lines, Vec3d camera, int x0, int y0, int z0, int x1, int y1, int z1) {
		final double minX = Math.min(x0, x1);
		final double minY = Math.min(y0, y1);
		final double minZ = Math.min(z0, z1);
		final double maxX = Math.max(x0, x1) + 1;
		final double maxY = Math.max(y0, y1) + 1;
		final double maxZ = Math.max(z0, z1) + 1;
		rect(matrix, lines, camera, (int) minX, (int) maxX - 1, (int) minZ, (int) maxZ - 1, minY, 220, 170, 60);
		rect(matrix, lines, camera, (int) minX, (int) maxX - 1, (int) minZ, (int) maxZ - 1, maxY, 220, 170, 60);
		line(matrix, lines, camera, minX, minY, minZ, minX, maxY, minZ, 220, 170, 60);
		line(matrix, lines, camera, maxX, minY, minZ, maxX, maxY, minZ, 220, 170, 60);
		line(matrix, lines, camera, maxX, minY, maxZ, maxX, maxY, maxZ, 220, 170, 60);
		line(matrix, lines, camera, minX, minY, maxZ, minX, maxY, maxZ, 220, 170, 60);
	}

	private static void line(Matrix4f matrix, VertexConsumer lines, Vec3d camera, double x0, double y0, double z0, double x1, double y1, double z1, int r, int g, int b) {
		final float nx = (float) (x1 - x0);
		final float ny = (float) (y1 - y0);
		final float nz = (float) (z1 - z0);
		lines.vertex(matrix, (float) (x0 - camera.x), (float) (y0 - camera.y), (float) (z0 - camera.z)).color(r, g, b, 255).normal(nx, ny, nz).next();
		lines.vertex(matrix, (float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z)).color(r, g, b, 255).normal(nx, ny, nz).next();
	}

	private static boolean near(Vec3d camera, double x, double y, double z) {
		final double dx = x - camera.x;
		final double dy = y - camera.y;
		final double dz = z - camera.z;
		return dx * dx + dy * dy + dz * dz <= FAR * FAR;
	}
}
