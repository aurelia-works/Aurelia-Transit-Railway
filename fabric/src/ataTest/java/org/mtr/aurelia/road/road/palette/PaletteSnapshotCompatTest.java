package org.mtr.aurelia.road.road.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Saved worlds name the palette snapshot each road was placed with, and plan it again with that snapshot (R-9). P11 added
 * the gully and dropped kerb roles: the Stratos UK palette as it was before must keep its snapshot id and place neither,
 * so old roads plan exactly as placed.
 */
class PaletteSnapshotCompatTest {
    @Test
    void oldStratosUkKeepsItsSnapshotAndPlacesNoDetails() throws Exception {
        Palette before = Palette.resolve(PaletteFile.read(read("/road/palette/stratos-uk-before-p11.json"), "stratos-uk-before-p11.json"),
                BuiltInPalettes.STRATOS);
        assertEquals("stratos_uk@eed470415e0a", before.snapshotId());
        assertFalse(before.gullies());
        assertFalse(before.droppedKerbs());
        assertEquals("vanilla@d7f86f8ae7ba", BuiltInPalettes.vanilla().snapshotId());
        assertFalse(BuiltInPalettes.vanilla().gullies());

        Palette now = BuiltInPalettes.stratosUk();
        assertNotEquals(before.snapshotId(), now.snapshotId());
        assertTrue(now.gullies());
        assertTrue(now.droppedKerbs());
        // Read back from its own snapshot, as a world keeps it, it is the same palette.
        assertEquals(now.snapshotId(), Palette.read(now.snapshotJson(), "snapshot").snapshotId());
    }

    private static String read(String name) throws IOException {
        try (InputStream in = PaletteSnapshotCompatTest.class.getResourceAsStream(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
