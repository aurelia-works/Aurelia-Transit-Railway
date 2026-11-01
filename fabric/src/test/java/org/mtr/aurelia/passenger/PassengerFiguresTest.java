package org.mtr.aurelia.passenger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.mtr.aurelia.client.PassengerFigures;
import org.mtr.libraries.com.google.gson.JsonObject;

/**
 * Drawing stays cheap: nothing past the draw distance, one figure in the middle band, and a cap up close.
 * A missing passenger-draw file's keys stay at those defaults.
 */
public final class PassengerFiguresTest {

	@Test
	public void distanceDecidesHowManyFigures() {
		Assertions.assertEquals(0, PassengerFigures.toDraw(40, 8, 33, 32, 12));
		Assertions.assertEquals(1, PassengerFigures.toDraw(40, 8, 20, 32, 12));
		Assertions.assertEquals(8, PassengerFigures.toDraw(40, 8, 10, 32, 12));
		Assertions.assertEquals(3, PassengerFigures.toDraw(3, 8, 10, 32, 12));
		Assertions.assertEquals(0, PassengerFigures.toDraw(40, 8, 10, 0, 0));

		final double[] at = new double[3];
		PassengerFigures.place(0, 64, 0, 10, 64, 0, 0, 1, 0.9, at);
		Assertions.assertEquals(5, at[0], 0.01);
		Assertions.assertEquals(64, at[1], 0.01);
		Assertions.assertEquals(0.9, at[2], 0.01);
		PassengerFigures.place(0, 64, 0, 10, 64, 0, 0, 1, 0, at);
		Assertions.assertEquals(5, at[0], 0.01);
		Assertions.assertEquals(0, at[2], 0.01);
	}

	@Test
	public void missingKeysKeepTheCheapDefaults() {
		final PassengerDrawSettings.Values read = PassengerDrawSettings.from(new JsonObject());
		Assertions.assertEquals(PassengerDrawSettings.DEFAULT, read);

		final JsonObject partial = new JsonObject();
		partial.addProperty("draw", true);
		partial.addProperty("detailDistance", 100);
		final PassengerDrawSettings.Values clamped = PassengerDrawSettings.from(partial);
		Assertions.assertTrue(clamped.draw());
		Assertions.assertEquals(PassengerDrawSettings.DEFAULT.drawDistance(), clamped.drawDistance());
		Assertions.assertEquals(PassengerDrawSettings.DEFAULT.maxPerPlace(), clamped.maxPerPlace());
		Assertions.assertTrue(clamped.detailDistance() <= clamped.drawDistance());
	}

	@Test
	public void standingOnALongPlatformCountsAsNear() {
		// 64 blocks long, camera at one end: 32 blocks from the middle, but on the platform
		final double distance = PassengerFigures.distanceToPlatform(0, 66, 3, 0, 64, 0, 64, 64, 0);
		Assertions.assertEquals(3.6, distance, 0.1);
		Assertions.assertEquals(8, PassengerFigures.toDraw(40, 8, distance, 32, 12));
	}

	@Test
	public void figuresStandOnThePlatformNotInTheTrain() {
		// Rail at y 64 along x. The only platform is on the +z side, its top at 65, from 2 blocks out. Ground at 64 elsewhere.
		final PassengerFigures.Ground ground = (x, z, railY) -> z >= 2 && z < 6 ? 65 : 64;
		final double[] spots = new double[8 * 3];
		PassengerFigures.spots(0, 64, 0, 32, 64, 0, 8, ground, spots);
		for (int i = 0; i < 8; i++) {
			Assertions.assertTrue(spots[i * 3 + 2] >= 2 && spots[i * 3 + 2] < 6, "figure " + i + " z " + spots[i * 3 + 2]);
			Assertions.assertEquals(65, spots[i * 3 + 1], 0.01, "figure " + i);
		}
	}
}
