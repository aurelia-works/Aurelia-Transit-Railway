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
import org.mtr.aurelia.route.RouteControl;

/**
 * Saves {@link RouteControlOptions} in the overworld's data folder ({@code atr_route_control.dat}), sends them to
 * every player on join and after each change, and takes changes from players who may edit MTR data (creative or
 * survival, as MTR). Same shape as {@link DepotOptionsSync}.
 */
public final class RouteControlSync {

	public static final Identifier SYNC = new Identifier("mtr", "atr_route_control");
	public static final Identifier SET = new Identifier("mtr", "atr_route_control_set");
	private static final String FILE = "atr_route_control";

	private RouteControlSync() {
	}

	public static void registerServer() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), state(server).options));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final long routeId = buf.readLong();
			final RouteControl control = RouteControlOptions.readControl(buf);
			server.execute(() -> set(server, player, routeId, control));
		});
	}

	/** Client: asks the server to replace one route's control data (empty clears it). */
	public static PacketByteBuf request(long routeId, RouteControl control) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(routeId);
		RouteControlOptions.writeControl(buf, control == null ? RouteControl.EMPTY : control);
		return buf;
	}

	private static void set(MinecraftServer server, ServerPlayerEntity player, long routeId, RouteControl control) {
		final GameMode gameMode = player.interactionManager.getGameMode();
		if (gameMode != GameMode.CREATIVE && gameMode != GameMode.SURVIVAL) {
			return;
		}
		final State state = state(server);
		if (state.options.set(routeId, control)) {
			state.markDirty();
			server.getPlayerManager().getPlayerList().forEach(each -> send(each, state.options));
		}
	}

	private static void send(ServerPlayerEntity player, RouteControlOptions options) {
		final PacketByteBuf buf = PacketByteBufs.create();
		options.write(buf);
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(nbt -> new State(RouteControlOptions.fromNbt(nbt)), () -> new State(new RouteControlOptions()), FILE);
	}

	private static final class State extends PersistentState {

		private final RouteControlOptions options;

		private State(RouteControlOptions options) {
			this.options = options;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			return options.toNbt(nbt);
		}
	}
}
