/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.util.Objects;
import java.util.Set;
import org.mtr.aurelia.road.road.palette.Palettes;

/**
 * Where a world lets blocks be placed: its build height ({@code minY} included, {@code maxY} not; −64 to 320 in a
 * 1.20.1 overworld) and its world border. A block is inside the border the way Minecraft 1.20.1 decides it
 * ({@code WorldBorder.isWithinBounds}): {@code x + 1 > minX}, {@code x < maxX}, and the same for z. Plans that leave
 * these limits are refused ({@code WORLD_LIMIT}, roads-v1 §12).
 *
 * <p>From R-6 it also says which of the other mods' blocks a planner may use this game has ({@code modBlocks}, runtime
 * ids such as {@code mtr:rail}): Stratos never depends on those mods (ADR-0007), so the platform looks each one up in
 * the game's block registry, on the client and on the server alike.
 *
 * <p>From R-9 also the palettes roads may use ({@link Palettes}): on the server, those it loaded and the world's
 * snapshots; on a client, the server's as it sent them. Stratos's built-in ones until then.
 */
public record WorldLimits(int minY, int maxY, double borderMinX, double borderMaxX, double borderMinZ, double borderMaxZ, Set<String> modBlocks,
        Palettes palettes) {
    /** A 1.20.1 overworld with the default border (60 million blocks wide), and no other mods' blocks. */
    public static final WorldLimits OVERWORLD = new WorldLimits(-64, 320, -29_999_984, 29_999_984, -29_999_984, 29_999_984);

    public WorldLimits {
        if (minY >= maxY) {
            throw new IllegalArgumentException("minY (" + minY + ") must be below maxY (" + maxY + ")");
        }
        if (!(borderMinX <= borderMaxX) || !(borderMinZ <= borderMaxZ)) {
            throw new IllegalArgumentException("The border's minimum must not be above its maximum");
        }
        modBlocks = Set.copyOf(Objects.requireNonNull(modBlocks, "modBlocks"));
        Objects.requireNonNull(palettes, "palettes");
    }

    /** Limits with these other mods' blocks, and Stratos's built-in palettes. */
    public WorldLimits(int minY, int maxY, double borderMinX, double borderMaxX, double borderMinZ, double borderMaxZ, Set<String> modBlocks) {
        this(minY, maxY, borderMinX, borderMaxX, borderMinZ, borderMaxZ, modBlocks, Palettes.BUILT_IN);
    }

    /** Limits without any other mods' blocks. */
    public WorldLimits(int minY, int maxY, double borderMinX, double borderMaxX, double borderMinZ, double borderMaxZ) {
        this(minY, maxY, borderMinX, borderMaxX, borderMinZ, borderMaxZ, Set.of());
    }

    /** The same limits, with these other mods' blocks. */
    public WorldLimits withModBlocks(Set<String> blocks) {
        return new WorldLimits(this.minY, this.maxY, this.borderMinX, this.borderMaxX, this.borderMinZ, this.borderMaxZ, blocks, this.palettes);
    }

    /** The same limits, with these palettes (R-9). */
    public WorldLimits withPalettes(Palettes palettes) {
        return new WorldLimits(this.minY, this.maxY, this.borderMinX, this.borderMaxX, this.borderMinZ, this.borderMaxZ, this.modBlocks, palettes);
    }

    /** Whether this game has the other mod's block {@code id} (a runtime id such as {@code mtr:rail}). */
    public boolean hasBlock(String id) {
        return this.modBlocks.contains(id);
    }

    /** Whether a block may be placed at {@code (x, y, z)}. */
    public boolean contains(int x, int y, int z) {
        return y >= this.minY && y < this.maxY && containsColumn(x, z);
    }

    /** Whether the column {@code (x, z)} is inside the world border. */
    public boolean containsColumn(int x, int z) {
        return (double) x + 1 > this.borderMinX && x < this.borderMaxX && (double) z + 1 > this.borderMinZ && z < this.borderMaxZ;
    }
}
