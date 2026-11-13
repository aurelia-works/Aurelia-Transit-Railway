package com.aureliatransit.architecture.bridge.world;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the wrench's bridge builder last built on each rail, per dimension: every cell it changed with the block it held before.
 * Building on the same rail again (a restyle) takes the old bridge down first. Kept in its own file, so worlds without it load
 * as they always did.
 */
final class RailBridgeStore extends PersistentState {

	private static final String FILE = "atr_rail_bridges";
	private final Map<String, List<BridgeBuildJob.Entry>> rails = new LinkedHashMap<>();

	static RailBridgeStore of(ServerWorld world) {
		return world.getPersistentStateManager().getOrCreate(RailBridgeStore::fromNbt, RailBridgeStore::new, FILE);
	}

	List<BridgeBuildJob.Entry> get(String rail) {
		return rails.getOrDefault(rail, List.of());
	}

	void put(String rail, List<BridgeBuildJob.Entry> entries) {
		if (entries.isEmpty()) {
			rails.remove(rail);
		} else {
			rails.put(rail, List.copyOf(entries));
		}
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		final NbtList list = new NbtList();
		for (final Map.Entry<String, List<BridgeBuildJob.Entry>> rail : rails.entrySet()) {
			final Map<String, Integer> palette = new LinkedHashMap<>();
			final List<BridgeBuildJob.Entry> entries = rail.getValue();
			final long[] positions = new long[entries.size()];
			final int[] before = new int[entries.size()];
			final int[] after = new int[entries.size()];
			for (int i = 0; i < entries.size(); i++) {
				final BridgeBuildJob.Entry entry = entries.get(i);
				positions[i] = entry.pos().asLong();
				before[i] = palette.computeIfAbsent(BridgeStateCodec.encode(entry.before()), k -> palette.size());
				after[i] = palette.computeIfAbsent(BridgeStateCodec.encode(entry.after()), k -> palette.size());
			}
			final NbtCompound record = new NbtCompound();
			record.putString("Rail", rail.getKey());
			final NbtList states = new NbtList();
			palette.keySet().forEach(state -> states.add(NbtString.of(state)));
			record.put("Palette", states);
			record.putLongArray("Pos", positions);
			record.putIntArray("Before", before);
			record.putIntArray("After", after);
			list.add(record);
		}
		nbt.put("Rails", list);
		return nbt;
	}

	private static RailBridgeStore fromNbt(NbtCompound nbt) {
		final RailBridgeStore store = new RailBridgeStore();
		final NbtList list = nbt.getList("Rails", NbtElement.COMPOUND_TYPE);
		for (int r = 0; r < list.size(); r++) {
			final NbtCompound record = list.getCompound(r);
			final NbtList states = record.getList("Palette", NbtElement.STRING_TYPE);
			final Map<Integer, BlockState> palette = new HashMap<>();
			for (int i = 0; i < states.size(); i++) {
				palette.put(i, BridgeStateCodec.decode(states.getString(i)));
			}
			final long[] positions = record.getLongArray("Pos");
			final int[] before = record.getIntArray("Before");
			final int[] after = record.getIntArray("After");
			final List<BridgeBuildJob.Entry> entries = new ArrayList<>();
			for (int i = 0; i < positions.length && i < before.length && i < after.length; i++) {
				final BlockState was = palette.get(before[i]);
				final BlockState now = palette.get(after[i]);
				if (was != null && now != null) {
					entries.add(new BridgeBuildJob.Entry(BlockPos.fromLong(positions[i]), was, now));
				}
			}
			if (!entries.isEmpty()) {
				store.rails.put(record.getString("Rail"), entries);
			}
		}
		return store;
	}
}
