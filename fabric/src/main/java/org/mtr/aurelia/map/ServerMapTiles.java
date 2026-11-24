package org.mtr.aurelia.map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.dimension.DimensionType;
import org.mtr.aurelia.Operators;
import org.mtr.aurelia.dashboard.DashboardPackets;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.mod.Init;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The server half of the map's squaremap-style fill: a player's map asks which squares the world has saved chunks in,
 * then asks for the squares in view that it has never explored, and the server draws them from the saved chunks
 * ({@link SavedChunkMap}) on one low-priority thread of its own, never touching the loaded world. Drawn squares are kept
 * under {@code <world>/aurelia/map/<dimension>/}, a folder of their own, and drawn again at most every
 * {@link #REDRAW_MILLIS} once their chunks changed. {@code config/aurelia/server-map.json} turns it off with
 * {@code "enabled": false}, and picks who is answered with {@code "players"}: {@code "ops"} (the default: operators and
 * the single-player host, since it shows the whole saved world, other players' bases included) or {@code "all"} (anyone who
 * may open the dashboard). A player gets the list of squares at most every {@link #LIST_MILLIS}, from a listing kept for
 * that long, and has at most {@link #MAX_QUEUED} squares waiting to be drawn, counted until they are done even across a
 * reconnect.
 */
public final class ServerMapTiles {

	/** Client to server: nothing. Server to client: the dimension, then the keys of the squares with saved chunks. */
	public static final Identifier LIST = new Identifier("mtr", "atr_map_list");
	/** Client to server: up to {@link #MAX_KEYS} square keys. Server to client: the dimension, a key, then the square in the map file format (empty: nothing there). */
	public static final Identifier TILE = new Identifier("mtr", "atr_map_tile");
	public static final int MAX_KEYS = 8;
	private static final int MAX_QUEUED = 16;
	/** Squares waiting to be drawn for everyone together. */
	private static final int MAX_QUEUED_ALL = 256;
	private static final long LIST_MILLIS = 30_000;
	private static final long REDRAW_MILLIS = 10 * 60_000;
	/** A custom payload must stay under a megabyte. */
	private static final int MAX_BYTES = 1_000_000;
	private static final ExecutorService DRAW = Executors.newSingleThreadExecutor(runnable -> {
		final Thread thread = new Thread(runnable, "ATR map draw");
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		return thread;
	});
	private static final Map<UUID, Integer> QUEUED = new ConcurrentHashMap<>();
	private static final AtomicInteger QUEUED_ALL = new AtomicInteger();
	/** Server thread only: when each player last got the list, and the listing of each dimension. */
	private static final Map<UUID, Listed> LISTED = new HashMap<>();
	private static final Map<String, Listing> LISTINGS = new HashMap<>();
	private static volatile boolean enabled = true;
	private static volatile boolean everyone;

	private record Listed(String dimension, long time) {
	}

	/** A dimension's list of squares, made on the draw thread; players who ask while it is made wait for it. */
	private static final class Listing {
		/** When the keys were listed; 0 before the first listing. */
		private long made;
		private long[] keys = new long[0];
		/** The same keys, so a request for a square not listed is ignored. */
		private LongOpenHashSet listed = new LongOpenHashSet();
		private boolean making;
		private final List<ServerPlayerEntity> waiting = new ArrayList<>();
	}

	private ServerMapTiles() {
	}

	public static void registerServer() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			readConfig(FabricLoader.getInstance().getConfigDir());
			LISTED.clear();
			LISTINGS.clear();
		});
		ServerPlayNetworking.registerGlobalReceiver(LIST, (server, player, handler, buf, responseSender) -> server.execute(() -> list(server, player)));
		ServerPlayNetworking.registerGlobalReceiver(TILE, (server, player, handler, buf, responseSender) -> {
			final int count = buf.readVarInt();
			if (count <= 0 || count > MAX_KEYS) {
				return;
			}
			final long[] keys = new long[count];
			for (int i = 0; i < count; i++) {
				keys[i] = buf.readLong();
			}
			server.execute(() -> tiles(server, player, keys));
		});
	}

	private static boolean allowed(ServerPlayerEntity player) {
		if (!enabled || player.getServerWorld().getDimension().hasCeiling()) {
			return false;
		}
		final boolean operator = Operators.isOperator(player);
		return everyone ? DashboardPackets.isAllowed(operator, DashboardPackets.hasDashboardItem(player)) : operator;
	}

	private static void list(MinecraftServer server, ServerPlayerEntity player) {
		if (!allowed(player)) {
			return;
		}
		final long now = System.currentTimeMillis();
		final ServerWorld world = player.getServerWorld();
		final String dimension = world.getRegistryKey().getValue().toString();
		final Listed last = LISTED.get(player.getUuid());
		// once per dimension every LIST_MILLIS: a new dimension is listed at once
		if (last != null && last.dimension.equals(dimension) && now - last.time < LIST_MILLIS) {
			return;
		}
		LISTED.put(player.getUuid(), new Listed(dimension, now));
		final Listing listing = LISTINGS.computeIfAbsent(dimension, key -> new Listing());
		if (now - listing.made < LIST_MILLIS) {
			sendList(player, dimension, listing.keys);
			return;
		}
		listing.waiting.add(player);
		if (listing.making) {
			return;
		}
		listing.making = true;
		final Path regionFolder = regionFolder(server, world);
		DRAW.execute(() -> {
			final long[] keys = listSquares(regionFolder);
			server.execute(() -> {
				listing.making = false;
				listing.made = System.currentTimeMillis();
				listing.keys = keys;
				listing.listed = new LongOpenHashSet(keys);
				for (final ServerPlayerEntity waiting : listing.waiting) {
					if (!waiting.isDisconnected()) {
						sendList(waiting, dimension, keys);
					}
				}
				listing.waiting.clear();
			});
		});
	}

	private static void sendList(ServerPlayerEntity player, String dimension, long[] keys) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(dimension);
		buf.writeVarInt(keys.length);
		for (final long key : keys) {
			buf.writeLong(key);
		}
		ServerPlayNetworking.send(player, LIST, buf);
	}

	/** The keys of the squares that hold saved chunks: four per region file with anything in it. */
	private static long[] listSquares(Path regionFolder) {
		final List<Long> keys = new ArrayList<>();
		try (DirectoryStream<Path> files = Files.isDirectory(regionFolder) ? Files.newDirectoryStream(regionFolder, "r.*.mca") : null) {
			if (files != null) {
				for (final Path file : files) {
					final String[] parts = file.getFileName().toString().split("\\.");
					if (parts.length == 4 && Files.size(file) > 8192) {
						final int mcaX = Integer.parseInt(parts[1]);
						final int mcaZ = Integer.parseInt(parts[2]);
						for (int i = 0; i < 4; i++) {
							keys.add(MapRegion.key(mcaX * 2 + (i & 1), mcaZ * 2 + (i >> 1)));
						}
					}
				}
			}
		} catch (IOException | NumberFormatException e) {
			Init.LOGGER.warn("[ATR] region folder {} could not be listed for the map", regionFolder, e);
		}
		final long[] array = new long[keys.size()];
		for (int i = 0; i < array.length; i++) {
			array[i] = keys.get(i);
		}
		return array;
	}

	private static void tiles(MinecraftServer server, ServerPlayerEntity player, long[] keys) {
		if (!allowed(player)) {
			return;
		}
		final ServerWorld world = player.getServerWorld();
		final String dimension = world.getRegistryKey().getValue().toString();
		final Path regionFolder = regionFolder(server, world);
		final Path cacheFolder = server.getSavePath(WorldSavePath.ROOT).resolve("aurelia").resolve("map").resolve(dimension.replaceAll("[^A-Za-z0-9._-]", "_"));
		final int bottomY = world.getBottomY();
		final int height = world.getHeight();
		final UUID uuid = player.getUuid();
		final Listing listing = LISTINGS.get(dimension);
		for (final long key : keys) {
			// only squares the listing holds, so the cache never grows past the world's saved chunks
			if (listing == null || !listing.listed.contains(key)) {
				continue;
			}
			// counted until drawn, not reset when the player reconnects, so the queue can only grow by what is done
			if (QUEUED.getOrDefault(uuid, 0) >= MAX_QUEUED || QUEUED_ALL.get() >= MAX_QUEUED_ALL) {
				return;
			}
			QUEUED.merge(uuid, 1, Integer::sum);
			QUEUED_ALL.incrementAndGet();
			DRAW.execute(() -> {
				// a player gone meanwhile is not drawn for, so their queue drains quickly
				final byte[] bytes = player.isDisconnected() ? null : tile(regionFolder, cacheFolder, (int) (key >> 32), (int) key, bottomY, height);
				server.execute(() -> {
					QUEUED.computeIfPresent(uuid, (id, queued) -> queued <= 1 ? null : queued - 1);
					QUEUED_ALL.decrementAndGet();
					if (bytes != null && !player.isDisconnected()) {
						final PacketByteBuf buf = PacketByteBufs.create();
						buf.writeString(dimension);
						buf.writeLong(key);
						buf.writeByteArray(bytes);
						ServerPlayNetworking.send(player, TILE, buf);
					}
				});
			});
		}
	}

	/** The square in the map file format: from the cache while it is current, else drawn again; empty when nothing is there. */
	private static byte[] tile(Path regionFolder, Path cacheFolder, int regionX, int regionZ, int bottomY, int height) {
		final Path mca = SavedChunkMap.mcaFile(regionFolder, regionX, regionZ);
		final Path cache = cacheFolder.resolve("r." + regionX + "." + regionZ + ".atrmap");
		try {
			if (!Files.isRegularFile(mca)) {
				return new byte[0];
			}
			if (Files.isRegularFile(cache)) {
				final long cached = Files.getLastModifiedTime(cache).toMillis();
				if (cached >= Files.getLastModifiedTime(mca).toMillis() || System.currentTimeMillis() - cached < REDRAW_MILLIS) {
					return Files.readAllBytes(cache);
				}
			}
			final MapRegion region = SavedChunkMap.render(regionFolder, regionX, regionZ, bottomY, height);
			if (region == null) {
				return new byte[0];
			}
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			MapRegionCodec.write(out, region.copyColors(), region.copyHeights());
			final byte[] bytes = out.toByteArray();
			if (bytes.length > MAX_BYTES) {
				return new byte[0];
			}
			Files.createDirectories(cacheFolder);
			final Path temp = cache.resolveSibling(cache.getFileName() + ".tmp");
			try (OutputStream file = Files.newOutputStream(temp)) {
				file.write(bytes);
			}
			Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			return bytes;
		} catch (IOException | RuntimeException e) {
			Init.LOGGER.warn("[ATR] map square {} {} could not be drawn from saved chunks", regionX, regionZ, e);
			return new byte[0];
		}
	}

	private static Path regionFolder(MinecraftServer server, ServerWorld world) {
		return DimensionType.getSaveDirectory(world.getRegistryKey(), server.getSavePath(WorldSavePath.ROOT)).resolve("region");
	}

	private static void readConfig(Path configDir) {
		enabled = true;
		everyone = false;
		final Path file = configDir.resolve("aurelia").resolve("server-map.json");
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
			everyone = root.has("players") && "all".equalsIgnoreCase(root.get("players").getAsString());
		} catch (Exception e) {
			Init.LOGGER.warn("[ATR] {} could not be read; the server map answers operators and the single-player host only", file, e);
		}
	}
}
