package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.zip.ZipCurve;
import org.mtr.mod.zip.ZipTravel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZipMathTest {

	@Test
	public void sagCurveStaysOnThePolesAndHangsInTheMiddle() {
		final double[] point = new double[3];
		final double sag = 0.06;
		ZipCurve.point(0, 0, 20, 0, 40, 10, 0, sag, point);
		assertEquals(0, point[0], 1.0e-6);
		assertEquals(20, point[1], 1.0e-6);
		assertEquals(0, point[2], 1.0e-6);
		ZipCurve.point(1, 0, 20, 0, 40, 10, 0, sag, point);
		assertEquals(40, point[0], 1.0e-6);
		assertEquals(10, point[1], 1.0e-6);
		ZipCurve.point(0.5, 0, 20, 0, 40, 10, 0, sag, point);
		assertEquals(15.0 - ZipCurve.midDrop(0, 0, 40, 0, sag), point[1], 1.0e-6);
		assertTrue(point[1] < 15.0);
		ZipCurve.point(0.5, 0, 20, 0, 40, 10, 0, 0, point);
		assertEquals(15, point[1], 1.0e-6);
	}

	@Test
	public void downhillSpeedsUpAndASeatStopsAtThePole() {
		assertTrue(ZipTravel.mayDrop(20, 10));
		assertFalse(ZipTravel.mayDrop(10, 10));
		assertFalse(ZipTravel.mayDrop(10, 20));
		ZipTravel.Step step = new ZipTravel.Step(0, 0, false, false);
		for (int i = 0; i < 20; i++) {
			step = ZipTravel.gravity(step.t(), step.speed(), 1, ZipTravel.TICK_SECONDS, 0, 20, 0, 40, 10, 0, 0.06, 12, 0.4);
		}
		assertTrue(step.t() > 0);
		assertTrue(step.speed() > 0);
		assertFalse(step.release());
		final ZipTravel.Step end = ZipTravel.gravity(0.99, 30, 1, 1, 0, 20, 0, 40, 10, 0, 0.06, 12, 0.4);
		assertTrue(end.release());
		assertEquals(1, end.t(), 1.0e-6);

		ZipTravel.Step seat = new ZipTravel.Step(0.98, 20, false, false);
		for (int i = 0; i < 40; i++) {
			seat = ZipTravel.seat(seat.t(), seat.speed(), 1, ZipTravel.TICK_SECONDS, 0, 8, 0, 30, 12, 0, 0.06, 12, 1);
			assertTrue(seat.t() <= 1);
			assertTrue(seat.t() >= 0.98);
		}
		assertTrue(seat.arrived());
		assertEquals(1, seat.t(), 1.0e-6);
		assertFalse(seat.release());
	}
}
