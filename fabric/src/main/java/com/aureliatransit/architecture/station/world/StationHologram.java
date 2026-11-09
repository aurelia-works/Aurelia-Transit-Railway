package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.network.ModPackets;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.PlannedBlock;
import com.aureliatransit.architecture.station.StationPlan;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

/**
 * Sends a pending plan to its planning player as a client-side hologram: every planned block's real state, the frame it is
 * placed in, and whether it would replace a block that already stands there. The plan is one compressed packet, so a big
 * station does not trickle block by block, and only the planning player receives it. The client builds the mesh once and
 * draws it; nothing is placed in the world.
 */
public final class StationHologram {

	/** Bumped when the packet layout changes; a client from another build drops the packet. */
	public static final int PROTOCOL = 1;
	/** Outer message types of {@link ModPackets#STATION_HOLOGRAM}. */
	public static final int TYPE_CLEAR = 0;
	public static final int TYPE_PLAN = 1;
	private static final int MAX_ID = 256;
	private static final int MAX_STATE = 512;
	/** Vanilla drops a custom payload over 1 MiB; the plan is compressed well below it or not sent. */
	private static final int MAX_PAYLOAD = 1_000_000;

	private StationHologram() {
	}

	/** Sends {@code pending}'s blocks to {@code player}; replaces any hologram the player already had. */
	public static void show(ServerPlayerEntity player, StationSessions.Pending pending) {
		if (player.networkHandler == null) {
			return;
		}
		final MinecraftServer server = player.getServer();
		final ServerWorld world = server == null ? null : server.getWorld(pending.dimension());
		if (world == null || player.getServerWorld() != world) {
			return;
		}
		final StationPlan plan = pending.plan();
		final LocalFrame frame = pending.frame();
		final byte[] raw = encode(pending.dimension().getValue().toString(), frame, plan, world);
		final byte[] compressed = deflate(raw);
		if (compressed.length > MAX_PAYLOAD) {
			clear(player);
			AureliaTransitArchitecture.LOGGER.warn("Station hologram for {} is {} compressed bytes; not sent", plan.blocks().size(), compressed.length);
			return;
		}
		final PacketByteBuf out = PacketByteBufs.create();
		out.writeByte(TYPE_PLAN);
		out.writeVarInt(raw.length);
		out.writeByteArray(compressed);
		ServerPlayNetworking.send(player, ModPackets.STATION_HOLOGRAM, out);
	}

	/** Tells {@code player} to stop drawing the hologram. */
	public static void clear(ServerPlayerEntity player) {
		if (player.networkHandler == null) {
			return;
		}
		final PacketByteBuf out = PacketByteBufs.create();
		out.writeByte(TYPE_CLEAR);
		ServerPlayNetworking.send(player, ModPackets.STATION_HOLOGRAM, out);
	}

	private static byte[] encode(String dimension, LocalFrame frame, StationPlan plan, ServerWorld world) {
		final List<PlannedBlock> blocks = plan.blocks();
		final Map<String, Integer> paletteIndex = new HashMap<>();
		final List<String> blockIds = new ArrayList<>();
		final List<String> states = new ArrayList<>();
		for (final PlannedBlock block : blocks) {
			final String key = block.blockId() + "\u0000" + block.state();
			if (!paletteIndex.containsKey(key)) {
				paletteIndex.put(key, blockIds.size());
				blockIds.add(block.blockId());
				states.add(block.state());
			}
		}
		final ByteBuf raw = Unpooled.buffer();
		final PacketByteBuf buf = new PacketByteBuf(raw);
		try {
			buf.writeVarInt(PROTOCOL);
			buf.writeString(dimension, MAX_ID);
			buf.writeInt(frame.ox());
			buf.writeInt(frame.oy());
			buf.writeInt(frame.oz());
			buf.writeVarInt(frame.forward().ordinal());
			buf.writeVarInt(blockIds.size());
			for (int i = 0; i < blockIds.size(); i++) {
				buf.writeString(blockIds.get(i), MAX_ID);
				buf.writeString(states.get(i), MAX_STATE);
			}
			buf.writeVarInt(blocks.size());
			for (final PlannedBlock block : blocks) {
				final int x = frame.x(block.u(), block.v());
				final int y = frame.y(block.y());
				final int z = frame.z(block.u(), block.v());
				buf.writeVarInt(paletteIndex.get(block.blockId() + "\u0000" + block.state()));
				writeSigned(buf, block.u());
				writeSigned(buf, block.y());
				writeSigned(buf, block.v());
				buf.writeVarInt(block.front().ordinal());
				buf.writeVarInt(block.role().ordinal());
				buf.writeBoolean(replaces(world, x, y, z));
			}
			final byte[] bytes = new byte[buf.readableBytes()];
			buf.readBytes(bytes);
			return bytes;
		} finally {
			buf.release();
		}
	}

	/** True when the cell already holds something, so the plan would replace it; unloaded cells are left unmarked. */
	private static boolean replaces(ServerWorld world, int x, int y, int z) {
		if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
			return false;
		}
		return !world.getBlockState(new BlockPos(x, y, z)).isAir();
	}

	private static void writeSigned(PacketByteBuf buf, int value) {
		buf.writeVarInt((value << 1) ^ (value >> 31));
	}

	private static byte[] deflate(byte[] raw) {
		final Deflater deflater = new Deflater(Deflater.BEST_SPEED);
		try {
			deflater.setInput(raw);
			deflater.finish();
			final ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
			final byte[] buffer = new byte[8192];
			while (!deflater.finished()) {
				final int written = deflater.deflate(buffer);
				out.write(buffer, 0, written);
			}
			return out.toByteArray();
		} finally {
			deflater.end();
		}
	}

}
