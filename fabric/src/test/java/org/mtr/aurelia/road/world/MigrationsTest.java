/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.spi.Storage;
import org.mtr.aurelia.road.undo.FakeStorage;

/**
 * The migration machinery (world-data-format §7; task F-5), with a test-only step {@code M(1→2)}: there are no real
 * steps yet, as {@code formatVersion} 1 is the first.
 */
class MigrationsTest {
    static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    static final String RECORD = "operations/records/a.json";

    final FakeStorage storage = new FakeStorage();
    final WorldMeta v1 = new WorldMeta(1, 3465, new UUID(5, 5), "stratos 0.1.0", "stratos 0.1.0", Instant.parse("2026-10-01T00:00:00Z"), List.of());

    /** Rewrites every record's "old" as "new"; running it again over its own output changes nothing. */
    static final class OneToTwo implements Migration {
        int runs;
        boolean crashAfterOneFile;

        @Override
        public int from() {
            return 1;
        }

        @Override
        public void migrate(Storage files) throws IOException {
            this.runs++;
            for (String file : files.list("operations/records")) {
                String text = new String(files.read(file), StandardCharsets.UTF_8);
                files.write(file, text.replace("old", "new").getBytes(StandardCharsets.UTF_8));
                if (this.crashAfterOneFile) {
                    throw new IOException("the server died (test)");
                }
            }
        }
    }

    void worldOfVersion1() throws IOException {
        this.storage.write(WorldData.META, this.v1.toJson().getBytes(StandardCharsets.UTF_8));
        this.storage.write(RECORD, "old".getBytes(StandardCharsets.UTF_8));
        this.storage.write("operations/records/b.json", "old".getBytes(StandardCharsets.UTF_8));
        this.storage.write("undo/index.json", "{}".getBytes(StandardCharsets.UTF_8));
        this.storage.write("undo/x/1/s.0.4.0.bin", new byte[] {1});
        this.storage.written.clear();
    }

    String text(String path) throws IOException {
        return new String(this.storage.read(path), StandardCharsets.UTF_8);
    }

    @Test
    void stepsRunAfterABackupAndMetaIsWrittenLast() throws IOException, JsonFormatException {
        worldOfVersion1();
        OneToTwo step = new OneToTwo();
        WorldMeta migrated = Migrations.run(this.storage, this.v1, 2, List.of(step), NOW, "stratos 0.2.0", 3465);
        assertEquals(2, migrated.formatVersion());
        String backup = "migrations/backup-v1-20261008T120000Z";
        assertEquals(List.of(new WorldMeta.Migrated(1, 2, NOW, backup)), migrated.migrations());
        assertEquals(migrated, WorldMeta.fromJson(text(WorldData.META)));
        assertEquals(WorldData.META, this.storage.written.get(this.storage.written.size() - 1), "meta.json last: the commit point");
        assertEquals("new", text(RECORD));
        assertEquals("old", text(backup + "/" + RECORD), "the backup has the old files");
        assertEquals(this.v1, WorldMeta.fromJson(text(backup + "/meta.json")));
        assertFalse(this.storage.exists(backup + "/undo"), "without the undo images");
        assertTrue(this.storage.exists(backup + "/" + Migrations.DONE));
        assertEquals(1, step.runs);
    }

    @Test
    void aCrashHalfwayLeavesTheOldVersionAndTheStepsRunAgain() throws IOException, JsonFormatException {
        worldOfVersion1();
        OneToTwo step = new OneToTwo();
        step.crashAfterOneFile = true;
        assertThrows(IOException.class, () -> Migrations.run(this.storage, this.v1, 2, List.of(step), NOW, "stratos 0.2.0", 3465));
        assertEquals(this.v1, WorldMeta.fromJson(text(WorldData.META)), "meta.json still says version 1");

        step.crashAfterOneFile = false;
        Instant later = NOW.plusSeconds(3600); // the server starts again an hour later
        WorldMeta migrated = Migrations.run(this.storage, this.v1, 2, List.of(step), later, "stratos 0.2.0", 3465);
        assertEquals(2, migrated.formatVersion());
        assertEquals("new", text(RECORD));
        assertEquals("new", text("operations/records/b.json"));
        String backup = "migrations/backup-v1-20261008T120000Z";
        assertEquals(List.of(new WorldMeta.Migrated(1, 2, later, backup)), migrated.migrations(), "the first run's backup is used again");
        assertEquals("old", text(backup + "/" + RECORD), "so it still has the files from before any step ran");
        assertFalse(this.storage.exists("migrations/backup-v1-20261008T130000Z"));
    }

    @Test
    void aMissingStepWritesNothing() throws IOException {
        worldOfVersion1();
        IOException e = assertThrows(IOException.class, () -> Migrations.run(this.storage, this.v1, 3, List.of(new OneToTwo()), NOW, "x", 3465));
        assertTrue(e.getMessage().contains("from formatVersion 2 to 3"), e.getMessage());
        assertEquals(List.of(), this.storage.written);
    }

    @Test
    void currentDataIsLeftAlone() throws IOException {
        worldOfVersion1();
        assertEquals(this.v1, Migrations.run(this.storage, this.v1, 1, List.of(new OneToTwo()), NOW, "x", 3465));
        assertEquals(List.of(), this.storage.written);
        assertEquals(List.of(), Migrations.ALL, "formatVersion 1 is the first: nothing to migrate from yet");
    }
}
