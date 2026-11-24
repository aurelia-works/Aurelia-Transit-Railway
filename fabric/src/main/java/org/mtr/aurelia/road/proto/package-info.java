/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The {@code aurelia:roads} protocol (editor-protocol spec), from task F-3. The platform layer only frames bytes.
 * <ul>
 *   <li><b>Records and their codec:</b> {@link org.mtr.aurelia.road.proto.Message} (ids 0–9: handshake, plan
 *       request, accept and reject, progress, undo and redo), {@link org.mtr.aurelia.road.proto.Messages},
 *       {@link org.mtr.aurelia.road.proto.ProtoWriter} and {@link org.mtr.aurelia.road.proto.ProtoReader}.
 *       Every bad input throws {@link org.mtr.aurelia.road.proto.MalformedMessage}.</li>
 *   <li><b>Plans from parameters:</b> {@link org.mtr.aurelia.road.proto.PlanModule} (roads:
 *       {@code core.road.RoadsModule}), {@link org.mtr.aurelia.road.proto.PlanResult}, and
 *       {@link org.mtr.aurelia.road.proto.PlanCheck}, the server's hash comparison.</li>
 *   <li><b>The two ends:</b> {@link org.mtr.aurelia.road.proto.EditorServer} (handshake, the permission gate,
 *       plans worked out off the server thread with a time limit, the apply engine, progress, undo and redo) and
 *       {@link org.mtr.aurelia.road.proto.EditorClient} (answers the handshake, sends plans with their hashes).</li>
 * </ul>
 */
package org.mtr.aurelia.road.proto;
