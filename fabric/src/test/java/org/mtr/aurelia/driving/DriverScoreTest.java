package org.mtr.aurelia.driving;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriverScoreTest {

	@Test
	void cleanStopScoresAccuracy() {
		final DriverScore score = new DriverScore();
		score.depart(0);
		final DriverScore.Run run = score.stop(60_000, 0.2, true);
		assertNotNull(run);
		assertEquals(1_300, run.points(), "1000 base plus full accuracy");
		assertEquals(1_300, score.totalPoints());
	}

	@Test
	void accuracyFallsWithTheStopError() {
		final DriverScore score = new DriverScore();
		score.depart(0);
		final DriverScore.Run run = score.stop(60_000, 3.0, true);
		assertEquals(1_000, run.points());
	}

	@Test
	void spadsCostMoreThanOverspeeds() {
		final DriverScore score = new DriverScore();
		score.depart(0);
		score.spad();
		score.overspeed();
		final DriverScore.Run run = score.stop(60_000, 1.5, true);
		assertEquals(767, run.points(), "1000 plus 167 accuracy, less 300 and 100");
	}

	@Test
	void timeOnlyCostsAfterThreeMinutes() {
		final DriverScore score = new DriverScore();
		score.depart(0);
		assertEquals(1_300, score.stop(180_000, 0, true).points());
		score.depart(180_000);
		assertEquals(1_298, score.stop(380_000, 0, true).points());
	}

	@Test
	void stopWithoutDepartureDoesNothing() {
		final DriverScore score = new DriverScore();
		assertNull(score.stop(0, 0, true));
		assertEquals(0, score.runs());
	}

	@Test
	void aNewRunStartsWithFreshCounters() {
		final DriverScore score = new DriverScore();
		score.depart(0);
		score.spad();
		score.stop(10_000, 0, true);
		score.depart(20_000);
		final DriverScore.Run second = score.stop(30_000, 0, true);
		assertEquals(0, second.spads());
		assertEquals(2, score.runs());
	}
}
