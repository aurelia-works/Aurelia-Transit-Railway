package com.aureliatransit.architecture.station.world;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * How many station cells one build or undo may change in a server tick. The dev panel's build-pace slider and
 * {@code /ata_station pace} write it; it lives in the world's {@code atr_station_build} file, a new key old worlds do not have.
 */
public final class StationBuildSettings extends PersistentState {

	public static final int MIN = 1;
	public static final int MAX = 4096;
	/** About 12,800 cells a second: a large station builds in seconds and a fresh-terrain chunk still only loads in its ring. */
	public static final int DEFAULT = 256;
	/** The steps the panel slider offers; the command accepts any value in the range. */
	public static final int[] STEPS = {16, 32, 64, 128, 256, 512, 1024, 2048, 4096};

	private static final String ID = "atr_station_build";
	private static final String BLOCKS_PER_TICK = "blocks_per_tick";

	private int blocksPerTick = DEFAULT;

	public StationBuildSettings() {
	}

	public static int blocksPerTick(MinecraftServer server) {
		return state(server).blocksPerTick;
	}

	/** Clamps and stores {@code blocks}; the next tick's builds use it. */
	public static void set(MinecraftServer server, int blocks) {
		final StationBuildSettings state = state(server);
		state.blocksPerTick = clamp(blocks);
		state.markDirty();
	}

	public static int clamp(int blocks) {
		return Math.max(MIN, Math.min(MAX, blocks));
	}

	/** The step nearest {@code blocks}, for the slider's position. */
	public static int nearestStep(int blocks) {
		int nearest = STEPS[0];
		int distance = Integer.MAX_VALUE;
		for (final int step : STEPS) {
			final int candidate = Math.abs(step - blocks);
			if (candidate < distance) {
				distance = candidate;
				nearest = step;
			}
		}
		return nearest;
	}

	private static StationBuildSettings state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(StationBuildSettings::fromNbt, StationBuildSettings::new, ID);
	}

	private static StationBuildSettings fromNbt(NbtCompound nbt) {
		final StationBuildSettings state = new StationBuildSettings();
		if (nbt.contains(BLOCKS_PER_TICK)) {
			state.blocksPerTick = clamp(nbt.getInt(BLOCKS_PER_TICK));
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.putInt(BLOCKS_PER_TICK, blocksPerTick);
		return nbt;
	}
}
