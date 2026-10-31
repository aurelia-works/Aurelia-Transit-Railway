package org.mtr.mod.data;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.PersistentState;

/**
 * Saves {@link DepotOptions} in the overworld's data folder ({@code atr_depot_options.dat}), sends them to every player
 * on join and after each change, and takes changes from players who may edit MTR data (creative or survival, as MTR).
 */
public final class DepotOptionsSync {

	public static final Identifier SYNC = new Identifier("mtr", "atr_depot_options");
	public static final Identifier SET = new Identifier("mtr", "atr_depot_option_set");
	private static final String FILE = "atr_depot_options";

	private DepotOptionsSync() {
	}

	public static void registerServer() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), state(server).options));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final long depotId = buf.readLong();
			final int option = buf.readVarInt();
			final boolean value = buf.readBoolean();
			server.execute(() -> set(server, player, depotId, option, value));
		});
	}

	/** Client: asks the server to change one switch. */
	public static PacketByteBuf request(long depotId, DepotOptions.Option option, boolean value) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(depotId);
		buf.writeVarInt(option.ordinal());
		buf.writeBoolean(value);
		return buf;
	}

	private static void set(MinecraftServer server, ServerPlayerEntity player, long depotId, int option, boolean value) {
		final GameMode gameMode = player.interactionManager.getGameMode();
		if (option < 0 || option >= DepotOptions.Option.values().length || gameMode != GameMode.CREATIVE && gameMode != GameMode.SURVIVAL) {
			return;
		}
		final State state = state(server);
		if (state.options.set(depotId, DepotOptions.Option.values()[option], value)) {
			state.markDirty();
			server.getPlayerManager().getPlayerList().forEach(each -> send(each, state.options));
		}
	}

	private static void send(ServerPlayerEntity player, DepotOptions options) {
		final PacketByteBuf buf = PacketByteBufs.create();
		options.write(buf);
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(nbt -> new State(DepotOptions.fromNbt(nbt)), () -> new State(new DepotOptions()), FILE);
	}

	private static final class State extends PersistentState {

		private final DepotOptions options;

		private State(DepotOptions options) {
			this.options = options;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			return options.toNbt(nbt);
		}
	}
}
