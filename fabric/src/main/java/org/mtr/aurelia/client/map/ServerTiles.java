package org.mtr.aurelia.client.map;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import org.mtr.aurelia.map.MapRegion;
import org.mtr.aurelia.map.MapRegionCodec;
import org.mtr.aurelia.map.ServerMapTiles;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrays;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectIterator;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * The client half of the server-drawn map ({@link ServerMapTiles}): asks the server which squares have saved chunks (again if no list came),
 * then, while a full-screen map is open, asks for the ones in view this client has never explored, nearest the view's centre
 * first and a few at a time. A square that arrives becomes part of the explored map and is saved with it, so it is asked
 * for once. A server without ATR's map never answers, and the map simply shows what this client explored.
 */
public final class ServerTiles {

	private static final int MAX_ASKED = 4;
	private static final long ANSWER_MILLIS = 30_000;
	/** The server lists at most every 30 seconds; a list that never came is asked for again after this. */
	private static final long LIST_RETRY_MILLIS = 35_000;

	private static final LongOpenHashSet AVAILABLE = new LongOpenHashSet();
	/** Square key to when it was asked for. */
	private static final Long2LongOpenHashMap ASKED = new Long2LongOpenHashMap();
	/** Squares the server had nothing for, so they are not asked for again. */
	private static final LongOpenHashSet EMPTY = new LongOpenHashSet();
	private static final long[] PICKED = new long[MAX_ASKED];
	private static long[] available = new long[0];
	@Nullable
	private static String dimension;
	private static long listAskedAt;
	private static boolean listed;
	/** The squares worth asking for in the last view, nearest its centre first; rebuilt only when the view or what is known changes. */
	private static long[] candidates = new long[64];
	private static int candidateCount;
	private static int cursor;
	private static int viewFirstX;
	private static int viewLastX;
	private static int viewFirstZ;
	private static int viewLastZ;
	private static boolean dirty = true;

	private ServerTiles() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ServerMapTiles.LIST, (client, handler, buf, responseSender) -> {
			final String from = buf.readString();
			final long[] keys = new long[buf.readVarInt()];
			for (int i = 0; i < keys.length; i++) {
				keys[i] = buf.readLong();
			}
			client.execute(() -> {
				if (from.equals(dimension)) {
					AVAILABLE.clear();
					for (final long key : keys) {
						AVAILABLE.add(key);
					}
					available = AVAILABLE.toLongArray();
					listed = true;
					dirty = true;
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(ServerMapTiles.TILE, (client, handler, buf, responseSender) -> {
			final String from = buf.readString();
			final long key = buf.readLong();
			final byte[] bytes = buf.readByteArray();
			MapRegion region = null;
			if (bytes.length > 0) {
				try {
					region = MapRegionCodec.read(new ByteArrayInputStream(bytes), (int) (key >> 32), (int) key);
				} catch (IOException e) {
					region = null;
				}
			}
			final MapRegion received = region;
			client.execute(() -> received(from, key, received));
		});
	}

	/** A new map was opened for this dimension: forget the last one's squares. */
	public static void start(String dimensionId) {
		reset();
		dimension = dimensionId;
	}

	public static void reset() {
		AVAILABLE.clear();
		ASKED.clear();
		EMPTY.clear();
		available = new long[0];
		dimension = null;
		listAskedAt = 0;
		listed = false;
		candidateCount = 0;
		cursor = 0;
		dirty = true;
	}

	/**
	 * Asks for the unexplored squares in a full-screen map view: the N map and MTR's dashboard map call this, the minimap
	 * never does, so walking about with it on downloads nothing. Render thread; scans the server's list only when the view
	 * moves to other squares or what is known changes, and allocates only when it sends.
	 */
	public static void askInView(int width, int height, double centerX, double centerZ, double scale) {
		final ExploredMap map = MapClient.map();
		if (dimension == null || map == null || width <= 0 || height <= 0) {
			return;
		}
		final int firstX = MapRegion.regionOf((int) Math.floor(centerX - width / 2.0 / scale));
		final int lastX = MapRegion.regionOf((int) Math.floor(centerX + width / 2.0 / scale));
		final int firstZ = MapRegion.regionOf((int) Math.floor(centerZ - height / 2.0 / scale));
		final int lastZ = MapRegion.regionOf((int) Math.floor(centerZ + height / 2.0 / scale));
		final long now = System.currentTimeMillis();
		if (!listed) {
			if (now - listAskedAt > LIST_RETRY_MILLIS && ClientPlayNetworking.canSend(ServerMapTiles.LIST)) {
				listAskedAt = now;
				ClientPlayNetworking.send(ServerMapTiles.LIST, PacketByteBufs.empty());
			}
			return;
		}
		if (!ASKED.isEmpty()) {
			final ObjectIterator<Long2LongMap.Entry> entries = ASKED.long2LongEntrySet().fastIterator();
			while (entries.hasNext()) {
				if (now - entries.next().getLongValue() > ANSWER_MILLIS) {
					entries.remove();
					// never answered: ask again
					dirty = true;
				}
			}
		}
		if (dirty || firstX != viewFirstX || lastX != viewLastX || firstZ != viewFirstZ || lastZ != viewLastZ) {
			dirty = false;
			viewFirstX = firstX;
			viewLastX = lastX;
			viewFirstZ = firstZ;
			viewLastZ = lastZ;
			collect(map, firstX, lastX, firstZ, lastZ);
		}
		int count = 0;
		while (ASKED.size() < MAX_ASKED && cursor < candidateCount) {
			final long key = candidates[cursor++];
			if (ASKED.containsKey(key) || EMPTY.contains(key) || map.known((int) (key >> 32), (int) key)) {
				continue;
			}
			ASKED.put(key, now);
			PICKED[count++] = key;
		}
		if (count > 0) {
			final PacketByteBuf packet = PacketByteBufs.create();
			packet.writeVarInt(count);
			for (int i = 0; i < count; i++) {
				packet.writeLong(PICKED[i]);
			}
			ClientPlayNetworking.send(ServerMapTiles.TILE, packet);
		}
	}

	/** The listed squares in view that this client has not explored, nearest the view's centre first. */
	private static void collect(ExploredMap map, int firstX, int lastX, int firstZ, int lastZ) {
		candidateCount = 0;
		cursor = 0;
		for (final long key : available) {
			final int regionX = (int) (key >> 32);
			final int regionZ = (int) key;
			if (regionX < firstX || regionX > lastX || regionZ < firstZ || regionZ > lastZ || EMPTY.contains(key) || map.known(regionX, regionZ)) {
				continue;
			}
			if (candidateCount == candidates.length) {
				candidates = Arrays.copyOf(candidates, candidateCount * 2);
			}
			candidates[candidateCount++] = key;
		}
		final long centerX = firstX + lastX;
		final long centerZ = firstZ + lastZ;
		LongArrays.quickSort(candidates, 0, candidateCount, (a, b) -> Long.compare(distance(a, centerX, centerZ), distance(b, centerX, centerZ)));
	}

	/** Squared distance from the view's centre, in half squares so it stays whole. */
	private static long distance(long key, long doubleCenterX, long doubleCenterZ) {
		final long dx = 2L * (int) (key >> 32) - doubleCenterX;
		final long dz = 2L * (int) key - doubleCenterZ;
		return dx * dx + dz * dz;
	}

	private static void received(String from, long key, @Nullable MapRegion region) {
		if (!from.equals(dimension)) {
			return;
		}
		ASKED.remove(key);
		final ExploredMap map = MapClient.map();
		if (region == null) {
			EMPTY.add(key);
		} else if (map != null) {
			map.adopt(region);
		}
	}
}
