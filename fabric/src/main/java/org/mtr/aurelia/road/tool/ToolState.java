/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

/** Where a tool is (spec mod-suite-v1 §6.2). Tools use the states they need; every tool starts and ends in {@link #IDLE}. */
public enum ToolState {
    /** Nothing picked yet. */
    IDLE,
    /** Some points are picked; more are needed. */
    PLACING,
    /** A complete plan is shown and can be committed. */
    PREVIEWING,
    /** The plan was sent to the server and the answer hasn't arrived. */
    COMMITTING
}
