package org.mtr.aurelia.client.map;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.texture.NativeImage;
import org.mtr.aurelia.map.MapRegion;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A small picture of each explored region, {@link #TILE} pixels a side (four blocks to a pixel), packed into a few large
 * textures. A view zoomed far out draws hundreds of regions from these in a handful of draw calls, without holding the
 * regions' full data. A tile is made from the region when it is in memory, or read from its file on the map's disk
 * thread, and it stays when the region leaves memory. Tile making and texture uploads are rationed per frame.
 */
public final class MapOverview {

	public static final int TILE = 64;
	public static final int PAGE = 1024;
	public static final int PER_PAGE = (PAGE / TILE) * (PAGE / TILE);
	private static final int STEP = MapRegion.SIZE / TILE;
	/** 16 pages hold 4096 tiles (64 MB of GPU memory); past that the tile drawn longest ago is reused. */
	private static final int MAX_PAGES = 16;
	private static final int MAX_SLOTS = MAX_PAGES * PER_PAGE;
	private static final int MAKES_PER_FRAME = 4;
	private static final int UPLOADS_PER_FRAME = 16;
	private static final int MAX_READS = 16;
	/** A tile of a region that keeps changing (the one being explored) is made again at most this often, in frames. */
	private static final int REMAKE_FRAMES = 60;
	/** A tile read from a file; a region in memory has a real version, which never matches this. */
	private static final int FROM_FILE = Integer.MIN_VALUE;

	private final Long2IntOpenHashMap slotOf = new Long2IntOpenHashMap();
	private final long[] slotKey = new long[MAX_SLOTS];
	private final int[] slotVersion = new int[MAX_SLOTS];
	private final int[] slotFrame = new int[MAX_SLOTS];
	private final int[] slotMade = new int[MAX_SLOTS];
	private final IntArrayList pages = new IntArrayList();
	private final LongOpenHashSet reading = new LongOpenHashSet();
	/** Files that could not be read, so they are not read again every frame. */
	private final LongOpenHashSet unreadable = new LongOpenHashSet();
	private final ConcurrentLinkedQueue<Tile> arrived = new ConcurrentLinkedQueue<>();
	private final int[] pixels = new int[TILE * TILE];
	@Nullable
	private NativeImage staging;
	private int used;
	private int frame;
	private int makesLeft;
	/** Goes up on {@link #clear}, so tiles still being read for the previous map are thrown away. */
	private int generation;

	private record Tile(long key, @Nullable int[] pixels, int generation) {
	}

	public MapOverview() {
		slotOf.defaultReturnValue(-1);
	}

	/** Takes in the tiles read since the last frame and resets this frame's rations. */
	public void beginFrame() {
		frame++;
		makesLeft = MAKES_PER_FRAME;
		Tile tile;
		for (int i = 0; i < UPLOADS_PER_FRAME && (tile = arrived.poll()) != null; i++) {
			if (tile.generation != generation || !reading.remove(tile.key)) {
				continue;
			}
			if (tile.pixels == null) {
				unreadable.add(tile.key);
				continue;
			}
			final int slot = slotOf.get(tile.key);
			// a tile made from the region in memory meanwhile is newer than the file
			if (slot < 0 || slotVersion[slot] == FROM_FILE) {
				store(tile.key, tile.pixels, FROM_FILE);
			}
		}
	}

	/**
	 * The slot of this region's tile, or -1 while there is none yet. A missing or outdated tile is made from the region
	 * when it is in memory, else read from its file, within this frame's rations.
	 */
	public int slot(ExploredMap map, int regionX, int regionZ) {
		final long key = MapRegion.key(regionX, regionZ);
		int slot = slotOf.get(key);
		final MapRegion region = map.loadedRegion(regionX, regionZ);
		if (region != null) {
			if ((slot < 0 || slotVersion[slot] != region.version() && frame - slotMade[slot] >= REMAKE_FRAMES) && makesLeft > 0) {
				makesLeft--;
				downsample(region, pixels);
				slot = store(key, pixels, region.version());
			}
		} else if (slot < 0 && reading.size() < MAX_READS && !unreadable.contains(key) && reading.add(key)) {
			final int readGeneration = generation;
			map.readOnDiskThread(regionX, regionZ, read -> arrived.add(new Tile(key, read == null ? null : downsample(read, new int[TILE * TILE]), readGeneration)));
		}
		if (slot >= 0) {
			slotFrame[slot] = frame;
		}
		return slot;
	}

	/** The region left memory: its tile stays, but is made again from the region the next time it is loaded. */
	public void forget(long key) {
		final int slot = slotOf.get(key);
		if (slot >= 0) {
			slotVersion[slot] = FROM_FILE;
		}
	}

	/** The OpenGL texture of a page. */
	public int page(int page) {
		return pages.getInt(page);
	}

	public int pageCount() {
		return pages.size();
	}

	public void clear() {
		for (int i = 0; i < pages.size(); i++) {
			TextureUtil.releaseTextureId(pages.getInt(i));
		}
		pages.clear();
		slotOf.clear();
		reading.clear();
		unreadable.clear();
		arrived.clear();
		used = 0;
		generation++;
		if (staging != null) {
			staging.close();
			staging = null;
		}
	}

	/** Averages each {@link #STEP}-square block of seen columns into one pixel; a block nobody has seen stays clear. */
	static int[] downsample(MapRegion region, int[] out) {
		for (int tileZ = 0; tileZ < TILE; tileZ++) {
			for (int tileX = 0; tileX < TILE; tileX++) {
				int r = 0;
				int g = 0;
				int b = 0;
				int seen = 0;
				for (int z = tileZ * STEP; z < tileZ * STEP + STEP; z++) {
					for (int x = tileX * STEP; x < tileX * STEP + STEP; x++) {
						final int color = region.color(x, z);
						if (color >>> 24 != 0) {
							r += color >> 16 & 0xFF;
							g += color >> 8 & 0xFF;
							b += color & 0xFF;
							seen++;
						}
					}
				}
				out[tileZ * TILE + tileX] = seen == 0 ? 0 : 0xFF000000 | r / seen << 16 | g / seen << 8 | b / seen;
			}
		}
		return out;
	}

	/** Writes a tile into its slot (taking one if needed) and uploads it. Returns the slot, or -1 when every slot was drawn this frame. */
	private int store(long key, int[] tile, int version) {
		int slot = slotOf.get(key);
		if (slot < 0) {
			slot = take();
			if (slot < 0) {
				return -1;
			}
			slotOf.put(key, slot);
			slotKey[slot] = key;
		}
		while (pages.size() <= slot / PER_PAGE) {
			final int texture = TextureUtil.generateTextureId();
			TextureUtil.prepareImage(texture, PAGE, PAGE);
			pages.add(texture);
		}
		if (staging == null) {
			staging = new NativeImage(TILE, TILE, false);
		}
		for (int z = 0; z < TILE; z++) {
			for (int x = 0; x < TILE; x++) {
				final int argb = tile[z * TILE + x];
				staging.setColor(x, z, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | (argb >> 16 & 0xFF));
			}
		}
		final int index = slot % PER_PAGE;
		GlStateManager._bindTexture(pages.getInt(slot / PER_PAGE));
		staging.upload(0, index % (PAGE / TILE) * TILE, index / (PAGE / TILE) * TILE, 0, 0, TILE, TILE, false, true, false, false);
		slotVersion[slot] = version;
		slotFrame[slot] = frame;
		slotMade[slot] = frame;
		return slot;
	}

	/** A free slot, or the one drawn longest ago when all are used. */
	private int take() {
		if (used < MAX_SLOTS) {
			return used++;
		}
		int oldest = -1;
		for (int slot = 0; slot < MAX_SLOTS; slot++) {
			if (slotFrame[slot] != frame && (oldest < 0 || slotFrame[slot] - slotFrame[oldest] < 0)) {
				oldest = slot;
			}
		}
		if (oldest >= 0) {
			slotOf.remove(slotKey[oldest]);
		}
		return oldest;
	}
}
