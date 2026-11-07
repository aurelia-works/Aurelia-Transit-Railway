package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the measured-station report: a named real station gets its deviations, an unnamed one gets no invented target. */
class StationAuditTest {

	@Test
	void aNamedRealStationReportsItsMeasuredDeviation() {
		final StationSpec spec = StationDescriptionParser.parse("eight track amsterdam station, four island platforms, 240 long, "
				+ "train shed, named Amsterdam Centraal").spec();
		final StationAudit.Result result = StationAudit.audit(StationPlanner.plan(spec));
		final StationAudit.Check fit = result.checks().stream().filter(c -> c.topic() == StationAudit.Topic.REAL).findFirst().orElse(null);
		assertNotNull(fit, "Amsterdam Centraal has a research sheet");
		assertTrue(fit.message().contains("15 tracks") && fit.message().contains("-47%"), fit.message());
	}

	@Test
	void anUnmeasuredStationGetsNoRealFitCheck() {
		final StationSpec spec = StationDescriptionParser.parse("four track modern station, named Somewhere New").spec();
		final StationAudit.Result result = StationAudit.audit(StationPlanner.plan(spec));
		assertFalse(result.checks().stream().anyMatch(c -> c.topic() == StationAudit.Topic.REAL));
	}
}
