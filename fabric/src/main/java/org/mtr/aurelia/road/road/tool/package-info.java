/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The road tools (R-1 onward). {@link org.mtr.aurelia.road.road.tool.StraightTool}: R-1 built its shell (a start
 * and live numbers); R-2 made it place roads in three steps (start, end, confirm), with a live preview worked out off
 * the client thread. R-3 adds {@link org.mtr.aurelia.road.road.tool.CurveTool}, whose ends are set like MTR
 * nodes (the player's facing, direction-less), and handles that can be picked up and moved in both tools.
 */
package org.mtr.aurelia.road.road.tool;
