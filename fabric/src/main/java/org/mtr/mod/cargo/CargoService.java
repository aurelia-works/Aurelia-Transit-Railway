package org.mtr.mod.cargo;

import org.mtr.core.Main;
import org.mtr.core.simulation.Simulator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.MinecraftServerHelper;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockTrainSensorBase;
import org.mtr.mod.packet.PacketSyncTrainCargo;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of freight. Every eight ticks the simulation thread publishes where stopped trains are. Cargo blocks
 * then move items on the server thread, which is the only place inventories may change. Cargo is saved next to the
 * simulation files and pushed to clients on join and after a transfer.
 */
public final class CargoService {

	/** Wait this long after the server starts before forgetting cargo for trains that are gone. */
	static final long PRUNE_AFTER_MILLIS = 180_000;
	private static final long SNAPSHOT_STALE_MILLIS = 3_000;
	private static final int SYNC_INTERVAL_TICKS = 20;
	private static final int REFRESH_INTERVAL_TICKS = 8;

	private static final TrainCargoStore STORE = new TrainCargoStore();
	private static final ConcurrentHashMap<String, TrainSnapshot> SNAPSHOTS = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, String> PLAYER_DIMENSION = new ConcurrentHashMap<>();

	private static Main main;
	private static Path file;
	private static ObjectImmutableList<Simulator> simulators;
	private static long startedMillis;
	private static int worldTicks;
	private static boolean loggedMissingSimulators;

	private CargoService() {
	}

	public static void attach(Main simulation, Path mtrDirectory) {
		main = simulation;
		file = mtrDirectory.resolve("atr-cargo.json");
		simulators = null;
		SNAPSHOTS.clear();
		PLAYER_DIMENSION.clear();
		startedMillis = System.currentTimeMillis();
		worldTicks = 0;
		STORE.load(file);
	}

	public static void stop() {
		STORE.save(file);
		main = null;
		simulators = null;
		SNAPSHOTS.clear();
		PLAYER_DIMENSION.clear();
	}

	public static void onAutosave() {
		if (STORE.isFileDirty()) {
			STORE.save(file);
		}
	}

	public static void onPlayerJoin(ServerPlayerEntity player) {
		final ServerWorld world = player.getServerWorld();
		if (world == null) {
			return;
		}
		final String dimension = Init.getWorldId(new World(world.data));
		PLAYER_DIMENSION.put(player.getUuid(), dimension);
		Init.REGISTRY.sendPacketToClient(player, new PacketSyncTrainCargo(true, STORE.dimensionJson(dimension)));
	}

	public static void onWorldEnd(net.minecraft.server.world.ServerWorld world, String dimension) {
		worldTicks++;
		if (main == null) {
			return;
		}
		if (worldTicks % REFRESH_INTERVAL_TICKS == 0) {
			refresh(dimension);
		}
		if (worldTicks % SYNC_INTERVAL_TICKS == 0) {
			syncPlayers(world, dimension);
			final TrainSnapshot snapshot = SNAPSHOTS.get(dimension);
			if (snapshot != null && snapshot.reliable() && System.currentTimeMillis() - startedMillis > PRUNE_AFTER_MILLIS) {
				STORE.prune(dimension, snapshot.allIds());
			}
			if (STORE.isFileDirty()) {
				STORE.save(file);
			}
		}
	}

	public static void transfer(BlockTrainSensorBase.BlockEntityBase entity, boolean load) {
		final net.minecraft.world.World minecraftWorld = entity.getWorld();
		if (!(minecraftWorld instanceof net.minecraft.server.world.ServerWorld)) {
			return;
		}
		final net.minecraft.server.world.ServerWorld world = (net.minecraft.server.world.ServerWorld) minecraftWorld;
		final net.minecraft.util.math.BlockPos pos = entity.getPos();
		final String dimension = Init.getWorldId(new World(world));
		final TrainSnapshot snapshot = SNAPSHOTS.get(dimension);
		if (snapshot == null || System.currentTimeMillis() - snapshot.millis() > SNAPSHOT_STALE_MILLIS) {
			return;
		}
		final StoppedTrain train = TrainProximity.closest(snapshot.stopped(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, TrainProximity.REACH);
		if (train == null) {
			return;
		}
		if (!BlockTrainSensorBase.matchesFilter(new World(world), new BlockPos(pos), train.routeId(), 0)) {
			return;
		}
		final TrainCargo hold = STORE.getOrCreate(dimension, train.id(), TrainCargo.slotsForCars(train.cars()));
		final int moved = CargoMachines.transfer(world, pos, load, hold, CargoMachines.ITEMS_PER_CYCLE);
		if (moved <= 0) {
			return;
		}
		if (hold.isEmpty()) {
			STORE.remove(train.id());
		}
		STORE.markDirty(dimension, train.id());
	}

	public static int itemCount(long vehicleId) {
		return STORE.itemCount(vehicleId);
	}

	public static TrainCargoStore store() {
		return STORE;
	}

	private static void refresh(String dimension) {
		final Simulator simulator = findSimulator(dimension);
		if (simulator == null) {
			return;
		}
		simulator.run(() -> {
			try {
				SNAPSHOTS.put(dimension, CargoVehicles.capture(simulator));
			} catch (Exception e) {
				Init.LOGGER.error("Cargo snapshot failed for {}", dimension, e);
				SNAPSHOTS.put(dimension, TrainSnapshot.unreliable());
			}
		});
	}

	private static void syncPlayers(net.minecraft.server.world.ServerWorld world, String dimension) {
		final List<Long> dirty = STORE.drainDirty(dimension);
		final String dirtyJson = dirty.isEmpty() ? null : STORE.vehiclesJson(dirty, true);
		MinecraftServerHelper.iteratePlayers(new ServerWorld(world), player -> {
			final String previous = PLAYER_DIMENSION.put(player.getUuid(), dimension);
			if (!dimension.equals(previous)) {
				Init.REGISTRY.sendPacketToClient(player, new PacketSyncTrainCargo(true, STORE.dimensionJson(dimension)));
			} else if (dirtyJson != null) {
				Init.REGISTRY.sendPacketToClient(player, new PacketSyncTrainCargo(false, dirtyJson));
			}
		});
	}

	private static Simulator findSimulator(String dimension) {
		final ObjectImmutableList<Simulator> loaded = simulators();
		if (loaded == null) {
			return null;
		}
		for (final Simulator simulator : loaded) {
			if (dimension.equals(simulator.dimension)) {
				return simulator;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private static ObjectImmutableList<Simulator> simulators() {
		if (simulators == null && main != null) {
			try {
				final Field field = Main.class.getDeclaredField("simulators");
				field.setAccessible(true);
				simulators = (ObjectImmutableList<Simulator>) field.get(main);
			} catch (Exception e) {
				if (!loggedMissingSimulators) {
					loggedMissingSimulators = true;
					Init.LOGGER.error("Cargo cannot reach the simulators", e);
				}
			}
		}
		return simulators;
	}
}
