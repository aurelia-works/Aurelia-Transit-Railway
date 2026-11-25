/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The road groups' widths and track speeds (ADR-0012, ADR-0039, ADR-0040, ADR-0041). */
class RoadGroupTest {
    /** MTR 4.0.5's rail-connector speeds in km/h (mtr:rail_connector_<speed>). */
    private static final Set<Integer> MTR_SPEEDS = Set.of(20, 40, 60, 80, 100, 120, 140, 160, 200, 300);

    @Test
    void trackSpeedsAreAdr0040sAndMtrHasAConnectorForEach() {
        Map<RoadGroup, Integer> expected = new EnumMap<>(RoadGroup.class);
        expected.put(RoadGroup.ALLEY, 20);
        expected.put(RoadGroup.RESIDENTIAL, 40); // owner, 2026-10-07 (G-4+)
        expected.put(RoadGroup.CITY_STREET, 40);
        expected.put(RoadGroup.A_ROAD, 60);
        expected.put(RoadGroup.DUAL_CARRIAGEWAY, 80);
        expected.put(RoadGroup.MOTORWAY, 120);
        for (RoadGroup group : RoadGroup.values()) {
            assertEquals(expected.get(group), group.trackSpeed(), group.name());
            assertTrue(MTR_SPEEDS.contains(group.trackSpeed()), group + " " + group.trackSpeed());
        }
    }

    @Test
    void everyAllowedWidthIsOdd() {
        assertEquals(Set.of(15, 17), RoadGroup.RESIDENTIAL.widths());
        assertEquals(Set.of(7), RoadGroup.ALLEY.widths());
        for (RoadGroup group : RoadGroup.values()) {
            for (int width : group.widths()) {
                assertEquals(1, width % 2, group + " " + width + " (ADR-0041)");
            }
        }
    }
}
