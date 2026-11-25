/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;
import org.mtr.aurelia.road.road.palette.Palettes;

/** The road choices kept across restarts (task UI-1a; owner, 2026-10-15): layout, palette and node aid. */
class RoadChoicesTest {
    @Test
    void writtenAndReadBack() {
        RoadChoices choices = new RoadChoices("alley-7", "my_bricks", RoadPlanParams.NodeAids.NONE);
        String json = choices.toJson();
        assertEquals("{\n  \"layout\": \"alley-7\",\n  \"palette\": \"my_bricks\",\n  \"nodeAids\": \"none\"\n}\n", json);
        assertEquals(choices, RoadChoices.fromJson(json));
    }

    @Test
    void anythingMissingOrUnreadableIsTheDefault() {
        assertEquals(RoadChoices.DEFAULT, RoadChoices.fromJson(""));
        assertEquals(RoadChoices.DEFAULT, RoadChoices.fromJson("not json {"));
        assertEquals(RoadChoices.DEFAULT, RoadChoices.fromJson("[1, 2]"));
        assertEquals(new RoadChoices("residential-15", BuiltInPalettes.DEFAULT_ID, RoadPlanParams.NodeAids.TRACK),
                RoadChoices.fromJson("{\"layout\": \"residential-15\", \"palette\": 7, \"nodeAids\": \"teleporters\", \"extra\": true}"));
    }

    @Test
    void restoredIntoTheSettingsAndTakenBack() {
        RoadSettings settings = new RoadSettings(() -> true, () -> true);
        settings.restore(new RoadChoices("residential-17", "vanilla", RoadPlanParams.NodeAids.PREORIENTED));
        assertEquals(RoadProfiles.RESIDENTIAL_17, settings.profile());
        assertEquals(BuiltInPalettes.VANILLA_ID, settings.palette().id());
        assertEquals(RoadPlanParams.NodeAids.PREORIENTED, settings.nodeAids());
        assertEquals(new RoadChoices("residential-17", "vanilla", RoadPlanParams.NodeAids.PREORIENTED), settings.choices());
    }

    @Test
    void choicesThisGameCantGiveStandForTheDefaultsButAreKept() {
        // A palette from another server, a layout from a newer Stratos, MTR nodes in a game without MTR.
        RoadSettings settings = new RoadSettings(() -> false, () -> false, () -> Palettes.BUILT_IN);
        settings.setProfile(RoadProfiles.ALLEY_7);
        settings.restore(new RoadChoices("motorway-99", "their_palette", RoadPlanParams.NodeAids.PREORIENTED));
        assertEquals(RoadProfiles.ALLEY_7, settings.profile(), "an unknown layout leaves it as it was");
        assertEquals(BuiltInPalettes.DEFAULT_ID, settings.palette().id());
        assertEquals(RoadPlanParams.NodeAids.WOOL, settings.nodeAids());
        assertEquals(List.of("their_palette", RoadPlanParams.NodeAids.PREORIENTED),
                List.of(settings.choices().paletteId(), settings.choices().nodeAids()), "kept for a game that has them");
    }
}
