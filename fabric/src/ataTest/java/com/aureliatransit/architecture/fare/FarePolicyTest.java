package com.aureliatransit.architecture.fare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarePolicyTest {

	@Test
	void missingTableAndZerosDoNotCharge() {
		final FarePolicy policy = FarePolicy.parse(0, 0, 0, null);
		assertEquals(0, policy.quote("Central", "Airport", 0));
		assertEquals(FarePolicy.OFF.enabled(), policy.enabled());
	}

	@Test
	void zonesAndDailyCap() {
		final FarePolicy policy = FarePolicy.parse(2, 3, 10, "Central=1, Airport=3, bad, nope=x");
		assertEquals(1, policy.zone(" central "));
		assertEquals(0, policy.zone("unknown"));
		// |3-1| = 2 zones, 2 + 3*2 = 8
		assertEquals(8, policy.quote("Central", "Airport", 0));
		assertEquals(4, policy.quote("Central", "Airport", 6));
		assertEquals(0, policy.quote("Central", "Airport", 10));
	}
}
