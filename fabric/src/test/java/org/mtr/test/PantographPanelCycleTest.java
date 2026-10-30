package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.core.serializer.JsonReader;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.mod.config.Client;
import org.mtr.mod.resource.PantographEnd;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PantographPanelCycleTest {

	@Test
	public void theDefaultEndButtonCyclesFrontRearBothWithoutADeadClick() {
		final Client client = new Client(new JsonReader(new JsonObject()));
		assertEquals(PantographEnd.BOTH, client.getPantographDefaultEnd());
		client.cyclePantographDefaultEnd();
		assertEquals(PantographEnd.FRONT, client.getPantographDefaultEnd());
		client.cyclePantographDefaultEnd();
		assertEquals(PantographEnd.REAR, client.getPantographDefaultEnd());
		client.cyclePantographDefaultEnd();
		assertEquals(PantographEnd.BOTH, client.getPantographDefaultEnd());
	}
}
