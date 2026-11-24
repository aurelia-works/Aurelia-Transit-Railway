/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The MTR node aids (spec roads-v1 §9; ADR-0033; task R-6), apart from the planner's markers and pre-oriented nodes:
 * <ul>
 *   <li>{@link org.mtr.aurelia.road.road.nodes.MtrNodes}: MTR's node block by runtime id and state strings (S6a's
 *       facts), and MTR's yaw rule;</li>
 *   <li>{@link org.mtr.aurelia.road.road.nodes.NodeCheck}: the wrong-node checker;</li>
 *   <li>{@link org.mtr.aurelia.road.road.nodes.PlacedRoads}: the roads placed this visit (client only);</li>
 *   <li>{@link org.mtr.aurelia.road.road.nodes.NodeAids}: the live checks and the "Lane nodes" panel's rows.</li>
 * </ul>
 * {@code stratos} never depends on MTR (ADR-0007): everything here works on block-state strings.
 */
package org.mtr.aurelia.road.road.nodes;
