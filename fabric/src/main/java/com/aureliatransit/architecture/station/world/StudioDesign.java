package com.aureliatransit.architecture.station.world;

import com.aureliatransit.architecture.station.ParseResult;
import com.aureliatransit.architecture.station.StationDescriptionParser;

/** Whether the design Station Studio sent is still the one the player planned. */
final class StudioDesign {

	private StudioDesign() {
	}

	static boolean matchesPlan(ParseResult planned, String description) {
		return planned.spec().equals(StationDescriptionParser.parse(description).spec());
	}
}
