package org.mtr.aurelia.crossing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mtr.mod.cargo.CrossingTrain;

/**
 * Tour 2026-11-24: a train at 80 km/h reached the crossing with the arm still half up. Council round 1: a standing or
 * other-line train must not hold the barriers, and a crossing that can't see the trains must not open.
 */
final class CrossingRuleTest {
    private static final double KMH_80 = 80 / 3.6;

    /** MTR's default acceleration, 4E-6 m/ms², in blocks per second squared. */
    private static final double DEFAULT_ACCELERATION = 4;

    /** A train heading along the x axis towards x = 0 from {@code headX}, {@code z} blocks off the crossing's line. */
    private static CrossingTrain train(double headX, double z, boolean moving, double speed) {
        return train(headX, z, moving, speed, 0, moving ? 0 : Double.POSITIVE_INFINITY);
    }

    private static CrossingTrain train(double headX, double z, boolean moving, double speed, double acceleration, double departsIn) {
        List<double[]> ahead = new ArrayList<>();
        for (double distance = 0; distance <= 400; distance += 2) {
            ahead.add(new double[]{headX - distance, 0, z, distance});
        }
        return new CrossingTrain(List.of(new double[]{headX, 0, z}, new double[]{headX + 20, 0, z}), moving, speed, acceleration, ahead, departsIn);
    }

    private static boolean closes(CrossingTrain train) {
        return CrossingRule.closes(List.of(train), 0.5, 0.5, 0.5);
    }

    @Test
    void an80KmhTrainClosesTheCrossingInTimeForTheArmToBeDown() {
        // gets there in the arm's swing, plus the crossing being late, plus a second: must be closing already
        double seconds = CrossingRule.ARM_SECONDS + CrossingRule.LATE_SECONDS + 1;
        assertTrue(closes(train(CrossingRule.ON_CROSSING + KMH_80 * seconds, 0, true, KMH_80)));
        assertFalse(closes(train(CrossingRule.ON_CROSSING + KMH_80 * 20, 0, true, KMH_80)), "20 s away is too early");
    }

    @Test
    void aTrainStandingAtAPlatformFurtherOffLetsItOpen() {
        assertFalse(closes(train(60, 0, false, 0)));
        assertTrue(closes(train(10, 0, false, 0)), "one standing on the crossing keeps it closed");
    }

    @Test
    void aTrainLeavingAPlatformNearbyFindsTheArmAlreadyDown() {
        // the tour's crossing sat about 48 blocks from each platform end; from a standstill at MTR's default acceleration
        // a train gets there in about 4 s, quicker than the arm comes down, so the crossing must close before it leaves
        CrossingTrain dwelling = train(48, 0, false, 0, DEFAULT_ACCELERATION, 30);
        assertFalse(closes(dwelling), "30 s of dwell left: still open");
        assertTrue(closes(train(48, 0, false, 0, DEFAULT_ACCELERATION, 3)), "about to leave: closed");
        assertTrue(closes(train(48, 0, false, 0, DEFAULT_ACCELERATION, Double.NaN)), "held at a signal: closed");
        assertFalse(closes(train(48, 0, false, 0, DEFAULT_ACCELERATION, Double.POSITIVE_INFINITY)), "parked off its route");
    }

    @Test
    void aTrainOnAnotherLineDoesNotCloseIt() {
        assertFalse(closes(train(100, 30, true, KMH_80)));
    }

    @Test
    void withNoFreshReadOfTheTrainsItStaysAsItIs() {
        assertTrue(CrossingRule.closed(false, null, 0.5, 0.5, 0.5, true));
        assertFalse(CrossingRule.closed(false, null, 0.5, 0.5, 0.5, false));
        assertTrue(CrossingRule.closed(true, List.of(), 0.5, 0.5, 0.5, false), "redstone closes it");
    }
}
