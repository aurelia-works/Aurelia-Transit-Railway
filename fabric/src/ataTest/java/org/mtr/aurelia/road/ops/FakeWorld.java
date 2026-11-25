/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mtr.aurelia.road.spi.ChunkXZ;
import org.mtr.aurelia.road.spi.WorldWriter;

/**
 * A world in memory. Each block write advances the clock by {@code writeCost}. Everything else is air. From F-1+, a
 * chest or a sign has a block entity: its data is {@link #blockEntityData} (or a default), and positions in
 * {@link #foreignBlockEntities} stand for other mods' block entities, which Stratos may not replace.
 */
public final class FakeWorld implements WorldWriter {
    public final Map<String, String> blocks = new HashMap<>();
    public final Set<String> blockEntities = new HashSet<>();
    /** Block-entity data by position, as SNBT (F-1+). */
    public final Map<String, String> blockEntityData = new HashMap<>();
    /** Positions whose block entity is another mod's: Stratos may not replace it (F-1+). */
    public final Set<String> foreignBlockEntities = new HashSet<>();
    /** Every neighbour update, as "x,y,z<-fromX,fromY,fromZ", in order (F-1+: the outer shell). */
    public final List<String> neighbourUpdates = new ArrayList<>();
    public final Set<ChunkXZ> retained = new HashSet<>();
    public final List<String> writes = new ArrayList<>();
    public long now;
    public long writeCost = 100_000; // 0.1 ms per block
    public long retainCost; // per chunk
    public long neighbourCost; // per neighbour update (F-1+)
    public final Map<ChunkXZ, Integer> retainCounts = new HashMap<>();
    public int loadAfterChecks; // isLoaded answers false this many times first
    public int dataVersion = 3465;
    public int retainCalls;
    public int releaseCalls;
    public Runnable afterWrite = () -> { };
    /** Other mods' blocks this world has, by id (T-2: MTR's node block for the track tests). */
    public final Set<String> modBlocks = new HashSet<>();

    public static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Override
    public int dataVersion() {
        return this.dataVersion;
    }

    @Override
    public boolean isValidState(String state) {
        boolean known = state.startsWith("minecraft:") || this.modBlocks.stream().anyMatch(id -> state.equals(id) || state.startsWith(id + "["));
        return known && !state.contains("nonsense");
    }

    @Override
    public String blockState(int x, int y, int z) {
        return this.blocks.getOrDefault(key(x, y, z), "minecraft:air");
    }

    @Override
    public boolean hasBlockEntity(int x, int y, int z) {
        return this.blockEntities.contains(key(x, y, z));
    }

    /** Whether a state has a block entity here: chests and signs. */
    public static boolean hasEntity(String state) {
        return state.startsWith("minecraft:chest") || state.startsWith("minecraft:oak_sign");
    }

    @Override
    public String blockEntityData(int x, int y, int z) {
        String key = key(x, y, z);
        return this.blockEntities.contains(key) ? this.blockEntityData.getOrDefault(key, "{}") : null;
    }

    @Override
    public boolean canReplaceBlockEntity(int x, int y, int z) {
        return !this.foreignBlockEntities.contains(key(x, y, z));
    }

    @Override
    public String setBlockState(int x, int y, int z, String state, String data) {
        String key = key(x, y, z);
        this.blocks.put(key, state);
        this.writes.add(key);
        this.blockEntities.remove(key);
        this.blockEntityData.remove(key);
        this.foreignBlockEntities.remove(key);
        String now = null;
        if (hasEntity(state)) {
            now = data != null ? data : "{}";
            this.blockEntities.add(key);
            this.blockEntityData.put(key, now);
        }
        this.now += this.writeCost;
        this.afterWrite.run();
        return now;
    }

    @Override
    public void neighbourChanged(int x, int y, int z, int fromX, int fromY, int fromZ) {
        this.neighbourUpdates.add(key(x, y, z) + "<-" + key(fromX, fromY, fromZ));
        this.now += this.neighbourCost;
    }

    @Override
    public void retainChunks(Collection<ChunkXZ> chunks) {
        this.retainCalls++;
        this.retained.addAll(chunks);
        chunks.forEach(chunk -> this.retainCounts.merge(chunk, 1, Integer::sum));
        this.now += this.retainCost * chunks.size();
    }

    @Override
    public void releaseChunks(Collection<ChunkXZ> chunks) {
        this.releaseCalls++;
        this.retained.removeAll(chunks);
    }

    /** F-7: each "blocks changed" notice, and whether all its chunks were still retained then. */
    public final List<List<ChunkXZ>> changedNotices = new ArrayList<>();
    public boolean noticesWhileRetained = true;

    @Override
    public void blocksChanged(Collection<ChunkXZ> chunks) {
        this.changedNotices.add(List.copyOf(chunks));
        this.noticesWhileRetained &= this.retained.containsAll(chunks);
    }

    @Override
    public boolean isLoaded(ChunkXZ chunk) {
        if (!this.retained.contains(chunk)) {
            return false;
        }
        if (this.loadAfterChecks > 0) {
            this.loadAfterChecks--;
            return false;
        }
        return true;
    }
}
