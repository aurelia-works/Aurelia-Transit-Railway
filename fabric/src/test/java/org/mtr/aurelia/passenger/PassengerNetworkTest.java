package org.mtr.aurelia.passenger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.passenger.PassengerNetwork;

import java.util.Random;
import java.util.Set;

/**
 * A three-station line A - B - C: passengers wait at A, board a train that stops at B and C, and each gets off at
 * their own station. Caps hold, and a deleted train takes its riders with it.
 */
public final class PassengerNetworkTest {

	private static final long A = 1, B = 2, C = 3;
	private static final long PLATFORM_A = 11, PLATFORM_B = 12, PLATFORM_C = 13;
	private static final long TRAIN = 100;
	private static final PassengerNetwork.Settings SETTINGS = new PassengerNetwork.Settings(true, 30, 20, 1000, 40);

	@Test
	public void passengersBoardRideAndLeaveAtTheirStation() {
		final PassengerNetwork network = new PassengerNetwork();
		network.spawn(PLATFORM_A, new long[]{B, C}, 1, SETTINGS, 0, new Random(1));
		Assertions.assertEquals(20, network.waitingAt(PLATFORM_A), "30 arrived in a minute but the platform holds 20");

		// The train stops at A going to B and C. Two cars: six through the doors a second.
		int boarded = 0;
		for (int second = 0; second < 10; second++) {
			boarded += network.exchange(TRAIN, PLATFORM_A, A, Set.of(B, C), 2, SETTINGS).boarded();
		}
		Assertions.assertEquals(20, boarded);
		Assertions.assertEquals(0, network.waitingAt(PLATFORM_A));
		Assertions.assertEquals(20, network.ridingOn(TRAIN));

		// At B only the B passengers leave; the C passengers stay on.
		int alightedAtB = 0;
		for (int second = 0; second < 10; second++) {
			alightedAtB += network.exchange(TRAIN, PLATFORM_B, B, Set.of(C), 2, SETTINGS).alighted();
		}
		Assertions.assertTrue(alightedAtB > 0 && alightedAtB < 20, "some went to B, some to C: " + alightedAtB);
		Assertions.assertEquals(20 - alightedAtB, network.ridingOn(TRAIN));

		// At the terminus C everyone left gets off.
		for (int second = 0; second < 10; second++) {
			network.exchange(TRAIN, PLATFORM_C, C, Set.of(), 2, SETTINGS);
		}
		Assertions.assertEquals(0, network.ridingOn(TRAIN));
		Assertions.assertEquals(0, network.total());
		Assertions.assertEquals(20, network.alightedTotal());
	}

	@Test
	public void capsHoldAndGoneTrainsTakeTheirRiders() {
		final PassengerNetwork network = new PassengerNetwork();
		final PassengerNetwork.Settings small = new PassengerNetwork.Settings(true, 60, 15, 25, 5);
		network.spawn(PLATFORM_A, new long[]{B}, 1, small, 0, new Random(2));
		network.spawn(PLATFORM_B, new long[]{C}, 1, small, 0, new Random(2));
		Assertions.assertEquals(15, network.waitingAt(PLATFORM_A), "a platform holds 15");
		Assertions.assertEquals(10, network.waitingAt(PLATFORM_B), "the whole world holds 25, so B gets the last 10");

		for (int second = 0; second < 10; second++) {
			network.exchange(TRAIN, PLATFORM_A, A, Set.of(B), 1, small);
		}
		Assertions.assertEquals(5, network.ridingOn(TRAIN), "one car with five seats");

		// The train is deleted and platform B is removed: their passengers go too.
		network.retain(Set.of(), Set.of(PLATFORM_A));
		Assertions.assertEquals(0, network.waitingAt(PLATFORM_B));
		Assertions.assertEquals(0, network.ridingOn(TRAIN));
		Assertions.assertEquals(10, network.total(), "only A's ten left waiting");

		network.expire(PassengerNetwork.GIVE_UP_MILLIS + 1);
		Assertions.assertEquals(0, network.total(), "waited too long and went home");
	}
}
