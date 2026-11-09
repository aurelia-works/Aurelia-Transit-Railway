package org.mtr.mod.data;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
	public static final Identifier MASTER = new Identifier("mtr", "atr_route_control_master");
	private static final String FILE = "atr_route_control";
	private static boolean startupRegen = true;

	private RouteControlSync() {
	}

	public static void registerServer() {
		ServerLifecycleEvents.SERVER_STARTED.register(RouteControlSync::onServerStarted);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RouteControlOptions.clearServer();
			startupRegen = true;
		});
		ServerTickEvents.START_SERVER_TICK.register(RouteControlSync::onServerTick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer(), state(server).options));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final long routeId = buf.readLong();
			final RouteControl control = RouteControlOptions.readControl(buf);
			server.execute(() -> set(server, player, routeId, control));
		});
		ServerPlayNetworking.registerGlobalReceiver(MASTER, (server, player, handler, buf, response) -> {
			final boolean fallbackMaster = buf.readBoolean();
			server.execute(() -> setMaster(server, player, fallbackMaster));
		});
	}

	/** Client: asks the server to replace one route's control data (empty clears it). */
	public static PacketByteBuf request(long routeId, RouteControl control) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeLong(routeId);
		RouteControlOptions.writeControl(buf, control == null ? RouteControl.EMPTY : control);
		return buf;
	}

	/** Client: asks the server to change the world's fallback master switch. */
	public static PacketByteBuf requestMaster(boolean fallbackMaster) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBoolean(fallbackMaster);
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
			RouteControlOptions.publishServer(state.options);
			server.getPlayerManager().getPlayerList().forEach(each -> send(each, state.options));
			org.mtr.aurelia.route.RouteControlRegen.route(routeId);
		}
	}

	private static void setMaster(MinecraftServer server, ServerPlayerEntity player, boolean fallbackMaster) {
		final GameMode gameMode = player.interactionManager.getGameMode();
		if (gameMode != GameMode.CREATIVE && gameMode != GameMode.SURVIVAL) {
			return;
		}
		final State state = state(server);
		if (state.options.setFallbackMaster(fallbackMaster)) {
			state.markDirty();
			RouteControlOptions.publishServer(state.options);
			server.getPlayerManager().getPlayerList().forEach(each -> send(each, state.options));
			org.mtr.aurelia.route.RouteControlRegen.fallbackMaster();
		}
	}

	private static void send(ServerPlayerEntity player, RouteControlOptions options) {
		final PacketByteBuf buf = PacketByteBufs.create();
		options.write(buf);
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	private static void onServerStarted(MinecraftServer server) {
		state(server);
		startupRegen = true;
	}

	/**
	 * MTR builds simulators (and their load-time stop times) before the mod's server-started callback, so those times
	 * used each route's own platforms. One tick after startup, rebuild every depot with route control once; routes
	 * without it are left untouched and load exactly as before.
	 */
	private static void onServerTick(MinecraftServer server) {
		if (startupRegen) {
			startupRegen = !org.mtr.aurelia.route.RouteControlRegen.controlledDepots();
		}
	}

	private static State state(MinecraftServer server) {
		final State state = server.getOverworld().getPersistentStateManager().getOrCreate(nbt -> new State(RouteControlOptions.fromNbt(nbt)), () -> new State(new RouteControlOptions()), FILE);
		// Called on join, after an edit and at server start, so the simulator thread always sees the saved data.
		RouteControlOptions.publishServer(state.options);
		return state;
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
