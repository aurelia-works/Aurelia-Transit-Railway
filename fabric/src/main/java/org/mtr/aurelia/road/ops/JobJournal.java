/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.ops;

import java.util.List;

/**
 * Where a job's journal records go (F-2+; world-data-format §5): the job hands over each section's pre-image (the
 * blocks it may change there, as found and as planned) while it checks the plan, and writes a section only once that
 * pre-image is on disk. Server thread only, but for {@link #onDisk} and {@link #failed}, which any thread may ask.
 */
public interface JobJournal {
    /**
     * Queues a pre-image part of {@code section}: the blocks the job may change there, each with its state and data as
     * found ({@code before}) and as planned ({@code after}). Returns a ticket for {@link #onDisk}.
     */
    long preimage(BlockPlan.SectionPos section, int dataVersion, List<BlockChange> changes);

    /** Whether the record with {@code ticket}, and every record before it, is on disk. */
    boolean onDisk(long ticket);

    /** Whether the journal can't be written any more (a full disk): the job must stop before writing more. */
    boolean failed();

    /** Notes which blocks of {@code section} the job has written: a bit per block ({@code y << 8 | z << 4 | x}), 64 longs. */
    void sectionDone(BlockPlan.SectionPos section, long[] written);
}
