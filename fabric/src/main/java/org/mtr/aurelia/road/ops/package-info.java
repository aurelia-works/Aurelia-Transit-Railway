/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * Operations: block plans (per chunk section, the sorted list of positions and new states), operations
 * and plan hashing. Tasks G-6 and G-9.
 *
 * <p>From G-6: {@link org.mtr.aurelia.road.ops.BlockPlan} (canonical order, refuses double claims) and
 * {@link org.mtr.aurelia.road.ops.PlanHash} (SHA-256 over the canonical encoding of spec mod-suite-v1 §6.4).
 *
 * <p>From F-1: the apply engine. {@link org.mtr.aurelia.road.ops.ApplyJob} places one plan a time-budgeted slice
 * at a time through the {@code WorldWriter} SPI, refuses plans over block entities, and records each changed block's
 * previous state for its undo plan; {@link org.mtr.aurelia.road.ops.ApplyScheduler} runs a world's jobs in
 * order within one budget per tick.
 */
package org.mtr.aurelia.road.ops;
