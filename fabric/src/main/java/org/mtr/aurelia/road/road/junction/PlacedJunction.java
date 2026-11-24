package org.mtr.aurelia.road.road.junction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.graph.RoadGraph;

/**
 * A junction template set in the world: its centre at block {@code (x, y, z)}, turned {@code quarterTurns} × 90° clockwise
 * seen from above. A junction is level: every arm and lane node is at {@code y}.
 */
public record PlacedJunction(JunctionTemplate template, int x, int y, int z, int quarterTurns) {
    public PlacedJunction {
        Objects.requireNonNull(template, "template");
        quarterTurns = StrictMath.floorMod(quarterTurns, 4);
    }

    /** Arm {@code arm}'s port in the world: its centre-line block, with the heading a road leaving the junction there runs. */
    public RoadPlanParams.End arm(int arm) {
        JunctionTemplate.Arm local = this.template.arms().get(arm);
        return at(local.x(), local.z(), local.outgoing());
    }

    /** Lane node {@code node} in the world, with the heading traffic runs there. */
    public RoadPlanParams.End node(int node) {
        JunctionTemplate.Node local = this.template.nodes().get(node);
        return at(local.x(), local.z(), local.heading());
    }

    /**
     * The junction as a road request records it (the {@link RoadPlanParams.Tool#JUNCTION} tool, which the wire format has
     * had since R-1): one end per arm at its port, facing the way a road leaving it runs, each naming the road end
     * {@code joined} gives for it, if any; and the template, size and arm layouts.
     */
    public RoadPlanParams params(List<Optional<RoadGraph.Port>> joined) {
        if (joined.size() != this.template.arms().size()) {
            throw new IllegalArgumentException("One join (or none) per arm: " + this.template.arms().size() + ", not " + joined.size());
        }
        List<RoadPlanParams.End> ends = new ArrayList<>();
        for (int i = 0; i < joined.size(); i++) {
            RoadPlanParams.End port = arm(i);
            Optional<RoadGraph.Port> road = joined.get(i);
            ends.add(new RoadPlanParams.End(port.x(), port.y(), port.z(), port.heading(), road.map(RoadGraph.Port::road),
                    road.map(p -> OptionalInt.of(p.index())).orElse(OptionalInt.empty())));
        }
        return new RoadPlanParams(RoadPlanParams.Tool.JUNCTION, this.template.armProfiles().get(0), Presets.Preset.GAME_SCALE, "vanilla",
                this.template.side(), ends, RoadPlanParams.Vertical.QUADRATIC, RoadPlanParams.NodeAids.NONE, false,
                Optional.of(new RoadPlanParams.Junction(this.template.templateId(), this.template.size(), this.template.armProfiles())),
                Optional.empty(), Optional.empty());
    }

    private RoadPlanParams.End at(int localX, int localZ, Heading heading) {
        BlockStep step = new BlockStep(localX, localZ);
        for (int i = 0; i < this.quarterTurns; i++) {
            step = step.rotateQuarterClockwise();
        }
        return RoadPlanParams.End.at(this.x + step.dx(), this.y, this.z + step.dz(), heading.rotate(4 * this.quarterTurns));
    }
}
