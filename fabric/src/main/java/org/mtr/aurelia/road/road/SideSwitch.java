/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.mtr.aurelia.road.road.junction.JunctionSide;

/**
 * The world's driving side against the roads (P9). The world has one side ({@code stratosDrivingSide}); each placed road
 * and junction records the side it was drawn for. Three rules follow from that:
 * <ul>
 *   <li><b>{@link #keeps}:</b> a request to place or rebuild a road must be for the world's side, or the server refuses it
 *       {@code DRIVING_SIDE_MISMATCH}: a client that hasn't heard of a switch yet draws for the wrong side, and a road not yet
 *       converted can't be edited as if it were. Deleting and clearing markers don't depend on the side.</li>
 *   <li><b>{@link #converted}:</b> when the world switches sides each road is rebuilt for the new one. A road's blocks are the
 *       same on both sides (only its hidden track runs the other way round); a junction is turned onto its mirrored twin
 *       ({@link JunctionSide}), which moves its arms' order, so a road drawn onto an arm is given the arm's new port.</li>
 *   <li><b>{@link #isSwitch}:</b> that rebuild moves nothing, so the planner doesn't check its joins against neighbours that
 *       are still on the old side ({@link RoadJoins#SIDE_MISMATCH}); nothing else may pass as one.</li>
 * </ul>
 * Pure functions on the roads standing, so the server's task and the tests share them.
 */
public final class SideSwitch {
    private SideSwitch() {
    }

    /**
     * Whether a request {@code params} keeps to the world's side {@code world}: every tool that draws or rebuilds a road
     * does; a delete or clearing markers works on a road whatever side it keeps.
     */
    public static boolean keeps(RoadPlanParams params, RoadPlanParams.DrivingSide world) {
        return params.tool() == RoadPlanParams.Tool.DELETE || params.tool() == RoadPlanParams.Tool.CLEAR_MARKERS || params.drivingSide() == world;
    }

    /**
     * Road {@code id}, standing among {@code placed}, for {@code side}: the same road with that side and, for a junction, the
     * mirrored twin on the same blocks ({@link JunctionSide}); the ends of a road drawn onto a junction's arm name the arm's
     * port as it stands now. Empty when there is no such road, or no twin for a junction.
     */
    public static Optional<RoadPlanParams> converted(UUID id, Map<UUID, RoadPlanParams> placed, RoadPlanParams.DrivingSide side) {
        RoadPlanParams road = placed.get(id);
        if (road == null) {
            return Optional.empty();
        }
        if (road.tool() == RoadPlanParams.Tool.JUNCTION) {
            return JunctionSide.convert(road, side).map(JunctionSide.Converted::params);
        }
        List<RoadPlanParams.End> ends = new ArrayList<>();
        for (RoadPlanParams.End end : road.ends()) {
            ends.add(onArm(end, placed));
        }
        return Optional.of(road.withEnds(ends).withDrivingSide(side));
    }

    /** {@code end}, if it is drawn onto a junction's arm, naming the arm that stands on its block now. */
    private static RoadPlanParams.End onArm(RoadPlanParams.End end, Map<UUID, RoadPlanParams> placed) {
        if (end.snapRoadId().isEmpty() || end.snapPortIndex().isEmpty() || end.snapPortIndex().getAsInt() < RoadJoins.ARM_PORTS) {
            return end;
        }
        RoadPlanParams junction = placed.get(end.snapRoadId().get());
        if (junction == null || junction.tool() != RoadPlanParams.Tool.JUNCTION) {
            return end;
        }
        for (int arm = 0; arm < junction.ends().size(); arm++) {
            RoadPlanParams.End at = junction.ends().get(arm);
            if (at.x() == end.x() && at.y() == end.y() && at.z() == end.z()) {
                return new RoadPlanParams.End(end.x(), end.y(), end.z(), end.heading(), end.snapRoadId(), OptionalInt.of(RoadJoins.ARM_PORTS + arm));
            }
        }
        return end;
    }

    /**
     * Whether {@code request} is road {@code id}'s switch of side: a rebuild asking for exactly what {@link #converted} gives
     * for the side it names (the palette aside), and that side is not the road's.
     */
    public static boolean isSwitch(UUID id, RoadPlanParams request, Map<UUID, RoadPlanParams> placed) {
        RoadPlanParams road = placed.get(id);
        if (road == null || request.tool() != RoadPlanParams.Tool.REBUILD || request.drivingSide() == road.drivingSide()) {
            return false;
        }
        return converted(id, placed, request.drivingSide())
                .map(expected -> expected.road().equals(request.withPalette(expected.paletteId()).road())).orElse(false);
    }

    /**
     * The roads and junctions among {@code placed} that don't keep to {@code side}, junctions first: a junction's switch
     * reorders its arms, and the roads drawn onto them take their new ports from the junction as it stands.
     */
    public static List<UUID> pending(Map<UUID, RoadPlanParams> placed, RoadPlanParams.DrivingSide side) {
        List<UUID> junctions = new ArrayList<>();
        List<UUID> roads = new ArrayList<>();
        placed.forEach((id, params) -> {
            if (params.drivingSide() != side) {
                (params.tool() == RoadPlanParams.Tool.JUNCTION ? junctions : roads).add(id);
            }
        });
        junctions.addAll(roads);
        return junctions;
    }
}
