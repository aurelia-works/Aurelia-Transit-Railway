/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The journal: crash-safe edits. Task F-2+ (world-data-format §5).
 *
 * <p>{@link org.mtr.aurelia.road.journal.Journal} keeps the running log of every apply, undo and redo (a run) in
 * append-only segments under {@code <world>/aurelia_roads/operations/journal/}, written by its own thread, each section's
 * pre-image on disk before the section changes ({@link org.mtr.aurelia.road.journal.JournalCodec}).
 * {@link org.mtr.aurelia.road.journal.JournalScan} reads it back when a world opens, and
 * {@link org.mtr.aurelia.road.journal.Recovery} repairs the world so its blocks and track match the saved undo
 * history: runs the saved index lists are completed, the others rolled back.
 */
package org.mtr.aurelia.road.journal;
