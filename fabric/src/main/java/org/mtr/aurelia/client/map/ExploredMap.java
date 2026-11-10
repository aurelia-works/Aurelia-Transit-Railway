package org.mtr.aurelia.client.map;

import org.mtr.aurelia.client.AureliaClient;
import org.mtr.aurelia.map.MapRegion;
import org.mtr.aurelia.map.MapRegionCodec;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongConsumer;

/**
 * The explored map of one world and dimension: regions kept in memory while used, and saved as
 * {@code r.<x>.<z>.atrmap} files in that world's map folder. Saving runs on one background thread from copies, so the
 * render thread never waits for the disk; loading a region is a small read done when it is first needed. At most
 * {@link #MAX_LOADED} regions stay in memory; the least recently used one is saved and dropped.
 */
public final class ExploredMap {

	public static final int MAX_LOADED = 160;
	private static final String SUFFIX = ".atrmap";
	private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATR map saver");
		thread.setDaemon(true);
		return thread;
	});

	private final Path folder;
	private final LinkedHashMap<Long, MapRegion> loaded = new LinkedHashMap<>(16, 0.75F, true);
	/** Regions that have a file, so the map only tries to load squares that were ever seen. */
	private final Set<Long> onDisk = new HashSet<>();
	private final LongConsumer onDrop;

	/** @param onDrop told the key of every region dropped from memory, so its texture can go too */
	public ExploredMap(Path folder, LongConsumer onDrop) {
		this.folder = folder;
		this.onDrop = onDrop;
		// let saves still queued for this folder land first, so the listing below sees their files
		try {
			SAVER.submit(() -> {
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

	/** The region, loading it from its file if needed; a new blank one when {@code create} and none exists. */
	@Nullable
	public MapRegion region(int regionX, int regionZ, boolean create) {
		final long key = MapRegion.key(regionX, regionZ);
		MapRegion region = loaded.get(key);
		if (region != null) {
			return region;
		}
		if (onDisk.contains(key)) {
			region = load(regionX, regionZ);
		}
		if (region == null && create) {
			region = new MapRegion(regionX, regionZ);
		}
		if (region != null) {
			loaded.put(key, region);
			trim();
		}
		return region;
	}

	/** Only a region already in memory, for drawing without touching the disk. */
	@Nullable
	public MapRegion loadedRegion(int regionX, int regionZ) {
		return loaded.get(MapRegion.key(regionX, regionZ));
	}

	/** Hands every unsaved region to the saver thread. */
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
		for (final long key : loaded.keySet()) {
			onDrop.accept(key);
		}
		loaded.clear();
	}

	private void trim() {
		final Iterator<Map.Entry<Long, MapRegion>> oldest = loaded.entrySet().iterator();
		while (loaded.size() > MAX_LOADED && oldest.hasNext()) {
			final MapRegion region = oldest.next().getValue();
			if (region.unsaved()) {
				save(region);
			}
			oldest.remove();
			onDrop.accept(region.key());
		}
	}

	private void save(MapRegion region) {
		region.markSaved();
		onDisk.add(region.key());
		final int[] colors = region.copyColors();
		final short[] heights = region.copyHeights();
		final Path file = file(region.regionX, region.regionZ);
		SAVER.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				final Path temp = file.resolveSibling(file.getFileName() + ".tmp");
				try (OutputStream out = Files.newOutputStream(temp)) {
					MapRegionCodec.write(out, colors, heights);
				}
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				AureliaClient.LOGGER.warn("[ATR] map region {} could not be saved", file, e);
			}
		});
	}

	@Nullable
	private MapRegion load(int regionX, int regionZ) {
		final Path file = file(regionX, regionZ);
		try (InputStream in = Files.newInputStream(file)) {
			return MapRegionCodec.read(in, regionX, regionZ);
		} catch (IOException e) {
			AureliaClient.LOGGER.warn("[ATR] map region {} could not be read; it starts blank", file, e);
			onDisk.remove(MapRegion.key(regionX, regionZ));
			return null;
		}
	}

	private Path file(int regionX, int regionZ) {
		return folder.resolve("r." + regionX + "." + regionZ + SUFFIX);
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
