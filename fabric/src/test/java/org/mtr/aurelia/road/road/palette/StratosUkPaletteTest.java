/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.raster.Column;
import org.mtr.aurelia.road.road.raster.KerbEdge;

/** B-1: the Stratos UK palette, and the raise and edge its footway and kerb have (ADR-0044's real kerbs); B-2: its line blocks. */
class StratosUkPaletteTest {
    private static final Column LANE = new Column(Band.LANE, false, false);
    private static final Column FOOTWAY = new Column(Band.FOOTWAY, false, false);
    private static final Column KERB = new Column(Band.KERB, false, false);
    private static final Column GUTTER = new Column(Band.GUTTER, false, false);
    private static final Column DASH = new Column(Band.CENTRE_LINE, true, false);

    @Test
    void stratosUkIsTheDefaultAndComesFirst() {
        assertEquals(BuiltInPalettes.STRATOS_UK_ID, BuiltInPalettes.DEFAULT_ID);
        assertEquals(List.of(BuiltInPalettes.STRATOS_UK_ID, BuiltInPalettes.VANILLA_ID), Palettes.BUILT_IN.all().stream().map(Palette::id).toList());
        assertTrue(BuiltInPalettes.isBuiltIn(BuiltInPalettes.STRATOS_UK_ID));
        assertEquals(List.of(BuiltInPalettes.STRATOS_UK_ID, BuiltInPalettes.VANILLA_ID), BuiltInPalettes.files().stream().map(PaletteFile::id).toList());
    }

    @Test
    void itsBlocksAreStratossOwnInEighths() {
        Palette uk = BuiltInPalettes.stratosUk();
        assertEquals(8, uk.steps(), "every surface role layered");
        assertEquals("stratos:asphalt[layers=8]", uk.state(LANE));
        assertEquals("stratos:asphalt[layers=3]", uk.partial(LANE, 3));
        assertEquals("stratos:white_line[chord=0,layers=8]", uk.state(DASH), "B-2: the white line block, the file's chord where a column has none");
        assertEquals("stratos:asphalt[layers=8]", uk.state(GUTTER), "UK gutters are asphalt (research 13)");
        assertEquals("stratos:footway_flags[layers=5]", uk.partial(FOOTWAY, 5));
        assertEquals("minecraft:stone", uk.foundation());
        assertEquals("minecraft:blue_wool", uk.nodeMarker());
    }

    @Test
    void theFootwayAndKerbStandOneLayerUp() {
        Palette uk = BuiltInPalettes.stratosUk();
        assertEquals(1, uk.raise(FOOTWAY));
        assertEquals(1, uk.raise(KERB));
        assertEquals(0, uk.raise(LANE));
        assertEquals(0, uk.raise(GUTTER));
        assertEquals(0, BuiltInPalettes.vanilla().raise(FOOTWAY), "Vanilla's footway stays level");
    }

    @Test
    void itsKerbTurnsToFaceTheRoad() {
        Palette uk = BuiltInPalettes.stratosUk();
        assertTrue(uk.kerbEdges());
        assertFalse(BuiltInPalettes.vanilla().kerbEdges());
        Column outer = KERB.with(Band.KERB, Optional.of(new KerbEdge(KerbEdge.Facing.EAST, KerbEdge.Shape.OUTER)));
        assertEquals("stratos:kerb[facing=east,layers=8,shape=outer]", uk.state(outer));
        assertEquals("stratos:kerb[facing=east,layers=2,shape=outer]", uk.partial(outer, 2));
        assertEquals("stratos:kerb[facing=north,layers=8,shape=straight]", uk.state(KERB), "the file's values where a column has no edge");
        assertEquals("minecraft:polished_andesite", BuiltInPalettes.vanilla().state(outer), "Vanilla's kerb has no edge");
    }

    @Test
    void itsWhiteMarkingIsALineBlockDrawnAlongEachChord() {
        // B-2 (ADR-0046): the line's chord comes from the plan (Rasteriser.withLines); the palette only writes it in.
        Palette uk = BuiltInPalettes.stratosUk();
        assertTrue(uk.lines(LineMarking.Colour.WHITE));
        assertFalse(uk.lines(LineMarking.Colour.YELLOW), "no yellow until a layout paints one");
        assertFalse(BuiltInPalettes.vanilla().lines(LineMarking.Colour.WHITE), "Vanilla keeps whole-block quartz dashes");
        Column painted = DASH.painted(DASH.paint(), OptionalInt.of(4321));
        assertEquals("stratos:white_line[chord=4321,layers=8]", uk.state(painted));
        assertEquals("stratos:white_line[chord=4321,layers=3]", uk.partial(painted, 3));
        assertEquals("stratos:asphalt[layers=8]", uk.under(painted), "the block hidden under a line's layer is plain asphalt: paint is only on top");
        assertEquals("stratos:kerb[facing=north,layers=8,shape=straight]", uk.under(KERB), "anything else is its own full block under its layer");
        assertEquals("minecraft:smooth_quartz", BuiltInPalettes.vanilla().state(painted), "Vanilla's dash has no chord");
        assertTrue(uk.snapshotJson().contains("\"line\":true"));
        assertFalse(BuiltInPalettes.vanilla().snapshotJson().contains("line"));
    }

    @Test
    void withoutStratossBlocksEachRoleFallsBackToVanilla() throws PaletteException {
        PaletteFile file = BuiltInPalettes.files().get(0);
        Palette vanillaOnly = Palette.resolve(file, BlockLookup.VANILLA);
        assertEquals(2, vanillaOnly.steps());
        assertEquals("minecraft:polished_deepslate", vanillaOnly.state(LANE));
        assertEquals(0, vanillaOnly.raise(FOOTWAY), "an eighth can't be shown in half steps");
        assertFalse(vanillaOnly.kerbEdges());
    }

    @Test
    void raiseAndEdgeAreWrittenOnlyWhereSet() throws PaletteException {
        PaletteFile uk = BuiltInPalettes.files().get(0);
        String json = StrictJson.write(uk.toJson(true));
        assertTrue(json.contains("\"raise\": 1"));
        assertTrue(json.contains("\"edge\": true"));
        assertEquals(uk, PaletteFile.read(json, "again.json"), "written and read back");
        String vanilla = BuiltInPalettes.vanilla().snapshotJson();
        assertFalse(vanilla.contains("raise") || vanilla.contains("edge"), "R-9's snapshots are unchanged");
        assertTrue(BuiltInPalettes.stratosUk().snapshotJson().contains("\"edge\":true"));
    }

    @Test
    void raiseAndEdgeAreRefusedWhereTheyMeanNothing() {
        String foundation = ukWith("{ \"full\": \"minecraft:stone\" }", "{ \"full\": \"minecraft:stone\", \"raise\": 1 }");
        assertTrue(refusal(foundation).contains("roles.foundation is a full block only"));
        String edgedFootway = ukWith("{ \"full\": \"stratos:footway_flags[layers=8]\", \"layers\": \"stratos:footway_flags[layers={layers}]\", \"raise\": 1 }",
                "{ \"full\": \"stratos:kerb[facing=north,layers=8,shape=straight]\", \"layers\": \"stratos:kerb[facing=north,layers={layers},shape=straight]\", \"edge\": true }");
        assertTrue(refusal(edgedFootway).contains("only the kerb, gully and dropped kerb turn to face the road"));
        String noShape = ukWith("\"full\": \"stratos:kerb[facing=north,layers=8,shape=straight]\"", "\"full\": \"stratos:kerb[facing=north,layers=8]\"");
        assertTrue(refusal(noShape).contains("an edge kerb's states have facing and shape"));
        assertTrue(refusal(ukWith("\"raise\": 1, \"edge\"", "\"raise\": 9, \"edge\"")).contains("raise is in eighths of a block, 0 to 8"));
        String linedSurface = ukWith("{ \"full\": \"stratos:asphalt[layers=8]\", \"layers\": \"stratos:asphalt[layers={layers}]\" }",
                "{ \"full\": \"stratos:white_line[chord=0,layers=8]\", \"layers\": \"stratos:white_line[chord=0,layers={layers}]\", \"line\": true }");
        assertTrue(refusal(linedSurface).contains("only the markings are painted along a chord"));
        String noChord = ukWith("\"full\": \"stratos:white_line[chord=0,layers=8]\"", "\"full\": \"stratos:white_line[layers=8]\"");
        assertTrue(refusal(noChord).contains("a line family's states have chord"));
    }

    @Test
    void anEdgeTheGameCantWriteRefusesThePalette() throws PaletteException {
        PaletteFile uk = BuiltInPalettes.files().get(0);
        BlockLookup noOuterCorners = new BlockLookup() {
            @Override
            public boolean has(String block) {
                return true;
            }

            @Override
            public String canonical(String state) {
                if (state.contains("shape=outer")) {
                    throw new IllegalArgumentException("the game can't read " + state);
                }
                return state;
            }
        };
        assertTrue(assertThrows(PaletteException.class, () -> Palette.resolve(uk, noOuterCorners)).getMessage().contains("shape=outer"));
    }

    /** The Stratos UK file as shipped, with {@code from} replaced by {@code to}. */
    private static String ukWith(String from, String to) {
        String json;
        try (InputStream in = BuiltInPalettes.class.getResourceAsStream("stratos-uk.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String changed = json.replace(from, to);
        if (changed.equals(json)) {
            throw new AssertionError(from + " isn't in the Stratos UK file");
        }
        return changed;
    }

    private static String refusal(String text) {
        return assertThrows(PaletteException.class, () -> PaletteFile.read(text, "uk.json")).getMessage();
    }
}
