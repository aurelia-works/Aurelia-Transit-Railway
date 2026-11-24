/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
/**
 * Roads: profiles, presets, the per-lane solver, the rasteriser, markings, palettes (as data), the road
 * graph, ports, the continuation classifier and junction templates. Tasks G-4 to G-11.
 *
 * <p><b>Profiles</b> (G-4) are built into the code (owner, 2026-10-04): {@link
 * org.mtr.aurelia.road.road.RoadProfiles} holds them by id, such as {@code city-17}, and validates each one
 * with {@link org.mtr.aurelia.road.road.ProfileValidator} when it loads. A profile is a list of
 * {@link org.mtr.aurelia.road.road.Section}s, left to right; its lanes, their offsets from the reference line
 * and their directions are derived from it. {@link org.mtr.aurelia.road.road.Presets} holds each group's
 * minimum inner-lane radius and steepest grade.
 *
 * <p><b>Requests</b> (F-3): {@link org.mtr.aurelia.road.road.RoadPlanParams} is the roads module's parameter
 * record on the network, {@link org.mtr.aurelia.road.road.RoadPlanner} turns it into a block plan or a refusal
 * (Slice A: straight roads), and {@link org.mtr.aurelia.road.road.RoadsModule} plugs both into the protocol.
 * From F-4 the planner also finds an in-line end a given length ahead ({@code endAlong}) and a road's preview handles.
 * From R-2 it gives warnings ({@code STEP_22_5}), the road tool's end rule ({@code snapEnd}: 22.5° roads end within
 * 0.05 block of their line) and a refused road's shape ({@code outcome}).
 */
package org.mtr.aurelia.road.road;
