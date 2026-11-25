/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.BlockLookup;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Family;
import org.mtr.aurelia.road.road.palette.Palette;
import org.mtr.aurelia.road.road.palette.PaletteException;
import org.mtr.aurelia.road.road.palette.PaletteFile;
import org.mtr.aurelia.road.road.palette.PaletteRole;
import org.mtr.aurelia.road.road.palette.Palettes;
import org.mtr.aurelia.road.tool.HudModel.Text;

/** The road tools' shared settings: the shape (task R-4), the node aid (R-6, T-2) and the layout (G-4+). */
class RoadSettingsTest {
    @Test
    void cityStreetFirstThenAnyBuiltInLayout() {
        RoadSettings settings = new RoadSettings();
        assertEquals(RoadProfiles.CITY_17, settings.profile());
        settings.setProfile(RoadProfiles.ALLEY_7);
        assertEquals(RoadProfiles.ALLEY_7, settings.profile());
        settings.toggleShape();
        assertEquals(RoadProfiles.ALLEY_7, settings.profile(), "the other settings leave it alone");
    }

    @Test
    void stratosUkFirstThenAnyPaletteTheServerSent() {
        // R-9 (owner, 2026-10-10): the hub's palette button steps through the server's palettes; B-1 (ADR-0044): Stratos UK first.
        List<Palettes> available = new ArrayList<>(List.of(Palettes.BUILT_IN));
        RoadSettings settings = new RoadSettings(() -> false, () -> false, () -> available.get(0));
        assertEquals(BuiltInPalettes.stratosUk(), settings.palette());
        settings.cyclePalette();
        assertEquals(BuiltInPalettes.vanilla(), settings.palette(), "then Vanilla");
        settings.cyclePalette();
        assertEquals(BuiltInPalettes.stratosUk(), settings.palette(), "wrapping round");
        assertFalse(settings.setPalette("granite"), "not a palette here");
        Palette other = BuiltInPalettes.provisional(); // stands in for another server's "vanilla"
        Palette granite = granite();
        available.set(0, Palettes.of(List.of(BuiltInPalettes.vanilla(), granite), List.of()));
        assertEquals(BuiltInPalettes.vanilla(), settings.palette(), "no Stratos UK on this server: Vanilla");
        assertTrue(settings.setPalette("granite"));
        assertEquals(granite, settings.palette());
        settings.cyclePalette();
        assertEquals(BuiltInPalettes.vanilla(), settings.palette(), "the next one, wrapping round");
        settings.cyclePalette();
        available.set(0, Palettes.of(List.of(other), List.of()));
        assertEquals(other, settings.palette(), "granite isn't on this server: Vanilla, whatever this server's is");
        assertEquals(Text.of("stratos.palette.stratos_uk"), RoadSettings.name(BuiltInPalettes.stratosUk()));
        assertEquals(Text.of("stratos.palette.vanilla"), RoadSettings.name(BuiltInPalettes.vanilla()));
        assertEquals(Text.of("stratos.palette.named", "Granite setts"), RoadSettings.name(granite));
        assertEquals(Text.of("stratos.palette.named", "gone"), RoadSettings.name("gone", Palettes.BUILT_IN));
    }

    private static Palette granite() {
        java.util.Map<PaletteRole, List<Family>> roles = new java.util.EnumMap<>(PaletteRole.class);
        for (PaletteRole role : PaletteRole.values()) {
            if (role.required()) {
                roles.put(role, List.of(Family.full("minecraft:granite")));
            }
        }
        try {
            return Palette.resolve(new PaletteFile("granite", java.util.Optional.of("Granite setts"), 3465, roles), BlockLookup.VANILLA);
        } catch (PaletteException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void quadraticFirstThenTwoRadiiWithItsRadius() {
        RoadSettings settings = new RoadSettings();
        assertEquals(RoadPlanParams.Vertical.QUADRATIC, settings.vertical());
        settings.toggleShape();
        assertEquals(new RoadPlanParams.Vertical(RoadPlanParams.Vertical.Shape.TWO_RADII, OptionalDouble.of(100)), settings.vertical());
        settings.toggleShape();
        assertEquals(RoadPlanParams.Vertical.QUADRATIC, settings.vertical(), "and back");
    }

    @Test
    void theKeysStepByTenAndStopAtTen() {
        RoadSettings settings = new RoadSettings();
        settings.stepRadius(true);
        assertEquals(110, settings.radius());
        for (int i = 0; i < 20; i++) {
            settings.stepRadius(false);
        }
        assertEquals(10, settings.radius(), "the keys stop at 10");
        settings.setRadius(4.5);
        settings.stepRadius(false);
        assertEquals(4.5, settings.radius(), "a typed radius below 10 stays");
        settings.stepRadius(true);
        assertEquals(14.5, settings.radius());
        settings.setRadius(RoadSettings.MAX_RADIUS);
        settings.stepRadius(true);
        assertEquals(RoadSettings.MAX_RADIUS, settings.radius(), "never past the most");
    }

    @Test
    void trackIsTheDefaultWhereItCanBeBuiltAndComesFirstInTheCycle() {
        boolean[] track = {true};
        RoadSettings settings = new RoadSettings(() -> true, () -> track[0]);
        assertTrue(settings.trackAvailable());
        assertEquals(RoadPlanParams.NodeAids.TRACK, settings.nodeAids(), "Track by default with Stratos Transit (owner, 2026-10-08)");
        List<RoadPlanParams.NodeAids> cycle = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            settings.cycleNodeAids();
            cycle.add(settings.nodeAids());
        }
        assertEquals(List.of(RoadPlanParams.NodeAids.WOOL, RoadPlanParams.NodeAids.PREORIENTED, RoadPlanParams.NodeAids.NONE,
                RoadPlanParams.NodeAids.TRACK), cycle, "Track, wool, MTR nodes, none (owner, 2026-10-08)");

        track[0] = false; // a server without Stratos Transit
        assertFalse(settings.trackAvailable());
        assertEquals(RoadPlanParams.NodeAids.WOOL, settings.nodeAids(), "Track stands for blue wool where it can't be built");
        cycle.clear();
        for (int i = 0; i < 3; i++) {
            settings.cycleNodeAids();
            cycle.add(settings.nodeAids());
        }
        assertEquals(List.of(RoadPlanParams.NodeAids.PREORIENTED, RoadPlanParams.NodeAids.NONE, RoadPlanParams.NodeAids.WOOL), cycle,
                "Track isn't in the cycle where it can't be built");
        track[0] = true;
        assertEquals(RoadPlanParams.NodeAids.WOOL, settings.nodeAids(), "a choice made is kept");

        RoadSettings noMtr = new RoadSettings(() -> false, () -> true);
        assertFalse(noMtr.trackAvailable(), "track needs MTR's node block too");
        assertEquals(RoadPlanParams.NodeAids.WOOL, noMtr.nodeAids());
    }

    @Test
    void theNodeAidCyclesWoolMtrNodesNoneAndSkipsMtrNodesWithoutMtr() {
        RoadSettings withMtr = new RoadSettings(() -> true);
        assertEquals(RoadPlanParams.NodeAids.WOOL, withMtr.nodeAids(), "blue wool by default without track (roads-v1 §9)");
        withMtr.cycleNodeAids();
        assertEquals(RoadPlanParams.NodeAids.PREORIENTED, withMtr.nodeAids());
        withMtr.cycleNodeAids();
        assertEquals(RoadPlanParams.NodeAids.NONE, withMtr.nodeAids());
        withMtr.cycleNodeAids();
        assertEquals(RoadPlanParams.NodeAids.WOOL, withMtr.nodeAids());

        RoadSettings without = new RoadSettings();
        without.cycleNodeAids();
        assertEquals(RoadPlanParams.NodeAids.NONE, without.nodeAids(), "no MTR: no MTR nodes");
        without.cycleNodeAids();
        assertEquals(RoadPlanParams.NodeAids.WOOL, without.nodeAids());
    }

    @Test
    void theToolbarChoosesAnAidThisGameCanUse() {
        // UI-1a (owner, 2026-10-15): the Nodes pop-up greys out Track and MTR nodes where they can't be used.
        boolean[] track = {false};
        RoadSettings settings = new RoadSettings(() -> true, () -> track[0]);
        assertFalse(settings.setNodeAids(RoadPlanParams.NodeAids.TRACK), "no Stratos Transit on the server");
        assertTrue(settings.setNodeAids(RoadPlanParams.NodeAids.PREORIENTED));
        assertEquals(RoadPlanParams.NodeAids.PREORIENTED, settings.nodeAids());
        track[0] = true;
        assertTrue(settings.setNodeAids(RoadPlanParams.NodeAids.TRACK));
        assertEquals(RoadPlanParams.NodeAids.TRACK, settings.nodeAids());
        assertTrue(settings.setNodeAids(RoadPlanParams.NodeAids.NONE));
        assertEquals(RoadPlanParams.NodeAids.NONE, settings.nodeAids());

        RoadSettings noMtr = new RoadSettings();
        assertFalse(noMtr.setNodeAids(RoadPlanParams.NodeAids.PREORIENTED), "no MTR: no MTR nodes");
        assertEquals(RoadPlanParams.NodeAids.WOOL, noMtr.nodeAids(), "unchanged");
    }

    @Test
    void aTypedRadiusIsAnyNumberInRangeToATenth() {
        RoadSettings settings = new RoadSettings();
        assertTrue(settings.setRadius(2500.04));
        assertEquals(2500, settings.radius());
        assertEquals(RoadPlanParams.Vertical.Shape.TWO_RADII, settings.shape(), "typing a radius chooses Two radii");
        assertTrue(settings.setRadius(0.1));
        assertFalse(settings.setRadius(0.04));
        assertFalse(settings.setRadius(100_000.1));
        assertFalse(settings.setRadius(Double.NaN));
        assertEquals(0.1, settings.radius(), "a refused value changes nothing");
        assertEquals("250", RoadSettings.format(250));
        assertEquals("12.5", RoadSettings.format(12.5));
    }
}
