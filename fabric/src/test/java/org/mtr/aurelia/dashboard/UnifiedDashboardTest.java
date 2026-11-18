package org.mtr.aurelia.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.aurelia.dashboard.DashboardLayout.ModeSlot;
import org.mtr.aurelia.dashboard.DashboardLayout.Rect;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.Filter;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.RoadVehicleRow;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.StationInterchangeRow;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.data.IGui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class UnifiedDashboardTest {

	@Test
	public void legacyDashboardItemsStartOnDedicatedModesWhilePrimaryRemembersLastMode() {
		// Fresh primary dashboard starts on Train
		assertEquals(DashboardMode.TRAIN, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, null));
		assertEquals(DashboardMode.TRAIN, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.TRAIN));

		// Primary dashboard remembers Bus & Road, Boat, Cable Car, or Plane once chosen
		assertEquals(DashboardMode.ROAD, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.ROAD));
		assertEquals(DashboardMode.BOAT, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.BOAT));
		assertEquals(DashboardMode.CABLE_CAR, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.CABLE_CAR));
		assertEquals(DashboardMode.AIRPLANE, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.AIRPLANE));

		// Legacy dashboard_2 (Boat), dashboard_3 (Cable Car), dashboard_4 (Airplane) always start on their own mode
		assertEquals(DashboardMode.BOAT, DashboardMode.resolveOpenMode(TransportMode.BOAT, true, DashboardMode.ROAD));
		assertEquals(DashboardMode.CABLE_CAR, DashboardMode.resolveOpenMode(TransportMode.CABLE_CAR, true, DashboardMode.TRAIN));
		assertEquals(DashboardMode.AIRPLANE, DashboardMode.resolveOpenMode(TransportMode.AIRPLANE, true, DashboardMode.BOAT));

		// Sneak-opening a station/platform/depot/siding uses the MTR transport mode rather than Road
		assertEquals(DashboardMode.TRAIN, DashboardMode.resolveOpenMode(TransportMode.TRAIN, false, DashboardMode.ROAD));
		assertEquals(DashboardMode.BOAT, DashboardMode.resolveOpenMode(TransportMode.BOAT, false, DashboardMode.ROAD));

		// Server open packet allows creative, op, or survival players carrying any dashboard item
		assertFalse(DashboardPackets.isAllowed(false, false, false));
		assertTrue(DashboardPackets.isAllowed(true, false, false));
		assertTrue(DashboardPackets.isAllowed(false, true, false));
		assertTrue(DashboardPackets.isAllowed(false, false, true));
		assertTrue(DashboardPackets.isDashboardItemId("mtr:dashboard"));
		assertTrue(DashboardPackets.isDashboardItemId("mtr:dashboard_4"));
		assertFalse(DashboardPackets.isDashboardItemId("minecraft:stick"));
	}

	@Test
	public void dashboardModeCycleAndSettingsRoundTrip(@TempDir Path tempDir) throws Exception {
		assertEquals(DashboardMode.ROAD, DashboardMode.TRAIN.next(1));
		assertEquals(DashboardMode.AIRPLANE, DashboardMode.TRAIN.next(-1));
		assertEquals(DashboardMode.TRAIN, DashboardMode.AIRPLANE.next(1));

		// Missing config defaults to TRAIN
		assertEquals(DashboardMode.TRAIN, DashboardSettings.read(tempDir));

		// Saved mode persists across reloads
		DashboardSettings.save(tempDir, DashboardMode.ROAD);
		assertEquals(DashboardMode.ROAD, DashboardSettings.read(tempDir));

		// Corrupt JSON falls back cleanly to TRAIN
		Files.writeString(tempDir.resolve("aurelia").resolve("dashboard.json"), "{broken");
		assertEquals(DashboardMode.TRAIN, DashboardSettings.read(tempDir));
	}

	@Test
	public void mapStateClampsInvalidNumbersAndPreservesCoordinates() {
		final DashboardMapState normal = new DashboardMapState(512.5, -240.25, 4.0, true);
		assertEquals(512.5, normal.centerX());
		assertEquals(-240.25, normal.centerZ());
		assertEquals(4.0, normal.scale());
		assertTrue(normal.currentY());

		final DashboardMapState nanSafe = new DashboardMapState(Double.NaN, Double.POSITIVE_INFINITY, -5.0, false);
		assertEquals(0.0, nanSafe.centerX());
		assertEquals(0.0, nanSafe.centerZ());
		assertEquals(DashboardMapState.DEFAULT_SCALE, nanSafe.scale());
	}

	@Test
	public void layoutFitsWithoutOverlapAt854x480And1080pWithAndWithoutMagic() {
		assertEquals(IGui.PANEL_WIDTH, DashboardLayout.PANEL_WIDTH);
		assertArrayEquals(new int[]{427, 240}, DashboardLayout.guiScaledSize(854, 480, 0));
		assertArrayEquals(new int[]{854, 480}, DashboardLayout.guiScaledSize(854, 480, 1));
		assertArrayEquals(new int[]{480, 270}, DashboardLayout.guiScaledSize(1920, 1080, 0));
		assertArrayEquals(new int[]{640, 360}, DashboardLayout.guiScaledSize(1920, 1080, 3));
		assertArrayEquals(new int[]{960, 540}, DashboardLayout.guiScaledSize(1920, 1080, 2));
		assertArrayEquals(new int[]{1920, 1080}, DashboardLayout.guiScaledSize(1920, 1080, 1));

		final int[][] resolutions = {
				DashboardLayout.guiScaledSize(854, 480, 0),   // 427x240 (min window auto scale)
				DashboardLayout.guiScaledSize(854, 480, 1),   // 854x480
				DashboardLayout.guiScaledSize(1920, 1080, 0), // 480x270 (1080p auto scale 4)
				DashboardLayout.guiScaledSize(1920, 1080, 3), // 640x360 (1080p scale 3)
				DashboardLayout.guiScaledSize(1920, 1080, 2), // 960x540 (1080p scale 2)
				DashboardLayout.guiScaledSize(1920, 1080, 1)  // 1920x1080 (1080p scale 1)
		};

		for (final boolean magicLoaded : new boolean[]{false, true}) {
			for (final int[] size : resolutions) {
				final int w = size[0];
				final int h = size[1];
				final DashboardLayout layout = DashboardLayout.compute(w, h, magicLoaded);
				final Rect modeBar = layout.modeBarBounds();
				final Rect mouseCoords = new Rect(w - 6 - 96, layout.mouseCoordsY(), 96, 10);
				final Rect editHint = new Rect(IGui.PANEL_WIDTH + 6, layout.editHintY(), 180, 10);

				assertEquals(5, layout.modeButtons().size());
				assertTrue(modeBar.x() >= IGui.PANEL_WIDTH, "mode bar clears left panel");
				assertTrue(modeBar.right() <= w - DashboardLayout.RIGHT_MARGIN, "mode bar stays inside right screen margin at " + w + "x" + h);
				assertFalse(modeBar.overlaps(layout.leftPanel()), "no overlap with IGui.PANEL_WIDTH panel");
				assertFalse(modeBar.overlaps(layout.atuButton()), "no overlap with Aurelia panel button");
				assertFalse(modeBar.overlaps(layout.atrMenuButton()), "no overlap with ATR menu button");
				assertFalse(modeBar.overlaps(layout.bottomControls()), "no overlap with bottom map buttons");
				assertFalse(modeBar.overlaps(mouseCoords), "no overlap with top-right world coordinates at " + w + "x" + h);
				assertFalse(editHint.overlaps(modeBar), "edit hint clears mode bar");
				assertFalse(editHint.overlaps(layout.atuButton()), "edit hint clears ATU button");
				assertFalse(editHint.overlaps(layout.atrMenuButton()), "edit hint clears ATR menu button");

				if (magicLoaded) {
					assertNotNull(layout.magicButton());
					assertFalse(modeBar.overlaps(layout.magicButton()), "no overlap with MAGIC overlay button");
					assertFalse(layout.atuButton().overlaps(layout.magicButton()), "ATU button sits below MAGIC button");
				}

				for (int i = 0; i < layout.modeButtons().size(); i++) {
					final ModeSlot slot = layout.modeButtons().get(i);
					assertTrue(slot.bounds().width() >= slot.mode().baseTextWidth + 4,
							slot.mode() + " fits its full label at " + w + "x" + h);
					for (int j = i + 1; j < layout.modeButtons().size(); j++) {
						assertFalse(slot.bounds().overlaps(layout.modeButtons().get(j).bounds()), "mode tabs never overlap each other");
					}
				}
			}
		}
	}

	@Test
	public void roadVehicleFilteringAndSortingCoversAllKindsQueriesAndRiddenPriority() {
		assertEquals(36.0, RoadVehicleListModel.speedKmhFromMetresPerTick(0.5), 1e-6);
		assertEquals(18.0, RoadVehicleListModel.speedKmhFromMetresPerTick(-0.25), 1e-6);

		final List<RoadVehicleRow> vehicles = List.of(
				new RoadVehicleRow(10, "car", "Car", 0.0, "", 0, 2, false, false, 0x2F5DA8, 0f, 100, 64, 200, 12.0),
				new RoadVehicleRow(11, "bus", "City Bus", 48.0, "Alex", 6, 24, false, false, 0xD9534F, 90f, 140, 64, 210, 45.0),
				new RoadVehicleRow(12, "bicycle", "Hire bike", 16.0, "Boon", 1, 1, true, true, 0xC8312B, 180f, 95, 64, 198, 25.0),
				new RoadVehicleRow(13, "taxi", "Yellow Taxi", 32.0, "Sam", 2, 4, false, false, 0xF0AD4E, 270f, -50, 64, 80, 5.0),
				new RoadVehicleRow(14, "van", "Delivery Van", 0.0, "", 0, 2, false, false, 0x5BC0DE, 0f, 300, 64, -120, 80.0)
		);

		// All: local player's ridden vehicle (id 12, 25m) sorts BEFORE closer non-ridden taxi (id 13, 5m), then by distance
		final List<RoadVehicleRow> all = RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.ALL, "");
		assertEquals(List.of(12, 13, 10, 11, 14), all.stream().map(RoadVehicleRow::entityId).toList());

		// Kind filters
		assertEquals(List.of(11), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.BUS, "").stream().map(RoadVehicleRow::entityId).toList());
		assertEquals(List.of(10), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.CAR, "").stream().map(RoadVehicleRow::entityId).toList());
		assertEquals(List.of(13), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.TAXI, "").stream().map(RoadVehicleRow::entityId).toList());
		assertEquals(List.of(14), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.VAN, "").stream().map(RoadVehicleRow::entityId).toList());
		assertEquals(List.of(12), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.BICYCLE, "").stream().map(RoadVehicleRow::entityId).toList());

		// Active (moving or occupied) excludes parked empty car (10) and van (14)
		assertEquals(List.of(12, 13, 11), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.ACTIVE, "").stream().map(RoadVehicleRow::entityId).toList());

		// Search by driver name, hired flag, and block coordinates
		assertEquals(List.of(11), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.ALL, "alex").stream().map(RoadVehicleRow::entityId).toList());
		assertEquals(List.of(12), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.ALL, "hired").stream().map(RoadVehicleRow::entityId).toList());
		assertEquals(List.of(14), RoadVehicleListModel.filterAndSortVehicles(vehicles, Filter.ALL, "300 -120").stream().map(RoadVehicleRow::entityId).toList());

		// Dynamic filter discovery
		assertEquals(List.of(Filter.ALL, Filter.CAR, Filter.BICYCLE, Filter.ACTIVE),
				RoadVehicleListModel.availableFilters(List.of("car", "bicycle")));
		assertEquals(List.of(Filter.ALL, Filter.BUS, Filter.CAR, Filter.TAXI, Filter.VAN, Filter.BICYCLE, Filter.ACTIVE),
				RoadVehicleListModel.availableFilters(List.of("bus", "car", "taxi", "van", "bicycle")));
	}

	@Test
	public void stationInterchangeFilterAndNearestSorting() {
		final List<StationInterchangeRow> stations = List.of(
				new StationInterchangeRow(1L, "Harbour Halt", 2, 0x3366CC, 1, 1, 1, 400, 100, 180.0),
				new StationInterchangeRow(2L, "Central Bus & Rail", 1, 0xCC3333, 4, 3, 2, 20, -10, 22.0),
				new StationInterchangeRow(3L, "Airport Pier", 3, 0x33AA66, 2, 1, 2, 120, 60, 95.0)
		);

		final List<StationInterchangeRow> all = RoadVehicleListModel.filterAndSortStations(stations, false, "");
		assertEquals(List.of(2L, 3L, 1L), all.stream().map(StationInterchangeRow::stationId).toList());

		final List<StationInterchangeRow> interchanges = RoadVehicleListModel.filterAndSortStations(stations, true, "");
		assertEquals(List.of(2L, 3L), interchanges.stream().map(StationInterchangeRow::stationId).toList());

		final List<StationInterchangeRow> zoneSearch = RoadVehicleListModel.filterAndSortStations(stations, false, "zone 2");
		assertEquals(List.of(1L), zoneSearch.stream().map(StationInterchangeRow::stationId).toList());
	}
}
