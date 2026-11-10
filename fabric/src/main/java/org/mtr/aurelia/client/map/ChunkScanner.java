package org.mtr.aurelia.client.map;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import org.mtr.aurelia.map.MapRegion;
import org.mtr.aurelia.map.MapShade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads loaded chunks into the {@link ExploredMap}, a few per tick (the Map tab's scan slider), nearest first. A chunk
 * the player stands in or next to is read again every few seconds, farther ones every half minute, so building shows
 * up on the map soon without the scanner reading the whole view distance each tick.
 */
public final class ChunkScanner {

	private static final long NEAR_MILLIS = 3_000;
	private static final long FAR_MILLIS = 30_000;
	private static final int MAX_RADIUS = 32;
	/** How far down to look past glass and other blocks the map does not colour. */
	private static final int CLEAR_DEPTH = 8;
	private static final int MAX_WATER_DEPTH = 16;

	/** Chunk offsets in order of distance from the centre, out to {@link #MAX_RADIUS}. */
	private static final int[][] SPIRAL = spiral();

	private final Map<Long, Long> lastRead = new HashMap<>();
	private final BlockPos.Mutable pos = new BlockPos.Mutable();

	public void reset() {
		lastRead.clear();
	}

	public void tick(ClientWorld world, ClientPlayerEntity player, ExploredMap map, int budget, int viewDistance) {
		final long now = System.currentTimeMillis();
		final int centerX = player.getBlockX() >> 4;
		final int centerZ = player.getBlockZ() >> 4;
		final int radius = Math.min(MAX_RADIUS, viewDistance);
		int read = 0;
		for (final int[] offset : SPIRAL) {
			if (read >= budget) {
				break;
			}
			if (Math.abs(offset[0]) > radius || Math.abs(offset[1]) > radius) {
				continue;
			}
			final int chunkX = centerX + offset[0];
			final int chunkZ = centerZ + offset[1];
			final long key = MapRegion.key(chunkX, chunkZ);
			final Long last = lastRead.get(key);
			final long wait = Math.max(Math.abs(offset[0]), Math.abs(offset[1])) <= 1 ? NEAR_MILLIS : FAR_MILLIS;
			if (last != null && now - last < wait) {
				continue;
			}
			final WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ, false);
			if (chunk == null || chunk.isEmpty()) {
				continue;
			}
			lastRead.put(key, now);
			read(world, chunk, player, map);
			read++;
		}
		if (lastRead.size() > 16384) {
			lastRead.clear();
		}
	}

	private void read(ClientWorld world, WorldChunk chunk, ClientPlayerEntity player, ExploredMap map) {
		final int startX = chunk.getPos().getStartX();
		final int startZ = chunk.getPos().getStartZ();
		final MapRegion region = map.region(MapRegion.regionOf(startX), MapRegion.regionOf(startZ), true);
		if (region == null) {
			return;
		}
		final boolean ceiling = world.getDimension().hasCeiling();
		final int bottom = world.getBottomY();
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				final int x = startX + dx;
				final int z = startZ + dz;
				int y = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, dx, dz);
				if (ceiling) {
					y = floorBelow(chunk, x, z, Math.min(y, player.getBlockY() + 2), bottom);
				}
				if (y < bottom) {
					continue;
				}
				BlockState state = chunk.getBlockState(pos.set(x, y, z));
				MapColor color = state.getMapColor(world, pos);
				for (int i = 0; color == MapColor.CLEAR && i < CLEAR_DEPTH && y > bottom; i++) {
					y--;
					state = chunk.getBlockState(pos.set(x, y, z));
					color = state.getMapColor(world, pos);
				}
				if (color == MapColor.CLEAR) {
					continue;
				}
				final int localX = MapRegion.localOf(x);
				final int localZ = MapRegion.localOf(z);
				final int argb;
				if (!state.getFluidState().isEmpty()) {
					int depth = 0;
					while (depth < MAX_WATER_DEPTH && y - depth - 1 > bottom && !chunk.getBlockState(pos.set(x, y - depth - 1, z)).getFluidState().isEmpty()) {
						depth++;
					}
					argb = MapShade.shade(color.color, MapShade.waterBrightness(depth, x, z));
				} else {
					argb = MapShade.shade(color.color, MapShade.brightness(y, northHeight(map, region, x, z)));
				}
				region.set(localX, localZ, argb, y);
			}
		}
	}

	/** In a dimension with a roof: the first standing place found going down from {@code start}. */
	private int floorBelow(WorldChunk chunk, int x, int z, int start, int bottom) {
		boolean air = false;
		for (int y = start; y > bottom; y--) {
			final boolean empty = chunk.getBlockState(pos.set(x, y, z)).isAir();
			if (air && !empty) {
				return y;
			}
			air |= empty;
		}
		return bottom - 1;
	}

	private static int northHeight(ExploredMap map, MapRegion region, int x, int z) {
		final int northZ = z - 1;
		final MapRegion north = MapRegion.regionOf(northZ) == region.regionZ ? region : map.loadedRegion(region.regionX, MapRegion.regionOf(northZ));
		return north == null ? MapRegion.UNKNOWN_HEIGHT : north.height(MapRegion.localOf(x), MapRegion.localOf(northZ));
	}

	private static int[][] spiral() {
		final List<int[]> offsets = new ArrayList<>();
		for (int x = -MAX_RADIUS; x <= MAX_RADIUS; x++) {
			for (int z = -MAX_RADIUS; z <= MAX_RADIUS; z++) {
				offsets.add(new int[]{x, z});
			}
		}
		offsets.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
		return offsets.toArray(new int[0][]);
	}
}
