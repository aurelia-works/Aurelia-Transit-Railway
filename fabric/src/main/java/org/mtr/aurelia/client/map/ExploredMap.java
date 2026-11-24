package org.mtr.aurelia.client.map;

import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.map.MapRegion;
import org.mtr.aurelia.map.MapRegionCodec;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongIterator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * The explored map of one world and dimension: regions kept in memory while used, and saved as
 * {@code r.<x>.<z>.atrmap} files in that world's map folder. Saves, and the loads a map view asks for, run on one
 * background disk thread from copies, so the render thread never waits for the disk; that thread works in order, so a
 * load always sees the save queued before it. At most {@link #MAX_LOADED} regions stay in memory; the least recently
 * used one is saved and dropped.
 */
public final class ExploredMap {

	public static final int MAX_LOADED = 160;
	private static final String SUFFIX = ".atrmap";
	private static final String TILE_SUFFIX = ".atrtile";
	private static final ExecutorService DISK = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATR map disk");
		thread.setDaemon(true);
		return thread;
	});

	private final Path folder;
	/** Least recently used first. */
	private final Long2ObjectLinkedOpenHashMap<MapRegion> loaded = new Long2ObjectLinkedOpenHashMap<>();
	/** Regions that have a file, so the map only tries to load squares that were ever seen. */
	private final LongOpenHashSet onDisk = new LongOpenHashSet();
	/** Regions asked for with {@link #request} and not back yet. One loaded another way meanwhile leaves this set, so its late read is not used. */
	private final LongOpenHashSet pending = new LongOpenHashSet();
	private final ConcurrentLinkedQueue<MapRegion> arrived = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<Long> unreadable = new ConcurrentLinkedQueue<>();
	private final LongConsumer onDrop;
	private long[] known = new long[0];
	private boolean knownChanged = true;

	/** @param onDrop told the key of every region dropped from memory, so its texture can go too */
	public ExploredMap(Path folder, LongConsumer onDrop) {
		this.folder = folder;
		this.onDrop = onDrop;
		// let saves still queued for this folder land first, so the listing below sees their files
		try {
			DISK.submit(() -> {
			}).get(2, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			AureliaClient.LOGGER.warn("[ATR] map saves still running while opening {}", folder);
		}
		try (DirectoryStream<Path> files = Files.isDirectory(folder) ? Files.newDirectoryStream(folder, "r.*" + SUFFIX) : null) {
			if (files != null) {
				for (final Path file : files) {
					final long key = parse(file.getFileName().toString());
					if (key != Long.MIN_VALUE) {
						onDisk.add(key);
					}
				}
			}
		} catch (IOException e) {
			AureliaClient.LOGGER.warn("[ATR] map folder {} could not be listed", folder, e);
		}
	}

	public Path folder() {
		return folder;
	}

	/** True when this square was ever seen: it is loaded, or has a file. */
	public boolean known(int regionX, int regionZ) {
		final long key = MapRegion.key(regionX, regionZ);
		return loaded.containsKey(key) || onDisk.contains(key);
	}

	/**
	 * A square the server drew from its saved chunks. Kept as this client's own, and saved, when this client has never
	 * seen the square; otherwise it only fills the columns this client has not seen.
	 */
	public void adopt(MapRegion drawn) {
		final MapRegion own = region(drawn.regionX, drawn.regionZ, false);
		if (own == null) {
			add(drawn);
			save(drawn);
			knownChanged = true;
			return;
		}
		for (int z = 0; z < MapRegion.SIZE; z++) {
			for (int x = 0; x < MapRegion.SIZE; x++) {
				if (own.color(x, z) == 0 && drawn.color(x, z) != 0) {
					own.set(x, z, drawn.color(x, z), drawn.height(x, z));
				}
			}
		}
	}

	/** The key of every square ever seen (loaded, or with a file). Shared, and rebuilt only when that set changes: read it, never change it. */
	public long[] knownKeys() {
		if (knownChanged) {
			knownChanged = false;
			final LongOpenHashSet keys = new LongOpenHashSet(onDisk);
			keys.addAll(loaded.keySet());
			known = keys.toLongArray();
		}
		return known;
	}

	/** The region, loading it from its file if needed; a new blank one when {@code create} and none exists. */
	@Nullable
	public MapRegion region(int regionX, int regionZ, boolean create) {
		final long key = MapRegion.key(regionX, regionZ);
		MapRegion region = loaded.getAndMoveToLast(key);
		if (region != null) {
			return region;
		}
		if (onDisk.contains(key)) {
			region = read(file(regionX, regionZ), regionX, regionZ);
			if (region == null) {
				onDisk.remove(key);
				knownChanged = true;
			}
		}
		if (region == null && create) {
			region = new MapRegion(regionX, regionZ);
			knownChanged = true;
		}
		if (region != null) {
			add(region);
		}
		return region;
	}

	/** Only a region already in memory, without touching the disk. */
	@Nullable
	public MapRegion loadedRegion(int regionX, int regionZ) {
		return loaded.get(MapRegion.key(regionX, regionZ));
	}

	/** Like {@link #loadedRegion}, and counts as a use, so a region on screen is the last to be dropped. */
	@Nullable
	public MapRegion useLoadedRegion(int regionX, int regionZ) {
		return loaded.getAndMoveToLast(MapRegion.key(regionX, regionZ));
	}

	/** Starts reading a region that has a file on the disk thread; it is in memory after a later {@link #drain}. */
	public void request(int regionX, int regionZ) {
		final long key = MapRegion.key(regionX, regionZ);
		if (loaded.containsKey(key) || !onDisk.contains(key) || !pending.add(key)) {
			return;
		}
		readOnDiskThread(regionX, regionZ, region -> {
			if (region == null) {
				unreadable.add(key);
			} else {
				arrived.add(region);
			}
		});
	}

	/** Takes in the regions read since the last call. Render thread only. */
	public void drain() {
		MapRegion region;
		while ((region = arrived.poll()) != null) {
			if (pending.contains(region.key())) {
				add(region);
			}
		}
		Long key;
		while ((key = unreadable.poll()) != null) {
			if (pending.remove(key.longValue())) {
				onDisk.remove(key.longValue());
				knownChanged = true;
			}
		}
	}

	/**
	 * Reads a region's file on the disk thread, after any save of it already queued, and hands the region (null when it
	 * could not be read) to {@code done} on that thread. Nothing of this map is touched there.
	 */
	public void readOnDiskThread(int regionX, int regionZ, Consumer<MapRegion> done) {
		final Path file = file(regionX, regionZ);
		DISK.execute(() -> done.accept(read(file, regionX, regionZ)));
	}

	/**
	 * Reads a region's overview tile on the disk thread and hands it (null when there is none) to {@code done} there. The
	 * small tile file written with each save is read when it is there; otherwise the tile is made from the region file
	 * and written for next time, so a map saved before tiles existed pays that once.
	 */
	public void readTileOnDiskThread(int regionX, int regionZ, Consumer<int[]> done) {
		final Path file = file(regionX, regionZ);
		final Path tile = tileFile(regionX, regionZ);
		DISK.execute(() -> {
			if (Files.isRegularFile(tile)) {
				try (InputStream in = Files.newInputStream(tile)) {
					done.accept(MapRegionCodec.readTile(in, MapOverview.TILE));
					return;
				} catch (IOException e) {
					// made again from the region below
				}
			}
			final MapRegion region = read(file, regionX, regionZ);
			if (region == null) {
				done.accept(null);
				return;
			}
			final int[] pixels = MapOverview.downsample(region, new int[MapOverview.TILE * MapOverview.TILE]);
			writeTile(tile, pixels);
			done.accept(pixels);
		});
	}

	/** Hands every unsaved region to the disk thread. */
	public void saveChanged() {
		for (final MapRegion region : loaded.values()) {
			if (region.unsaved()) {
				save(region);
			}
		}
	}

	/** Saves everything and forgets it; the map is no longer used. */
	public void close() {
		saveChanged();
		final LongIterator keys = loaded.keySet().iterator();
		while (keys.hasNext()) {
			onDrop.accept(keys.nextLong());
		}
		loaded.clear();
		pending.clear();
	}

	/** Waits (at most 5 seconds) for the queued saves to reach the disk; the game calls it as it quits, before the disk thread dies with it. */
	public static void flush() {
		try {
			DISK.submit(() -> {
			}).get(5, java.util.concurrent.TimeUnit.SECONDS);
		} catch (Exception e) {
			AureliaClient.LOGGER.warn("[ATR] map saves still running at quit", e);
		}
	}

	private void add(MapRegion region) {
		loaded.putAndMoveToLast(region.key(), region);
		pending.remove(region.key());
		while (loaded.size() > MAX_LOADED) {
			final MapRegion oldest = loaded.removeFirst();
			if (oldest.unsaved()) {
				save(oldest);
			}
			onDrop.accept(oldest.key());
		}
	}

	private void save(MapRegion region) {
		region.markSaved();
		onDisk.add(region.key());
		final int[] colors = region.copyColors();
		final short[] heights = region.copyHeights();
		final Path file = file(region.regionX, region.regionZ);
		final Path tile = tileFile(region.regionX, region.regionZ);
		final int regionX = region.regionX;
		final int regionZ = region.regionZ;
		DISK.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				final Path temp = file.resolveSibling(file.getFileName() + ".tmp");
				try (OutputStream out = Files.newOutputStream(temp)) {
					MapRegionCodec.write(out, colors, heights);
				}
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				AureliaClient.LOGGER.warn("[ATR] map region {} could not be saved", file, e);
				return;
			}
			writeTile(tile, MapOverview.downsample(new MapRegion(regionX, regionZ, colors, heights), new int[MapOverview.TILE * MapOverview.TILE]));
		});
	}

	private static void writeTile(Path tile, int[] pixels) {
		try {
			final Path temp = tile.resolveSibling(tile.getFileName() + ".tmp");
			try (OutputStream out = Files.newOutputStream(temp)) {
				MapRegionCodec.writeTile(out, pixels, MapOverview.TILE);
			}
			Files.move(temp, tile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			AureliaClient.LOGGER.warn("[ATR] map tile {} could not be saved", tile, e);
		}
	}

	@Nullable
	private static MapRegion read(Path file, int regionX, int regionZ) {
		try (InputStream in = Files.newInputStream(file)) {
			return MapRegionCodec.read(in, regionX, regionZ);
		} catch (IOException e) {
			AureliaClient.LOGGER.warn("[ATR] map region {} could not be read; it starts blank", file, e);
			return null;
		}
	}

	private Path file(int regionX, int regionZ) {
		return folder.resolve("r." + regionX + "." + regionZ + SUFFIX);
	}

	/** The region's overview tile, beside its file. Its name does not match the region files' pattern, so listing ignores it. */
	private Path tileFile(int regionX, int regionZ) {
		return folder.resolve("t." + regionX + "." + regionZ + TILE_SUFFIX);
	}

	private static long parse(String name) {
		final String[] parts = name.substring(0, name.length() - SUFFIX.length()).split("\\.");
		if (parts.length != 3) {
			return Long.MIN_VALUE;
		}
		try {
			return MapRegion.key(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
		} catch (NumberFormatException e) {
			return Long.MIN_VALUE;
		}
	}
}
