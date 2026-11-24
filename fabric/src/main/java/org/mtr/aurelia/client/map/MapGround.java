package org.mtr.aurelia.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.mtr.aurelia.map.MapRegion;

import java.util.Arrays;

/**
 * Draws the explored ground of the current world into a screen rectangle, for every map view: the N map, the road
 * dashboard and MTR's dashboard map. Close in, each region is drawn from its full texture; zoomed out, or when more
 * regions show than fit in memory, from the {@link MapOverview} tiles, a few draw calls for the whole view. Tiles also
 * stand in for regions still loading. Regions are loaded and uploaded a few per frame, so no frame stalls, and the draw
 * itself allocates nothing.
 */
public final class MapGround {

	/** Below this zoom a region is at most 32 screen pixels wide, so its 64-pixel tile is as sharp as the full texture. */
	private static final double FULL_MIN_SCALE = 0.125;
	/** More regions than this in view are drawn from tiles, so the view never asks for more than {@link ExploredMap#MAX_LOADED}. */
	private static final int FULL_MAX_REGIONS = 96;
	/** New regions read from disk per frame. */
	private static final int LOADS_PER_FRAME = 4;
	/** Keeps nearest-pixel sampling inside a tile, away from its neighbour in the page. */
	private static final float INSET = 0.05F / MapOverview.PAGE;
	private static final int TILES_PER_ROW = MapOverview.PAGE / MapOverview.TILE;

	private static long[] visible = new long[256];
	private static int[] slots = new int[256];

	private MapGround() {
	}

	public static void draw(DrawContext context, int left, int top, int width, int height, double centerX, double centerZ, double scale) {
		final ExploredMap map = MapClient.map();
		if (map == null || width <= 0 || height <= 0) {
			return;
		}
		map.drain();
		final MapOverview overview = MapClient.overview();
		overview.beginFrame();
		final int firstX = MapRegion.regionOf((int) Math.floor(centerX - width / 2.0 / scale));
		final int lastX = MapRegion.regionOf((int) Math.floor(centerX + width / 2.0 / scale));
		final int firstZ = MapRegion.regionOf((int) Math.floor(centerZ - height / 2.0 / scale));
		final int lastZ = MapRegion.regionOf((int) Math.floor(centerZ + height / 2.0 / scale));

		int count = 0;
		for (final long key : map.knownKeys()) {
			final int regionX = (int) (key >> 32);
			final int regionZ = (int) key;
			if (regionX >= firstX && regionX <= lastX && regionZ >= firstZ && regionZ <= lastZ) {
				if (count == visible.length) {
					visible = Arrays.copyOf(visible, count * 2);
					slots = Arrays.copyOf(slots, count * 2);
				}
				visible[count++] = key;
			}
		}
		if (count == 0) {
			return;
		}

		final Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
		final double originX = left + width / 2.0 - centerX * scale;
		final double originZ = top + height / 2.0 - centerZ * scale;
		final float size = (float) (MapRegion.SIZE * scale);
		RenderSystem.setShader(GameRenderer::getPositionTexProgram);

		// tiles first: the whole view far out, and what is still loading close in
		for (int i = 0; i < count; i++) {
			slots[i] = overview.slot(map, (int) (visible[i] >> 32), (int) visible[i]);
		}
		final BufferBuilder buffer = Tessellator.getInstance().getBuffer();
		for (int page = 0; page < overview.pageCount(); page++) {
			boolean started = false;
			for (int i = 0; i < count; i++) {
				final int slot = slots[i];
				if (slot < 0 || slot / MapOverview.PER_PAGE != page) {
					continue;
				}
				if (!started) {
					started = true;
					RenderSystem.setShaderTexture(0, overview.page(page));
					buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
				}
				final int index = slot % MapOverview.PER_PAGE;
				final float u = (float) (index % TILES_PER_ROW) / TILES_PER_ROW;
				final float v = (float) (index / TILES_PER_ROW) / TILES_PER_ROW;
				final float x = (float) (originX + (int) (visible[i] >> 32) * (double) MapRegion.SIZE * scale);
				final float y = (float) (originZ + (int) visible[i] * (double) MapRegion.SIZE * scale);
				quad(buffer, matrix, x, y, x + size, y + size, u + INSET, v + INSET, u + 1F / TILES_PER_ROW - INSET, v + 1F / TILES_PER_ROW - INSET);
			}
			if (started) {
				BufferRenderer.drawWithGlobalProgram(buffer.end());
			}
		}

		if (scale < FULL_MIN_SCALE || count > FULL_MAX_REGIONS) {
			return;
		}
		final MapTextures textures = MapClient.textures();
		textures.beginFrame();
		int loads = LOADS_PER_FRAME;
		for (int i = 0; i < count; i++) {
			final int regionX = (int) (visible[i] >> 32);
			final int regionZ = (int) visible[i];
			final MapRegion region = map.useLoadedRegion(regionX, regionZ);
			if (region == null) {
				if (loads > 0) {
					loads--;
					map.request(regionX, regionZ);
				}
				continue;
			}
			final Identifier texture = textures.texture(region);
			if (texture != null) {
				final float x = (float) (originX + regionX * (double) MapRegion.SIZE * scale);
				final float y = (float) (originZ + regionZ * (double) MapRegion.SIZE * scale);
				RenderSystem.setShaderTexture(0, texture);
				buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
				quad(buffer, matrix, x, y, x + size, y + size, 0, 0, 1, 1);
				BufferRenderer.drawWithGlobalProgram(buffer.end());
			}
		}
	}

	private static void quad(BufferBuilder buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2) {
		buffer.vertex(matrix, x1, y1, 0).texture(u1, v1).next();
		buffer.vertex(matrix, x1, y2, 0).texture(u1, v2).next();
		buffer.vertex(matrix, x2, y2, 0).texture(u2, v2).next();
		buffer.vertex(matrix, x2, y1, 0).texture(u2, v1).next();
	}
}
