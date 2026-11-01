package org.mtr.aurelia.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.mtr.mod.zip.ZipCurve;
import org.mtr.mod.zip.ZipLines;

/**
 * Cables as line segments. Far ones are skipped, mid ones use 4 segments, near ones use 12. The point buffer is reused.
 */
public final class ZipCableRenderer {

	private static final double FAR = 96;
	private static final double MID = 48;
	private static final double SEAT = 0.28;

	private final double[] point = new double[3];
	private double previousX;
	private double previousY;
	private double previousZ;

	private ZipCableRenderer() {
	}

	public static void register() {
		final ZipCableRenderer renderer = new ZipCableRenderer();
		WorldRenderEvents.BEFORE_DEBUG_RENDER.register(renderer::render);
	}

	private void render(WorldRenderContext context) {
		if (!ZipClient.settings().enabled() || context.consumers() == null || context.camera() == null || context.matrixStack() == null) {
			return;
		}
		final int count = ZipClient.count();
		if (count == 0) {
			return;
		}
		final Vec3d camera = context.camera().getPos();
		final MatrixStack matrices = context.matrixStack();
		final VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
		final double sag = ZipClient.settings().sag();
		final long now = System.nanoTime();
		final ZipClient.View[] views = ZipClient.views();
		for (int i = 0; i < count; i++) {
			final ZipClient.View view = views[i];
			final double x0 = view.ax() + 0.5;
			final double y0 = view.ay() + 1;
			final double z0 = view.az() + 0.5;
			final double x1 = view.bx() + 0.5;
			final double y1 = view.by() + 1;
			final double z1 = view.bz() + 0.5;
			final double midX = (x0 + x1) * 0.5;
			final double midY = (y0 + y1) * 0.5;
			final double midZ = (z0 + z1) * 0.5;
			final double distance = distance(camera, midX, midY, midZ);
			if (distance > FAR) {
				continue;
			}
			final int segments = distance > MID ? 4 : 12;
			ZipCurve.point(0, x0, y0, z0, x1, y1, z1, sag, point);
			previousX = point[0];
			previousY = point[1];
			previousZ = point[2];
			for (int segment = 1; segment <= segments; segment++) {
				ZipCurve.point(segment / (double) segments, x0, y0, z0, x1, y1, z1, sag, point);
				line(matrices, lines, camera, previousX, previousY, previousZ, point[0], point[1], point[2]);
				previousX = point[0];
				previousY = point[1];
				previousZ = point[2];
			}
			if (view.seat()) {
				ZipCurve.point(view.seatT(now), x0, y0, z0, x1, y1, z1, sag, point);
				final double y = point[1] - ZipLines.SEAT_HANG;
				WorldRenderer.drawBox(matrices, lines,
						point[0] - camera.x - SEAT, y - camera.y, point[2] - camera.z - SEAT,
						point[0] - camera.x + SEAT, y - camera.y + 0.35, point[2] - camera.z + SEAT,
						0.75F, 0.55F, 0.25F, 1.0F);
			}
		}
	}

	private static void line(MatrixStack matrices, VertexConsumer lines, Vec3d camera, double x0, double y0, double z0, double x1, double y1, double z1) {
		final Matrix4f matrix = matrices.peek().getPositionMatrix();
		final float nx = (float) (x1 - x0);
		final float ny = (float) (y1 - y0);
		final float nz = (float) (z1 - z0);
		lines.vertex(matrix, (float) (x0 - camera.x), (float) (y0 - camera.y), (float) (z0 - camera.z)).color(30, 32, 36, 255).normal(nx, ny, nz).next();
		lines.vertex(matrix, (float) (x1 - camera.x), (float) (y1 - camera.y), (float) (z1 - camera.z)).color(30, 32, 36, 255).normal(nx, ny, nz).next();
	}

	private static double distance(Vec3d camera, double x, double y, double z) {
		final double dx = x - camera.x;
		final double dy = y - camera.y;
		final double dz = z - camera.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
