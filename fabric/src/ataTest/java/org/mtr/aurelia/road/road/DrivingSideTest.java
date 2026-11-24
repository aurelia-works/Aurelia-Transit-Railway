package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.proto.EditorClient;
import org.mtr.aurelia.road.proto.Message;
import org.mtr.aurelia.road.proto.Messages;

/** P9: the world's driving side. Left-hand keeps every road as it was built before the switch existed. */
class DrivingSideTest {
    /** Right-hand traffic swaps the way City street's two lanes run; left-hand leaves them as authored; a shared lane stays shared. */
    @Test
    void rightHandSwapsTheCarriagewaysAndLeftHandKeepsThem() {
        List<Lane> lanes = RoadProfiles.CITY_17.lanes();
        assertEquals(Lane.Direction.WITH_REFERENCE, lanes.get(0).direction().on(RoadPlanParams.DrivingSide.LHT));
        assertEquals(Lane.Direction.AGAINST_REFERENCE, lanes.get(1).direction().on(RoadPlanParams.DrivingSide.LHT));
        assertEquals(Lane.Direction.AGAINST_REFERENCE, lanes.get(0).direction().on(RoadPlanParams.DrivingSide.RHT));
        assertEquals(Lane.Direction.WITH_REFERENCE, lanes.get(1).direction().on(RoadPlanParams.DrivingSide.RHT));
        Lane alley = RoadProfiles.ALLEY_7.lanes().get(0);
        assertEquals(Lane.Direction.BOTH, alley.direction().on(RoadPlanParams.DrivingSide.RHT));
    }

    /** A client is left-hand until the server says otherwise, follows the server's TrafficSide, and is left-hand again once disconnected. */
    @Test
    void clientFollowsTheServersSideAndDefaultsToLeftHand() {
        EditorClient client = new EditorClient("test", "test", List.of(), message -> {
        }, line -> {
        });
        assertEquals(RoadPlanParams.DrivingSide.LHT, client.drivingSide());
        client.receive(Messages.encode(new Message.TrafficSide(true)));
        assertEquals(RoadPlanParams.DrivingSide.RHT, client.drivingSide());
        client.receive(Messages.encode(new Message.TrafficSide(false)));
        assertEquals(RoadPlanParams.DrivingSide.LHT, client.drivingSide());
        client.receive(Messages.encode(new Message.TrafficSide(true)));
        client.disconnected();
        assertEquals(RoadPlanParams.DrivingSide.LHT, client.drivingSide());
    }
}
