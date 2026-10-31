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
}
