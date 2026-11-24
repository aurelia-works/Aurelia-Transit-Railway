/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.journal;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.mtr.aurelia.road.ops.BlockChange;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.SideEffect;

/**
 * One record of the journal (world-data-format §5; task F-2+), about one <b>run</b>: an apply, undo or redo as the
 * journal sees it, with its own id. {@link JournalCodec} writes and reads them.
 */
public sealed interface JournalRecord {
    /** The run it is about. */
    UUID run();

    /** How a run's job ended. */
    enum EndStatus {
        DONE,
        FAILED,
        /** The server stopped it. */
        STOPPED
    }

    /** What a run asked the side-effect writer to do. */
    enum TrackAction {
        MAKE,
        REMOVE
    }

    /** What a repair did with a run. */
    enum Outcome {
        ROLLED_BACK,
        COMPLETED,
        NEEDS_ATTENTION
    }

    /**
     * Before any block changes: the run's kind, the operation it is about (an apply's own id, an undo's or redo's target),
     * its dimension, owner and plan hash ({@code ""} for none), and when it began (milliseconds since 1970, UTC).
     */
    record Begin(UUID run, Operations.Kind kind, UUID operation, String dimension, String owner, String planHash, long time) implements JournalRecord {
        public Begin {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(planHash, "planHash");
        }
    }

    /**
     * A pre-image part of one section: the blocks the run may change there, each with its state and data as found
     * ({@code before}) and as planned ({@code after}). Written to disk before any of them changes.
     */
    record Preimage(UUID run, BlockPlan.SectionPos section, int dataVersion, List<BlockChange> changes) implements JournalRecord {
        public Preimage {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(section, "section");
            changes = List.copyOf(changes);
        }
    }

    /** Which blocks of a section the run has written so far: a bit per block ({@code y << 8 | z << 4 | x}), 64 longs. */
    record SectionDone(UUID run, BlockPlan.SectionPos section, long[] written) implements JournalRecord {
        public SectionDone {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(section, "section");
            if (written.length != 64) {
                throw new IllegalArgumentException("A section has 4,096 blocks: 64 longs, not " + written.length);
            }
            written = written.clone();
        }

        @Override
        public long[] written() {
            return this.written.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof SectionDone done && done.run.equals(this.run) && done.section.equals(this.section)
                    && java.util.Arrays.equals(done.written, this.written);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.run, this.section, java.util.Arrays.hashCode(this.written));
        }
    }

    /** The run's job ended: how, its failure ({@code ""} for none) and how many blocks it wrote. */
    record End(UUID run, EndStatus status, String failure, int blocksWritten) implements JournalRecord {
        public End {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failure, "failure");
        }
    }

    /** Before the side-effect writer is asked: what to make or remove (G-9, T-2: track). */
    record Track(UUID run, TrackAction action, List<SideEffect> effects) implements JournalRecord {
        public Track {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(action, "action");
            effects = List.copyOf(effects);
        }
    }

    /** The side-effect writer's answers, one per effect, in the order asked. */
    record TrackDone(UUID run, TrackAction action, List<SideEffect> effects, List<SideEffectWriter.Outcome> outcomes) implements JournalRecord {
        public TrackDone {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(action, "action");
            effects = List.copyOf(effects);
            outcomes = List.copyOf(outcomes);
            if (effects.size() != outcomes.size()) {
                throw new IllegalArgumentException("One answer per side effect: " + effects.size() + " effects, " + outcomes.size() + " answers");
            }
        }
    }

    /** A repair after a crash dealt with the run: rolled back, completed, or not fully ({@code detail} says what). */
    record Recovered(UUID run, Outcome outcome, String detail) implements JournalRecord {
        public Recovered {
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /** The run is safely saved by Stratos and by Minecraft: no repair ever needs it again. */
    record Retired(UUID run) implements JournalRecord {
        public Retired {
            Objects.requireNonNull(run, "run");
        }
    }
}
