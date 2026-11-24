package org.mtr.mod.engine;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

/**
 * The on/off switches for {@link EngineFeature}s. Read from MTR's simulation threads, so the current values sit in a
 * volatile array that is replaced, never changed in place. Only operators may change them.
 */
public final class EngineSettings {

	public static final Identifier SYNC = new Identifier("mtr", "atr_engine");
	public static final Identifier SET = new Identifier("mtr", "atr_engine_set");
	public static final Identifier LAG = new Identifier("mtr", "atr_engine_lag");
	private static final String FILE = "atr_engine";

	private static volatile boolean[] enabled = defaults();

	private EngineSettings() {
	}

	public static boolean enabled(EngineFeature feature) {
		return enabled[feature.ordinal()];
	}

	/**
	 * Client: takes the values the server sent, so the client's own train prediction (hills, for one) matches the
	 * server's. In single player this is the same copy the server uses, and the values are the same.
	 */
	public static void mirror(Map<EngineFeature, Boolean> values) {
		final boolean[] next = defaults();
		values.forEach((feature, value) -> next[feature.ordinal()] = value);
		enabled = next;
	}

	public static void registerServer() {
		// as soon as the overworld exists, before MTR's simulators build any paths
		ServerWorldEvents.LOAD.register((server, world) -> {
			if (world.getRegistryKey() == World.OVERWORLD) {
				enabled = state(world).values.clone();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			enabled = defaults();
			EngineLag.clear();
		});
		// once a second, operators get the lag of the dimension they are in
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 == 0 && enabled(EngineFeature.LAG_READOUT)) {
				server.getPlayerManager().getPlayerList().forEach(player -> {
					if (player.hasPermissionLevel(2)) {
						final EngineLag.Reading reading = EngineLag.reading(org.mtr.mod.Init.getWorldId(new org.mtr.mapping.holder.World(player.getServerWorld())));
						final PacketByteBuf buf = PacketByteBufs.create();
						buf.writeVarLong(reading.averageMicros());
						buf.writeVarLong(reading.worstMicros());
						ServerPlayNetworking.send(player, LAG, buf);
					}
				});
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final int ordinal = buf.readVarInt();
			final boolean value = buf.readBoolean();
			server.execute(() -> set(server, player, ordinal, value));
		});
	}

	/** Client: asks the server to switch one feature. */
	public static PacketByteBuf request(EngineFeature feature, boolean value) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(feature.ordinal());
		buf.writeBoolean(value);
		return buf;
	}

	/** Client: reads what {@link #send} wrote. Unknown keys from a newer server are skipped. */
	public static Map<EngineFeature, Boolean> read(PacketByteBuf buf) {
		final Map<EngineFeature, Boolean> values = new EnumMap<>(EngineFeature.class);
		final int count = buf.readVarInt();
		for (int i = 0; i < count; i++) {
			final String key = buf.readString();
			final boolean value = buf.readBoolean();
			for (final EngineFeature feature : EngineFeature.values()) {
				if (feature.key.equals(key)) {
					values.put(feature, value);
				}
			}
		}
		return values;
	}

	private static void set(MinecraftServer server, ServerPlayerEntity player, int ordinal, boolean value) {
		if (!player.hasPermissionLevel(2) || ordinal < 0 || ordinal >= EngineFeature.values().length) {
			return;
		}
		final State state = state(server);
		state.values[ordinal] = value;
		state.markDirty();
		enabled = state.values.clone();
		server.getPlayerManager().getPlayerList().forEach(EngineSettings::send);
	}

	private static void send(ServerPlayerEntity player) {
		final PacketByteBuf buf = PacketByteBufs.create();
		final boolean[] values = enabled;
		buf.writeVarInt(values.length);
		for (final EngineFeature feature : EngineFeature.values()) {
			buf.writeString(feature.key);
			buf.writeBoolean(values[feature.ordinal()]);
		}
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	private static boolean[] defaults() {
		final boolean[] values = new boolean[EngineFeature.values().length];
		java.util.Arrays.fill(values, true);
		return values;
	}

	private static State state(MinecraftServer server) {
		return state(server.getOverworld());
	}

	private static State state(net.minecraft.server.world.ServerWorld overworld) {
		return overworld.getPersistentStateManager().getOrCreate(State::fromNbt, () -> new State(defaults()), FILE);
	}

	private static final class State extends PersistentState {

		private final boolean[] values;

		private State(boolean[] values) {
			this.values = values;
		}

		/** A missing key is a feature added after the file was written: it starts on. */
		private static State fromNbt(NbtCompound nbt) {
			final boolean[] values = defaults();
			for (final EngineFeature feature : EngineFeature.values()) {
				if (nbt.contains(feature.key)) {
					values[feature.ordinal()] = nbt.getBoolean(feature.key);
				}
			}
			return new State(values);
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			for (final EngineFeature feature : EngineFeature.values()) {
				nbt.putBoolean(feature.key, values[feature.ordinal()]);
			}
			return nbt;
		}
	}
}
