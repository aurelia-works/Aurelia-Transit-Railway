/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The deterministic output of core for one request (spec mod-suite-v1 §6.4): per chunk section (16 × 16 × 16), the
 * blocks to set and their new states. Canonical order: sections by (x, z, y), blocks inside a section by (y, z, x).
 * The builder refuses a second block at the same position, so a plan can never claim a position twice.
 */
public final class BlockPlan {
    /**
     * One block to set: its position, its new state string (with the plan's DataVersion), and (F-1+) the data its block
     * entity gets, as SNBT, or null for none or the game's default (undo and redo put back what was there).
     */
    public record Block(int x, int y, int z, String state, String data) {
        public Block {
            Objects.requireNonNull(state, "state");
        }

        /** A block with no block-entity data of its own. */
        public Block(int x, int y, int z, String state) {
            this(x, y, z, state, null);
        }
    }

    /** A chunk section's position: block coordinates divided by 16, rounded down. */
    public record SectionPos(int x, int y, int z) {
        public static SectionPos of(int blockX, int blockY, int blockZ) {
            return new SectionPos(StrictMath.floorDiv(blockX, 16), StrictMath.floorDiv(blockY, 16), StrictMath.floorDiv(blockZ, 16));
        }
    }

    static final Comparator<SectionPos> SECTION_ORDER =
            Comparator.comparingInt(SectionPos::x).thenComparingInt(SectionPos::z).thenComparingInt(SectionPos::y);
    static final Comparator<Block> BLOCK_ORDER =
            Comparator.comparingInt(Block::y).thenComparingInt(Block::z).thenComparingInt(Block::x);

    private final int dataVersion;
    private final SortedMap<SectionPos, List<Block>> sections;

    private BlockPlan(int dataVersion, SortedMap<SectionPos, List<Block>> sections) {
        this.dataVersion = dataVersion;
        this.sections = sections;
    }

    public static Builder builder(int dataVersion) {
        return new Builder(dataVersion);
    }

    /**
     * The plan of {@code blocks}, if they are in canonical order with no position twice, built without the
     * {@link Builder}'s maps (F-2b: an undo's blocks come unpacked in that order, and a million of them in the builder's
     * maps made garbage enough to pause the server); empty if they aren't, for the caller to use a builder.
     */
    public static Optional<BlockPlan> ofCanonical(int dataVersion, List<Block> blocks) {
        SortedMap<SectionPos, List<Block>> sorted = new TreeMap<>(SECTION_ORDER);
        List<Block> section = null;
        Block previous = null;
        int sx = 0;
        int sy = 0;
        int sz = 0;
        for (Block block : blocks) {
            int x = StrictMath.floorDiv(block.x(), 16);
            int y = StrictMath.floorDiv(block.y(), 16);
            int z = StrictMath.floorDiv(block.z(), 16);
            if (section == null || x != sx || y != sy || z != sz) {
                SectionPos pos = new SectionPos(x, y, z);
                if (section != null && SECTION_ORDER.compare(new SectionPos(sx, sy, sz), pos) >= 0) {
                    return Optional.empty(); // sections out of order, or one seen before
                }
                section = new ArrayList<>();
                sorted.put(pos, section);
                sx = x;
                sy = y;
                sz = z;
            } else if (BLOCK_ORDER.compare(previous, block) >= 0) {
                return Optional.empty(); // blocks out of order, or a position twice
            }
            section.add(block);
            previous = block;
        }
        sorted.replaceAll((pos, list) -> Collections.unmodifiableList(list));
        return Optional.of(new BlockPlan(dataVersion, Collections.unmodifiableSortedMap(sorted)));
    }

    public int dataVersion() {
        return this.dataVersion;
    }

    /** The sections in canonical order, each with its blocks in canonical order. Unmodifiable. */
    public SortedMap<SectionPos, List<Block>> sections() {
        return this.sections;
    }

    /** Every block, in canonical order. */
    public List<Block> blocks() {
        List<Block> all = new ArrayList<>();
        this.sections.values().forEach(all::addAll);
        return all;
    }

    public int blockCount() {
        int count = 0;
        for (List<Block> blocks : this.sections.values()) {
            count += blocks.size();
        }
        return count;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BlockPlan plan && plan.dataVersion == this.dataVersion && plan.sections.equals(this.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.dataVersion, this.sections);
    }

    /** Collects blocks; refuses a position it already has. */
    public static final class Builder {
        private final int dataVersion;
        private final Map<SectionPos, Map<Long, Block>> sections = new HashMap<>();

        private Builder(int dataVersion) {
            this.dataVersion = dataVersion;
        }

        public Builder set(int x, int y, int z, String state) {
            return set(x, y, z, state, null);
        }

        /** As {@link #set(int, int, int, String)}, with the block entity's data (F-1+), or null. */
        public Builder set(int x, int y, int z, String state, String data) {
            SectionPos section = SectionPos.of(x, y, z);
            long local = ((long) StrictMath.floorMod(x, 16) << 8) | ((long) StrictMath.floorMod(z, 16) << 4) | StrictMath.floorMod(y, 16);
            Block block = new Block(x, y, z, state, data);
            Block previous = this.sections.computeIfAbsent(section, s -> new HashMap<>()).putIfAbsent(local, block);
            if (previous != null) {
                throw new IllegalStateException("Double claim: (" + x + ", " + y + ", " + z + ") is already set to " + previous.state()
                        + ", and a second block (" + state + ") was planned there");
            }
            return this;
        }

        public BlockPlan build() {
            SortedMap<SectionPos, List<Block>> sorted = new TreeMap<>(SECTION_ORDER);
            for (Map.Entry<SectionPos, Map<Long, Block>> entry : this.sections.entrySet()) {
                List<Block> blocks = new ArrayList<>(entry.getValue().values());
                blocks.sort(BLOCK_ORDER);
                sorted.put(entry.getKey(), List.copyOf(blocks));
            }
            return new BlockPlan(this.dataVersion, Collections.unmodifiableSortedMap(sorted));
        }
    }
}
