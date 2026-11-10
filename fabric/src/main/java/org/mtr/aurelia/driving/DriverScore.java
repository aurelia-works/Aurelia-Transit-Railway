package org.mtr.aurelia.driving;

/**
 * A score for one manual run: from leaving a stop to coming to a stand at the next one.
 *
 * <p>Stop accuracy dominates, as it does on a real railway: full marks inside 0.3 m, falling to nothing at 3 m. SPADs
 * cost more than overspeeds; leaving without the crew's signal (when a guard or dispatcher works the train) costs a
 * little less than a SPAD. Time is recorded for every run and only costs points after three minutes, because MTR
 * does not give the client a timetable target to be early or late against.</p>
 */
public final class DriverScore {

	private static final int BASE_POINTS = 1000;
	private static final int ACCURACY_POINTS = 300;
	private static final double ACCURACY_ZERO_METRES = 3;
	private static final int SPAD_PENALTY = 300;
	private static final int OVERSPEED_PENALTY = 100;
	private static final int NO_RIGHT_AWAY_PENALTY = 200;
	private static final long FREE_TIME_MILLIS = 180_000;
	private static final long TIME_PENALTY_PER_MILLIS = 10_000;

	private long runStartedMillis = -1;
	private int spads;
	private int overspeeds;
	private int noRightAways;
	private Run lastRun;
	private int totalPoints;
	private int runs;

	/** @param noRightAways departures without the crew's signal */
	public record Run(long startedMillis, long endedMillis, double stopErrorMetres, int spads, int overspeeds, boolean platformStop, int noRightAways) {

		public long elapsedMillis() {
			return Math.max(0, endedMillis - startedMillis);
		}

		public int points() {
			int points = BASE_POINTS;
			if (platformStop && !Double.isNaN(stopErrorMetres)) {
				final double error = Math.abs(stopErrorMetres);
				final double accuracy = error <= 0.3 ? 1 : Math.max(0, (ACCURACY_ZERO_METRES - error) / (ACCURACY_ZERO_METRES - 0.3));
				points += (int) Math.round(ACCURACY_POINTS * accuracy);
			}
			points -= SPAD_PENALTY * spads + OVERSPEED_PENALTY * overspeeds + NO_RIGHT_AWAY_PENALTY * noRightAways;
			points -= (int) Math.max(0, (elapsedMillis() - FREE_TIME_MILLIS) / TIME_PENALTY_PER_MILLIS);
			return Math.max(0, points);
		}
	}

	public void depart(long nowMillis) {
		if (runStartedMillis < 0) {
			runStartedMillis = nowMillis;
			spads = 0;
			overspeeds = 0;
			noRightAways = 0;
		}
	}

	/** The train left a platform before its crew gave the signal. */
	public void noRightAway() {
		if (isRunning()) {
			noRightAways++;
		}
	}

	public int currentNoRightAways() {
		return noRightAways;
	}

	public boolean isRunning() {
		return runStartedMillis >= 0;
	}

	public void spad() {
		if (isRunning()) {
			spads++;
		}
	}

	public void overspeed() {
		if (isRunning()) {
			overspeeds++;
		}
	}

	/** Stop timing without recording a run, for a dismount or a change of train. */
	public void cancel() {
		runStartedMillis = -1;
	}

	public int currentSpads() {
		return spads;
	}

	public int currentOverspeeds() {
		return overspeeds;
	}

	public long runStartedMillis() {
		return runStartedMillis;
	}

	/** @param stopErrorMetres NaN when the stop was not at a platform stop marker */
	public Run stop(long nowMillis, double stopErrorMetres, boolean platformStop) {
		if (!isRunning()) {
			return null;
		}
		final Run run = new Run(runStartedMillis, nowMillis, stopErrorMetres, spads, overspeeds, platformStop, noRightAways);
		runStartedMillis = -1;
		lastRun = run;
		totalPoints += run.points();
		runs++;
		return run;
	}

	public Run getLastRun() {
		return lastRun;
	}

	public int totalPoints() {
		return totalPoints;
	}

	public int runs() {
		return runs;
	}
}
