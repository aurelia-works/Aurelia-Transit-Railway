package org.mtr.test;

import com.aureliatransit.architecture.airport.AircraftSample;
import com.aureliatransit.architecture.airport.AirportDepartures;
import com.aureliatransit.architecture.airport.JetBridgeMotion;
import com.aureliatransit.architecture.transit.ServiceSnapshot;
import com.aureliatransit.architecture.transit.StationSnapshot;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public final class JetBridgeMotionTest {

	@Test
	public void aStoppedPlaneWithOpenDoorsExtendsTheBridge() {
		final AircraftSample docked = new AircraftSample(4, 1, 4, false, 0.4, true);
		Assertions.assertTrue(JetBridgeMotion.wants(4, 1, 4, List.of(docked)));
		float extension = 0;
		for (int i = 0; i < 40; i++) {
			extension = JetBridgeMotion.step(extension, true);
		}
		Assertions.assertEquals(1f, extension, 0.001f);
		extension = JetBridgeMotion.step(extension, true);
		Assertions.assertEquals(1f, extension, 0.001f);
	}

	@Test
	public void aMovingPlaneOrClosedDoorsOrATrainRetracts() {
		Assertions.assertFalse(JetBridgeMotion.wants(0, 0, 0, List.of(new AircraftSample(1, 0, 0, true, 1, true))));
		Assertions.assertFalse(JetBridgeMotion.wants(0, 0, 0, List.of(new AircraftSample(1, 0, 0, false, 0, true))));
		Assertions.assertFalse(JetBridgeMotion.wants(0, 0, 0, List.of(new AircraftSample(1, 0, 0, false, 1, false))));
		Assertions.assertFalse(JetBridgeMotion.wants(0, 0, 0, List.of(new AircraftSample(JetBridgeMotion.REACH + 2, 0, 0, false, 1, true))));
		float extension = 1f;
		extension = JetBridgeMotion.step(extension, false);
		Assertions.assertTrue(extension < 1f);
		for (int i = 0; i < 40; i++) {
			extension = JetBridgeMotion.step(extension, false);
		}
		Assertions.assertEquals(0f, extension, 0.001f);
	}

	@Test
	public void theBoardPrefersGateFlightsAndDropsDepartedOnes() {
		final long now = 1_000_000L;
		final ServiceSnapshot gate = service("Gate 2", "Lisbon", now + 120_000L);
		final ServiceSnapshot train = service("Platform 1", "Porto", now + 60_000L);
		final ServiceSnapshot gone = service("Gate 9", "Faro", now - 1);
		final StationSnapshot snapshot = new StationSnapshot(null, List.of(), List.of(gone, train, gate), 1);
		final List<String> lines = AirportDepartures.lines(snapshot, now, 5);
		Assertions.assertEquals(1, lines.size());
		Assertions.assertTrue(lines.get(0).contains("Gate 2"), lines.get(0));
		Assertions.assertTrue(lines.get(0).contains("Lisbon"), lines.get(0));
		Assertions.assertTrue(lines.get(0).startsWith("2m"), lines.get(0));
		Assertions.assertTrue(AirportDepartures.lines(new StationSnapshot(null, List.of(), List.of(train), 1), now, 5).get(0).contains("Porto"));
		Assertions.assertTrue(AirportDepartures.lines(StationSnapshot.EMPTY, now, 5).isEmpty());
	}

	private static ServiceSnapshot service(String platform, String destination, long departure) {
		return new ServiceSnapshot(1, "TP", "TP", 0xFFFFFF, destination, 2, platform, departure - 60_000L, departure, 0, false, false, List.of());
	}
}
