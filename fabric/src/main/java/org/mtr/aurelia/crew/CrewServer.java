package org.mtr.aurelia.crew;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.mtr.mod.block.BlockSignalBase;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The crew roles on the server: who is guard, dispatcher or signaller, the bells they ring, and the signals held at
 * danger. A bell goes to the players near the one who rang it; each client works out whether it is for the train it
 * rides. Holds are kept per dimension in their own file ({@code atr_signal_holds}), so old worlds load unchanged, and
 * every player in the dimension is told when one changes.
 */
public final class CrewServer {

	/** Client to server: a bell (index, vehicle id or -1). */
	public static final Identifier BELL = new Identifier("mtr", "atr_crew_bell");
	/** Server to client: a bell rung nearby (index, vehicle id or -1, who rang it). */
	public static final Identifier BELL_HEARD = new Identifier("mtr", "atr_crew_bell_heard");
	/** Client to server: hold or release the signal at a position. */
	public static final Identifier HOLD = new Identifier("mtr", "atr_crew_hold");
	/** Server to client: every held signal in the player's dimension. */
	public static final Identifier HOLDS = new Identifier("mtr", "atr_crew_holds");
	/** Server to client: the player's own role. */
	public static final Identifier ROLE = new Identifier("mtr", "atr_crew_role");

	/** How far a guard's bell carries: the length of a long train and then some. */
	static final int BELL_RANGE = 256;
	/** How far a dispatcher's right away carries: the trains at the platforms around them. */
	static final int DISPATCH_RANGE = 48;
	/** How far away a signaller may hold a signal. */
	static final int HOLD_REACH = 96;
	static final int MAX_HOLDS = 1024;
	private static final long BELL_GAP_MILLIS = 800;
	private static final String FILE = "atr_signal_holds";

	private static final Map<UUID, CrewRole> ROLES = new HashMap<>();
	private static final Map<UUID, Long> LAST_BELL = new HashMap<>();

	private CrewServer() {
	}

	public static void registerServer() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			sendHolds(handler.player);
			sendRole(handler.player);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ROLES.remove(handler.player.getUuid());
			LAST_BELL.remove(handler.player.getUuid());
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> sendHolds(player));
		ServerPlayNetworking.registerGlobalReceiver(BELL, (server, player, handler, buf, response) -> {
			final int index = buf.readVarInt();
			final long vehicleId = buf.readLong();
			server.execute(() -> bell(player, CrewBell.byIndex(index), vehicleId));
		});
		ServerPlayNetworking.registerGlobalReceiver(HOLD, (server, player, handler, buf, response) -> {
			final BlockPos pos = buf.readBlockPos();
			server.execute(() -> hold(player, pos));
		});
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		final String[] roles = Arrays.stream(CrewRole.values()).map(CrewRole::id).toArray(String[]::new);
		dispatcher.register(CommandManager.literal("atr_crew")
				.then(CommandManager.literal("role")
						.executes(context -> {
							final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
							player.sendMessage(Text.translatable("gui.aurelia.crew.role.now", Text.translatable("gui.aurelia.crew.role." + role(player).id())), false);
							return 1;
						})
						.then(CommandManager.argument("role", StringArgumentType.word())
								.suggests((context, builder) -> CommandSource.suggestMatching(roles, builder))
								.executes(context -> {
									final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
									final CrewRole role = CrewRole.byId(StringArgumentType.getString(context, "role"));
									if (role == null) {
										player.sendMessage(Text.translatable("gui.aurelia.crew.role.unknown").formatted(Formatting.RED), false);
										return 0;
									}
									setRole(player, role);
									return 1;
								})))
				.then(CommandManager.literal("holds").executes(context -> {
					final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
					final Set<Long> held = state(player.getServer()).held(dimension(player.getServerWorld()));
					player.sendMessage(Text.translatable("gui.aurelia.crew.holds", held.size()), false);
					for (final long pos : held) {
						final BlockPos at = BlockPos.fromLong(pos);
						player.sendMessage(Text.literal("  " + at.getX() + " " + at.getY() + " " + at.getZ()), false);
					}
					return 1;
				}))
				.then(CommandManager.literal("release_all").requires(source -> source.hasPermissionLevel(2)).executes(context -> {
					final ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
					final ServerWorld world = player.getServerWorld();
					final State state = state(player.getServer());
					final int count = state.held(dimension(world)).size();
					state.clear(dimension(world));
					broadcastHolds(world);
					player.sendMessage(Text.translatable("gui.aurelia.crew.released_all", count), false);
					return 1;
				})));
	}

	public static CrewRole role(ServerPlayerEntity player) {
		return ROLES.getOrDefault(player.getUuid(), CrewRole.NONE);
	}

	private static void setRole(ServerPlayerEntity player, CrewRole role) {
		if (role == CrewRole.NONE) {
			ROLES.remove(player.getUuid());
		} else {
			ROLES.put(player.getUuid(), role);
		}
		sendRole(player);
		player.sendMessage(Text.translatable("gui.aurelia.crew.role.set", Text.translatable("gui.aurelia.crew.role." + role.id()))
				.append(" ").append(Text.translatable("gui.aurelia.crew.role." + role.id() + ".how")).formatted(Formatting.AQUA), false);
	}

	// ---- bells -----------------------------------------------------------------------------------------------------

	private static void bell(ServerPlayerEntity player, CrewBell bell, long vehicleId) {
		if (bell == null) {
			return;
		}
		if (role(player) != bell.role()) {
			player.sendMessage(Text.translatable("gui.aurelia.crew.not_role", Text.translatable("gui.aurelia.crew.role." + bell.role().id())).formatted(Formatting.RED), true);
			return;
		}
		final long now = System.currentTimeMillis();
		final Long last = LAST_BELL.get(player.getUuid());
		if (last != null && now - last < BELL_GAP_MILLIS) {
			return;
		}
		LAST_BELL.put(player.getUuid(), now);
		final int range = bell == CrewBell.RIGHT_AWAY ? DISPATCH_RANGE : BELL_RANGE;
		final long sent = bell == CrewBell.RIGHT_AWAY ? -1 : vehicleId;
		int heard = 0;
		for (final ServerPlayerEntity other : player.getServerWorld().getPlayers()) {
			if (other != player && other.squaredDistanceTo(player) <= (double) range * range) {
				final PacketByteBuf buf = PacketByteBufs.create();
				buf.writeVarInt(bell.ordinal());
				buf.writeLong(sent);
				buf.writeString(player.getName().getString());
				ServerPlayNetworking.send(other, BELL_HEARD, buf);
				heard++;
			}
		}
		player.sendMessage(Text.translatable(heard == 0 ? "gui.aurelia.crew.rang_nobody" : "gui.aurelia.crew.rang." + (bell == CrewBell.RIGHT_AWAY ? "right_away" : "ready")), true);
	}

	// ---- holds -----------------------------------------------------------------------------------------------------

	private static void hold(ServerPlayerEntity player, BlockPos pos) {
		if (role(player) != CrewRole.SIGNALLER) {
			player.sendMessage(Text.translatable("gui.aurelia.crew.not_role", Text.translatable("gui.aurelia.crew.role.signaller")).formatted(Formatting.RED), true);
			return;
		}
		final ServerWorld world = player.getServerWorld();
		if (!pos.isWithinDistance(player.getPos(), HOLD_REACH) || !world.isChunkLoaded(pos) || !(world.getBlockState(pos).getBlock() instanceof BlockSignalBase)) {
			player.sendMessage(Text.translatable("gui.aurelia.crew.hold.no_signal").formatted(Formatting.RED), true);
			return;
		}
		final State state = state(player.getServer());
		final String dimension = dimension(world);
		final boolean held = state.toggle(dimension, pos.asLong());
		if (held && state.held(dimension).size() > MAX_HOLDS) {
			state.toggle(dimension, pos.asLong());
			player.sendMessage(Text.translatable("gui.aurelia.crew.hold.full", MAX_HOLDS).formatted(Formatting.RED), true);
			return;
		}
		broadcastHolds(world);
		player.sendMessage(Text.translatable(held ? "gui.aurelia.crew.hold.on" : "gui.aurelia.crew.hold.off", pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
	}

	private static void broadcastHolds(ServerWorld world) {
		for (final ServerPlayerEntity player : world.getPlayers()) {
			sendHolds(player);
		}
	}

	private static void sendHolds(ServerPlayerEntity player) {
		final Set<Long> held = state(player.getServer()).held(dimension(player.getServerWorld()));
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(held.size());
		for (final long pos : held) {
			buf.writeLong(pos);
		}
		ServerPlayNetworking.send(player, HOLDS, buf);
	}

	private static void sendRole(ServerPlayerEntity player) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeVarInt(role(player).ordinal());
		ServerPlayNetworking.send(player, ROLE, buf);
	}

	private static String dimension(ServerWorld world) {
		return world.getRegistryKey().getValue().toString();
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(State::fromNbt, State::new, FILE);
	}

	private static final class State extends PersistentState {

		private final Map<String, Set<Long>> holds = new HashMap<>();

		private static State fromNbt(NbtCompound nbt) {
			final State state = new State();
			final NbtList list = nbt.getList("dimensions", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				final NbtCompound entry = list.getCompound(i);
				final Set<Long> held = state.holds.computeIfAbsent(entry.getString("dim"), key -> new LinkedHashSet<>());
				for (final long pos : entry.getLongArray("held")) {
					if (held.size() < MAX_HOLDS) {
						held.add(pos);
					}
				}
			}
			return state;
		}

		private Set<Long> held(String dimension) {
			return holds.getOrDefault(dimension, Set.of());
		}

		/** Holds the signal if it was not held, else releases it; true when it is now held. */
		private boolean toggle(String dimension, long pos) {
			final Set<Long> held = holds.computeIfAbsent(dimension, key -> new LinkedHashSet<>());
			final boolean now = held.add(pos) || !held.remove(pos);
			markDirty();
			return now;
		}

		private void clear(String dimension) {
			holds.remove(dimension);
			markDirty();
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			final NbtList list = new NbtList();
			for (final Map.Entry<String, Set<Long>> entry : holds.entrySet()) {
				if (entry.getValue().isEmpty()) {
					continue;
				}
				final NbtCompound compound = new NbtCompound();
				compound.putString("dim", entry.getKey());
				compound.putLongArray("held", entry.getValue().stream().mapToLong(Long::longValue).toArray());
				list.add(compound);
			}
			nbt.put("dimensions", list);
			return nbt;
		}
	}
}
