package org.mtr.aurelia.road.road.junction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.proto.Protocol;
import org.mtr.aurelia.road.road.Lane;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanner;
import org.mtr.aurelia.road.road.RoadProfile;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.graph.RoadGraph;
import org.mtr.aurelia.road.road.solve.LaneNode;

/** G-11, R-8a, R-8b: junction templates, their left/right-hand mirror, and snapping them onto the road graph. */
class JunctionTemplatesTest {
    private static final RoadPlanParams.DrivingSide[] SIDES = RoadPlanParams.DrivingSide.values();
    private static final List<RoadProfile> TWO_WAY = List.of(RoadProfiles.CITY_17, RoadProfiles.RESIDENTIAL_15, RoadProfiles.RESIDENTIAL_17);

    /**
     * A T-junction, crossroads and a roundabout snap onto a road that ends at the arm and onto one that starts there, in
     * both driving sides: the arm's entry and exit lane nodes are the road's own end nodes for the lanes running in and
     * out, and the graph then has the road's end taken. A crossroads arm landing on another road's free end takes it too.
     */
    @Test
    void junctionsSnapOntoRoadEnds() {
        for (RoadPlanParams.DrivingSide side : SIDES) {
            for (String id : List.of(JunctionTemplates.T_JUNCTION, JunctionTemplates.CROSSROADS, JunctionTemplates.ROUNDABOUT)) {
                int size = id.equals(JunctionTemplates.ROUNDABOUT) ? 13 : 10;
                JunctionTemplate template = JunctionTemplates.of(id, size, profiles(id, RoadProfiles.CITY_17), side).orElseThrow();
                // A road ending at the junction (its end, index 1), and one starting from it (index 0).
                RoadPlanParams ending = RoadPlanParams.straight("city-17", end(80, Heading.E), end(100, Heading.E)).withDrivingSide(side);
                RoadPlanParams starting = RoadPlanParams.straight("city-17", end(400, Heading.E), end(420, Heading.E)).withDrivingSide(side);
                for (int index = 0; index < 2; index++) {
                    RoadPlanParams road = index == 1 ? ending : starting;
                    UUID roadId = UUID.randomUUID();
                    Map<UUID, RoadPlanParams> roads = new LinkedHashMap<>();
                    roads.put(roadId, road);
                    RoadPlanParams.End roadEnd = road.ends().get(index);
                    // Crossroads: a third road starting where the far arm will land.
                    int armLength = template.arms().get(0).x();
                    UUID farId = UUID.randomUUID();
                    if (id.equals(JunctionTemplates.CROSSROADS)) {
                        int farX = index == 1 ? roadEnd.x() + 2 * armLength : roadEnd.x() - 2 * armLength;
                        Heading away = index == 1 ? Heading.E : Heading.W;
                        roads.put(farId, RoadPlanParams.straight("city-17", end(farX, away), end(farX + (away == Heading.E ? 20 : -20), away)).withDrivingSide(side));
                    }
                    String what = id + " " + side + " road index " + index;

                    JunctionSnap.Result result = JunctionSnap.snap(template, RoadGraph.of(roads), roadEnd.x() + 1, 64, roadEnd.z() + 1, 0);
                    JunctionSnap.Placed placed = assertInstanceOf(JunctionSnap.Placed.class, result, what);
                    PlacedJunction junction = placed.junction();
                    RoadPlanParams.End port = junction.arm(0);
                    assertEquals(List.of(roadEnd.x(), roadEnd.y(), roadEnd.z()), List.of(port.x(), port.y(), port.z()), what);

                    // The road's lanes at that end: a lane runs into the junction when its traffic runs along the road
                    // towards that end.
                    List<LaneNode> nodes = RoadPlanner.laneNodes(road).orElseThrow();
                    List<Lane> lanes = RoadProfiles.CITY_17.lanes();
                    for (int lane = 0; lane < lanes.size(); lane++) {
                        LaneNode node = nodes.get(2 * lane + index);
                        boolean withRoad = lanes.get(lane).direction().on(side) == Lane.Direction.WITH_REFERENCE;
                        boolean into = withRoad == (index == 1);
                        JunctionTemplate.Arm arm = template.arms().get(0);
                        RoadPlanParams.End armNode = junction.node(into ? arm.entry() : arm.exit());
                        assertEquals(List.of(node.x(), node.z()), List.of(armNode.x(), armNode.z()), what + " lane " + lane);
                    }

                    UUID junctionId = UUID.randomUUID();
                    roads.put(junctionId, placed.params());
                    RoadGraph graph = RoadGraph.of(roads);
                    assertTrue(graph.taken(roadId, index), what);
                    assertEquals(template.arms().size(), graph.arms(junctionId).size(), what);
                    if (id.equals(JunctionTemplates.CROSSROADS)) {
                        assertTrue(graph.taken(farId, 0), what + ": the far arm takes the road lined up with it");
                    }
                }
            }
        }
    }

    /**
     * Every template, size, two-way layout and driving side places with every lane one MTR lays as given (G-11), each
     * movement a chain of links from an arm's entry to another arm's exit. Ring nodes lie on the ring: R13, R26 and R39
     * exactly, R7 within 0.08 block (its (5, 5) node is 0.071 off: roads-v1 §11.3, R-8b's documented node error).
     */
    @Test
    void everyTemplatePlacesWithEveryLaneMtrValid() {
        for (RoadPlanParams.DrivingSide side : SIDES) {
            for (RoadProfile profile : TWO_WAY) {
                for (String id : List.of(JunctionTemplates.T_JUNCTION, JunctionTemplates.CROSSROADS, JunctionTemplates.ROUNDABOUT)) {
                    for (int size : id.equals(JunctionTemplates.ROUNDABOUT) ? JunctionTemplates.RING_RADII : JunctionTemplates.CORNER_RADII) {
                        String what = id + " " + size + " " + profile.id() + " " + side;
                        JunctionTemplate template = JunctionTemplates.of(id, size, profiles(id, profile), side).orElseThrow(() -> new AssertionError(what));
                        assertEquals(List.of(), template.unlaidLinks(), what);
                        int arms = template.arms().size();
                        assertEquals(arms * (arms - 1), template.movements().size(), what);
                        for (JunctionTemplate.Movement movement : template.movements()) {
                            List<JunctionTemplate.Link> path = movement.links().stream().map(template.links()::get).toList();
                            assertEquals(template.arms().get(movement.fromArm()).entry(), path.get(0).from(), what);
                            assertEquals(template.arms().get(movement.toArm()).exit(), path.get(path.size() - 1).to(), what);
                            for (int i = 0; i + 1 < path.size(); i++) {
                                assertEquals(path.get(i).to(), path.get(i + 1).from(), what);
                            }
                        }
                        if (id.equals(JunctionTemplates.ROUNDABOUT)) {
                            for (int k = 0; k < 8; k++) {
                                JunctionTemplate.Node node = template.nodes().get(k);
                                double error = StrictMath.abs(StrictMath.hypot(node.x(), node.z()) - size);
                                assertTrue(size == 7 ? error < 0.08 : error < 1e-9, what + " ring node " + k + " off by " + error);
                            }
                        }
                    }
                }
            }
        }
        // A T whose minor arm is residential on a City street.
        JunctionTemplate mixed = JunctionTemplates.of(JunctionTemplates.T_JUNCTION, 6, List.of("city-17", "city-17", "residential-15"),
                RoadPlanParams.DrivingSide.LHT).orElseThrow();
        assertEquals(List.of(), mixed.unlaidLinks());
        // The alley's shared lane has no junction piece yet.
        assertEquals(Optional.empty(), JunctionTemplates.of(JunctionTemplates.CROSSROADS, 6, profiles(JunctionTemplates.CROSSROADS, RoadProfiles.ALLEY_7),
                RoadPlanParams.DrivingSide.LHT));
    }

    /**
     * Right-hand traffic is the left-hand template reflected (roads-v1 §11.4): positions {@code (x, z) → (x, −z)}, headings
     * {@code i → 16 − i}, left and right turns swapped, and the ring anticlockwise. Mirroring twice gives it back.
     */
    @Test
    void rightHandMirrorsTheTemplate() {
        for (String id : List.of(JunctionTemplates.T_JUNCTION, JunctionTemplates.CROSSROADS, JunctionTemplates.ROUNDABOUT)) {
            int size = id.equals(JunctionTemplates.ROUNDABOUT) ? 26 : 15;
            JunctionTemplate left = JunctionTemplates.of(id, size, profiles(id, RoadProfiles.CITY_17), RoadPlanParams.DrivingSide.LHT).orElseThrow();
            JunctionTemplate right = JunctionTemplates.of(id, size, profiles(id, RoadProfiles.CITY_17), RoadPlanParams.DrivingSide.RHT).orElseThrow();
            assertEquals(RoadPlanParams.DrivingSide.RHT, right.side());
            assertEquals(left.nodes().size(), right.nodes().size());
            for (int i = 0; i < left.nodes().size(); i++) {
                JunctionTemplate.Node l = left.nodes().get(i);
                JunctionTemplate.Node r = right.nodes().get(i);
                assertEquals(List.of(l.x(), -l.z(), (16 - l.heading().index()) % 16), List.of(r.x(), r.z(), r.heading().index()), id + " node " + i);
            }
            for (int i = 0; i < left.movements().size(); i++) {
                JunctionTemplate.Turn turn = left.movements().get(i).turn();
                JunctionTemplate.Turn swapped = turn == JunctionTemplate.Turn.LEFT ? JunctionTemplate.Turn.RIGHT
                        : turn == JunctionTemplate.Turn.RIGHT ? JunctionTemplate.Turn.LEFT : turn;
                assertEquals(swapped, right.movements().get(i).turn(), id + " movement " + i);
            }
            assertEquals(left, right.mirrored());
            assertEquals(List.of(), right.unlaidLinks(), id);
        }
        // The ring: clockwise seen from above in left-hand traffic (z grows southwards), anticlockwise in right-hand.
        for (RoadPlanParams.DrivingSide side : SIDES) {
            JunctionTemplate ring = JunctionTemplates.of(JunctionTemplates.ROUNDABOUT, 13, profiles(JunctionTemplates.ROUNDABOUT, RoadProfiles.CITY_17), side).orElseThrow();
            JunctionTemplate.Node a = ring.nodes().get(0);
            JunctionTemplate.Node b = ring.nodes().get(1);
            long cross = (long) a.x() * b.z() - (long) a.z() * b.x();
            assertEquals(side == RoadPlanParams.DrivingSide.LHT, cross > 0, side + ": ring direction");
        }
    }

    /**
     * Left-hand traffic is the default and is the template as authored: leaving the east arm, traffic keeps left (north,
     * −z). A road graph without junctions is as before, and the planner still doesn't build junctions in the world.
     */
    @Test
    void leftHandIsTheDefaultAndOldBehaviourStays() {
        JunctionTemplate t = JunctionTemplates.of(JunctionTemplates.T_JUNCTION, 6, profiles(JunctionTemplates.T_JUNCTION, RoadProfiles.CITY_17),
                RoadPlanParams.DrivingSide.LHT).orElseThrow();
        assertEquals(RoadPlanParams.DrivingSide.LHT, RoadPlanParams.straight("city-17", end(0, Heading.E), end(10, Heading.E)).drivingSide());
        JunctionTemplate.Arm east = t.arms().get(0);
        assertEquals(Heading.E, east.outgoing());
        assertTrue(t.nodes().get(east.exit()).z() < 0, "leaving east, traffic keeps to the north side");
        assertTrue(t.nodes().get(east.entry()).z() > 0, "arriving from the east, traffic keeps to the south side");

        Map<UUID, RoadPlanParams> roads = new LinkedHashMap<>();
        UUID a = UUID.randomUUID();
        roads.put(a, RoadPlanParams.straight("city-17", end(0, Heading.E), end(20, Heading.E)));
        RoadGraph graph = RoadGraph.of(roads);
        assertEquals(0, graph.junctions().size());
        assertEquals(0, graph.armJoins().size());
        assertTrue(!graph.taken(a, 0) && !graph.taken(a, 1));

        JunctionSnap.Placed placed = assertInstanceOf(JunctionSnap.Placed.class, JunctionSnap.snap(t, graph, 20, 64, 0, 0));
        assertEquals(Optional.of(Protocol.NOT_AVAILABLE_YET), RoadPlanner.plan(placed.params(), WorldLimits.OVERWORLD).code());
    }

    private static RoadPlanParams.End end(int x, Heading heading) {
        return RoadPlanParams.End.at(x, 64, 0, heading);
    }

    private static List<String> profiles(String templateId, RoadProfile profile) {
        return java.util.Collections.nCopies(JunctionTemplates.armCount(templateId), profile.id());
    }
}
