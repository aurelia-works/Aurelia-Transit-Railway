/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.json.JsonFormatException;

/** The server's limits file, {@code config/stratos/server.json} (F-6). */
class EditorLimitsTest {
    @Test
    void theDefaultsAreWrittenAndReadBack() throws JsonFormatException {
        String text = EditorLimits.DEFAULTS.toJson();
        assertTrue(text.contains("\"maxBlocksPerOperation\": 250000"), text);
        assertEquals(EditorLimits.DEFAULTS, EditorLimits.fromJson(text));
        assertEquals(new EditorLimits(250_000, 1_000_000, 2, 5), EditorLimits.DEFAULTS);
    }

    @Test
    void aMissingKeyKeepsItsDefaultAndZeroMeansNone() throws JsonFormatException {
        assertEquals(new EditorLimits(1000, 1_000_000, 0, 5), EditorLimits.fromJson("{\"maxBlocksPerOperation\": 1000, \"planRequestsPerSecond\": 0}"));
        assertEquals(EditorLimits.DEFAULTS, EditorLimits.fromJson("{}"));
    }

    @Test
    void badFilesAreRefusedWithTheReason() {
        assertTrue(assertThrows(JsonFormatException.class, () -> EditorLimits.fromJson("{\"maxBlocksPerOperation\": -1}")).getMessage()
                .contains("negative"));
        assertTrue(assertThrows(JsonFormatException.class, () -> EditorLimits.fromJson("{\"planRequestBurst\": 0}")).getMessage()
                .contains("planRequestBurst"));
        assertThrows(JsonFormatException.class, () -> EditorLimits.fromJson("{\"maxBlocks\": 10}"), "an unknown key");
        assertThrows(JsonFormatException.class, () -> EditorLimits.fromJson("{\"maxBlocksPerOperation\": 1.5}"));
        assertThrows(JsonFormatException.class, () -> EditorLimits.fromJson("not json"));
    }
}
