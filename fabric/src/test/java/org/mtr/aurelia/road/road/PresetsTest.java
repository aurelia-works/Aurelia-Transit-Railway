/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.road.Presets.Limits;
import org.mtr.aurelia.road.road.Presets.Preset;

/** The preset limits (spec roads-v1 §3) and exact grades. */
class PresetsTest {
    @Test
    void cityStreetLimits() {
        assertEquals(new Limits(30, Grade.oneIn(12)), Presets.limits(RoadGroup.CITY_STREET, Preset.GAME_SCALE));
        // UK-faithful: 127 (owner, 2026-10-04); DMRB gives no grade, so the game-scale 1:12 applies.
        assertEquals(new Limits(127, Grade.oneIn(12)), Presets.limits(RoadGroup.CITY_STREET, Preset.UK_FAITHFUL));
    }

    @Test
    void residentialAndAlleyLimits() {
        // roads-v1 §3 (G-4+): residential 15 and 1:10, UK-faithful 90 with the game-scale grade; alley 8 and 1:6 under both,
        // as DMRB gives no alley values ("—" rule, G-4).
        assertEquals(new Limits(15, Grade.oneIn(10)), Presets.limits(RoadGroup.RESIDENTIAL, Preset.GAME_SCALE));
        assertEquals(new Limits(90, Grade.oneIn(10)), Presets.limits(RoadGroup.RESIDENTIAL, Preset.UK_FAITHFUL));
        assertEquals(new Limits(8, Grade.oneIn(6)), Presets.limits(RoadGroup.ALLEY, Preset.GAME_SCALE));
        assertEquals(new Limits(8, Grade.oneIn(6)), Presets.limits(RoadGroup.ALLEY, Preset.UK_FAITHFUL));
    }

    @Test
    void everyBuiltInProfileHasLimitsUnderBothPresets() {
        for (RoadProfile profile : RoadProfiles.all()) {
            for (Preset preset : Preset.values()) {
                assertTrue(Presets.limits(profile.group(), preset).minInnerLaneRadius() > 0, profile.id() + " " + preset);
            }
        }
    }

    @Test
    void motorwayIsNotDefinedYet() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Presets.limits(RoadGroup.MOTORWAY, Preset.GAME_SCALE));
        assertTrue(e.getMessage().contains("G-4+"), e.getMessage());
    }

    @Test
    void gradesAreExactFractions() {
        assertEquals(new Grade(1, 12), Grade.oneIn(12));
        assertEquals(new Grade(3, 50), Grade.percent(6));
        assertEquals(Grade.percent(6), new Grade(6, 100), "kept in lowest terms");
        assertNotEquals(Grade.oneIn(12), Grade.percent(8));
        assertEquals(1.0 / 12, Grade.oneIn(12).value());
        // A rise of 1 over 12 blocks is exactly 1:12, not steeper; 1 over 11.99 is steeper.
        assertFalse(Grade.oneIn(12).isExceededBy(1, 12));
        assertTrue(Grade.oneIn(12).isExceededBy(1, 11.99));
        assertFalse(Grade.oneIn(12).isExceededBy(5, 60));
        assertThrows(IllegalArgumentException.class, () -> new Grade(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Grade(-1, 12));
    }
}
