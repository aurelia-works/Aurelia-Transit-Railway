package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.LocalDir;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.StationOverride;
import com.aureliatransit.architecture.station.StationPlan;
import com.aureliatransit.architecture.station.StationPlanner;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The player's per-cell edits of built stations, saved in the world's own data file ({@code ata_station_overrides.dat}) under
 * keys no older version reads, and applied after every planning rule when a station is built or rebuilt. A station is keyed by
 * its frame's origin and facing, so a rebuild at the same place finds its edits and a station elsewhere never picks them up.
 */
public final class StationOverrides {

	/** A new file, so old worlds and old saved data are untouched. */
	static final String FILE = "ata_station_overrides";
	/** Kept small: more than this many stations or cells is a hoard, not a fine edit. */
	static final int MAX_STATIONS = 256;
	static final int MAX_CELLS = 8192;

	private StationOverrides() {
	}

	/** The edits saved for the station at {@code frame}, in local cells. */
	public static Map<StationPlanner.Cell, StationOverride> get(ServerWorld world, LocalFrame frame) {
		final State state = state(world.getServer());
		return state.stations.getOrDefault(new Key(world.getRegistryKey().getValue().toString(), frame), Map.of());
	}

	/** This plan with the station's saved edits applied last. */
	public static StationPlan apply(ServerWorld world, StationPlan plan, LocalFrame frame) {
		final Map<StationPlanner.Cell, StationOverride> cells = get(world, frame);
		return cells.isEmpty() ? plan : plan.withOverrides(cells);
	}

	/** Saves one cell edit; {@code override} of air clears the cell. False when the station's map is full. */
	public static boolean put(ServerWorld world, LocalFrame frame, StationPlanner.Cell cell, StationOverride override) {
		final State state = state(world.getServer());
		final Key key = new Key(world.getRegistryKey().getValue().toString(), frame);
		final Map<StationPlanner.Cell, StationOverride> cells = state.stations.computeIfAbsent(key, k -> new LinkedHashMap<>());
		if (!cells.containsKey(cell) && cells.size() >= MAX_CELLS) {
			return false;
		}
		cells.put(cell, override);
		state.markDirty();
		// drop the oldest stations rather than grow a world's save without bound
		while (state.stations.size() > MAX_STATIONS) {
			final Key oldest = state.stations.keySet().iterator().next();
			state.stations.remove(oldest);
		}
		return true;
	}

	/** Removes one saved cell edit; true when there was one. */
	public static boolean remove(ServerWorld world, LocalFrame frame, StationPlanner.Cell cell) {
		final State state = state(world.getServer());
		final Map<StationPlanner.Cell, StationOverride> cells = state.stations.get(new Key(world.getRegistryKey().getValue().toString(), frame));
		if (cells == null || cells.remove(cell) == null) {
			return false;
		}
		state.markDirty();
		return true;
	}

	/** How many edits the station at {@code frame} has. */
	public static int count(ServerWorld world, LocalFrame frame) {
		return get(world, frame).size();
	}

	private static State state(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(State::fromNbt, State::new, FILE);
	}

	/** A station by its local frame: origin and facing (the dimension is part of it too). */
	private record Key(String dimension, int ox, int oy, int oz, String forward) {

		Key(String dimension, LocalFrame frame) {
			this(dimension, frame.ox(), frame.oy(), frame.oz(), frame.forward().name());
		}
	}

	private static final class State extends PersistentState {

		private final Map<Key, Map<StationPlanner.Cell, StationOverride>> stations = new LinkedHashMap<>();

		private static State fromNbt(NbtCompound nbt) {
			final State state = new State();
			final NbtList list = nbt.getList("stations", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size() && state.stations.size() < MAX_STATIONS; i++) {
				final NbtCompound entry = list.getCompound(i);
				final Key key = new Key(entry.getString("dim"), entry.getInt("ox"), entry.getInt("oy"), entry.getInt("oz"), entry.getString("forward"));
				final Map<StationPlanner.Cell, StationOverride> cells = new LinkedHashMap<>();
				final NbtList saved = entry.getList("cells", NbtElement.COMPOUND_TYPE);
				for (int j = 0; j < saved.size() && cells.size() < MAX_CELLS; j++) {
					final NbtCompound cell = saved.getCompound(j);
					cells.put(new StationPlanner.Cell(cell.getInt("u"), cell.getInt("y"), cell.getInt("v")),
							new StationOverride(cell.getString("id"), dir(cell.getString("front")), cell.getString("state")));
				}
				if (!cells.isEmpty()) {
					state.stations.put(key, cells);
				}
			}
			return state;
		}

		@Override
		public NbtCompound writeNbt(NbtCompound nbt) {
			final NbtList list = new NbtList();
			for (final Map.Entry<Key, Map<StationPlanner.Cell, StationOverride>> station : stations.entrySet()) {
				final NbtCompound entry = new NbtCompound();
				entry.putString("dim", station.getKey().dimension());
				entry.putInt("ox", station.getKey().ox());
				entry.putInt("oy", station.getKey().oy());
				entry.putInt("oz", station.getKey().oz());
				entry.putString("forward", station.getKey().forward());
				final NbtList cells = new NbtList();
				for (final Map.Entry<StationPlanner.Cell, StationOverride> cell : station.getValue().entrySet()) {
					final NbtCompound saved = new NbtCompound();
					saved.putInt("u", cell.getKey().u());
					saved.putInt("y", cell.getKey().y());
					saved.putInt("v", cell.getKey().v());
					saved.putString("id", cell.getValue().blockId());
					saved.putString("front", cell.getValue().front().name());
					saved.putString("state", cell.getValue().state());
					cells.add(saved);
				}
				entry.put("cells", cells);
				list.add(entry);
			}
			nbt.put("stations", list);
			return nbt;
		}

		private static LocalDir dir(String name) {
			try {
				return LocalDir.valueOf(name);
			} catch (IllegalArgumentException e) {
				return LocalDir.PLUS_V;
			}
		}
	}
}
