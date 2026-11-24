package org.mtr.aurelia.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.OptimizedRenderer;
import org.mtr.mod.resource.VehiclePreviewModels;
import org.mtr.mod.resource.VehicleResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Draws trains in 3D inside a screen, standing on a short piece of track. It uses its own copy of MTR's fast model
 * renderer, so the preview never mixes with the trains in the world, and borrows the screen's projection.
 */
public final class TrainPreview {

	public enum State {DRAWN, LOADING, UNSUPPORTED}

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR Train Preview");
	private static final double GAUGE = 1.435;
	private static final int BALLAST = 0xFF3A3632;
	private static final int BALLAST_EDGE = 0xFF2B2826;
	private static final int SLEEPER = 0xFF5A4636;
	private static final int RAIL_TOP = 0xFFC9CED6;
	private static final int RAIL_SIDE = 0xFF6E6A66;
	/** About how tall a train is with its pantograph, in blocks; used to fit the stage. */
	private static final double TRAIN_HEIGHT = 4.6;

	private static OptimizedRenderer renderer;
	private static boolean failed;

	private TrainPreview() {
	}

	/**
	 * @param yaw   degrees; 90 shows the train side-on
	 * @param pitch degrees; positive looks down onto the roof
	 */
	public static State draw(DrawContext context, List<VehicleResource> cars, int x, int y, int width, int height, float yaw, float pitch) {
		if (cars.isEmpty()) {
			return State.DRAWN;
		}
		final OptimizedRenderer optimizedRenderer = renderer();
		if (optimizedRenderer == null) {
			return State.UNSUPPORTED;
		}

		final int count = cars.size();
		final VehiclePreviewModels[] models = new VehiclePreviewModels[count];
		final double[] centers = new double[count];
		double cursor = 0;
		double maxWidth = 1;
		for (int i = 0; i < count; i++) {
			final VehicleResource car = cars.get(i);
			models[i] = VehiclePreviewModels.of(car, i, count);
			if (models[i] == null) {
				return State.LOADING;
			}
			cursor += car.getCouplingPadding1();
			centers[i] = cursor + car.getLength() / 2;
			cursor += car.getLength() + car.getCouplingPadding2();
			maxWidth = Math.max(maxWidth, car.getWidth());
		}
		final double total = cursor;

		final double sin = Math.abs(Math.sin(Math.toRadians(yaw)));
		final double cos = Math.abs(Math.cos(Math.toRadians(yaw)));
		final double across = total * sin + (maxWidth + 1) * cos;
		final double deep = total * cos + (maxWidth + 1) * sin;
		// fill the stage, but keep the far end of the train inside the screen's depth range
		final float scale = (float) Math.max(1, Math.min(Math.min(width * 0.88 / across, height * 0.62 / TRAIN_HEIGHT), Math.min(48, 1300 / Math.max(1, deep))));

		context.draw();
		context.enableScissor(x, y, x + width, y + height);
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

		final MatrixStack matrices = new MatrixStack();
		matrices.peek().getPositionMatrix().set(RenderSystem.getModelViewMatrix()).mul(context.getMatrices().peek().getPositionMatrix());
		matrices.translate(x + width / 2F, y + height * 0.70F, 150);
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		// models are drawn as MTR draws them in the world, mirrored to suit the screen's downward y
		matrices.scale(scale, scale, -scale);
		matrices.translate(0, 0, -total / 2);

		final MatrixStack modelView = RenderSystem.getModelViewStack();
		modelView.push();
		modelView.loadIdentity();
		RenderSystem.applyModelViewMatrix();
		final float fogStart = RenderSystem.getShaderFogStart();
		RenderSystem.setShaderFogStart(Float.MAX_VALUE);
		final MinecraftClient client = MinecraftClient.getInstance();
		try {
			track(matrices.peek().getPositionMatrix(), total, maxWidth);

			DiffuseLighting.method_34742();
			client.gameRenderer.getLightmapTextureManager().enable();
			for (int i = 0; i < count; i++) {
				final VehicleResource car = cars.get(i);
				matrices.push();
				matrices.translate(0, 0, centers[i]);
				queue(matrices, models[i].body);
				queueAt(matrices, models[i].bogie1, car.getBogie1Position());
				queueAt(matrices, models[i].bogie2, car.getBogie2Position());
				matrices.pop();
			}
			optimizedRenderer.render(true);
		} catch (RuntimeException e) {
			LOGGER.error("[ATR] The train preview could not be drawn; it stays off until the game restarts", e);
			failed = true;
			renderer = null;
		} finally {
			client.gameRenderer.getLightmapTextureManager().disable();
			DiffuseLighting.enableGuiDepthLighting();
			RenderSystem.setShaderFogStart(fogStart);
			modelView.pop();
			RenderSystem.applyModelViewMatrix();
			RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
			context.disableScissor();
		}
		return failed ? State.UNSUPPORTED : State.DRAWN;
	}

	private static OptimizedRenderer renderer() {
		if (renderer == null && !failed && OptimizedRenderer.hasOptimizedRendering()) {
			try {
				final OptimizedRenderer newRenderer = new OptimizedRenderer();
				// compiles this renderer's own shader programs
				newRenderer.beginReload();
				newRenderer.finishReload();
				renderer = newRenderer;
			} catch (RuntimeException e) {
				LOGGER.error("[ATR] The train preview renderer could not start", e);
				failed = true;
			}
		}
		return renderer;
	}

	private static void queue(MatrixStack matrices, List<OptimizedModel> models) {
		if (!models.isEmpty()) {
			GraphicsHolder.createInstanceSafe(matrices, MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(), graphicsHolder -> models.forEach(model -> renderer.queue(model, graphicsHolder, 0xFFFFFFFF, LightmapTextureManager.MAX_LIGHT_COORDINATE)));
		}
	}

	private static void queueAt(MatrixStack matrices, List<OptimizedModel> models, double z) {
		matrices.push();
		matrices.translate(0, 0, z);
		queue(matrices, models);
		matrices.pop();
	}

	/** Ballast, sleepers and two rails under the whole train, a little longer at both ends. In model space y points down. */
	private static void track(Matrix4f matrix, double total, double carWidth) {
		final float start = (float) -2;
		final float end = (float) (total + 2);
		final float bed = (float) Math.max(1.6, carWidth / 2 + 0.2);
		final float rail = (float) (GAUGE / 2);

		RenderSystem.disableCull();
		RenderSystem.enableDepthTest();
		RenderSystem.setShader(GameRenderer::getPositionColorProgram);
		final BufferBuilder buffer = Tessellator.getInstance().getBuffer();
		buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
		flat(buffer, matrix, -bed - 0.35F, start, bed + 0.35F, end, 0.32F, BALLAST_EDGE);
		flat(buffer, matrix, -bed, start, bed, end, 0.2F, BALLAST);
		for (float z = start + 0.3F; z < end - 0.3F; z += 0.65F) {
			flat(buffer, matrix, -1.25F, z, 1.25F, z + 0.26F, 0.14F, SLEEPER);
		}
		for (final float side : new float[]{-rail, rail}) {
			flat(buffer, matrix, side - 0.04F, start, side + 0.04F, end, 0.004F, RAIL_TOP);
			side(buffer, matrix, side - 0.04F, start, end, 0.004F, 0.14F, RAIL_SIDE);
			side(buffer, matrix, side + 0.04F, start, end, 0.004F, 0.14F, RAIL_SIDE);
		}
		Tessellator.getInstance().draw();
		RenderSystem.enableCull();
	}

	private static void flat(BufferBuilder buffer, Matrix4f matrix, float x1, float z1, float x2, float z2, float y, int color) {
		vertex(buffer, matrix, x1, y, z1, color);
		vertex(buffer, matrix, x1, y, z2, color);
		vertex(buffer, matrix, x2, y, z2, color);
		vertex(buffer, matrix, x2, y, z1, color);
	}

	private static void side(BufferBuilder buffer, Matrix4f matrix, float x, float z1, float z2, float y1, float y2, int color) {
		vertex(buffer, matrix, x, y1, z1, color);
		vertex(buffer, matrix, x, y2, z1, color);
		vertex(buffer, matrix, x, y2, z2, color);
		vertex(buffer, matrix, x, y1, z2, color);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, int color) {
		buffer.vertex(matrix, x, y, z).color((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F, (color >>> 24) / 255F).next();
	}
}
