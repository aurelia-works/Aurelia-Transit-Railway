/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.ManualHelper;
import org.mtr.aurelia.road.undo.Operations;

/** The journal's writing (F-2+): group flushes on its own thread, and a disk that fails. */
class JournalTest {
    final FakeStorage storage = new FakeStorage();
    final ManualHelper helper = new ManualHelper();
    final List<String> log = new ArrayList<>();

    Journal journal() {
        return new Journal(this.storage, this.helper, () -> Instant.parse("2026-10-09T12:00:00Z"), this.log::add, JournalScan.empty());
    }

    static List<BlockChange> changes(int x) {
        return List.of(new BlockChange(x, 64, 0, "minecraft:air", "minecraft:stone"));
    }

    @Test
    void recordsQueuedTogetherAreFlushedOnceOnTheJournalThread() {
        Journal journal = journal();
        Journal.Run run = journal.begin(Operations.Kind.APPLY, new UUID(7, 1), Operations.OVERWORLD, "alice", "");
        long first = run.preimage(new BlockPlan.SectionPos(0, 4, 0), 3465, changes(1));
        long second = run.preimage(new BlockPlan.SectionPos(1, 4, 0), 3465, changes(17));
        assertFalse(run.onDisk(first), "nothing is written on the calling thread");
        assertEquals(1, this.helper.journal.size(), "one drain queued for all three records");
        this.helper.runJournal();
        assertTrue(run.onDisk(second));
        assertEquals(1, this.storage.logFlushes, "one flush for the lot");
        assertEquals(3L, journal.lastTicket());
    }

    @Test
    void aWorldSaveRightAfterSomeRecordsFlushesThemIntoTheOldSegmentFirst() throws IOException {
        Journal journal = journal();
        Journal.Run run = journal.begin(Operations.Kind.APPLY, new UUID(7, 1), Operations.OVERWORLD, "alice", "");
        long ticket = run.preimage(new BlockPlan.SectionPos(0, 4, 0), 3465, changes(1));
        journal.worldSaved(); // a new segment, queued behind the records, drained with them
        this.helper.runJournal();
        assertTrue(run.onDisk(ticket));
        assertEquals(1, this.storage.logFlushes, "flushed before the segment closed");
        run.sectionDone(new BlockPlan.SectionPos(0, 4, 0), new long[64]);
        this.helper.runJournal();
        assertEquals(List.of(Journal.path(0), Journal.path(1)), this.storage.list(Journal.FOLDER));
        this.storage.crash(0); // nothing unflushed is lost
        assertEquals(2, JournalScan.read(this.storage).segments().get(0).size() + JournalScan.read(this.storage).segments().get(1).size());
        assertEquals(1, JournalScan.read(this.storage).runs().get(0).preimages().size());
    }

    @Test
    void aRunCaughtByAFailingDiskNeverTrustsItsRecordsAndANewRunTriesAgain() throws IOException {
        Journal journal = journal();
        Journal.Run caught = journal.begin(Operations.Kind.APPLY, new UUID(7, 1), Operations.OVERWORLD, "alice", "");
        this.helper.runJournal(); // its BEGIN is on disk
        this.storage.failWrites = true;
        long lost = caught.preimage(new BlockPlan.SectionPos(0, 4, 0), 3465, changes(1));
        this.helper.runJournal();
        assertTrue(caught.failed());
        assertFalse(caught.onDisk(lost));

        this.storage.failWrites = false;
        Journal.Run next = journal.begin(Operations.Kind.APPLY, new UUID(7, 2), Operations.OVERWORLD, "alice", "");
        long ticket = next.preimage(new BlockPlan.SectionPos(0, 4, 0), 3465, changes(2));
        this.helper.runJournal();
        assertFalse(next.failed(), "a new run tries again");
        assertTrue(next.onDisk(ticket));
        assertTrue(caught.failed(), "the caught run stays failed");
        assertFalse(caught.onDisk(lost), "its lost pre-image never counts as on disk, though later records are");
        assertEquals(List.of(Journal.path(0), Journal.path(1)), this.storage.list(Journal.FOLDER), "in a new segment");
        assertTrue(this.log.stream().anyMatch(line -> line.contains("couldn't be written")), String.join("\n", this.log));

        JournalScan scan = JournalScan.read(this.storage);
        assertEquals(2, scan.runs().size());
        assertEquals(1, scan.runs().get(1).preimages().size());
    }
}
