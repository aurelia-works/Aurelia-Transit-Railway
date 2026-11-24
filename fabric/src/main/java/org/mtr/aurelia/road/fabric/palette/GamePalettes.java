/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.palette;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import org.mtr.aurelia.road.road.palette.BlockLookup;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteException;
import org.mtr.aurelia.road.road.palette.PaletteFile;
import org.mtr.aurelia.road.road.palette.Palettes;
import org.mtr.aurelia.road.fabric.StratosMod;
import org.mtr.aurelia.road.fabric.apply.ApplyService;
import org.mtr.aurelia.road.fabric.paint.PaintLines;

/**
 * The palettes roads may use in this game (task R-9; owner, 2026-10-10).
 * <ul>
 *   <li><b>On the server,</b> read when it starts: Stratos's built-in palettes, then every {@code *.json} in
 *       {@code config/aurelia/roads/palettes/} (the folder is made if it isn't there), in file-name order. Each is resolved with
 *       the game's block registry and state parser ({@link #GAME}): per role, the first candidate whose blocks the game
 *       has, every state written as the game writes it. A file with a problem is refused whole and logged; a file can't
 *       take a built-in palette's id, nor another file's. The world's snapshots ({@code WorldData.paletteSnapshots}) are
 *       known too, so older roads plan exactly as placed.</li>
 *   <li><b>On a client,</b> the server's, as it sent them ({@code Palettes} message); Stratos's built-in ones until then.</li>
 * </ul>
 */
public final class GamePalettes {
    /** Where players and packs add palettes, inside the game's config folder. */
    public static final String FOLDER = "aurelia/roads/palettes";

    /** What this game knows about blocks: its block registry, and its parser for states. */
    public static final BlockLookup GAME = new BlockLookup() {
        @Override
        public boolean has(String block) {
            if (PaintLines.kindOf(block) != null) {
                return true; // a road line: asphalt and paint in ATR, not a block (PaintLines)
            }
            try {
                return Registries.BLOCK.containsId(new Identifier(block));
            } catch (InvalidIdentifierException e) {
                return false;
            }
        }

        @Override
        public String canonical(String state) {
            PaintLines.Line line = PaintLines.parse(state);
            if (line != null) {
                return line.state();
            }
            try {
                return BlockArgumentParser.stringifyBlockState(BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), state, false).blockState());
            } catch (CommandSyntaxException e) {
                throw new IllegalArgumentException("the game can't read " + state + " (" + e.getMessage() + ")");
            }
        }
    };

    private static volatile Palettes server;
    private static volatile List<String> problems = List.of();
    private static volatile Palettes client = Palettes.BUILT_IN;

    private GamePalettes() {
    }

    /** Reads the server's palettes as it starts, and forgets them when it stops. Called once, from the mod initialiser. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(minecraft -> load(FabricLoader.getInstance().getConfigDir().resolve(FOLDER)));
        ServerLifecycleEvents.SERVER_STOPPED.register(minecraft -> {
            server = null;
            problems = List.of();
        });
    }

    /**
     * The palettes the server's planner uses: those it read, and every snapshot its world keeps. The world's data is told
     * the current ones, so it can save the snapshot of any palette a record names.
     */
    public static Palettes server(MinecraftServer minecraft) {
        Palettes loaded = server;
        if (loaded == null) {
            loaded = load(FabricLoader.getInstance().getConfigDir().resolve(FOLDER));
        }
        var data = ApplyService.data(minecraft);
        data.addPalettes(loaded.all());
        return loaded.withSnapshots(data.paletteSnapshots());
    }

    /** The problems found reading the palette files when the server started, one line each (the harness reads them). */
    public static List<String> problems() {
        return problems;
    }

    /** The palettes a client plans with: the server's, once they arrive. */
    public static Palettes client() {
        return client;
    }

    /** The server's palettes, as the client received them (or the built-in ones, leaving a world). */
    public static void setClient(Palettes palettes) {
        client = palettes;
    }

    private static Palettes load(Path folder) {
        List<Palette> current = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (PaletteFile file : BuiltInPalettes.files()) {
            try {
                current.add(Palette.resolve(file, GAME));
            } catch (PaletteException e) {
                // A built-in palette names only Minecraft's and Stratos's own blocks, so this is a bug; the core-resolved one stands in.
                found.add("built-in " + e.getMessage());
                current.add(BuiltInPalettes.all().stream().filter(palette -> palette.id().equals(file.id())).findFirst().orElseThrow());
            }
        }
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            found.add("couldn't make " + folder + ": " + e);
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> listed = Files.list(folder)) {
            listed.filter(path -> path.getFileName().toString().endsWith(".json") && Files.isRegularFile(path)).sorted().forEach(files::add);
        } catch (IOException e) {
            found.add("couldn't list " + folder + ": " + e);
        }
        for (Path path : files) {
            String name = path.getFileName().toString();
            try {
                Palette palette = Palette.resolve(PaletteFile.read(Files.readString(path, StandardCharsets.UTF_8), name), GAME);
                if (current.stream().anyMatch(other -> other.id().equals(palette.id()))) {
                    found.add(name + ": the id " + palette.id() + " is taken (by " + (BuiltInPalettes.isBuiltIn(palette.id()) ? "a built-in palette" : "another file")
                            + ")");
                    continue;
                }
                current.add(palette);
            } catch (IOException e) {
                found.add(name + ": couldn't be read (" + e + ")");
            } catch (PaletteException e) {
                found.add(e.getMessage().startsWith(name) ? e.getMessage() : name + ": " + e.getMessage());
            }
        }
        for (String problem : found) {
            StratosMod.LOGGER.warn("[stratos] palette refused: {}", problem);
        }
        List<String> ids = current.stream().map(Palette::snapshotId).toList();
        StratosMod.LOGGER.info("[stratos] palettes: {}", ids);
        Palettes loaded = Palettes.of(current, List.of(BuiltInPalettes.provisional()));
        server = loaded;
        problems = List.copyOf(found);
        return loaded;
    }
}
