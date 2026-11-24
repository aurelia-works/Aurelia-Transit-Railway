/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What {@code undo/index.json} holds (world-data-format §6; task F-5): the stored operations, oldest first, and every
 * player's undo and redo history in each dimension. Its JSON form is {@link UndoIndexJson}.
 *
 * @param operations the undo store's operations, oldest first, their images on disk ({@link UndoStore#flush})
 * @param histories  one per player and dimension with anything to undo or redo
 * @param journal    the journal's runs whose results this index holds and which the journal still keeps (F-2+): after a
 *                   crash these are completed, the others rolled back ({@code journal.Recovery})
 */
public record UndoIndex(List<UndoStore.Saved> operations, List<History> histories, List<UUID> journal) {
    public static final UndoIndex EMPTY = new UndoIndex(List.of(), List.of(), List.of());

    public UndoIndex {
        operations = List.copyOf(operations);
        histories = List.copyOf(histories);
        journal = List.copyOf(journal);
    }

    /** An index listing no journal runs. */
    public UndoIndex(List<UndoStore.Saved> operations, List<History> histories) {
        this(operations, histories, List.of());
    }

    /**
     * One player's history in one dimension: the operations they can undo and redo, each list newest first (the order
     * {@code -} and {@code =} take them in).
     */
    public record History(String dimension, String owner, List<UUID> undo, List<UUID> redo) {
        public History {
            Objects.requireNonNull(dimension, "dimension");
            Objects.requireNonNull(owner, "owner");
            undo = List.copyOf(undo);
            redo = List.copyOf(redo);
        }
    }
}
