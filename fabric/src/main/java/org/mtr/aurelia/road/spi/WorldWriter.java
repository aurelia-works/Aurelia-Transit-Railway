/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import java.util.Collection;

/**
 * How the core reads and writes one world (spec mod-suite-v1 §5). Block states are strings, such as
 * {@code minecraft:oak_slab[type=bottom,waterlogged=false]}, with the world's DataVersion. Every call is made on the
 * server thread; the apply engine ({@code core.ops.ApplyJob}) decides how much to do per tick.
 *
 * <p><b>From F-1+</b> a block entity's data (a chest's items, a sign's text) travels as SNBT, Minecraft's text form of
 * NBT, so core can keep it with the undo images without knowing NBT.
 */
public interface WorldWriter {
    /** The DataVersion of the running game (3465 on 1.20.1). */
    int dataVersion();

    /** Whether {@code state} names a real block state in this game. */
    boolean isValidState(String state);

    /** The state of the block at a position. The chunk must be loaded. */
    String blockState(int x, int y, int z);

    /** Whether the block at a position has a block entity (a chest, a sign…). The chunk must be loaded. */
    boolean hasBlockEntity(int x, int y, int z);

    /** The data of the block entity at a position, as SNBT; null if the block has none (F-1+). */
    String blockEntityData(int x, int y, int z);

    /**
     * Whether Stratos may replace the block entity at a position and put it back from its data (F-1+: Minecraft's own
     * kinds only, owner 2026-10-08). True where there is none.
     */
    boolean canReplaceBlockEntity(int x, int y, int z);

    /** Sets one block, without neighbour updates or drops; the game handles lighting and tells players. */
    default void setBlockState(int x, int y, int z, String state) {
        setBlockState(x, y, z, state, null);
    }

    /**
     * Sets one block, without neighbour updates or drops; the game handles lighting and tells players (F-1+). A block
     * entity already there is emptied first, so nothing drops; if the new block has one and {@code data} isn't null, it
     * gets that data (SNBT). Returns the new block entity's data as SNBT, or null if the new block has none.
     */
    String setBlockState(int x, int y, int z, String state, String data);

    /**
     * The outer shell (F-1+): tells the block at (x, y, z) that its neighbour at (fromX, fromY, fromZ) changed, as
     * Minecraft's neighbour update does, so redstone and machines react. Shapes aren't recomputed (owner, 2026-10-08).
     * Nothing happens outside the build height or in a chunk that isn't loaded.
     */
    void neighbourChanged(int x, int y, int z, int fromX, int fromY, int fromZ);

    /** Keeps these chunks loaded (loading them if needed) until {@link #releaseChunks} is called with the same set. */
    void retainChunks(Collection<ChunkXZ> chunks);

    /** Lets these chunks unload again. */
    void releaseChunks(Collection<ChunkXZ> chunks);

    /**
     * An edit that changed blocks in these chunks has finished: every block and its outer shell are written, and the
     * chunks are still retained ({@link #releaseChunks} follows). F-7: the platform tells LOD mods (Distant Horizons) so
     * their far view catches up. Nothing by default.
     */
    default void blocksChanged(Collection<ChunkXZ> chunks) {
    }

    /** Whether a chunk is fully loaded, so its blocks can be read and written. */
    boolean isLoaded(ChunkXZ chunk);
}
