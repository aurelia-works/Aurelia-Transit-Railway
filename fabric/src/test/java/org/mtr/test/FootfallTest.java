package org.mtr.test;

import com.aureliatransit.architecture.transit.Footfall;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.Data;
import org.mtr.core.data.Position;
import org.mtr.core.data.Station;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.mod.servlet.MapLayerJson;

public final class FootfallTest {

	@Test
	public void theSameStationAndMinuteAlwaysAgree() {
		final long id = Footfall.key("Leidsche Rijn");
		final int morning = Footfall.passengersAtMinute(id, Footfall.MORNING_PEAK_MINUTE);
		Assertions.assertEquals(morning, Footfall.passengersAtMinute(id, Footfall.MORNING_PEAK_MINUTE));
		Assertions.assertEquals(morning, Footfall.passengers(id, Footfall.MORNING_PEAK_MINUTE * 60_000L));
		Assertions.assertTrue(Footfall.passengersAtMinute(id, 3 * 60) < morning, "night is quieter than the morning peak");
	}

	@Test
	public void peaksAreBusyForSomeStationsAndTheNightIsNot() {
		int busyStations = 0;
		int quietStations = 0;
		for (long id = 1; id <= 400; id++) {
			final int morning = Footfall.passengersAtMinute(id, Footfall.MORNING_PEAK_MINUTE);
			final int night = Footfall.passengersAtMinute(id, 3 * 60);
			Assertions.assertFalse(Footfall.busy(night), "station " + id + " is busy at night with " + night);
			Assertions.assertTrue(morning > night);
			if (Footfall.busy(morning)) {
				busyStations++;
				Assertions.assertEquals("Busy, try the next train", Footfall.notice(id, Footfall.MORNING_PEAK_MINUTE * 60_000L));
			} else {
				quietStations++;
				Assertions.assertNull(Footfall.notice(id, Footfall.MORNING_PEAK_MINUTE * 60_000L));
			}
		}
		Assertions.assertTrue(busyStations > 0, "no station is busy at the peak");
		Assertions.assertTrue(quietStations > 0, "every station is busy at the peak");
	}

	@Test
	public void theMapReportsTheSameCountTheDisplaysUse() {
		final Data data = new Data() {
		};
		final Station station = new Station(data);
		station.setName("Alpha");
		station.setCorners(new Position(-10, 0, -10), new Position(30, 128, 10));
		data.stations.add(station);
		data.sync();
		final long millis = 8 * 60 * 60_000L;
		final long previous = MapLayerJson.nowMillisOverride;
		MapLayerJson.nowMillisOverride = millis;
		try {
			final JsonObject stationJson = MapLayerJson.network(data, new MapLayerJson.ShapeCache()).getAsJsonArray("stations").get(0).getAsJsonObject();
			final int passengers = Footfall.passengers(station.getId(), millis);
			Assertions.assertEquals(passengers, stationJson.get("passengers").getAsInt());
			Assertions.assertEquals(Footfall.busy(passengers), stationJson.get("busy").getAsBoolean());
		} finally {
			MapLayerJson.nowMillisOverride = previous;
		}
	}
}
