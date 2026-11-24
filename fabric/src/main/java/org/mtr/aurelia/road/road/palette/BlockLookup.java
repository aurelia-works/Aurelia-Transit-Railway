/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

/**
 * What a game knows about blocks, for resolving a palette (task R-9): which blocks it has, and how it writes a state.
 * The platform answers from the game's block registry and its state parser; core never sees the game (ADR-0009).
 */
public interface BlockLookup {
    /**
     * Assumes every {@code minecraft:} block exists and every state is already written as the game writes it: true of
     * Stratos's own built-in palettes, which name every property in the game's order.
     */
    BlockLookup VANILLA = new BlockLookup() {
        @Override
        public boolean has(String block) {
            return block.startsWith("minecraft:");
        }

        @Override
        public String canonical(String state) {
            return state;
        }
    };

    /**
     * Takes every block and state as given: for palettes another side has already resolved (the server's, which a client
     * receives with {@code Palettes}; a world's snapshots).
     */
    BlockLookup RESOLVED = new BlockLookup() {
        @Override
        public boolean has(String block) {
            return true;
        }

        @Override
        public String canonical(String state) {
            return state;
        }
    };

    /** Whether this game has the block {@code block}, such as {@code minecraft:smooth_stone_slab}. */
    boolean has(String block);

    /**
     * {@code state} as the game writes it (every property, in its order), or an {@link IllegalArgumentException} saying
     * why the game can't read it (an unknown property or value). Only asked for blocks the game {@link #has}.
     */
    String canonical(String state);
}
