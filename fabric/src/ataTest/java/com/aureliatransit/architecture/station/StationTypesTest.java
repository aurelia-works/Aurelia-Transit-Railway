package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the two small station types: a one-platform halt and a kerb-side bus/tram stop. */
class StationTypesTest {

	private static StationPlan plan(String key) {
		final StationPresets.Preset preset = StationPresets.ALL.stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
		return StationPlanner.plan(StationDescriptionParser.parse(preset.description()).spec());
	}

	@Test
	void aHaltIsOneShortOpenPlatformWithoutABuilding() {
		final StationPlan plan = plan("halt");
		assertTrue(plan.spec().building() == BuildingStyle.NONE);
		assertTrue(plan.layout().platforms().size() == 1);
		assertTrue(plan.blocks().stream().anyMatch(b -> b.role() == Role.LAMP || b.blockId().contains("bus_shelter")),
				"the halt has a lamp or a shelter");
		assertTrue(LayoutValidator.validate(plan).isEmpty());
		assertTrue(StationAudit.audit(plan).buildable());
	}

	@Test
	void aBusOrTramStopHasAStreetTrackAndAStopSign() {
		final StationPlan plan = plan("bus_tram_stop");
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals("aurelia_transit_architecture:tram_track")));
		assertTrue(plan.blocks().stream().anyMatch(b -> b.blockId().equals("aurelia_transit_architecture:tram_stop_sign")));
		assertFalse(plan.spec().hasBuilding());
		assertTrue(LayoutValidator.validate(plan).isEmpty());
		assertTrue(StationAudit.audit(plan).buildable());
	}
}
