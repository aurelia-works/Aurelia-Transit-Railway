/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.junction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.mtr.aurelia.road.geom.BlockStep;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.Lane;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.Section;

/**
 * The v1 urban junction pieces (roads-v1 §11.2; tasks R-8a and R-8b), built from upstream's spec: T-junctions and
 * crossroads with UK corner radii 6, 10 or 15 (CD 123), and roundabouts R7 (mini), R13 (compact), R26 and R39 (normal)
 * with node-exact rings (§11.3). A template is named as a road request names it ({@link RoadPlanParams.Junction}: template
 * id, size, one layout per arm) and comes for the driving side asked for: authored left-hand, mirrored for right-hand.
 *
 * <p>Arms take the two-lane layouts (City street and both residential); the alley's one shared lane joins only the alley
 * (R-5), so it has no junction piece yet.
 */
public final class JunctionTemplates {
    /** A T-junction: a major road east–west, the minor arm to the south; arms east, west, south. Size: corner radius. */
    public static final String T_JUNCTION = "t_junction";
    /** Crossroads: arms east, south, west, north. Size: corner radius. */
    public static final String CROSSROADS = "crossroads";
    /** A roundabout: arms east, south, west, north, a one-lane ring clockwise in left-hand traffic. Size: the ring's radius. */
    public static final String ROUNDABOUT = "roundabout";

    /** Corner radii for T-junctions and crossroads (CD 123), in blocks. */
    public static final List<Integer> CORNER_RADII = List.of(6, 10, 15);
    /** Ring radii (the circulating lane's centre, §11.3): mini R7, compact R13, normal R26 and R39. */
    public static final List<Integer> RING_RADII = List.of(7, 13, 26, 39);

    /** The longest a roundabout's arm reaches past the ring's edge while looking for MTR-valid entries and exits. */
    private static final int MAX_ARM_SEARCH = 40;

    private JunctionTemplates() {
    }

    /** How many arms the template {@code templateId} has, or 0 for an unknown template. */
    public static int armCount(String templateId) {
        return switch (templateId) {
            case T_JUNCTION -> 3;
            case CROSSROADS, ROUNDABOUT -> 4;
            default -> 0;
        };
    }

    /** The template a road request names, for {@code side}; empty when there is no such piece (see {@link #of(String, int, List, RoadPlanParams.DrivingSide)}). */
    public static Optional<JunctionTemplate> of(RoadPlanParams.Junction junction, RoadPlanParams.DrivingSide side) {
        return of(junction.templateId(), junction.size(), junction.armProfiles(), side);
    }

    /**
     * The template {@code templateId} at {@code size} with the layouts {@code armProfiles} (one per arm, in the template's
     * arm order), for {@code side}; empty for an unknown template or size, the wrong number of arms, or a layout without
     * one lane each way on block centres. Left-hand traffic gets the template as authored.
     */
    public static Optional<JunctionTemplate> of(String templateId, int size, List<String> armProfiles, RoadPlanParams.DrivingSide side) {
        return CACHE.computeIfAbsent(List.of(templateId, size, List.copyOf(armProfiles), side), key -> work(templateId, size, armProfiles, side));
    }

    /** The templates worked out so far, by id, size, layouts and side: the planner and the tool ask on every request and aim. */
    private static final Map<List<Object>, Optional<JunctionTemplate>> CACHE = new ConcurrentHashMap<>();

    private static Optional<JunctionTemplate> work(String templateId, int size, List<String> armProfiles, RoadPlanParams.DrivingSide side) {
        int arms = armCount(templateId);
        if (arms == 0 || armProfiles.size() != arms) {
            return Optional.empty();
        }
        List<RoadProfile> profiles = new ArrayList<>();
        for (String id : armProfiles) {
            Optional<RoadProfile> profile = RoadProfiles.byId(id).filter(JunctionTemplates::twoWay);
            if (profile.isEmpty()) {
                return Optional.empty();
            }
            profiles.add(profile.get());
        }
        Optional<JunctionTemplate> authored = switch (templateId) {
            case T_JUNCTION, CROSSROADS -> CORNER_RADII.contains(size) ? Optional.of(crossing(templateId, size, profiles)) : Optional.empty();
            default -> RING_RADII.contains(size) ? roundabout(size, profiles) : Optional.empty();
        };
        return side == RoadPlanParams.DrivingSide.LHT ? authored : authored.map(JunctionTemplate::mirrored);
    }

    /** A T-junction or crossroads: every arm's entry joined straight to every other arm's exit by one rail. */
    private static JunctionTemplate crossing(String templateId, int cornerRadius, List<RoadProfile> profiles) {
        List<Heading> headings = templateId.equals(T_JUNCTION) ? List.of(Heading.E, Heading.W, Heading.S) : List.of(Heading.E, Heading.S, Heading.W, Heading.N);
        int armLength = (int) StrictMath.ceil(widestHalf(profiles) + cornerRadius);
        List<JunctionTemplate.Node> nodes = new ArrayList<>();
        List<JunctionTemplate.Arm> arms = arms(headings, profiles, armLength, nodes);
        List<JunctionTemplate.Link> links = new ArrayList<>();
        List<JunctionTemplate.Movement> movements = new ArrayList<>();
        for (int a = 0; a < arms.size(); a++) {
            for (int b = 0; b < arms.size(); b++) {
                int steps = arms.get(a).outgoing().opposite().stepsTo(arms.get(b).outgoing());
                if (a == b || steps == 8) {
                    continue;
                }
                JunctionTemplate.Turn turn = steps < 0 ? JunctionTemplate.Turn.LEFT : steps == 0 ? JunctionTemplate.Turn.AHEAD : JunctionTemplate.Turn.RIGHT;
                links.add(new JunctionTemplate.Link(arms.get(a).entry(), arms.get(b).exit(), JunctionTemplate.Kind.TURN));
                movements.add(new JunctionTemplate.Movement(a, b, turn, List.of(links.size() - 1)));
            }
        }
        return new JunctionTemplate(templateId, cornerRadius, ids(profiles), RoadPlanParams.DrivingSide.LHT, arms, nodes, links, movements);
    }

    /**
     * A roundabout of ring radius {@code radius}: eight ring nodes (§11.3: at 45° for R7, offset by 22.5° for R13, R26 and
     * R39), joined clockwise; each arm's entry joins the ring at the node just past the arm and its exit leaves from the
     * node just before it. Arms are as short as MTR lays every entry and exit at; empty if none is (not expected).
     */
    private static Optional<JunctionTemplate> roundabout(int radius, List<RoadProfile> profiles) {
        List<JunctionTemplate.Node> ring = ring(radius);
        boolean offset = radius != 7;
        int shortest = radius + (int) StrictMath.ceil(widestHalf(profiles)) + 1;
        for (int armLength = shortest; armLength <= shortest + MAX_ARM_SEARCH; armLength++) {
            List<JunctionTemplate.Node> nodes = new ArrayList<>(ring);
            List<JunctionTemplate.Arm> arms = arms(List.of(Heading.E, Heading.S, Heading.W, Heading.N), profiles, armLength, nodes);
            List<JunctionTemplate.Link> links = new ArrayList<>();
            for (int k = 0; k < ring.size(); k++) {
                links.add(new JunctionTemplate.Link(k, (k + 1) % ring.size(), JunctionTemplate.Kind.RING));
            }
            int[] onto = new int[arms.size()];
            int[] off = new int[arms.size()];
            for (int j = 0; j < arms.size(); j++) {
                onto[j] = offset ? 2 * j : 2 * j + 1;
                off[j] = StrictMath.floorMod(2 * j - 1, ring.size());
                links.add(new JunctionTemplate.Link(arms.get(j).entry(), onto[j], JunctionTemplate.Kind.ENTRY));
                links.add(new JunctionTemplate.Link(off[j], arms.get(j).exit(), JunctionTemplate.Kind.EXIT));
            }
            JunctionTemplate trial = new JunctionTemplate(ROUNDABOUT, radius, ids(profiles), RoadPlanParams.DrivingSide.LHT, arms, nodes, links, List.of());
            if (!trial.unlaidLinks().isEmpty()) {
                continue;
            }
            List<JunctionTemplate.Movement> movements = new ArrayList<>();
            for (int a = 0; a < arms.size(); a++) {
                for (int b = 0; b < arms.size(); b++) {
                    int round = StrictMath.floorMod(b - a, arms.size());
                    if (round == 0) {
                        continue;
                    }
                    List<Integer> path = new ArrayList<>();
                    path.add(ring.size() + 2 * a);
                    for (int k = onto[a]; k != off[b]; k = (k + 1) % ring.size()) {
                        path.add(k);
                    }
                    path.add(ring.size() + 2 * b + 1);
                    JunctionTemplate.Turn turn = round == 1 ? JunctionTemplate.Turn.LEFT : round == 2 ? JunctionTemplate.Turn.AHEAD : JunctionTemplate.Turn.RIGHT;
                    movements.add(new JunctionTemplate.Movement(a, b, turn, path));
                }
            }
            return Optional.of(new JunctionTemplate(ROUNDABOUT, radius, ids(profiles), RoadPlanParams.DrivingSide.LHT, arms, nodes, links, movements));
        }
        return Optional.empty();
    }

    /** The ring's eight nodes for {@code radius}, clockwise from the east, each with its clockwise heading. */
    private static List<JunctionTemplate.Node> ring(int radius) {
        List<JunctionTemplate.Node> ring = new ArrayList<>();
        switch (radius) {
            case 7 -> {
                ring.add(new JunctionTemplate.Node(7, 0, Heading.S));
                ring.add(new JunctionTemplate.Node(5, 5, Heading.SW));
            }
            default -> {
                // Pythagorean points: 5-12-13 and its multiples lie exactly on the circle.
                int k = radius / 13;
                ring.add(new JunctionTemplate.Node(12 * k, 5 * k, Heading.SSW));
                ring.add(new JunctionTemplate.Node(5 * k, 12 * k, Heading.WSW));
            }
        }
        for (int i = 2; i < 8; i++) {
            JunctionTemplate.Node back = ring.get(i - 2);
            BlockStep turned = new BlockStep(back.x(), back.z()).rotateQuarterClockwise();
            ring.add(new JunctionTemplate.Node(turned.dx(), turned.dz(), back.heading().rotate(4)));
        }
        return ring;
    }

    /**
     * The arms at {@code headings}, each {@code armLength} blocks out, adding their entry and exit nodes to {@code nodes}:
     * traffic keeps left, so a road leaving the arm has its exit lane on its left and the entry lane on its right.
     */
    private static List<JunctionTemplate.Arm> arms(List<Heading> headings, List<RoadProfile> profiles, int armLength, List<JunctionTemplate.Node> nodes) {
        List<JunctionTemplate.Arm> arms = new ArrayList<>();
        for (int j = 0; j < headings.size(); j++) {
            Heading out = headings.get(j);
            BlockStep along = unit(out);
            BlockStep right = unit(out.rotate(4));
            int d = laneOffset(profiles.get(j));
            BlockStep port = along.times(armLength);
            nodes.add(new JunctionTemplate.Node(port.dx() + right.dx() * d, port.dz() + right.dz() * d, out.opposite()));
            nodes.add(new JunctionTemplate.Node(port.dx() - right.dx() * d, port.dz() - right.dz() * d, out));
            arms.add(new JunctionTemplate.Arm(port.dx(), port.dz(), out, profiles.get(j).id(), nodes.size() - 2, nodes.size() - 1));
        }
        return arms;
    }

    /** One lane each way, on block centres, either side of the reference line. */
    private static boolean twoWay(RoadProfile profile) {
        List<Lane> lanes = profile.lanes();
        return profile.referenceOnBlockCentre() && lanes.size() == 2 && lanes.get(0).direction() == Lane.Direction.WITH_REFERENCE
                && lanes.get(1).offsetHalfBlocks() == -lanes.get(0).offsetHalfBlocks() && lanes.get(1).offsetHalfBlocks() % 2 == 0;
    }

    /** How far each lane's centre is from the reference line, in whole blocks. */
    private static int laneOffset(RoadProfile profile) {
        return profile.lanes().get(1).offsetHalfBlocks() / 2;
    }

    /** Half the widest carriageway (outer edge of the first lane to the outer edge of the last), in blocks. */
    private static double widestHalf(List<RoadProfile> profiles) {
        double widest = 0;
        for (RoadProfile profile : profiles) {
            int start = 0;
            int first = -1;
            int last = 0;
            for (Section section : profile.sections()) {
                if (section.band() == Band.LANE) {
                    first = first < 0 ? start : first;
                    last = start + section.width();
                }
                start += section.width();
            }
            widest = StrictMath.max(widest, (last - first) / 2.0);
        }
        return widest;
    }

    /** The one-block step along a cardinal heading. */
    private static BlockStep unit(Heading heading) {
        return new BlockStep((int) StrictMath.round(heading.cos()), (int) StrictMath.round(heading.sin()));
    }

    private static List<String> ids(List<RoadProfile> profiles) {
        List<String> ids = new ArrayList<>();
        for (RoadProfile profile : profiles) {
            ids.add(profile.id());
        }
        return ids;
    }
}
