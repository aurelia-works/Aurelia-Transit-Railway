/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.raster.Column;

/** R-9: palette files read and refused, resolved against a game's blocks, their steps, and their snapshots. */
class PaletteTest {
    /** A file with every required role, {@code surface} given as {@code surface} (JSON) and the rest vanilla slabs. */
    private static String file(String extra, String surface) {
        return """
                { "format": 1, "id": "test", %s"dataVersion": 3465, "roles": {
                  "surface": %s,
                  "marking_white": [ { "full": "minecraft:smooth_quartz", "half": "minecraft:smooth_quartz_slab[type=bottom,waterlogged=false]" } ],
                  "kerb": [ { "full": "minecraft:polished_andesite", "half": "minecraft:polished_andesite_slab[type=bottom,waterlogged=false]" } ],
                  "footway": [ { "full": "minecraft:smooth_stone", "half": "minecraft:smooth_stone_slab[type=bottom,waterlogged=false]" } ],
                  "gutter": [ { "full": "minecraft:polished_andesite", "half": "minecraft:polished_andesite_slab[type=bottom,waterlogged=false]" } ],
                  "foundation": [ { "full": "minecraft:stone" } ],
                  "node_marker": [ { "full": "minecraft:blue_wool" } ] } }
                """.formatted(extra, surface);
    }

    private static final String VANILLA_SURFACE = "[ { \"full\": \"minecraft:polished_deepslate\", \"half\": \"minecraft:polished_deepslate_slab[type=bottom,waterlogged=false]\" } ]";

    /** A game with Minecraft's blocks and {@code mods}' blocks, which writes a slab state with every property. */
    private static BlockLookup game(Set<String> mods) {
        return new BlockLookup() {
            @Override
            public boolean has(String block) {
                return block.startsWith("minecraft:") || mods.contains(block);
            }

            @Override
            public String canonical(String state) {
                if (state.contains("colour=")) {
                    throw new IllegalArgumentException("the game can't read " + state);
                }
                return state.endsWith("[type=bottom]") ? state.replace("[type=bottom]", "[type=bottom,waterlogged=false]") : state;
            }
        };
    }

    private static String refusal(String text) {
        return assertThrows(PaletteException.class, () -> PaletteFile.read(text, "test.json")).getMessage();
    }

    @Test
    void aFileReadsAndWritesBack() throws PaletteException {
        PaletteFile read = PaletteFile.read(file("\"name\": \"Test roads\", ", VANILLA_SURFACE), "test.json");
        assertEquals("test", read.id());
        assertEquals(Optional.of("Test roads"), read.name());
        assertEquals(7, read.roles().size());
        assertEquals(read, PaletteFile.read(StrictJson.write(read.toJson(true)), "again.json"), "written and read back");
    }

    @Test
    void aBrokenFileIsRefusedWithItsReason() {
        assertTrue(refusal("{").startsWith("test.json isn't JSON"));
        assertTrue(refusal(file("\"colour\": \"red\", ", VANILLA_SURFACE)).contains("test.json.colour isn't a field Stratos knows"));
        assertTrue(refusal(file("", VANILLA_SURFACE).replace("\"kerb\"", "\"kerbb\"")).contains("roles.kerbb isn't a role Stratos knows"));
        assertTrue(refusal(file("", VANILLA_SURFACE).replace("\"footway\": [", "\"verge\": [")).contains("roles.footway is missing"));
        assertTrue(refusal(file("", "[]")).contains("roles.surface has no blocks"));
        assertTrue(refusal(file("", "[ { \"full\": \"test:asphalt\" } ]")).contains("roles.surface's last candidate must be vanilla"));
        assertTrue(refusal(file("", "[ { \"full\": \"minecraft:stone\", \"half\": \"Stone Slab\" } ]")).contains("half isn't a block state"));
        assertTrue(refusal(file("", "[ { \"full\": \"minecraft:stone\", \"half\": \"minecraft:stone_slab\", \"layers\": \"minecraft:snow[layers={layers}]\" } ]"))
                .contains("half or layers, not both"));
        assertTrue(refusal(file("", "[ { \"full\": \"minecraft:snow\", \"layers\": \"minecraft:snow[layers=1]\" } ]")).contains("exactly once"));
        assertTrue(refusal(file("", VANILLA_SURFACE).replace("{ \"full\": \"minecraft:stone\" }",
                "{ \"full\": \"minecraft:stone\", \"half\": \"minecraft:stone_slab[type=bottom,waterlogged=false]\" }"))
                .contains("roles.foundation is a full block only"));
        assertTrue(refusal(file("", VANILLA_SURFACE).replace("\"id\": \"test\"", "\"id\": \"Test Roads\"")).contains("lowercase"));
        assertTrue(refusal(file("", VANILLA_SURFACE).replace("3465", "3700")).contains("dataVersion is 3700"));
        assertTrue(refusal(file("", VANILLA_SURFACE).replace("\"format\": 1", "\"format\": 2")).contains("format 2 isn't one this Stratos reads"));
    }

    @Test
    void theFirstCandidateTheGameHasWinsAndStatesAreWrittenAsTheGameWritesThem() throws PaletteException {
        String surface = "[ { \"full\": \"test:asphalt\", \"layers\": \"test:asphalt[layers={layers}]\" },"
                + " { \"full\": \"minecraft:polished_deepslate\", \"half\": \"minecraft:polished_deepslate_slab[type=bottom]\" } ]";
        PaletteFile written = PaletteFile.read(file("", surface), "test.json");
        Palette withoutMod = Palette.resolve(written, game(Set.of()));
        assertEquals("minecraft:polished_deepslate_slab[type=bottom,waterlogged=false]", withoutMod.family(PaletteRole.SURFACE).orElseThrow().half().orElseThrow(),
                "the vanilla fallback, as the game writes it");
        Palette withMod = Palette.resolve(written, game(Set.of("test:asphalt")));
        assertEquals("test:asphalt", withMod.family(PaletteRole.SURFACE).orElseThrow().full());
        assertNotEquals(withoutMod.snapshotId(), withMod.snapshotId(), "another game, another snapshot");
        assertTrue(assertThrows(PaletteException.class, () -> Palette.resolve(PaletteFile.read(file("",
                "[ { \"full\": \"minecraft:stone\", \"half\": \"minecraft:stone_slab[colour=red]\" } ]"), "test.json"), game(Set.of())))
                .getMessage().contains("can't read minecraft:stone_slab[colour=red]"));
    }

    @Test
    void aPalettesStepsAreItsCoarsestRoles() throws PaletteException {
        String layered = "[ { \"full\": \"test:asphalt[layers=8]\", \"layers\": \"test:asphalt[layers={layers}]\" }, { \"full\": \"minecraft:stone\" } ]";
        Palette mixed = Palette.resolve(PaletteFile.read(file("", layered), "test.json"), game(Set.of("test:asphalt")));
        assertEquals(2, mixed.steps(), "the other roles have slabs only");
        assertEquals("test:asphalt[layers=4]", mixed.partial(new Column(Band.LANE, false, false), 1), "layers stand at a half with 4");
        Palette full = Palette.resolve(PaletteFile.read(file("", "[ { \"full\": \"minecraft:gray_concrete\" } ]"), "test.json"), game(Set.of()));
        assertEquals(1, full.steps(), "one role without partial blocks makes the road climb in whole blocks");
        assertEquals(2, BuiltInPalettes.vanilla().steps());
        Family layers = Family.withLayers("test:asphalt[layers=8]", "test:asphalt[layers={layers}]");
        assertEquals(8, layers.steps());
        assertEquals("test:asphalt[layers=3]", layers.partial(3, 8));
        assertThrows(IllegalArgumentException.class, () -> Family.withHalf("minecraft:stone", "minecraft:stone_slab").partial(3, 8));
    }

    @Test
    void columnsTakeTheirBandsOrTheirPaintsRole() {
        Palette vanilla = BuiltInPalettes.vanilla();
        assertEquals("minecraft:polished_deepslate", vanilla.state(new Column(Band.LANE, false, false)));
        assertEquals("minecraft:polished_deepslate", vanilla.state(new Column(Band.CENTRE_LINE, false, false)), "an unpainted centre column");
        assertEquals("minecraft:smooth_quartz", vanilla.state(new Column(Band.CENTRE_LINE, true, false)));
        assertEquals(PaletteRole.MARKING_YELLOW, PaletteRole.of(new Column(Band.CENTRE_LINE, Optional.of(LineMarking.Colour.YELLOW), false)));
        assertThrows(IllegalArgumentException.class, () -> vanilla.state(new Column(Band.CENTRE_LINE, Optional.of(LineMarking.Colour.YELLOW), false)),
                "Vanilla has no yellow yet: no layout paints one");
        assertThrows(IllegalArgumentException.class, () -> vanilla.state(new Column(Band.VERGE, false, false)), "verges come with Phase 2's layouts");
    }

    @Test
    void aSnapshotIsTheResolvedPaletteWithoutItsName() throws PaletteException, org.mtr.aurelia.road.json.JsonFormatException {
        Palette named = Palette.resolve(PaletteFile.read(file("\"name\": \"Test roads\", ", VANILLA_SURFACE), "test.json"), game(Set.of()));
        Palette unnamed = Palette.resolve(PaletteFile.read(file("", VANILLA_SURFACE), "test.json"), game(Set.of()));
        assertEquals(named.snapshotId(), unnamed.snapshotId(), "a name doesn't change the blocks");
        assertTrue(named.snapshotId().matches("test@[0-9a-f]{12}"), named.snapshotId());
        assertFalse(named.snapshotJson().contains("Test roads"));
        Palette back = Palette.read(StrictJson.write(StrictJson.parseObject(named.snapshotJson(), "x")), "test@x.json");
        assertEquals(named.snapshotId(), back.snapshotId(), "read back from a pretty-printed file");
        assertEquals(named.families(), back.families());
        Palette sent = Palette.read(named.json(), "the server's");
        assertEquals(Optional.of("Test roads"), sent.name(), "what a client receives keeps the name");
        // A resolved palette's one candidate may be a mod's block: no vanilla rule for snapshots.
        Palette modded = Palette.resolve(PaletteFile.read(file("", "[ { \"full\": \"test:asphalt\" }, { \"full\": \"minecraft:stone\" } ]"), "t.json"),
                game(Set.of("test:asphalt")));
        assertEquals("test:asphalt", Palette.read(modded.snapshotJson(), "snapshot").family(PaletteRole.SURFACE).orElseThrow().full());
    }

    @Test
    void theProvisionalPaletteIsTheBlocksOfSliceAToR7() {
        Palette old = BuiltInPalettes.provisional();
        assertEquals("vanilla", old.id());
        assertEquals("minecraft:gray_concrete", old.state(new Column(Band.LANE, false, false)));
        assertEquals("minecraft:deepslate_tile_slab[type=bottom,waterlogged=false]", old.partial(new Column(Band.LANE, false, false), 1));
        assertEquals("minecraft:white_concrete", old.state(new Column(Band.CENTRE_LINE, true, false)));
        assertNotEquals(BuiltInPalettes.vanilla().snapshotId(), old.snapshotId());
    }

    @Test
    void thePaletteSetFindsCurrentPalettesAndSnapshots() throws PaletteException {
        Palette test = Palette.resolve(PaletteFile.read(file("", VANILLA_SURFACE), "test.json"), game(Set.of()));
        Palettes palettes = Palettes.of(List.of(BuiltInPalettes.vanilla(), test), List.of(BuiltInPalettes.provisional()));
        assertEquals(List.of(BuiltInPalettes.vanilla(), test), palettes.all());
        assertEquals("test", palettes.next("vanilla"));
        assertEquals("vanilla", palettes.next("test"), "wrapping round");
        assertEquals("vanilla", palettes.next("gone"));
        assertEquals(Optional.of(BuiltInPalettes.provisional()), palettes.snapshot(BuiltInPalettes.provisional().snapshotId()));
        assertEquals(Optional.of(test), palettes.snapshot(test.snapshotId()), "every current palette's snapshot is known");
        Palettes pinned = palettes.pinned(BuiltInPalettes.provisional());
        assertEquals(Optional.of(BuiltInPalettes.provisional()), pinned.byId("vanilla"), "as placed, before R-9");
        assertThrows(IllegalArgumentException.class, () -> Palettes.of(List.of(test, test), List.of()));
    }
}
