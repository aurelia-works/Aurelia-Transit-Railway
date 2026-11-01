package org.mtr.mod.zip;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cables, seats and riders for one server. Saved as {@code atr_zip} on the overworld. Old worlds have no such file and
 * load with the defaults and no cables.
 */
public final class ZipLines {

	public static final Identifier SYNC = new Identifier("mtr", "atr_zip");
	public static final Identifier SET = new Identifier("mtr", "atr_zip_set");
	public static final int MIN_SPAN = 4;
	public static final int MAX_SPAN = 96;
	public static final int MAX_LINES = 64;
	/** How far below the cable a handle rider hangs, in blocks. */
	public static final double HANG = 1.45;
	public static final double SEAT_HANG = 1.75;

	private static final String FILE = "atr_zip";
	private static final double[] POINT = new double[3];
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static int ticks;

	private ZipLines() {
	}

	public static void registerServer() {
		ServerTickEvents.END_SERVER_TICK.register(ZipLines::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
		ServerPlayNetworking.registerGlobalReceiver(SET, (server, player, handler, buf, response) -> {
			final ZipSettings requested = readSettings(buf).clamped();
			server.execute(() -> applySettings(server, player, requested));
		});
	}

	public static boolean mayBuild(PlayerEntity player) {
		return player.isCreative() || player.hasPermissionLevel(2);
	}

	public static void tell(PlayerEntity player, String key) {
		player.sendMessage(Text.translatable(key), true);
	}

	public static ActionResult link(PlayerEntity player, World world, BlockPos pos) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.zip.creative");
			return ActionResult.FAIL;
		}
		final MinecraftServer server = serverWorld.getServer();
		final State state = state(server);
		if (cableAt(state, worldKey(serverWorld), pos) != null) {
			tell(serverPlayer, "message.mtr.zip.already");
			return ActionResult.FAIL;
		}
		final String dimension = worldKey(serverWorld);
		final Pending pending = PENDING.get(serverPlayer.getUuid());
		if (pending == null || !pending.dimension.equals(dimension) || pending.pos.equals(pos)) {
			PENDING.put(serverPlayer.getUuid(), new Pending(dimension, pos.toImmutable()));
			tell(serverPlayer, "message.mtr.zip.marked");
			return ActionResult.SUCCESS;
		}
		final double span = Math.sqrt(pending.pos.getSquaredDistance(pos));
		if (span < MIN_SPAN || span > MAX_SPAN) {
			tell(serverPlayer, "message.mtr.zip.span");
			return ActionResult.FAIL;
		}
		if (state.cables.size() >= MAX_LINES) {
			tell(serverPlayer, "message.mtr.zip.full");
			return ActionResult.FAIL;
		}
		if (cableAt(state, dimension, pending.pos) != null) {
			PENDING.remove(serverPlayer.getUuid());
			tell(serverPlayer, "message.mtr.zip.already");
			return ActionResult.FAIL;
		}
		final Cable cable = new Cable(state.nextId++, dimension, pending.pos.toImmutable(), pos.toImmutable());
		state.cables.add(cable);
		state.markDirty();
		PENDING.remove(serverPlayer.getUuid());
		tell(serverPlayer, "message.mtr.zip.linked");
		sync(server);
		return ActionResult.SUCCESS;
	}

	public static ActionResult ride(PlayerEntity player, World world, BlockPos pos) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.zip.creative");
			return ActionResult.FAIL;
		}
		final State state = state(serverWorld.getServer());
		if (!state.settings.enabled()) {
			tell(serverPlayer, "message.mtr.zip.disabled");
			return ActionResult.FAIL;
		}
		final Cable cable = cableAt(state, worldKey(serverWorld), pos);
		if (cable == null) {
			tell(serverPlayer, "message.mtr.zip.none");
			return ActionResult.FAIL;
		}
		if (cable.seatMode) {
			tell(serverPlayer, "message.mtr.zip.use_seat");
			return ActionResult.FAIL;
		}
		if (cable.rider != null || riderAnywhere(state, serverPlayer.getUuid())) {
			tell(serverPlayer, "message.mtr.zip.busy");
			return ActionResult.FAIL;
		}
		final boolean fromA = cable.a.equals(pos);
		final double startY = fromA ? cable.a.getY() : cable.b.getY();
		final double endY = fromA ? cable.b.getY() : cable.a.getY();
		if (!ZipTravel.mayDrop(startY, endY)) {
			tell(serverPlayer, "message.mtr.zip.downhill");
			return ActionResult.FAIL;
		}
		cable.rider = serverPlayer.getUuid();
		cable.riderT = fromA ? 0 : 1;
		cable.riderSpeed = 0;
		cable.riderDir = fromA ? 1 : -1;
		cable.seatRide = false;
		state.markDirty();
		return ActionResult.SUCCESS;
	}

	public static ActionResult placeSeat(PlayerEntity player, World world, BlockPos pos, Hand hand) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.zip.creative");
			return ActionResult.FAIL;
		}
		final State state = state(serverWorld.getServer());
		final Cable cable = cableAt(state, worldKey(serverWorld), pos);
		if (cable == null) {
			tell(serverPlayer, "message.mtr.zip.none");
			return ActionResult.FAIL;
		}
		if (cable.rider != null) {
			tell(serverPlayer, "message.mtr.zip.busy");
			return ActionResult.FAIL;
		}
		cable.seatMode = true;
		cable.seatT = cable.a.equals(pos) ? 0 : 1;
		cable.riderDir = 0;
		state.markDirty();
		if (!serverPlayer.isCreative()) {
			serverPlayer.getStackInHand(hand).decrement(1);
		}
		tell(serverPlayer, "message.mtr.zip.seat");
		sync(serverWorld.getServer());
		return ActionResult.SUCCESS;
	}

	public static ActionResult boardSeat(PlayerEntity player, World world, BlockPos pos) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
			return ActionResult.FAIL;
		}
		if (!mayBuild(serverPlayer)) {
			tell(serverPlayer, "message.mtr.zip.creative");
			return ActionResult.FAIL;
		}
		final State state = state(serverWorld.getServer());
		if (!state.settings.enabled()) {
			tell(serverPlayer, "message.mtr.zip.disabled");
			return ActionResult.FAIL;
		}
		final Cable cable = cableAt(state, worldKey(serverWorld), pos);
		if (cable == null || !cable.seatMode) {
			return ActionResult.PASS;
		}
		if (cable.rider != null || riderAnywhere(state, serverPlayer.getUuid())) {
			tell(serverPlayer, "message.mtr.zip.busy");
			return ActionResult.FAIL;
		}
		final boolean atA = cable.a.equals(pos);
		final boolean waitingHere = atA ? cable.seatT < 0.02 : cable.seatT > 0.98;
		if (!waitingHere) {
			tell(serverPlayer, "message.mtr.zip.seat_away");
			return ActionResult.FAIL;
		}
		cable.rider = serverPlayer.getUuid();
		cable.seatRide = true;
		cable.riderT = cable.seatT;
		cable.riderSpeed = 0;
		cable.riderDir = atA ? 1 : -1;
		state.markDirty();
		return ActionResult.SUCCESS;
	}

	public static void removePole(World world, BlockPos pos) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return;
		}
		final MinecraftServer server = serverWorld.getServer();
		final State state = state(server);
		final String dimension = worldKey(serverWorld);
		boolean changed = false;
		final Iterator<Cable> iterator = state.cables.iterator();
		while (iterator.hasNext()) {
			final Cable cable = iterator.next();
			if (cable.dimension.equals(dimension) && (cable.a.equals(pos) || cable.b.equals(pos))) {
				release(server, cable, true);
				iterator.remove();
				changed = true;
			}
		}
		if (changed) {
			state.markDirty();
			sync(server);
		}
	}

	public static void writeSettings(PacketByteBuf buf, ZipSettings settings) {
		buf.writeBoolean(settings.enabled());
		buf.writeDouble(settings.speed());
		buf.writeDouble(settings.brake());
		buf.writeDouble(settings.sag());
	}

	public static ZipSettings readSettings(PacketByteBuf buf) {
		return new ZipSettings(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble());
	}

	private static void applySettings(MinecraftServer server, ServerPlayerEntity player, ZipSettings requested) {
		if (!player.hasPermissionLevel(2)) {
			return;
		}
		final State state = state(server);
		state.settings = requested;
		state.markDirty();
		if (!requested.enabled()) {
			for (final Cable cable : state.cables) {
				release(server, cable, true);
			}
		}
		sync(server);
	}

	private static void tick(MinecraftServer server) {
		final State state = state(server);
		final boolean enabled = state.settings.enabled();
		boolean moving = false;
		for (final Cable cable : state.cables) {
			if (cable.rider == null) {
				continue;
			}
			final ServerPlayerEntity player = server.getPlayerManager().getPlayer(cable.rider);
			if (!enabled || player == null || player.isRemoved() || player.isDisconnected()) {
				release(server, cable, false);
				state.markDirty();
				continue;
			}
			if (player.isSneaking()) {
				release(server, cable, false);
				state.markDirty();
				continue;
			}
			final ServerWorld world = world(server, cable.dimension);
			if (world == null || missingPole(world, cable)) {
				release(server, cable, true);
				state.markDirty();
				continue;
			}
			if (cable.seatRide && cable.riderDir == 0) {
				hold(player, cable, state.settings.sag(), true);
				continue;
			}
			final ZipTravel.Step step = cable.seatRide
					? ZipTravel.seat(cable.riderT, cable.riderSpeed, cable.riderDir, ZipTravel.TICK_SECONDS, cable.a.getX() + 0.5, cable.a.getY() + 1, cable.a.getZ() + 0.5, cable.b.getX() + 0.5, cable.b.getY() + 1, cable.b.getZ() + 0.5, state.settings.sag(), state.settings.speed(), state.settings.brake())
					: ZipTravel.gravity(cable.riderT, cable.riderSpeed, cable.riderDir, ZipTravel.TICK_SECONDS, cable.a.getX() + 0.5, cable.a.getY() + 1, cable.a.getZ() + 0.5, cable.b.getX() + 0.5, cable.b.getY() + 1, cable.b.getZ() + 0.5, state.settings.sag(), state.settings.speed(), state.settings.brake());
			cable.riderT = step.t();
			cable.riderSpeed = step.speed();
			if (cable.seatRide) {
				cable.seatT = step.t();
			}
			if (step.release() || (step.arrived() && !cable.seatRide)) {
				dropAtEnd(player, cable);
				cable.rider = null;
				cable.riderSpeed = 0;
				state.markDirty();
			} else if (step.arrived() && cable.seatRide) {
				cable.riderDir = 0;
				cable.riderSpeed = 0;
				hold(player, cable, state.settings.sag(), true);
				state.markDirty();
				sync(server);
			} else {
				hold(player, cable, state.settings.sag(), cable.seatRide);
				moving = true;
			}
		}
		if (moving && ++ticks % 2 == 0) {
			sync(server);
		}
	}

	private static boolean missingPole(ServerWorld world, Cable cable) {
		return !world.getBlockState(cable.a).isOf(ZipContent.POLE) || !world.getBlockState(cable.b).isOf(ZipContent.POLE);
	}

	private static void hold(ServerPlayerEntity player, Cable cable, double sag, boolean seat) {
		ZipCurve.point(cable.riderT, cable.a.getX() + 0.5, cable.a.getY() + 1, cable.a.getZ() + 0.5, cable.b.getX() + 0.5, cable.b.getY() + 1, cable.b.getZ() + 0.5, sag, POINT);
		final double hang = seat ? SEAT_HANG : HANG;
		final int dir = cable.riderDir == 0 ? 1 : cable.riderDir;
		final double dx = (cable.b.getX() - cable.a.getX()) * dir;
		final double dz = (cable.b.getZ() - cable.a.getZ()) * dir;
		final float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		player.networkHandler.requestTeleport(POINT[0], POINT[1] - hang, POINT[2], yaw, player.getPitch());
		player.setVelocity(0, 0, 0);
		player.fallDistance = 0;
	}

	private static void dropAtEnd(ServerPlayerEntity player, Cable cable) {
		final BlockPos end = cable.riderT < 0.5 ? cable.a : cable.b;
		final BlockPos start = end == cable.a ? cable.b : cable.a;
		final double dx = end.getX() - start.getX();
		final double dz = end.getZ() - start.getZ();
		final double h = Math.hypot(dx, dz);
		final double ox = h < 1.0e-4 ? 0.9 : -dz / h * 0.9;
		final double oz = h < 1.0e-4 ? 0 : dx / h * 0.9;
		player.networkHandler.requestTeleport(end.getX() + 0.5 + ox, end.getY(), end.getZ() + 0.5 + oz, player.getYaw(), player.getPitch());
		player.setVelocity(0, 0, 0);
		player.fallDistance = 0;
	}

	private static void release(MinecraftServer server, Cable cable, boolean beside) {
		if (cable.rider == null) {
			return;
		}
		final ServerPlayerEntity player = server.getPlayerManager().getPlayer(cable.rider);
		cable.rider = null;
		cable.riderSpeed = 0;
		cable.riderDir = 0;
		if (player != null && beside) {
			dropAtEnd(player, cable);
		}
	}

	private static void sync(MinecraftServer server) {
		final State state = state(server);
		for (final ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			send(player, state);
		}
	}

	private static void send(ServerPlayerEntity player) {
		send(player, state(player.getServer()));
	}

	private static void send(ServerPlayerEntity player, State state) {
		final PacketByteBuf buf = PacketByteBufs.create();
		writeSettings(buf, state.settings);
		final String dimension = worldKey(player.getServerWorld());
		int shown = 0;
		for (final Cable cable : state.cables) {
			if (cable.dimension.equals(dimension)) {
				shown++;
			}
		}
		buf.writeVarInt(shown);
		for (final Cable cable : state.cables) {
			if (!cable.dimension.equals(dimension)) {
				continue;
			}
			buf.writeLong(cable.id);
			buf.writeBlockPos(cable.a);
			buf.writeBlockPos(cable.b);
			buf.writeBoolean(cable.seatMode);
			buf.writeFloat((float) cable.seatT);
		}
		ServerPlayNetworking.send(player, SYNC, buf);
	}

	private static boolean riderAnywhere(State state, UUID rider) {
		for (final Cable cable : state.cables) {
			if (rider.equals(cable.rider)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static Cable cableAt(State state, String dimension, BlockPos pos) {
		for (final Cable cable : state.cables) {
			if (cable.dimension.equals(dimension) && (cable.a.equals(pos) || cable.b.equals(pos))) {
				return cable;
			}
		}
		return null;
	}

	private static String worldKey(ServerWorld world) {
		final Identifier id = world.getRegistryKey().getValue();
		return id.getNamespace() + ":" + id.getPath();
	}

	@Nullable
	private static ServerWorld world(MinecraftServer server, String key) {
		final Identifier id = Identifier.tryParse(key);
		if (id == null) {
			return null;
		}
		for (final ServerWorld world : server.getWorlds()) {
			if (world.getRegistryKey().getValue().equals(id)) {
				return world;
			}
		}
		return null;
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(State::fromNbt, State::new, FILE);
	}

	private static final class Pending {
		private final String dimension;
		private final BlockPos pos;

		private Pending(String dimension, BlockPos pos) {
			this.dimension = dimension;
			this.pos = pos;
		}
	}

	private static final class Cable {
		private final long id;
		private final String dimension;
		private final BlockPos a;
		private final BlockPos b;
		private boolean seatMode;
		private double seatT;
		@Nullable
		private UUID rider;
		private double riderT;
		private double riderSpeed;
		private int riderDir;
		private boolean seatRide;

		private Cable(long id, String dimension, BlockPos a, BlockPos b) {
			this.id = id;
			this.dimension = dimension;
			this.a = a;
			this.b = b;
		}
	}

	private static final class State extends PersistentState {
		private ZipSettings settings = ZipSettings.DEFAULT;
		private final List<Cable> cables = new ArrayList<>();
		private long nextId = 1;

		private static State fromNbt(NbtCompound nbt) {
			final State state = new State();
			final ZipSettings fallback = ZipSettings.DEFAULT;
			state.settings = new ZipSettings(
					!nbt.contains("enabled") || nbt.getBoolean("enabled"),
					nbt.contains("speed") ? nbt.getDouble("speed") : fallback.speed(),
					nbt.contains("brake") ? nbt.getDouble("brake") : fallback.brake(),
					nbt.contains("sag") ? nbt.getDouble("sag") : fallback.sag()
			).clamped();
			state.nextId = nbt.getLong("next");
			if (state.nextId < 1) {
				state.nextId = 1;
			}
			final NbtList list = nbt.getList("cables", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size() && state.cables.size() < MAX_LINES; i++) {
				final NbtCompound entry = list.getCompound(i);
				final Cable cable = new Cable(entry.getLong("id"), entry.getString("dim"),
						new BlockPos(entry.getInt("ax"), entry.getInt("ay"), entry.getInt("az")),
						new BlockPos(entry.getInt("bx"), entry.getInt("by"), entry.getInt("bz")));
				cable.seatMode = entry.getBoolean("seat");
				cable.seatT = entry.getDouble("seat_t");
				state.cables.add(cable);
				if (cable.id >= state.nextId) {
					state.nextId = cable.id + 1;
				}
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			nbt.putBoolean("enabled", settings.enabled());
			nbt.putDouble("speed", settings.speed());
			nbt.putDouble("brake", settings.brake());
			nbt.putDouble("sag", settings.sag());
			nbt.putLong("next", nextId);
			final NbtList list = new NbtList();
			for (final Cable cable : cables) {
				final NbtCompound entry = new NbtCompound();
				entry.putLong("id", cable.id);
				entry.putString("dim", cable.dimension);
				entry.putInt("ax", cable.a.getX());
				entry.putInt("ay", cable.a.getY());
				entry.putInt("az", cable.a.getZ());
				entry.putInt("bx", cable.b.getX());
				entry.putInt("by", cable.b.getY());
				entry.putInt("bz", cable.b.getZ());
				entry.putBoolean("seat", cable.seatMode);
				entry.putDouble("seat_t", cable.seatT);
				list.add(entry);
			}
			nbt.put("cables", list);
			return nbt;
		}
	}
}
