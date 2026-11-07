package org.mtr.mod.render;

import org.mtr.core.data.VehicleCar;
import org.mtr.mapping.holder.Direction;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mod.Init;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.data.IGui;

/**
 * Far train LOD tier: one plain box per car in the vehicle's colour, through MTR's own textured-quad path (batched by
 * texture, so all boxes together cost a few draw calls). The car's model is never requested.
 * Ported from Aurelia Transit Utilities 1.4.0 (TrainProxy).
 */
public final class TrainProxy implements IGui {

	private static final Identifier WHITE = new Identifier(Init.MOD_ID, "textures/block/white.png");
	/** Body from just above the rail to roof height, in blocks; MTR's car frame has -y pointing up. */
	private static final float BOTTOM = -0.5F;
	private static final float TOP = -3.75F;
	/** Gap between neighbouring boxes so a train still reads as separate cars. */
	private static final float END_GAP = 0.25F;

	private TrainProxy() {
	}

	public static void queue(VehicleCar vehicleCar, PositionAndRotation positionAndRotation, int rgb) {
		final float halfWidth = (float) Math.max(0.5, vehicleCar.getWidth() / 2);
		final float halfLength = (float) Math.max(0.5, vehicleCar.getLength() / 2 - END_GAP);
		final int color = ARGB_BLACK | rgb;
		// Slight face shading gives the box a roof and ends instead of a flat silhouette; it is still one box per car
		final int topColor = shade(color, 0.85F);
		final int endColor = shade(color, 0.9F);
		final int light = positionAndRotation.light;
		final StoredMatrixTransformations transformations = RenderVehicles.getStoredMatrixTransformations(true, positionAndRotation, 0);
		MainRenderer.scheduleRender(WHITE, false, QueuedRenderLayer.EXTERIOR, (graphicsHolder, offset) -> {
			transformations.transform(graphicsHolder, offset);
			// Each face is drawn with both windings so it shows whatever the face culling does
			face(graphicsHolder, -halfWidth, TOP, -halfLength, halfWidth, TOP, -halfLength, halfWidth, TOP, halfLength, -halfWidth, TOP, halfLength, Direction.UP, topColor, light);
			face(graphicsHolder, -halfWidth, BOTTOM, -halfLength, -halfWidth, BOTTOM, halfLength, -halfWidth, TOP, halfLength, -halfWidth, TOP, -halfLength, Direction.WEST, color, light);
			face(graphicsHolder, halfWidth, BOTTOM, -halfLength, halfWidth, BOTTOM, halfLength, halfWidth, TOP, halfLength, halfWidth, TOP, -halfLength, Direction.EAST, color, light);
			face(graphicsHolder, -halfWidth, BOTTOM, -halfLength, halfWidth, BOTTOM, -halfLength, halfWidth, TOP, -halfLength, -halfWidth, TOP, -halfLength, Direction.NORTH, endColor, light);
			face(graphicsHolder, -halfWidth, BOTTOM, halfLength, halfWidth, BOTTOM, halfLength, halfWidth, TOP, halfLength, -halfWidth, TOP, halfLength, Direction.SOUTH, endColor, light);
			graphicsHolder.pop();
		});
	}

	private static int shade(int color, float factor) {
		final int alpha = color & 0xFF000000;
		final int red = (int) (((color >> 16) & 0xFF) * factor);
		final int green = (int) (((color >> 8) & 0xFF) * factor);
		final int blue = (int) ((color & 0xFF) * factor);
		return alpha | red << 16 | green << 8 | blue;
	}

	private static void face(GraphicsHolder graphicsHolder, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, Direction direction, int color, int light) {
		IDrawing.drawTexture(graphicsHolder, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, 0, 0, 1, 1, direction, color, light);
		IDrawing.drawTexture(graphicsHolder, x4, y4, z4, x3, y3, z3, x2, y2, z2, x1, y1, z1, 0, 0, 1, 1, direction, color, light);
	}
}
