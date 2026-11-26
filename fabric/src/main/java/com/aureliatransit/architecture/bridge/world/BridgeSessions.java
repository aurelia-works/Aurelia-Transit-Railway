package com.aureliatransit.architecture.bridge.world;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player bridge work and the one server tick that drives it: running build/undo tasks and the last build's undo log.
 * Server thread only. A running task keeps going when its player leaves, as the station builder's jobs do. The undo log
 * is in memory, like the station builder's, so restarting the server loses undo.
 */
final class BridgeSessions {

	/** Server time all bridge tasks together may use in a tick: a fifth of the 50 ms tick. */
	static final long NANOS_PER_TICK = 10_000_000L;

	private record Undo(RegistryKey<World> dimension, List<BridgeBuildJob.Entry> entries) {
	}

	private static final Map<UUID, BridgeTask> JOBS = new LinkedHashMap<>();
	private static final Map<UUID, Undo> UNDO = new HashMap<>();

	private BridgeSessions() {
	}

	/** Starts a task and runs its first slice at once; false when the player already has one running. */
	static boolean start(UUID owner, BridgeTask task) {
		if (JOBS.containsKey(owner)) {
			return false;
		}
		JOBS.put(owner, task);
		if (task.step(System.nanoTime() + NANOS_PER_TICK)) {
			JOBS.remove(owner);
			task.finish();
		}
		return true;
	}

	static boolean hasJob(UUID owner) {
		return JOBS.containsKey(owner);
	}

	static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		final long deadline = System.nanoTime() + NANOS_PER_TICK;
		final Iterator<Map.Entry<UUID, BridgeTask>> jobs = JOBS.entrySet().iterator();
		while (jobs.hasNext()) {
			final BridgeTask task = jobs.next().getValue();
			if (task.step(deadline)) {
				jobs.remove();
				task.finish();
			} else if (System.nanoTime() >= deadline) {
				break;
			}
		}
	}

	/** The dimension of the player's last build, or null when there is nothing to undo. */
	static RegistryKey<World> undoDimension(UUID owner) {
		final Undo undo = UNDO.get(owner);
		return undo == null ? null : undo.dimension();
	}

	/** Starts undoing the player's last build; false when there is nothing to undo, it was in another dimension, or a task is already running. */
	static boolean undo(ServerWorld world, UUID owner) {
		final Undo undo = UNDO.get(owner);
		if (undo == null || undo.entries().isEmpty() || !undo.dimension().equals(world.getRegistryKey())) {
			return false;
		}
		if (JOBS.containsKey(owner)) {
			return false;
		}
		return start(owner, new BridgeUndoJob(world, owner, undo.entries()));
	}

	/** Keeps a finished build's changes for undo; a build that changed nothing leaves the last log alone. */
	static void rememberUndo(UUID owner, RegistryKey<World> dimension, List<BridgeBuildJob.Entry> entries) {
		if (!entries.isEmpty()) {
			UNDO.put(owner, new Undo(dimension, List.copyOf(entries)));
		}
	}

	static void clearUndo(UUID owner) {
		UNDO.remove(owner);
	}

	static void clear() {
		JOBS.clear();
		UNDO.clear();
	}
}
