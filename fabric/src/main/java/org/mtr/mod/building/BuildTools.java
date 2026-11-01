package org.mtr.mod.building;

import com.aureliatransit.architecture.station.world.BuildStationHook;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.data.RailType;
import org.mtr.mod.packet.PacketUpdateData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Marks, station creation, rail drawing and the paced tunnel job. Saved as {@code atr_build} on the overworld. Old worlds
 * have no such file and load with the defaults and an empty undo.
 */
public final class BuildTools {

	public static final Identifier SYNC = new Identifier("mtr", "atr_build");
	public static final Identifier SET = new Identifier("mtr", "atr_build_set");

	private static final String FILE = "atr_build";
	private static final int MAX_SLOPE = 8;
	private static final int[] AIM = new int[2];
	private static final Map<UUID, Mark> MARKS = new HashMap<>();
	@Nullable
	private static Job job;

	private BuildTools() {
	}

	public static void registerServer() {
		ServerTickEvents.END_SERVER_TICK.register(BuildTools::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final BuildSettings requested = readSettings(buf).clamped();
			server.execute(() -> applySettings(server, player, requested));
		});
	}

	public static boolean mayBuild(PlayerEntity player) {
		return player.isCreative() || player.hasPermissionLevel(2);
	}

	public static ActionResult markStation(PlayerEntity player, World world, net.minecraft.util.math.BlockPos pos, boolean sneak) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.build.creative");
			return ActionResult.FAIL;
		}
		final State state = state(serverWorld.getServer());
		if (!state.settings.stationEnabled()) {
			tell(serverPlayer, "message.mtr.build.station_off");
			return ActionResult.FAIL;
		}
		final Mark mark = mark(serverPlayer, serverWorld);
		if (!sneak || mark.stationA == null || mark.stationB == null) {
			if (mark.stationA == null || mark.stationB != null) {
				mark.stationA = pos.toImmutable();
				mark.stationB = null;
				tell(serverPlayer, "message.mtr.build.station_corner");
			} else {
				mark.stationB = pos.toImmutable();
				final int dx = BuildMath.side(mark.stationA.getX(), pos.getX());
				final int dz = BuildMath.side(mark.stationA.getZ(), pos.getZ());
				tell(serverPlayer, "message.mtr.build.station_ready", dx, dz);
			}
			send(serverPlayer);
			return ActionResult.SUCCESS;
		}
		final int minX = Math.min(mark.stationA.getX(), mark.stationB.getX());
		final int maxX = Math.max(mark.stationA.getX(), mark.stationB.getX());
		final int minZ = Math.min(mark.stationA.getZ(), mark.stationB.getZ());
		final int maxZ = Math.max(mark.stationA.getZ(), mark.stationB.getZ());
		final int side = state.settings.stationSide();
		if (BuildMath.side(minX, maxX) > side || BuildMath.side(minZ, maxZ) > side) {
			tell(serverPlayer, "message.mtr.build.station_side", side);
			return ActionResult.FAIL;
		}
		final int y = Math.min(mark.stationA.getY(), mark.stationB.getY());
		final String name = "Station " + minX + " " + minZ;
		BuildStationHook.create(serverWorld, minX, minZ, maxX, maxZ, y, name, state.settings.stationColour(), (id, existing) -> {
			if (id == 0) {
				tell(serverPlayer, "message.mtr.build.station_exists", existing);
			} else {
				tell(serverPlayer, "message.mtr.build.station_placed", name);
				mark.stationA = null;
				mark.stationB = null;
				send(serverPlayer);
			}
		});
		return ActionResult.SUCCESS;
	}

	public static ActionResult drawRail(PlayerEntity player, World world, net.minecraft.util.math.BlockPos pos, boolean sneak) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.build.creative");
			return ActionResult.FAIL;
		}
		final State state = state(serverWorld.getServer());
		if (!state.settings.railEnabled()) {
			tell(serverPlayer, "message.mtr.build.rail_off");
			return ActionResult.FAIL;
		}
		final org.mtr.mapping.holder.World mappedWorld = new org.mtr.mapping.holder.World(world);
		final org.mtr.mapping.holder.BlockPos mappedPos = new org.mtr.mapping.holder.BlockPos(pos.getX(), pos.getY(), pos.getZ());
		final org.mtr.mapping.holder.BlockState mappedState = mappedWorld.getBlockState(mappedPos);
		final Mark mark = mark(serverPlayer, serverWorld);
		final boolean trainNode = mappedState.getBlock().data instanceof BlockNode node && node.transportMode == TransportMode.TRAIN && !node.transportMode.continuousMovement;
		if (mark.rail == null || (sneak && trainNode)) {
			if (!trainNode) {
				tell(serverPlayer, "message.mtr.build.rail_node");
				return ActionResult.FAIL;
			}
			mark.rail = pos.toImmutable();
			mark.railYaw = BlockNode.getAngle(mappedState);
			tell(serverPlayer, "message.mtr.build.rail_start");
			send(serverPlayer);
			return ActionResult.SUCCESS;
		}
		final org.mtr.mapping.holder.BlockPos mappedStart = new org.mtr.mapping.holder.BlockPos(mark.rail.getX(), mark.rail.getY(), mark.rail.getZ());
		final org.mtr.mapping.holder.BlockState startState = mappedWorld.getBlockState(mappedStart);
		if (!(startState.getBlock().data instanceof BlockNode)) {
			mark.rail = null;
			tell(serverPlayer, "message.mtr.build.rail_node");
			send(serverPlayer);
			return ActionResult.FAIL;
		}
		final int reach = state.settings.railReach();
		final int horizontal = (int) Math.round(Math.sqrt(mark.rail.getSquaredDistance(pos.getX(), mark.rail.getY(), pos.getZ())));
		final int distance = Math.max(1, Math.min(reach, horizontal));
		final int yaw = BuildMath.snapYaw(player.getYaw(), state.settings.snapDegrees());
		BuildMath.aim(distance, yaw, AIM);
		final net.minecraft.util.math.BlockPos target = mark.rail.add(AIM[0], Math.max(-MAX_SLOPE, Math.min(MAX_SLOPE, pos.getY() - mark.rail.getY())), AIM[1]);
		if (target.equals(mark.rail) || Math.abs(target.getY() - mark.rail.getY()) > MAX_SLOPE) {
			tell(serverPlayer, "message.mtr.build.rail_reach", reach);
			return ActionResult.FAIL;
		}
		final org.mtr.mapping.holder.BlockPos mappedTarget = new org.mtr.mapping.holder.BlockPos(target.getX(), target.getY(), target.getZ());
		final org.mtr.mapping.holder.BlockState occupied = mappedWorld.getBlockState(mappedTarget);
		if (!occupied.isAir()) {
			tell(serverPlayer, "message.mtr.build.rail_blocked");
			return ActionResult.FAIL;
		}
		final org.mtr.mapping.holder.BlockState placed = BlockNode.getStateWithAngle(startState, yaw);
		mappedWorld.setBlockState(mappedTarget, placed);
		final var angles = Rail.getAngles(Init.blockPosToPosition(mappedStart), BlockNode.getAngle(startState), Init.blockPosToPosition(mappedTarget), yaw);
		final Rail rail = Rail.newRail(Init.blockPosToPosition(mappedStart), angles.left(), Init.blockPosToPosition(mappedTarget), angles.right(), RailType.IRON.railShape, 0, new ObjectArrayList<>(), RailType.IRON.speedLimit, RailType.IRON.speedLimit, false, false, RailType.IRON.canAccelerate, false, RailType.IRON.hasSignal, TransportMode.TRAIN);
		if (!rail.isValid()) {
			serverWorld.setBlockState(target, Blocks.AIR.getDefaultState());
			tell(serverPlayer, "message.mtr.build.rail_invalid");
			return ActionResult.FAIL;
		}
		mappedWorld.setBlockState(mappedStart, startState.with(new org.mtr.mapping.holder.Property<>(BlockNode.IS_CONNECTED.data), true));
		mappedWorld.setBlockState(mappedTarget, mappedWorld.getBlockState(mappedTarget).with(new org.mtr.mapping.holder.Property<>(BlockNode.IS_CONNECTED.data), true));
		PacketUpdateData.sendDirectlyToServerRail(org.mtr.mapping.holder.ServerWorld.cast(mappedWorld), rail);
		mark.rail = target.toImmutable();
		mark.railYaw = yaw;
		tell(serverPlayer, "message.mtr.build.rail_placed");
		send(serverPlayer);
		return ActionResult.SUCCESS;
	}

	public static ActionResult markTunnel(PlayerEntity player, World world, net.minecraft.util.math.BlockPos pos, boolean sneak) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.build.creative");
			return ActionResult.FAIL;
		}
		final State state = state(serverWorld.getServer());
		if (!state.settings.tunnelEnabled()) {
			tell(serverPlayer, "message.mtr.build.tunnel_off");
			return ActionResult.FAIL;
		}
		final Mark mark = mark(serverPlayer, serverWorld);
		if (!sneak || mark.tunnelA == null || mark.tunnelB == null) {
			if (mark.tunnelA == null || mark.tunnelB != null) {
				mark.tunnelA = pos.toImmutable();
				mark.tunnelB = null;
				tell(serverPlayer, "message.mtr.build.tunnel_corner");
			} else {
				mark.tunnelB = pos.toImmutable();
				final long count = BuildMath.volume(mark.tunnelA.getX(), mark.tunnelA.getY(), mark.tunnelA.getZ(), pos.getX(), pos.getY(), pos.getZ(), state.settings.tunnelMode());
				tell(serverPlayer, "message.mtr.build.tunnel_ready", count);
			}
			send(serverPlayer);
			return ActionResult.SUCCESS;
		}
		if (job != null) {
			tell(serverPlayer, "message.mtr.build.tunnel_busy");
			return ActionResult.FAIL;
		}
		final int x0 = mark.tunnelA.getX();
		final int y0 = mark.tunnelA.getY();
		final int z0 = mark.tunnelA.getZ();
		final int x1 = mark.tunnelB.getX();
		final int y1 = mark.tunnelB.getY();
		final int z1 = mark.tunnelB.getZ();
		if (!BuildMath.sidesFit(x0, y0, z0, x1, y1, z1)) {
			tell(serverPlayer, "message.mtr.build.tunnel_side", BuildMath.MAX_SIDE);
			return ActionResult.FAIL;
		}
		final long count = BuildMath.volume(x0, y0, z0, x1, y1, z1, state.settings.tunnelMode());
		if (!BuildMath.volumeFits(count, state.settings.tunnelMaxBlocks())) {
			tell(serverPlayer, "message.mtr.build.tunnel_cap", state.settings.tunnelMaxBlocks());
			return ActionResult.FAIL;
		}
		final int bottom = serverWorld.getBottomY();
		final int top = serverWorld.getTopY() - 1;
		if (Math.min(y0, y1) < bottom || Math.max(y0, y1) > top) {
			tell(serverPlayer, "message.mtr.build.tunnel_height");
			return ActionResult.FAIL;
		}
		state.undo.clear();
		state.undoDimension = worldKey(serverWorld);
		state.undoOwner = serverPlayer.getUuid();
		state.markDirty();
		job = new Job(serverWorld, serverPlayer.getUuid(), x0, y0, z0, x1, y1, z1, state.settings.tunnelMode(), (int) count, false);
		mark.tunnelA = null;
		mark.tunnelB = null;
		tell(serverPlayer, "message.mtr.build.tunnel_started", count);
		send(serverPlayer);
		return ActionResult.SUCCESS;
	}

	public static ActionResult undoTunnel(PlayerEntity player, World world) {
		if (world.isClient()) {
			return player.isSneaking() ? ActionResult.SUCCESS : ActionResult.PASS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!player.isSneaking()) {
			return ActionResult.PASS;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.build.creative");
			return ActionResult.FAIL;
		}
		final State state = state(serverWorld.getServer());
		if (job != null) {
			if (!job.owner.equals(serverPlayer.getUuid())) {
				tell(serverPlayer, "message.mtr.build.tunnel_busy");
				return ActionResult.FAIL;
			}
			job = null;
		}
		if (state.undo.isEmpty() || !serverPlayer.getUuid().equals(state.undoOwner) || !worldKey(serverWorld).equals(state.undoDimension)) {
			tell(serverPlayer, "message.mtr.build.tunnel_nothing");
			return ActionResult.FAIL;
		}
		job = new Job(serverWorld, serverPlayer.getUuid(), 0, 0, 0, 0, 0, 0, BuildMath.Mode.FULL, state.undo.size(), true);
		tell(serverPlayer, "message.mtr.build.tunnel_undo", state.undo.size());
		return ActionResult.SUCCESS;
	}

	public static void writeSettings(PacketByteBuf buf, BuildSettings settings) {
		buf.writeBoolean(settings.stationEnabled());
		buf.writeVarInt(settings.stationSide());
		buf.writeInt(settings.stationColour());
		buf.writeBoolean(settings.railEnabled());
		buf.writeVarInt(settings.snapDegrees());
		buf.writeVarInt(settings.railReach());
		buf.writeBoolean(settings.tunnelEnabled());
		buf.writeVarInt(settings.tunnelMode().ordinal());
		buf.writeVarInt(settings.tunnelMaxBlocks());
		buf.writeVarInt(settings.blocksPerTick());
	}

	public static BuildSettings readSettings(PacketByteBuf buf) {
		return new BuildSettings(buf.readBoolean(), buf.readVarInt(), buf.readInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), BuildMath.Mode.byOrdinal(buf.readVarInt()), buf.readVarInt(), buf.readVarInt());
	}

	private static void tick(MinecraftServer server) {
		if (job == null) {
			return;
		}
		if (server.getWorld(job.world.getRegistryKey()) != job.world) {
			job = null;
			return;
		}
		final State state = state(server);
		final int pace = state.settings.blocksPerTick();
		final ServerWorld world = job.world;
		final int[] at = new int[3];
		for (int step = 0; step < pace && job.cursor < job.total; step++) {
			if (job.undo) {
				final int index = state.undo.size() - 1;
				if (index < 0) {
					break;
				}
				final NbtCompound entry = state.undo.remove(index);
				final net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
				world.setBlockState(pos, BlockState.CODEC.parse(NbtOps.INSTANCE, entry.getCompound("s")).result().orElse(Blocks.AIR.getDefaultState()));
			} else {
				BuildMath.at(job.cursor, job.x0, job.y0, job.z0, job.x1, job.y1, job.z1, job.mode, at);
				final net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(at[0], at[1], at[2]);
				final BlockState current = world.getBlockState(pos);
				if (!current.isOf(Blocks.BEDROCK) && !current.isOf(Blocks.BARRIER)) {
					final BlockState next = nextState(current, at[0], at[1], at[2], job);
					if (next != current && state.undo.size() < state.settings.tunnelMaxBlocks()) {
						final var encoded = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, current).result();
						if (encoded.isPresent()) {
							final NbtCompound entry = new NbtCompound();
							entry.putInt("x", at[0]);
							entry.putInt("y", at[1]);
							entry.putInt("z", at[2]);
							entry.put("s", encoded.get());
							state.undo.add(entry);
							world.setBlockState(pos, next);
						}
					}
				}
			}
			job.cursor++;
		}
		state.markDirty();
		if (job.cursor >= job.total) {
			final ServerPlayerEntity owner = server.getPlayerManager().getPlayer(job.owner);
			if (owner != null) {
				tell(owner, job.undo ? "message.mtr.build.tunnel_restored" : "message.mtr.build.tunnel_done");
			}
			if (job.undo) {
				state.undoOwner = null;
				state.markDirty();
			}
			job = null;
		}
	}

	private static BlockState nextState(BlockState current, int x, int y, int z, Job job) {
		if (job.mode == BuildMath.Mode.SHELL && BuildMath.shell(x, y, z, job.x0, job.y0, job.z0, job.x1, job.y1, job.z1)) {
			return Blocks.STONE_BRICKS.getDefaultState();
		}
		return Blocks.AIR.getDefaultState();
	}

	private static void applySettings(MinecraftServer server, ServerPlayerEntity player, BuildSettings requested) {
		if (!player.hasPermissionLevel(2)) {
			return;
		}
		final State state = state(server);
		state.settings = requested;
		state.markDirty();
		for (final ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
			send(online);
		}
	}

	private static void tell(PlayerEntity player, String key, Object... args) {
		player.sendMessage(Text.translatable(key, args), true);
	}

	private static Mark mark(ServerPlayerEntity player, ServerWorld world) {
		final String dimension = worldKey(world);
		Mark mark = MARKS.get(player.getUuid());
		if (mark == null || !mark.dimension.equals(dimension)) {
			mark = new Mark(dimension);
			MARKS.put(player.getUuid(), mark);
		}
		return mark;
	}

	private static void send(ServerPlayerEntity player) {
		final State state = state(player.getServer());
		final Mark mark = MARKS.get(player.getUuid());
		final PacketByteBuf buf = PacketByteBufs.create();
		writeSettings(buf, state.settings);
		writePos(buf, mark == null ? null : mark.stationA);
		writePos(buf, mark == null ? null : mark.stationB);
		writePos(buf, mark == null ? null : mark.rail);
		if (mark != null && mark.rail != null) {
			buf.writeFloat(mark.railYaw);
		}
		writePos(buf, mark == null ? null : mark.tunnelA);
		writePos(buf, mark == null ? null : mark.tunnelB);
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	private static void writePos(PacketByteBuf buf, @Nullable net.minecraft.util.math.BlockPos pos) {
		buf.writeBoolean(pos != null);
		if (pos != null) {
			buf.writeBlockPos(pos);
		}
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(State::fromNbt, State::new, FILE);
	}

	private static String worldKey(ServerWorld world) {
		final Identifier id = world.getRegistryKey().getValue();
		return id.getNamespace() + ":" + id.getPath();
	}

	private static final class Mark {
		private final String dimension;
		@Nullable
		private net.minecraft.util.math.BlockPos stationA;
		@Nullable
		private net.minecraft.util.math.BlockPos stationB;
		@Nullable
		private net.minecraft.util.math.BlockPos rail;
		private float railYaw;
		@Nullable
		private net.minecraft.util.math.BlockPos tunnelA;
		@Nullable
		private net.minecraft.util.math.BlockPos tunnelB;

		private Mark(String dimension) {
			this.dimension = dimension;
		}
	}

	private static final class Job {
		private final ServerWorld world;
		private final UUID owner;
		private final int x0;
		private final int y0;
		private final int z0;
		private final int x1;
		private final int y1;
		private final int z1;
		private final BuildMath.Mode mode;
		private final int total;
		private final boolean undo;
		private int cursor;

		private Job(ServerWorld world, UUID owner, int x0, int y0, int z0, int x1, int y1, int z1, BuildMath.Mode mode, int total, boolean undo) {
			this.world = world;
			this.owner = owner;
			this.x0 = x0;
			this.y0 = y0;
			this.z0 = z0;
			this.x1 = x1;
			this.y1 = y1;
			this.z1 = z1;
			this.mode = mode;
			this.total = total;
			this.undo = undo;
		}
	}

	private static final class State extends PersistentState {
		private BuildSettings settings = BuildSettings.DEFAULT;
		private final List<NbtCompound> undo = new ArrayList<>();
		private String undoDimension = "";
		@Nullable
		private UUID undoOwner;

		private static State fromNbt(NbtCompound nbt) {
			final State state = new State();
			final BuildSettings fallback = BuildSettings.DEFAULT;
			state.settings = new BuildSettings(
					!nbt.contains("station") || nbt.getBoolean("station"),
					nbt.contains("side") ? nbt.getInt("side") : fallback.stationSide(),
					nbt.contains("colour") ? nbt.getInt("colour") : fallback.stationColour(),
					!nbt.contains("rail") || nbt.getBoolean("rail"),
					nbt.contains("snap") ? nbt.getInt("snap") : fallback.snapDegrees(),
					nbt.contains("reach") ? nbt.getInt("reach") : fallback.railReach(),
					!nbt.contains("tunnel") || nbt.getBoolean("tunnel"),
					BuildMath.Mode.byOrdinal(nbt.getInt("mode")),
					nbt.contains("max") ? nbt.getInt("max") : fallback.tunnelMaxBlocks(),
					nbt.contains("pace") ? nbt.getInt("pace") : fallback.blocksPerTick()
			).clamped();
			state.undoDimension = nbt.getString("undo_dim");
			if (nbt.containsUuid("undo_owner")) {
				state.undoOwner = nbt.getUuid("undo_owner");
			}
			final NbtList list = nbt.getList("undo", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size() && state.undo.size() < state.settings.tunnelMaxBlocks(); i++) {
				state.undo.add(list.getCompound(i));
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			nbt.putBoolean("station", settings.stationEnabled());
			nbt.putInt("side", settings.stationSide());
			nbt.putInt("colour", settings.stationColour());
			nbt.putBoolean("rail", settings.railEnabled());
			nbt.putInt("snap", settings.snapDegrees());
			nbt.putInt("reach", settings.railReach());
			nbt.putBoolean("tunnel", settings.tunnelEnabled());
			nbt.putInt("mode", settings.tunnelMode().ordinal());
			nbt.putInt("max", settings.tunnelMaxBlocks());
			nbt.putInt("pace", settings.blocksPerTick());
			nbt.putString("undo_dim", undoDimension);
			if (undoOwner != null) {
				nbt.putUuid("undo_owner", undoOwner);
			}
			final NbtList list = new NbtList();
			for (final NbtCompound entry : undo) {
				list.add(entry);
			}
			nbt.put("undo", list);
			return nbt;
		}
	}
}
