/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What one operation was and did (spec world-data-format §6; task G-9): an apply, kept up to date by its undo and redo.
 * Its JSON form is {@link OperationJson}, under {@code formatVersion} 1 (F-5 saves it with the world).
 *
 * @param id            the operation's id (the one the client learns in {@code PlanAccepted})
 * @param dimension     where it happened, such as {@code minecraft:overworld} (F-5)
 * @param module        the module that planned it, such as {@code roads}
 * @param tool          the tool, such as {@code STRAIGHT}
 * @param params        the module's parameters as JSON text ({@code PlanModule.paramsToJson}), enough to plan it again
 * @param planHash      the plan hash, 64 hex digits
 * @param coreVersion   the core version that planned it
 * @param author        who made it: a player's UUID, or another owner; stays in the world, never sent anywhere
 * @param time          when its blocks finished
 * @param status        where it stands now
 * @param undoAvailable false once the undo store's cap has dropped its images
 * @param sections      every chunk section it changed, with the hash of what it left there ({@link SectionHashes})
 * @param planned       the side effects its plan asked for
 * @param owned         the side effects it made that are still there (ADR-0040: never a player's)
 * @param inherited     R-7: the side effects of its plan that the road it rebuilt already had, which it kept: not its to
 *                      remove on undo, but the road's now
 * @param removed       R-7: the side effects of the road it deleted or rebuilt that it removed: undo makes them again,
 *                      redo removes them again
 * @param palettes      R-9: the snapshot ids of the palettes its plan used ({@code vanilla@1a2b3c4d5e6f}), which the world
 *                      keeps under {@code palettes/}; empty for an operation planned without one (before R-9, a road's
 *                      was the provisional vanilla palette)
 */
public record OperationRecord(UUID id, String dimension, String module, String tool, String params, String planHash, String coreVersion, String author, Instant time,
        Status status, boolean undoAvailable, List<SectionHash> sections, List<SideEffect> planned, List<SideEffect> owned, List<SideEffect> inherited,
        List<SideEffect> removed, List<String> palettes) {
    /** Where an operation stands. */
    public enum Status {
        /** Every block written. */
        APPLIED,
        /** Stopped part of the way: some blocks written. */
        PARTLY_APPLIED,
        /** Every block put back. */
        UNDONE,
        /** An undo stopped part of the way. */
        PARTLY_UNDONE,
        /** Nothing written. */
        FAILED,
        /** Rolled back by the repair after a crash (F-2+): it wasn't in the last save, so its blocks were put back. */
        ROLLED_BACK
    }

    /** One changed chunk section (section coordinates) and the hash of its post-image, 64 hex digits. */
    public record SectionHash(int x, int y, int z, String hash) {
        public SectionHash {
            Objects.requireNonNull(hash, "hash");
        }
    }

    /** What the caller knows before an operation runs: everything but its outcome. */
    public record Draft(String module, String tool, String params, String planHash, List<SideEffect> sideEffects, List<String> palettes) {
        /** For operations no module planned (the GameTests, older callers): no parameters, no side effects. */
        public static final Draft NONE = new Draft("none", "none", "{}", "", List.of());

        public Draft {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(tool, "tool");
            Objects.requireNonNull(params, "params");
            Objects.requireNonNull(planHash, "planHash");
            sideEffects = List.copyOf(sideEffects);
            palettes = List.copyOf(palettes);
        }

        /** A draft that names no palette. */
        public Draft(String module, String tool, String params, String planHash, List<SideEffect> sideEffects) {
            this(module, tool, params, planHash, sideEffects, List.of());
        }
    }

    public OperationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(params, "params");
        Objects.requireNonNull(planHash, "planHash");
        Objects.requireNonNull(coreVersion, "coreVersion");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(status, "status");
        sections = List.copyOf(sections);
        planned = List.copyOf(planned);
        owned = List.copyOf(owned);
        inherited = List.copyOf(inherited);
        removed = List.copyOf(removed);
        palettes = List.copyOf(palettes);
    }

    /** A record that names no palette (R-9). */
    public OperationRecord(UUID id, String dimension, String module, String tool, String params, String planHash, String coreVersion, String author,
            Instant time, Status status, boolean undoAvailable, List<SectionHash> sections, List<SideEffect> planned, List<SideEffect> owned,
            List<SideEffect> inherited, List<SideEffect> removed) {
        this(id, dimension, module, tool, params, planHash, coreVersion, author, time, status, undoAvailable, sections, planned, owned, inherited, removed,
                List.of());
    }

    /** A record that inherited and removed nothing (every operation but a delete or a rebuild, R-7). */
    public OperationRecord(UUID id, String dimension, String module, String tool, String params, String planHash, String coreVersion, String author,
            Instant time, Status status, boolean undoAvailable, List<SectionHash> sections, List<SideEffect> planned, List<SideEffect> owned) {
        this(id, dimension, module, tool, params, planHash, coreVersion, author, time, status, undoAvailable, sections, planned, owned, List.of(), List.of(),
                List.of());
    }

    /** The side effects the road it built has now (R-7): those it made and those it inherited. */
    public List<SideEffect> roadEffects() {
        List<SideEffect> all = new java.util.ArrayList<>(this.owned);
        this.inherited.stream().filter(effect -> !all.contains(effect)).forEach(all::add);
        return all;
    }

    public OperationRecord withStatus(Status status) {
        return new OperationRecord(this.id, this.dimension, this.module, this.tool, this.params, this.planHash, this.coreVersion, this.author, this.time, status,
                this.undoAvailable, this.sections, this.planned, this.owned, this.inherited, this.removed, this.palettes);
    }

    public OperationRecord withOwned(List<SideEffect> owned) {
        return new OperationRecord(this.id, this.dimension, this.module, this.tool, this.params, this.planHash, this.coreVersion, this.author, this.time, this.status,
                this.undoAvailable, this.sections, this.planned, owned, this.inherited, this.removed, this.palettes);
    }

    /** The same record with the side effects it made, inherited and removed (R-7). */
    public OperationRecord withTrack(List<SideEffect> owned, List<SideEffect> inherited, List<SideEffect> removed) {
        return new OperationRecord(this.id, this.dimension, this.module, this.tool, this.params, this.planHash, this.coreVersion, this.author, this.time, this.status,
                this.undoAvailable, this.sections, this.planned, owned, inherited, removed, this.palettes);
    }

    public OperationRecord withUndoAvailable(boolean undoAvailable) {
        return new OperationRecord(this.id, this.dimension, this.module, this.tool, this.params, this.planHash, this.coreVersion, this.author, this.time, this.status,
                undoAvailable, this.sections, this.planned, this.owned, this.inherited, this.removed, this.palettes);
    }
}
