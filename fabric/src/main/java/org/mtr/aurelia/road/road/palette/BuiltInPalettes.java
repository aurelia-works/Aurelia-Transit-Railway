/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The palettes Stratos ships (task R-9), as files in core's jar, in the hub's order.
 * <ul>
 *   <li><b>Stratos UK</b> ({@code stratos-uk.json}; B-1, ADR-0044): Stratos's own blocks in eighth-block layers. Asphalt
 *       for the road, its gutters and (until B-2's lines) its markings; footway flags, and kerbs facing the road, both a
 *       layer (125 mm) above it; stone under the road, blue wool markers. From ATR P11 also gullies (B-4) and dropped kerbs
 *       at crossings, so its snapshot id changed; roads placed before keep theirs. Each role falls back to Vanilla's
 *       family. The default palette.</li>
 *   <li><b>Vanilla</b> ({@code vanilla.json}; owner, 2026-10-10): matched families (ADR-0044), each part's full block with
 *       its own slab: polished deepslate for the asphalt, smooth quartz for white lines, polished andesite for the kerb and
 *       gutter, smooth stone for the footway; stone under the road, blue wool markers. For worlds that must never depend
 *       on Stratos.</li>
 *   <li><b>The provisional vanilla palette</b> ({@code vanilla-provisional.json}): the blocks Stratos built roads with from
 *       Slice A to R-7 (grey concrete with deepslate tile slabs, white concrete dashes with smooth quartz slabs). Never
 *       offered; a roads record without {@code palettes} was placed with it (owner, 2026-10-10), so its snapshot keeps
 *       R-7's hand-edit check exact on those roads.</li>
 * </ul>
 * Their states name every property in the game's order, so {@link #STRATOS} resolves them exactly as the game does (the
 * harness checks the server's built-in palettes have the same snapshot ids).
 */
public final class BuiltInPalettes {
    /** The Vanilla palette's id. */
    public static final String VANILLA_ID = "vanilla";
    /** The Stratos UK palette's id. */
    public static final String STRATOS_UK_ID = "stratos_uk";
    /** The default palette's id (ADR-0044: Stratos UK wherever Stratos is installed). */
    public static final String DEFAULT_ID = STRATOS_UK_ID;

    /**
     * Assumes every {@code minecraft:} and {@code stratos:} block exists, every state already written as the game writes
     * it: true of the built-in palettes in a game with Stratos.
     */
    static final BlockLookup STRATOS = new BlockLookup() {
        @Override
        public boolean has(String block) {
            return block.startsWith("minecraft:") || block.startsWith("stratos:");
        }

        @Override
        public String canonical(String state) {
            return state;
        }
    };

    private static final PaletteFile STRATOS_UK_FILE = file("stratos-uk.json");
    private static final PaletteFile VANILLA_FILE = file("vanilla.json");
    private static final Palette STRATOS_UK = resolve(STRATOS_UK_FILE);
    private static final Palette VANILLA = resolve(VANILLA_FILE);
    private static final Palette PROVISIONAL = resolve(file("vanilla-provisional.json"));

    private BuiltInPalettes() {
    }

    /** The Stratos UK palette, resolved without a game (as a game with Stratos resolves it). */
    public static Palette stratosUk() {
        return STRATOS_UK;
    }

    /** The Vanilla palette, resolved without a game (every block is Minecraft's own). */
    public static Palette vanilla() {
        return VANILLA;
    }

    /** The palette roads were built with before R-9, as a snapshot (its id is {@code vanilla}). */
    public static Palette provisional() {
        return PROVISIONAL;
    }

    /** The built-in palettes resolved without a game, in the hub's order (the default first). */
    public static List<Palette> all() {
        return List.of(STRATOS_UK, VANILLA);
    }

    /** The built-in palette files, for a game to resolve with what it knows, in the hub's order. */
    public static List<PaletteFile> files() {
        return List.of(STRATOS_UK_FILE, VANILLA_FILE);
    }

    /** Whether {@code id} is a built-in palette's: named in the lang file ({@code stratos.palette.<id>}), never replaced by a file. */
    public static boolean isBuiltIn(String id) {
        return VANILLA_ID.equals(id) || STRATOS_UK_ID.equals(id);
    }

    private static PaletteFile file(String name) {
        try (InputStream in = BuiltInPalettes.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException(name + " is missing from the core jar");
            }
            return PaletteFile.read(new String(in.readAllBytes(), StandardCharsets.UTF_8), name);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + name, e);
        } catch (PaletteException e) {
            throw new IllegalStateException("A built-in palette is broken: " + e.getMessage(), e);
        }
    }

    private static Palette resolve(PaletteFile file) {
        try {
            return Palette.resolve(file, STRATOS);
        } catch (PaletteException e) {
            throw new IllegalStateException("A built-in palette is broken: " + e.getMessage(), e);
        }
    }
}
