/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * The only way the core reaches the game (spec mod-suite-v1 §5): {@code WorldWriter}, {@code PreviewSink},
 * {@code MessageChannel}, {@code PermissionCheck} and {@code Storage}. Each interface arrives with its first
 * user, from task F-1 on.
 *
 * <p>From F-1: {@link org.mtr.aurelia.road.spi.WorldWriter}, per-block reads and writes plus chunk tickets, used
 * by the apply engine. From F-2: {@link org.mtr.aurelia.road.spi.Storage}, atomic files under
 * {@code <world>/aurelia_roads/}, used by the undo store. From F-3: {@link org.mtr.aurelia.road.spi.MessageChannel},
 * one connection's end of {@code aurelia:roads}, and {@link org.mtr.aurelia.road.spi.PermissionCheck}, used by
 * the protocol's server end. From F-4: {@link org.mtr.aurelia.road.spi.PreviewSink}, the client's preview
 * renderer. From R-2: {@link org.mtr.aurelia.road.spi.ToolEnvironment}, what the client gives the tool session
 * (limits, a planner thread, the client thread, sending a plan).
 */
package org.mtr.aurelia.road.spi;
