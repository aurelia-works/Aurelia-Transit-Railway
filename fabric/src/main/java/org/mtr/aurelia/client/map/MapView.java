package org.mtr.aurelia.client.map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.mtr.aurelia.map.MapCamera;
import org.mtr.aurelia.map.MapRegion;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;

/**
 * Draws a map view into a screen rectangle: the explored ground, then the rails, stations and trains the Map tab
 * switches on, then the player. The big map and the minimap both draw through here, so they always agree.
 */
public final class MapView {

	private static final int BACKGROUND = 0xFF111111;
	private static final int PLATFORM = 0xFFFFFFFF;
	private static final int SIDING = 0xFF9AA0A6;
	private static final int BOAT = 0xFF4AA3DF;
	private static final int RUNWAY = 0xFF6B7078;
	private static final int OUTLINE = 0xFF000000;
	/** New regions read from disk per frame, so zooming far out fills in over a few frames instead of in one stall. */
	private static final int LOADS_PER_FRAME = 4;
	/** Station names show from this zoom in. */
	private static final double LABEL_SCALE = 0.5;

	private MapView() {
	}

	public static void draw(DrawContext context, int left, int top, int width, int height, MapCamera camera, boolean big) {
		final ExploredMap map = MapClient.map();
		final SeenNetwork network = MapClient.network();
		final MinecraftClient client = MinecraftClient.getInstance();
		context.enableScissor(left, top, left + width, top + height);
		context.fill(left, top, left + width, top + height, BACKGROUND);
		if (map != null) {
			drawGround(context, map, left, top, width, height, camera);
		}
		if (network != null) {
			if (MapSettings.stations()) {
				drawStations(context, client.textRenderer, network, left, top, width, height, camera, big);
			}
			if (MapSettings.rails()) {
				drawRails(context, network, left, top, width, height, camera, big ? 2 : 1);
			}
		}
		if (MapSettings.trains()) {
			drawTrains(context, left, top, width, height, camera, big ? 4 : 3);
		}
		if (client.player != null) {
			drawPlayer(context, client.player, camera, left, top, width, height);
		}
		context.disableScissor();
	}

	private static void drawGround(DrawContext context, ExploredMap map, int left, int top, int width, int height, MapCamera camera) {
		final MapTextures textures = MapClient.textures();
		textures.beginFrame();
		final double scale = camera.scale();
		final int firstX = MapRegion.regionOf((int) Math.floor(camera.worldX(left, left, width)));
		final int lastX = MapRegion.regionOf((int) Math.floor(camera.worldX(left + width, left, width)));
		final int firstZ = MapRegion.regionOf((int) Math.floor(camera.worldZ(top, top, height)));
		final int lastZ = MapRegion.regionOf((int) Math.floor(camera.worldZ(top + height, top, height)));
		int loads = LOADS_PER_FRAME;
		context.getMatrices().push();
		context.getMatrices().translate(left + width / 2.0 - camera.centerX() * scale, top + height / 2.0 - camera.centerZ() * scale, 0);
		context.getMatrices().scale((float) scale, (float) scale, 1);
		for (int regionZ = firstZ; regionZ <= lastZ; regionZ++) {
			for (int regionX = firstX; regionX <= lastX; regionX++) {
				if (!map.known(regionX, regionZ)) {
					continue;
				}
				MapRegion region = map.loadedRegion(regionX, regionZ);
				if (region == null && loads > 0) {
					loads--;
					region = map.region(regionX, regionZ, false);
				}
				if (region == null) {
					continue;
				}
				final Identifier texture = textures.texture(region);
				if (texture != null) {
					context.drawTexture(texture, regionX * MapRegion.SIZE, regionZ * MapRegion.SIZE, 0, 0, MapRegion.SIZE, MapRegion.SIZE, MapRegion.SIZE, MapRegion.SIZE);
				}
			}
		}
		context.getMatrices().pop();
	}

	private static void drawRails(DrawContext context, SeenNetwork network, int left, int top, int width, int height, MapCamera camera, int thickness) {
		final int track = MapSettings.railColor();
		final double viewMinX = camera.worldX(left, left, width) - 2;
		final double viewMaxX = camera.worldX(left + width, left, width) + 2;
		final double viewMinZ = camera.worldZ(top, top, height) - 2;
		final double viewMaxZ = camera.worldZ(top + height, top, height) + 2;
		for (final SeenNetwork.Line line : network.rails()) {
			if (line.points().length < 4 || line.maxX() < viewMinX || line.minX() > viewMaxX || line.maxZ() < viewMinZ || line.minZ() > viewMaxZ) {
				continue;
			}
			final int color = switch (line.kind()) {
				case PLATFORM -> PLATFORM;
				case SIDING -> SIDING;
				case BOAT -> BOAT;
				case RUNWAY -> RUNWAY;
				default -> track;
			};
			final float[] points = line.points();
			double lastX = camera.screenX(points[0], left, width);
			double lastY = camera.screenY(points[1], top, height);
			for (int i = 2; i + 1 < points.length; i += 2) {
				final double x = camera.screenX(points[i], left, width);
				final double y = camera.screenY(points[i + 1], top, height);
				if (inView(lastX, lastY, left, top, width, height) || inView(x, y, left, top, width, height)) {
					segment(context, lastX, lastY, x, y, thickness, color);
				}
				lastX = x;
				lastY = y;
			}
		}
	}

	private static void drawStations(DrawContext context, TextRenderer text, SeenNetwork network, int left, int top, int width, int height, MapCamera camera, boolean big) {
		for (final SeenNetwork.Area area : network.stations()) {
			final int x1 = (int) Math.floor(camera.screenX(area.minX(), left, width));
			final int y1 = (int) Math.floor(camera.screenY(area.minZ(), top, height));
			final int x2 = (int) Math.ceil(camera.screenX(area.maxX() + 1, left, width));
			final int y2 = (int) Math.ceil(camera.screenY(area.maxZ() + 1, top, height));
			if (x2 < left || y2 < top || x1 > left + width || y1 > top + height) {
				continue;
			}
			context.fill(x1, y1, Math.max(x1 + 2, x2), Math.max(y1 + 2, y2), 0x60000000 | area.color() & 0xFFFFFF);
			if (big && camera.scale() >= LABEL_SCALE) {
				final String name = area.name().split("\\|", 2)[0];
				context.drawCenteredTextWithShadow(text, name, (x1 + x2) / 2, Math.max(y1, (y1 + y2) / 2 - 4), 0xFFFFFF);
			}
		}
	}

	private static void drawTrains(DrawContext context, int left, int top, int width, int height, MapCamera camera, int size) {
		for (final VehicleExtension vehicle : MinecraftClientData.getInstance().vehicles) {
			final Vector head = vehicle.getHeadPosition();
			if (head == null) {
				continue;
			}
			final int x = (int) Math.round(camera.screenX(head.x, left, width));
			final int y = (int) Math.round(camera.screenY(head.z, top, height));
			if (!inView(x, y, left, top, width, height)) {
				continue;
			}
			final int routeColor = vehicle.vehicleExtraData.getThisRouteColor() & 0xFFFFFF;
			final int half = size / 2;
			context.fill(x - half - 1, y - half - 1, x - half + size + 1, y - half + size + 1, OUTLINE);
			context.fill(x - half, y - half, x - half + size, y - half + size, 0xFF000000 | (routeColor == 0 ? 0xFFFFFF : routeColor));
		}
	}

	/** An arrow pointing where the player looks; north is up. */
	private static void drawPlayer(DrawContext context, ClientPlayerEntity player, MapCamera camera, int left, int top, int width, int height) {
		final double x = camera.screenX(player.getX(), left, width);
		final double y = camera.screenY(player.getZ(), top, height);
		context.getMatrices().push();
		context.getMatrices().translate(x, y, 0);
		context.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(player.getYaw() - 180));
		for (int row = 0; row < 6; row++) {
			final int half = row / 2 + 1;
			context.fill(-half - 1, row - 4, half + 1, row - 3, OUTLINE);
		}
		for (int row = 0; row < 5; row++) {
			final int half = row / 2;
			context.fill(-half, row - 3, half + 1, row - 2, 0xFFFFFFFF);
		}
		context.getMatrices().pop();
	}

	private static void segment(DrawContext context, double x1, double y1, double x2, double y2, int thickness, int color) {
		final double length = Math.hypot(x2 - x1, y2 - y1);
		final int steps = Math.max(1, (int) Math.ceil(length / Math.max(1, thickness * 0.75)));
		final int half = thickness / 2;
		for (int i = 0; i <= steps; i++) {
			final int x = (int) Math.round(x1 + (x2 - x1) * i / steps);
			final int y = (int) Math.round(y1 + (y2 - y1) * i / steps);
			context.fill(x - half, y - half, x - half + thickness, y - half + thickness, color);
		}
	}

	private static boolean inView(double x, double y, int left, int top, int width, int height) {
		return x >= left - 8 && y >= top - 8 && x <= left + width + 8 && y <= top + height + 8;
	}
}
