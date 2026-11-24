package org.mtr.aurelia.client.map;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.texture.NativeImage;
import org.mtr.aurelia.map.MapRegion;
import org.mtr.aurelia.map.OverviewLevels;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Small pictures of the explored world, {@link #TILE} pixels a side, packed into a few large textures. At level 0 a tile
 * is one region (four blocks to a pixel); at each {@link OverviewLevels level} up it covers twice as many regions a side.
 * A view zoomed far out draws hundreds of tiles in a handful of draw calls, without holding the regions' full data. A
 * level-0 tile is made from the region when it is in memory, else read from its file on the map's disk thread; a higher
 * tile is put together from its regions' tiles on the disk thread. Tiles stay when regions leave memory. A slot is taken
 * before a tile is read or made, so nothing is read that has nowhere to go. Tile making and uploads are rationed per frame.
 */
public final class MapOverview {

	public static final int TILE = 64;
	public static final int PAGE = 1024;
	public static final int PER_PAGE = (PAGE / TILE) * (PAGE / TILE);
	private static final int STEP = MapRegion.SIZE / TILE;
	/** 16 pages hold 4096 tiles (64 MB of GPU memory), twice what one view draws, so last frame's tiles keep their slots. */
	private static final int MAX_PAGES = 16;
	private static final int MAX_SLOTS = MAX_PAGES * PER_PAGE;
	private static final int MAKES_PER_FRAME = 4;
	private static final int UPLOADS_PER_FRAME = 16;
	private static final int MAX_READS = 16;
	/** Higher-level tiles started per frame; each one reads many region tiles. */
	private static final int GROUPS_PER_FRAME = 2;
	/** A tile of a region that keeps changing (the one being explored) is made again at most this often, in frames. */
	private static final int REMAKE_FRAMES = 60;
	/** A tile read from a file; a region in memory has a real version, which never matches this. */
	private static final int FROM_FILE = Integer.MIN_VALUE;
	/** A slot taken for a tile still being read. */
	private static final int PENDING = Integer.MIN_VALUE + 1;
	/** A higher-level tile one of whose regions was saved since: still drawn, and read again. */
	private static final int STALE = Integer.MIN_VALUE + 2;
	/** A stale tile being read again: still drawn until the new one arrives. */
	private static final int REREADING = Integer.MIN_VALUE + 3;
	/** A higher-level tile is read again at most this often, in frames, since each read covers many regions. */
	private static final int REREAD_FRAMES = 600;

	private final Long2IntOpenHashMap[] slotOf = new Long2IntOpenHashMap[OverviewLevels.LEVELS];
	private final long[] slotKey = new long[MAX_SLOTS];
	private final byte[] slotLevel = new byte[MAX_SLOTS];
	private final int[] slotVersion = new int[MAX_SLOTS];
	private final int[] slotFrame = new int[MAX_SLOTS];
	private final int[] slotMade = new int[MAX_SLOTS];
	private final IntArrayList pages = new IntArrayList();
	/** Tiles that could not be read, per level, so they are not read again every frame. */
	private final LongOpenHashSet[] unreadable = new LongOpenHashSet[OverviewLevels.LEVELS];
	private final ConcurrentLinkedQueue<Tile> arrived = new ConcurrentLinkedQueue<>();
	private final int[] pixels = new int[TILE * TILE];
	@Nullable
	private NativeImage staging;
	private int used;
	/** Where {@link #take} looks next once every slot is used. */
	private int hand;
	private int frame;
	private int makesLeft;
	private int groupsLeft;
	private int reading;
	/** Every slot was on screen when a tile last needed one (this frame or the one before): no new tile is read or made meanwhile. */
	private boolean fullThisFrame;
	private boolean fullLastFrame;
	/** Goes up on {@link #clear}, so tiles still being read for the previous map are thrown away. */
	private int generation;

	private record Tile(int level, long key, @Nullable int[] pixels, int generation) {
	}

	public MapOverview() {
		for (int level = 0; level < OverviewLevels.LEVELS; level++) {
			slotOf[level] = new Long2IntOpenHashMap();
			slotOf[level].defaultReturnValue(-1);
			unreadable[level] = new LongOpenHashSet();
		}
	}

	/** Takes in the tiles read since the last frame and resets this frame's rations. */
	public void beginFrame() {
		frame++;
		makesLeft = MAKES_PER_FRAME;
		groupsLeft = GROUPS_PER_FRAME;
		fullLastFrame = fullThisFrame;
		fullThisFrame = false;
		Tile tile;
		for (int i = 0; i < UPLOADS_PER_FRAME && (tile = arrived.poll()) != null; i++) {
			if (tile.generation != generation) {
				continue;
			}
			reading--;
			final int slot = slotOf[tile.level].get(tile.key);
			// the slot was reused meanwhile, or a tile made from the region in memory is newer than the file
			if (slot < 0 || slotVersion[slot] != PENDING && slotVersion[slot] != REREADING) {
				continue;
			}
			if (tile.pixels == null && slotVersion[slot] == REREADING) {
				// keep what was drawn
				slotVersion[slot] = FROM_FILE;
			} else if (tile.pixels == null) {
				unreadable[tile.level].add(tile.key);
				release(slot);
			} else {
				upload(slot, tile.pixels, FROM_FILE);
			}
		}
	}

	/**
	 * The slot of a tile at a level, or -1 while there is none to draw yet. A missing or outdated level-0 tile is made from
	 * the region when it is in memory, else read from its file; a missing higher tile is put together on the disk thread.
	 * All within this frame's rations, and only when a slot is free for it.
	 */
	public int slot(ExploredMap map, int level, int tileX, int tileZ) {
		final long key = MapRegion.key(tileX, tileZ);
		int slot = slotOf[level].get(key);
		if (slot >= 0) {
			slotFrame[slot] = frame;
		}
		final MapRegion region = level == 0 ? map.loadedRegion(tileX, tileZ) : null;
		if (region != null) {
			if ((slot < 0 || slotVersion[slot] != region.version() && (slotVersion[slot] == PENDING || frame - slotMade[slot] >= REMAKE_FRAMES)) && makesLeft > 0) {
				if (slot < 0) {
					slot = reserve(level, key);
				}
				if (slot >= 0) {
					makesLeft--;
					downsample(region, pixels);
					upload(slot, pixels, region.version());
				}
			}
		} else if (slot >= 0 && slotVersion[slot] == STALE && frame - slotMade[slot] >= REREAD_FRAMES && reading < MAX_READS && groupsLeft > 0) {
			slotVersion[slot] = REREADING;
			reading++;
			groupsLeft--;
			final int readGeneration = generation;
			map.readLevelTileOnDiskThread(level, regionsIn(map, level, tileX, tileZ), read -> arrived.add(new Tile(level, key, read, readGeneration)));
		} else if (slot < 0 && reading < MAX_READS && (level == 0 || groupsLeft > 0) && !unreadable[level].contains(key)) {
			slot = reserve(level, key);
			if (slot >= 0) {
				slotVersion[slot] = PENDING;
				reading++;
				final int readGeneration = generation;
				if (level == 0) {
					map.readTileOnDiskThread(tileX, tileZ, read -> arrived.add(new Tile(0, key, read, readGeneration)));
				} else {
					groupsLeft--;
					map.readLevelTileOnDiskThread(level, regionsIn(map, level, tileX, tileZ), read -> arrived.add(new Tile(level, key, read, readGeneration)));
				}
			}
		}
		return slot >= 0 && slotVersion[slot] != PENDING ? slot : -1;
	}

	/** The region left memory: its tile stays, but is made again from the region the next time it is loaded. */
	public void forget(long key) {
		final int slot = slotOf[0].get(key);
		if (slot >= 0 && slotVersion[slot] != PENDING) {
			slotVersion[slot] = FROM_FILE;
		}
	}

	/**
	 * A region was queued for saving: the higher-level tiles holding it are read again (they are made only from files),
	 * and one that had nothing to show is tried again.
	 */
	public void saved(long regionKey) {
		final int regionX = (int) (regionKey >> 32);
		final int regionZ = (int) regionKey;
		for (int level = 1; level < OverviewLevels.LEVELS; level++) {
			final long key = MapRegion.key(OverviewLevels.tileOf(regionX, level), OverviewLevels.tileOf(regionZ, level));
			unreadable[level].remove(key);
			final int slot = slotOf[level].get(key);
			if (slot >= 0 && slotVersion[slot] == FROM_FILE) {
				slotVersion[slot] = STALE;
			}
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
		for (int level = 0; level < OverviewLevels.LEVELS; level++) {
			slotOf[level].clear();
			unreadable[level].clear();
		}
		arrived.clear();
		used = 0;
		hand = 0;
		reading = 0;
		fullThisFrame = false;
		fullLastFrame = false;
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

	/**
	 * Shrinks a region's level-0 tile into its place in a tile of a higher level: the region at {@code (dx, dz)} within
	 * that tile's square of regions, each region {@code TILE >> level} pixels a side. Unseen pixels stay clear.
	 */
	static void shrinkInto(int[] regionTile, int level, int dx, int dz, int[] out) {
		final int span = TILE >> level;
		final int step = 1 << level;
		for (int z = 0; z < span; z++) {
			for (int x = 0; x < span; x++) {
				int r = 0;
				int g = 0;
				int b = 0;
				int seen = 0;
				for (int sz = z * step; sz < z * step + step; sz++) {
					for (int sx = x * step; sx < x * step + step; sx++) {
						final int color = regionTile[sz * TILE + sx];
						if (color >>> 24 != 0) {
							r += color >> 16 & 0xFF;
							g += color >> 8 & 0xFF;
							b += color & 0xFF;
							seen++;
						}
					}
				}
				out[(dz * span + z) * TILE + dx * span + x] = seen == 0 ? 0 : 0xFF000000 | r / seen << 16 | g / seen << 8 | b / seen;
			}
		}
	}

	/** The known regions inside a tile of a higher level. */
	private static long[] regionsIn(ExploredMap map, int level, int tileX, int tileZ) {
		final LongArrayList keys = new LongArrayList();
		for (final long key : map.knownKeys()) {
			if (OverviewLevels.tileOf((int) (key >> 32), level) == tileX && OverviewLevels.tileOf((int) key, level) == tileZ) {
				keys.add(key);
			}
		}
		return keys.toLongArray();
	}

	/** Takes a slot for a tile and maps it; -1 when every slot was drawn this frame or the one before. */
	private int reserve(int level, long key) {
		if (fullThisFrame || fullLastFrame) {
			return -1;
		}
		final int slot = take();
		if (slot < 0) {
			return -1;
		}
		slotOf[level].put(key, slot);
		slotKey[slot] = key;
		slotLevel[slot] = (byte) level;
		slotFrame[slot] = frame;
		return slot;
	}

	/** Unmaps a slot that got no tile, so it is the next one reused. */
	private void release(int slot) {
		slotOf[slotLevel[slot]].remove(slotKey[slot]);
		slotFrame[slot] = Integer.MIN_VALUE;
		slotVersion[slot] = FROM_FILE;
	}

	/** Writes a tile into its slot and uploads it. */
	private void upload(int slot, int[] tile, int version) {
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
	}

	/**
	 * A free slot; when all are used, the next one round from the last taken that was not drawn this frame or the one
	 * before (a tile drawn last frame is likely on screen again now). -1, and no reads or makes for a frame or two, when
	 * every slot is in use.
	 */
	private int take() {
		if (used < MAX_SLOTS) {
			return used++;
		}
		for (int i = 0; i < MAX_SLOTS; i++) {
			hand = (hand + 1) % MAX_SLOTS;
			if (slotFrame[hand] < frame - 1) {
				slotOf[slotLevel[hand]].remove(slotKey[hand]);
				return hand;
			}
		}
		fullThisFrame = true;
		return -1;
	}
}
