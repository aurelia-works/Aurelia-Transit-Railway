package org.mtr.aurelia.flight;

import org.junit.jupiter.api.Test;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.data.AirplanePath;

import static org.junit.jupiter.api.Assertions.*;

/** The position correction runs on every vehicle's position lookup, so it must leave every mode but airplanes alone. */
public final class AirplaneOnlyTest {

	@Test
	public void onlyAirplanesAreCorrected() {
		assertTrue(AirplanePath.appliesTo(TransportMode.AIRPLANE));
		for (final TransportMode mode : TransportMode.values()) {
			if (mode != TransportMode.AIRPLANE) {
				assertFalse(AirplanePath.appliesTo(mode), mode + " keeps the core's position");
			}
		}
	}
}
