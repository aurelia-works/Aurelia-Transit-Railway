package org.mtr.mod.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the rule that keeps trains from locking each other: a train in front of the express never gives way to it. */
class JunctionPriorityTest {

	@AfterEach
	void clear() {
		JunctionPriority.clear();
	}

	@Test
	void slowTrainGivesWayOnlyAtTheJunction() {
		final long express = 1;
		final long slow = 2;
		JunctionPriority.claim(express, Set.of("approach", "junction", "beyond"), 1_000);

		// coming in from the branch: waits for the express
		assertTrue(JunctionPriority.claimedByOther(slow, List.of("junction"), "branch", 1_500));
		// already standing on the express's track, so in front of it: never waits, or the two would lock
		assertFalse(JunctionPriority.claimedByOther(slow, List.of("junction"), "approach", 1_500));
		// a train never waits for its own claim
		assertFalse(JunctionPriority.claimedByOther(express, List.of("junction"), "elsewhere", 1_500));
		// a claim the express stopped renewing (it left or was removed) lets everyone through after its 2.5 s life
		assertFalse(JunctionPriority.claimedByOther(slow, List.of("junction"), "branch", 4_000));
	}

	/** The engine asks again a second after a train starts braking for the signal; it must not let the train go then. */
	@Test
	void aTrainThatStartedGivingWayStaysHeldAtTheSignal() {
		final GiveWay giveWay = new GiveWay();
		// the engine asks about each signal from the train's front onwards; 6 is the train's own block, not claimed
		// 22 m/s, 5 m outside its 30 m stopping distance: it can still stop, so it gives way
		assertFalse(giveWay.hold(6, -3, 30, false, 1_000));
		assertTrue(giveWay.hold(7, 35, 30, true, 1_000));
		// a second later it has braked to within stopping distance of the signal, and the express is still coming
		assertFalse(giveWay.hold(6, -14, 11.5, false, 2_000));
		assertTrue(giveWay.hold(7, 12, 11.5, true, 2_000));
		// standing at the signal
		assertTrue(giveWay.hold(7, 0, 0, true, 3_000));
		// the express is through, but a single missed renewal is not the express leaving: it keeps holding briefly
		assertTrue(giveWay.hold(7, 0, 0, false, 4_000));
		assertFalse(giveWay.hold(7, 0, 0, false, 6_000));
		// a train that never gave way at this signal and can no longer stop is not stopped in front of it
		assertFalse(new GiveWay().hold(7, 12, 30, true, 2_000));
	}

	/** A stall longer than the engine's ask gap must not release a train that is standing at the signal. */
	@Test
	void aStallAtTheSignalDoesNotReleaseTheTrain() {
		final GiveWay giveWay = new GiveWay();
		assertTrue(giveWay.hold(7, 30, 30, true, 1_000));
		// a 7 s stall, and the express's claim is missed on the first ask back
		assertTrue(giveWay.hold(7, 0, 0, false, 8_000));
		// the claim is still gone after the grace: now it may go
		assertFalse(giveWay.hold(7, 0, 0, false, 10_000));
	}

	/**
	 * Replays the engine's 1000 ms moving / 50 ms stopped signal loop at 8, 15 and 22 m/s toward a claimed junction
	 * signal block: the slower train must stop at the signal entrance, stay held while the express claim is live, and
	 * move off once the claim lapses or the 60 s cap is reached.
	 */
	@Test
	void slowerTrainStopsAtJunctionSignalAtAllSpeeds() {
		final Set<String> junction = Set.of("rail-30", "rail-31", "rail-32");
		for (final double cruise : new double[]{8.0, 15.0, 22.0}) {
			for (int offset = 0; offset < 20; offset++) {
				JunctionPriority.clear();
				final GiveWay giveWay = new GiveWay();
				double pos = 20.0 + offset * 1.1;
				double speed = cruise;
				long cooldown = 0;
				long stoppedSince = -1;
				double stopPoint = Double.POSITIVE_INFINITY;

				for (long now = 0; now <= 60_000; now += 50) {
					JunctionPriority.claim(1L, junction, now);
					final boolean stopped = speed <= 0;
					if (stopped || cooldown <= 0) {
						final double stopMeters = stopped ? 0 : 0.5 * speed * speed;
						final int front = (int) Math.floor(pos / 10.0);
						final int occupied = (int) Math.floor(GiveWay.occupiedProgress(pos) / 10.0);
						double blocked = -1;
						for (int i = front; i < 40 && i * 10.0 < pos + stopMeters + 8.0; i++) {
							if (i != 12 && i != 18 && i != 30) {
								continue;
							}
							final Collection<String> block = i == 30 ? junction : List.of("rail-" + i);
							if (giveWay.hold(i, i * 10.0 - pos, stopMeters, JunctionPriority.claimedByOther(2L, block, "rail-" + occupied, now), now)) {
								blocked = Math.max(0.0, i * 10.0 - pos);
								break;
							}
						}
						stopPoint = blocked >= 0 ? pos + blocked : Double.POSITIVE_INFINITY;
						if (!stopped) {
							cooldown = 1_000;
						}
					}
					if (!stopped && cooldown > 0) {
						cooldown -= 50;
					}

					if (speed <= 0) {
						if (Double.isInfinite(stopPoint)) {
							speed = 0.04;
							pos += speed * 0.05;
						}
					} else {
						final double remaining = stopPoint - pos;
						if (remaining < 0.5 * speed * speed) {
							speed = Math.max(4.0e-3, speed - 0.5 * speed * speed / Math.max(remaining, 1.0e-3) * 0.05);
						} else {
							speed = Math.min(cruise, speed + 0.04);
						}
						pos += speed * 0.05;
						if (pos >= stopPoint - 0.01) {
							pos = stopPoint;
							speed = 0;
						}
					}

					assertTrue(pos <= 300.0 + 1e-3, "Entered the claimed junction at " + cruise + " m/s, start offset " + offset);
					if (speed == 0 && Math.abs(pos - 300.0) < 1e-3) {
						if (stoppedSince < 0) {
							stoppedSince = now;
						} else if (now - stoppedSince >= 5_000) {
							break;
						}
					} else {
						stoppedSince = -1;
					}
				}
				assertTrue(stoppedSince >= 0, "Did not stop at the junction signal at " + cruise + " m/s, start offset " + offset);
			}
		}
	}
}
