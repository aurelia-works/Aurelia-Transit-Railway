package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.StationDescriptionParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** "Build it" built the last plan even after the design on the page had changed. */
public final class StudioDesignTest {

	@Test
	public void aChangedDesignNoLongerMatchesThePlan() {
		final String planned = "a through station with 2 tracks and 120 block platforms";
		Assertions.assertTrue(StudioDesign.matchesPlan(StationDescriptionParser.parse(planned), planned));
		Assertions.assertFalse(StudioDesign.matchesPlan(StationDescriptionParser.parse(planned), "a through station with 6 tracks and 120 block platforms"));
	}
}
