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
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectIterator;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * The client half of the server-drawn map ({@link ServerMapTiles}): asks the server once which squares have saved chunks,
 * then, while a map view is open, asks for the ones in view this client has never explored, nearest the view's centre
 * first and a few at a time. A square that arrives becomes part of the explored map and is saved with it, so it is asked
 * for once. A server without ATR's map never answers, and the map simply shows what this client explored.
 */
public final class ServerTiles {

	private static final int MAX_ASKED = 4;
	private static final long ANSWER_MILLIS = 30_000;

	private static final LongOpenHashSet AVAILABLE = new LongOpenHashSet();
	/** Square key to when it was asked for. */
	private static final Long2LongOpenHashMap ASKED = new Long2LongOpenHashMap();
	/** Squares the server had nothing for, so they are not asked for again. */
	private static final LongOpenHashSet EMPTY = new LongOpenHashSet();
	private static final long[] PICKED = new long[MAX_ASKED];
	private static long[] available = new long[0];
	@Nullable
	private static String dimension;
	private static boolean listAsked;

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
		listAsked = false;
	}

	/** Asks for the unexplored squares in this view of region range. Render thread; allocates only when it sends. */
	public static void askInView(ExploredMap map, int firstX, int lastX, int firstZ, int lastZ) {
		if (dimension == null) {
			return;
		}
		if (!listAsked) {
			if (ClientPlayNetworking.canSend(ServerMapTiles.LIST)) {
				listAsked = true;
				ClientPlayNetworking.send(ServerMapTiles.LIST, PacketByteBufs.empty());
			}
			return;
		}
		final long now = System.currentTimeMillis();
		if (!ASKED.isEmpty()) {
			final ObjectIterator<Long2LongMap.Entry> entries = ASKED.long2LongEntrySet().fastIterator();
			while (entries.hasNext()) {
				if (now - entries.next().getLongValue() > ANSWER_MILLIS) {
					entries.remove();
				}
			}
		}
		final double centerX = (firstX + lastX) / 2.0;
		final double centerZ = (firstZ + lastZ) / 2.0;
		int count = 0;
		while (ASKED.size() < MAX_ASKED) {
			long best = 0;
			double bestDistance = Double.MAX_VALUE;
			for (final long key : available) {
				final int regionX = (int) (key >> 32);
				final int regionZ = (int) key;
				if (regionX < firstX || regionX > lastX || regionZ < firstZ || regionZ > lastZ || ASKED.containsKey(key) || EMPTY.contains(key) || map.known(regionX, regionZ)) {
					continue;
				}
				final double distance = (regionX - centerX) * (regionX - centerX) + (regionZ - centerZ) * (regionZ - centerZ);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = key;
				}
			}
			if (bestDistance == Double.MAX_VALUE) {
				break;
			}
			ASKED.put(best, now);
			PICKED[count++] = best;
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
