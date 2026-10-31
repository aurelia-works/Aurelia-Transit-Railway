package com.aureliatransit.architecture.fare;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardHistoryTest {

	@Test
	void capKeepsTheNewestAndSpentSumsExitFaresThatDay() {
		List<CardHistory.Tap> kept = new ArrayList<>();
		for (int i = 0; i < CardHistory.MAX + 3; i++) {
			kept = CardHistory.append(kept, new CardHistory.Tap("S" + i, false, 1, CardHistory.DAY_MILLIS + i));
		}
		assertEquals(CardHistory.MAX, kept.size());
		assertEquals("S3", kept.get(0).station());
		assertEquals(CardHistory.MAX, CardHistory.spentOnDay(kept, CardHistory.DAY_MILLIS + 10));
		assertEquals(0, CardHistory.spentOnDay(kept, 10));
	}
}
