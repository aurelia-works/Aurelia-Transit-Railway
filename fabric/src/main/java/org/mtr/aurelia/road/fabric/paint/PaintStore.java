/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.paint;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import org.mtr.atr.compat.Compat;

/**
 * One dimension's road paint ({@link PaintLines}): the painted cells, by block position, saved in the dimension's own
 * {@code data/stratos_paint.dat}, a file of its own, so a world from before it loads unchanged. Changes wait in
 * {@link #pending} until {@link PaintSync} sends them to the dimension's players at the end of the tick. Server thread.
 */
public final class PaintStore extends PersistentState {
    static final String NAME = "stratos_paint";

    private final Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
    /** Changed cells not yet sent: the new value, 0 where the paint went. */
    private final Long2IntOpenHashMap pending = new Long2IntOpenHashMap();

    private PaintStore() {
    }

    /** The paint of {@code world}, read from its save the first time. */
    public static PaintStore of(ServerWorld world) {
        return Compat.getOrCreate(world.getPersistentStateManager(), PaintStore::load, PaintStore::new, NAME);
    }

    private static PaintStore load(NbtCompound tag) {
        PaintStore store = new PaintStore();
        long[] positions = tag.getLongArray("positions");
        int[] values = tag.getIntArray("values");
        for (int i = 0; i < StrictMath.min(positions.length, values.length); i++) {
            if (PaintLines.valid(values[i])) {
                store.cells.put(positions[i], values[i]);
            }
        }
        return store;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound tag) {
        long[] positions = new long[this.cells.size()];
        int[] values = new int[this.cells.size()];
        int i = 0;
        for (Long2IntMap.Entry entry : this.cells.long2IntEntrySet()) {
            positions[i] = entry.getLongKey();
            values[i] = entry.getIntValue();
            i++;
        }
        tag.putLongArray("positions", positions);
        tag.putIntArray("values", values);
        return tag;
    }

    /** The value painted at {@code position} ({@code BlockPos.asLong}), 0 if none. */
    public int get(long position) {
        return this.cells.get(position);
    }

    /** Paints a cell; {@code value} 0 takes its paint away. */
    public void set(long position, int value) {
        int old = value == 0 ? this.cells.remove(position) : this.cells.put(position, value);
        if (old != value) {
            this.pending.put(position, value);
            markDirty();
        }
    }

    public void remove(long position) {
        set(position, 0);
    }

    /** A copy of every painted cell, for a reader off the server thread (a world bake reads saved chunks there). */
    public Long2IntMap snapshot() {
        return new Long2IntOpenHashMap(this.cells);
    }

    /** Every painted cell, to send to a player who arrives. */
    Long2IntMap cells() {
        return this.cells;
    }

    /** The changes since the last call, which are then forgotten. */
    Long2IntMap drain() {
        if (this.pending.isEmpty()) {
            return Long2IntMaps.EMPTY_MAP;
        }
        Long2IntOpenHashMap changes = new Long2IntOpenHashMap(this.pending);
        this.pending.clear();
        return changes;
    }
}
