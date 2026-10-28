package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.client.RainInsideVehicle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RainInsideVehicleTest {

	@Test
	public void hidesRainWhenRidingAndEnabled() {
		assertTrue(RainInsideVehicle.shouldHideRain(true, true));
	}

	@Test
	public void keepsRainWhenNotRiding() {
		assertFalse(RainInsideVehicle.shouldHideRain(false, true));
	}

	@Test
	public void keepsRainWhenSwitchedOff() {
		assertFalse(RainInsideVehicle.shouldHideRain(true, false));
		assertFalse(RainInsideVehicle.shouldHideRain(false, false));
	}
}
