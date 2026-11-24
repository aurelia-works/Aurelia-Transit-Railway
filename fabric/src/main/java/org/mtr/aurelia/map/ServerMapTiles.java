package org.mtr.aurelia.map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.dimension.DimensionType;
import org.mtr.aurelia.dashboard.DashboardPackets;
import org.mtr.libraries.com.google.gson.JsonParser;
import org.mtr.mod.Init;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The server half of the map's squaremap-style fill: a player's map asks which squares the world has saved chunks in,
 * then asks for the squares in view that it has never explored, and the server draws them from the saved chunks
 * ({@link SavedChunkMap}) on one low-priority thread of its own, never touching the loaded world. Drawn squares are kept
 * under {@code <world>/aurelia/map/<dimension>/}, a folder of their own, and drawn again at most every
 * {@link #REDRAW_MILLIS} once their chunks changed. Only players who may open the dashboard are answered, and only while
 * {@code config/aurelia/server-map.json} has {@code "enabled": true} (the default).
 */
public final class ServerMapTiles {

	/** Client to server: nothing. Server to client: the dimension, then the keys of the squares with saved chunks. */
	public static final Identifier LIST = new Identifier("mtr", "atr_map_list");
	/** Client to server: up to {@link #MAX_KEYS} square keys. Server to client: the dimension, a key, then the square in the map file format (empty: nothing there). */
	public static final Identifier TILE = new Identifier("mtr", "atr_map_tile");
	public static final int MAX_KEYS = 8;
	private static final int MAX_QUEUED = 16;
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
	private static volatile boolean enabled = true;

	private ServerMapTiles() {
	}

	public static void registerServer() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> enabled = readEnabled(FabricLoader.getInstance().getConfigDir()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> QUEUED.remove(handler.player.getUuid()));
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
		return enabled && !player.getServerWorld().getDimension().hasCeiling()
				&& DashboardPackets.isAllowed(player.isCreative(), player.hasPermissionLevel(2), DashboardPackets.hasDashboardItem(player));
	}

	private static void list(MinecraftServer server, ServerPlayerEntity player) {
		if (!allowed(player)) {
			return;
		}
		final ServerWorld world = player.getServerWorld();
		final String dimension = world.getRegistryKey().getValue().toString();
		final Path regionFolder = regionFolder(server, world);
		DRAW.execute(() -> {
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
			final PacketByteBuf buf = PacketByteBufs.create();
			buf.writeString(dimension);
			buf.writeVarInt(keys.size());
			for (final long key : keys) {
				buf.writeLong(key);
			}
			server.execute(() -> {
				if (!player.isDisconnected()) {
					ServerPlayNetworking.send(player, LIST, buf);
				}
			});
		});
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
		for (final long key : keys) {
			if (QUEUED.merge(uuid, 1, Integer::sum) > MAX_QUEUED) {
				QUEUED.merge(uuid, -1, Integer::sum);
				return;
			}
			DRAW.execute(() -> {
				final byte[] bytes = tile(regionFolder, cacheFolder, (int) (key >> 32), (int) key, bottomY, height);
				final PacketByteBuf buf = PacketByteBufs.create();
				buf.writeString(dimension);
				buf.writeLong(key);
				buf.writeByteArray(bytes);
				server.execute(() -> {
					QUEUED.computeIfPresent(uuid, (id, queued) -> queued <= 1 ? null : queued - 1);
					if (!player.isDisconnected()) {
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

	private static boolean readEnabled(Path configDir) {
		final Path file = configDir.resolve("aurelia").resolve("server-map.json");
		if (!Files.isRegularFile(file)) {
			return true;
		}
		try {
			final var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			return !root.has("enabled") || root.get("enabled").getAsBoolean();
		} catch (Exception e) {
			Init.LOGGER.warn("[ATR] {} could not be read; the server map stays on", file, e);
			return true;
		}
	}
}
