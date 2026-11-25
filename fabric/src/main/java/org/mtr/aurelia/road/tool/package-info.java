/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * Editor tools (spec mod-suite-v1 §6.2), from task R-1.
 * <ul>
 *   <li><b>{@link org.mtr.aurelia.road.tool.Tool}:</b> a state machine driven by use, attack and aim input. It
 *       has an output (a plan job and handles, shown through the preview) and a HUD model of lang keys.</li>
 *   <li><b>{@link org.mtr.aurelia.road.tool.ToolSession}:</b> the tools and the one in hand. Choosing another tool
 *       or putting it away resets it to idle and clears its preview; settings stay.</li>
 * </ul>
 * From R-2 a tool can place what it shows: its output names a {@link org.mtr.aurelia.road.tool.PlanJob}, which
 * the session works out off the client thread; the tool offers a {@link org.mtr.aurelia.road.tool.Commit}, which
 * the session sends; and the session passes the server's answers back as {@link org.mtr.aurelia.road.tool.Answer}s.
 * The platform maps the game's input to {@link org.mtr.aurelia.road.tool.ToolInput}, gives the session a
 * {@code ToolEnvironment} and draws the HUD. From R-3 the input carries the player's yaw, a
 * {@link org.mtr.aurelia.road.tool.HandlePicker} decides which handle can be picked up, and a change of handles
 * alone is redrawn without working the plan out again.
 */
package org.mtr.aurelia.road.tool;
