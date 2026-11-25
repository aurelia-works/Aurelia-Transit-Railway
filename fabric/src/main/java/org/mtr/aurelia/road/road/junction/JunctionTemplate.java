/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.junction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.solve.PieceSolver;

/**
 * A junction piece (task G-11; roads-v1 §11.1, ADR-0013): arms that roads join, and the lanes that carry traffic across
 * it, laid out around the junction's centre (block {@code (0, 0)}, x east and z south). Built from upstream's spec; upstream
 * has no junction code yet.
 *
 * <p>Each lane is a {@link Link}: one MTR rail between two {@link Node}s, each a block centre with one of the 16 headings
 * (the way traffic runs there). A {@link Movement} is the links a vehicle follows from one arm to another. Every link must
 * be one MTR lays as given ({@link #unlaidLinks}): that is G-11's acceptance, checked for every template in both
 * driving sides.
 *
 * <p>Templates are authored for left-hand traffic (UK). {@link #mirrored} reflects one for right-hand traffic (§11.4).
 */
public record JunctionTemplate(String templateId, int size, List<String> armProfiles, RoadPlanParams.DrivingSide side, List<Arm> arms,
        List<Node> nodes, List<Link> links, List<Movement> movements) {
    /** A lane node: a block centre relative to the junction's centre, and the heading traffic runs there. */
    public record Node(int x, int z, Heading heading) {
        public Node {
            Objects.requireNonNull(heading, "heading");
        }

        Node mirrored() {
            BlockStep at = new BlockStep(this.x, this.z).mirrored();
            return new Node(at.dx(), at.dz(), this.heading.mirrored());
        }
    }

    /**
     * An arm: its port, the centre-line block a road joins at (relative to the junction's centre), the heading a road
     * leaving the junction there runs, its layout, and its two lane nodes: {@code entry} (traffic arriving) and
     * {@code exit} (traffic leaving), as indexes into {@link #nodes}.
     */
    public record Arm(int x, int z, Heading outgoing, String profileId, int entry, int exit) {
        public Arm {
            Objects.requireNonNull(outgoing, "outgoing");
            Objects.requireNonNull(profileId, "profileId");
        }

        Arm mirrored() {
            BlockStep at = new BlockStep(this.x, this.z).mirrored();
            return new Arm(at.dx(), at.dz(), this.outgoing.mirrored(), this.profileId, this.entry, this.exit);
        }
    }

    /** What a link is for. */
    public enum Kind {
        /** From an arm's entry straight to another arm's exit (T-junctions and crossroads). */
        TURN,
        /** From an arm's entry onto a roundabout's ring. */
        ENTRY,
        /** One piece of a roundabout's ring. */
        RING,
        /** From a roundabout's ring to an arm's exit. */
        EXIT
    }

    /** One MTR rail, from node {@code from} to node {@code to} (indexes into {@link #nodes}). */
    public record Link(int from, int to, Kind kind) {
        public Link {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** Which way a movement turns, as the driver sees it. */
    public enum Turn {
        LEFT,
        AHEAD,
        RIGHT;

        Turn mirrored() {
            return this == LEFT ? RIGHT : this == RIGHT ? LEFT : AHEAD;
        }
    }

    /** Traffic from arm {@code fromArm} to arm {@code toArm}, along {@code links} (indexes into {@link #links}) in order. */
    public record Movement(int fromArm, int toArm, Turn turn, List<Integer> links) {
        public Movement {
            Objects.requireNonNull(turn, "turn");
            links = List.copyOf(links);
        }
    }

    public JunctionTemplate {
        Objects.requireNonNull(templateId, "templateId");
        armProfiles = List.copyOf(armProfiles);
        Objects.requireNonNull(side, "side");
        arms = List.copyOf(arms);
        nodes = List.copyOf(nodes);
        links = List.copyOf(links);
        movements = List.copyOf(movements);
    }

    /**
     * This template for the other driving side (roads-v1 §11.4): reflected in its x axis, positions {@code (x, z) → (x, −z)}
     * and headings {@code i → (16 − i) mod 16}. Traffic keeps to the other side and a ring runs the other way round;
     * left and right turns swap. Mirroring twice gives the template back.
     */
    public JunctionTemplate mirrored() {
        List<Arm> arms = new ArrayList<>();
        for (Arm arm : this.arms) {
            arms.add(arm.mirrored());
        }
        List<Node> nodes = new ArrayList<>();
        for (Node node : this.nodes) {
            nodes.add(node.mirrored());
        }
        List<Movement> movements = new ArrayList<>();
        for (Movement movement : this.movements) {
            movements.add(new Movement(movement.fromArm(), movement.toArm(), movement.turn().mirrored(), movement.links()));
        }
        RoadPlanParams.DrivingSide other = this.side == RoadPlanParams.DrivingSide.LHT ? RoadPlanParams.DrivingSide.RHT : RoadPlanParams.DrivingSide.LHT;
        return new JunctionTemplate(this.templateId, this.size, this.armProfiles, other, arms, nodes, this.links, movements);
    }

    /**
     * The links MTR doesn't lay as given ({@link PieceSolver#laneLink}), and any link that repeats another's two nodes
     * (MTR keeps one rail per pair of nodes), as indexes into {@link #links}. Empty for a usable template.
     */
    public List<Integer> unlaidLinks() {
        List<Integer> unlaid = new ArrayList<>();
        Set<Long> pairs = new HashSet<>();
        for (int i = 0; i < this.links.size(); i++) {
            Link link = this.links.get(i);
            Node from = this.nodes.get(link.from());
            Node to = this.nodes.get(link.to());
            long pair = ((long) StrictMath.min(link.from(), link.to()) << 32) | StrictMath.max(link.from(), link.to());
            if (!pairs.add(pair) || PieceSolver.laneLink(new BlockStep(to.x() - from.x(), to.z() - from.z()), from.heading(), to.heading()).isEmpty()) {
                unlaid.add(i);
            }
        }
        return unlaid;
    }
}
