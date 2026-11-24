/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * Undo: the pre-/post-image model. Tasks F-2 and G-9; the journal is {@code core.journal} (F-2+).
 *
 * <p>From F-2: {@link org.mtr.aurelia.road.undo.Operations} keeps each player's undo and redo history on top of
 * the apply engine; {@link org.mtr.aurelia.road.undo.UndoStore} holds the compressed, sparse per-section images
 * ({@link org.mtr.aurelia.road.undo.UndoCodec}) in memory up to a budget, spills the rest to
 * {@code <world>/stratos/undo/} and enforces the cap (200 operations or 1 GB). Session-only until G-9/F-5.
 */
package org.mtr.aurelia.road.undo;
