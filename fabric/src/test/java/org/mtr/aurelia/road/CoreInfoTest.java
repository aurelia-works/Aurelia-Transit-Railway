/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The core version that goes into every plan hash (task F-3). */
class CoreInfoTest {
    @Test
    void theVersionComesFromTheBuild() {
        String version = CoreInfo.version();
        assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), "a Stratos version, not a placeholder: " + version);
    }
}
