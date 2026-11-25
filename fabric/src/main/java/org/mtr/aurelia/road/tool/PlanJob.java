/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

import org.mtr.aurelia.road.ops.WorldLimits;

/**
 * A plan a tool wants worked out (task R-2). The session works it out on the tool planner thread, never on the client
 * thread (spec mod-suite-v1 §7), and hands the answer back with {@link Tool#planned}. Jobs are compared by value: one
 * equal to the job already shown isn't worked out again.
 */
public interface PlanJob {
    /**
     * Works the plan out. Runs on the tool planner thread, so it must only read its own fields; it may stop with a
     * {@link java.util.concurrent.CancellationException} when its thread is interrupted (a newer job replaced it).
     */
    Planned run(WorldLimits limits);
}
