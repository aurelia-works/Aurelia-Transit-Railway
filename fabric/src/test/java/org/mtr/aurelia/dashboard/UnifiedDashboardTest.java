package org.mtr.aurelia.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.aurelia.dashboard.DashboardLayout.ModeSlot;
import org.mtr.aurelia.dashboard.DashboardLayout.Rect;
import org.mtr.aurelia.dashboard.DashboardOpenState.PacketResolution;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.Filter;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.RoadVehicleRow;
import org.mtr.aurelia.dashboard.RoadVehicleListModel.StationInterchangeRow;
import org.mtr.core.data.Data;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Position;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.data.Station;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.data.IGui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

		// Server open packet allows ops, or players carrying any dashboard item; creative mode alone does not
		assertFalse(DashboardPackets.isAllowed(false, false));
		assertTrue(DashboardPackets.isAllowed(true, false));
		assertTrue(DashboardPackets.isAllowed(false, true));
		assertTrue(DashboardPackets.isDashboardItemId("mtr:dashboard"));
		assertTrue(DashboardPackets.isDashboardItemId("mtr:dashboard_4"));
		assertFalse(DashboardPackets.isDashboardItemId("minecraft:stick"));
		assertFalse(DashboardPackets.isDashboardItemId(null));

		// Primary vs legacy item tooltip lines
		assertTrue(DashboardPackets.isPrimaryDashboardItemId("mtr:dashboard"));
		assertFalse(DashboardPackets.isPrimaryDashboardItemId("mtr:dashboard_2"));
		assertEquals(
				List.of("item.aurelia.transit_dashboard.modes", "item.aurelia.transit_dashboard.sneak"),
				DashboardPackets.tooltipTranslationKeys("mtr:dashboard")
		);
		assertEquals(List.of("item.aurelia.transit_dashboard.legacy"), DashboardPackets.tooltipTranslationKeys("mtr:dashboard_2"));
		assertEquals(List.of("item.aurelia.transit_dashboard.legacy"), DashboardPackets.tooltipTranslationKeys("mtr:dashboard_3"));
		assertEquals(List.of("item.aurelia.transit_dashboard.legacy"), DashboardPackets.tooltipTranslationKeys("mtr:dashboard_4"));
		assertEquals(List.of(), DashboardPackets.tooltipTranslationKeys("minecraft:stick"));
		assertEquals(List.of(), DashboardPackets.tooltipTranslationKeys(null));
	}

	@Test
	public void pendingOpenStateExpiresDisconnectsIgnoresLegacyItemsAndPreservesSneakLastMode() {
		final DashboardOpenState state = new DashboardOpenState();
		final Object menuScreen = new Object();
		final Object otherScreen = new Object();

		// Sneak-edit (screenType != DEFAULT) never mutates lastMode and is not handled by unified default screen
		final PacketResolution sneakBoat = state.resolvePacket(TransportMode.BOAT, false, DashboardMode.ROAD, null, 1_000L);
		assertFalse(sneakBoat.handledByUnified());
		assertNull(sneakBoat.targetMode());
		assertFalse(sneakBoat.updateLastMode());

		// Armed menu request is NOT consumed or applied when legacy dashboard_2 (BOAT) arrives
		state.arm(DashboardMode.ROAD, menuScreen, 1_000L);
		assertTrue(state.hasPending(1_500L));
		final PacketResolution legacyBoat = state.resolvePacket(TransportMode.BOAT, true, DashboardMode.TRAIN, null, 1_500L);
		assertTrue(legacyBoat.handledByUnified());
		assertEquals(DashboardMode.BOAT, legacyBoat.targetMode());
		assertFalse(legacyBoat.forceReplaceScreen());
		assertTrue(state.hasPending(1_500L), "pending menu request remains untouched by non-TRAIN legacy packet");

		// Armed menu request IS consumed on TRAIN + DEFAULT, honoring forceReplaceScreen only for null or originScreen
		final PacketResolution fromOrigin = state.resolvePacket(TransportMode.TRAIN, true, DashboardMode.TRAIN, menuScreen, 2_000L);
		assertTrue(fromOrigin.handledByUnified());
		assertEquals(DashboardMode.ROAD, fromOrigin.targetMode());
		assertTrue(fromOrigin.forceReplaceScreen());
		assertFalse(state.hasPending(2_000L), "consumed once");

		// Unrelated currentScreen does not force-replace
		state.arm(DashboardMode.BOAT, menuScreen, 3_000L);
		final PacketResolution onDifferentScreen = state.resolvePacket(TransportMode.TRAIN, true, DashboardMode.TRAIN, otherScreen, 3_200L);
		assertEquals(DashboardMode.BOAT, onDifferentScreen.targetMode());
		assertFalse(onDifferentScreen.forceReplaceScreen());

		// Expires after 3_000 ms
		state.arm(DashboardMode.AIRPLANE, menuScreen, 5_000L);
		assertTrue(state.hasPending(8_000L));
		assertFalse(state.hasPending(8_001L));
		final PacketResolution afterExpiry = state.resolvePacket(TransportMode.TRAIN, true, DashboardMode.ROAD, menuScreen, 8_005L);
		assertEquals(DashboardMode.ROAD, afterExpiry.targetMode());
		assertFalse(afterExpiry.forceReplaceScreen());

		// Disconnect clears pending state immediately
		state.arm(DashboardMode.CABLE_CAR, menuScreen, 10_000L);
		state.clear();
		assertFalse(state.hasPending(10_050L));
	}

	@Test
	public void tabBarUpdatesAcrossResizesAndLocksDuringEditsOrMagicOverlay() {
		final DashboardTabBar tabBar = new DashboardTabBar();

		final DashboardLayout minWindow = tabBar.rebuild(DashboardMode.TRAIN, 427, 240, false);
		assertEquals(1, tabBar.generation());
		assertEquals(DashboardMode.TRAIN, tabBar.activeMode());
		assertEquals(5, tabBar.slots().size());
		final int minRoadWidth = minWindow.modeButtons().get(1).bounds().width();

		final DashboardLayout fullHdScale2 = tabBar.rebuild(DashboardMode.TRAIN, 960, 540, true);
		assertEquals(2, tabBar.generation());
		assertEquals(5, tabBar.slots().size());
		final int hdRoadWidth = fullHdScale2.modeButtons().get(1).bounds().width();
		assertTrue(hdRoadWidth >= minRoadWidth, "resized tab bar replaces narrower 427x240 slot bounds");
		assertEquals(hdRoadWidth, tabBar.slots().get(1).bounds().width());

		// Mode tabs lock while editing a station/depot/route or while MAGIC dropdown is open
		assertTrue(tabBar.updateEnabled(false, false));
		assertFalse(tabBar.updateEnabled(true, false));
		assertFalse(tabBar.updateEnabled(false, true));
		assertFalse(tabBar.updateEnabled(true, true));
		assertTrue(tabBar.updateEnabled(false, false));
	}

	@Test
	public void dashboardSettingsRoundTrip(@TempDir Path tempDir) throws Exception {
		// Missing config defaults to TRAIN
		assertEquals(DashboardMode.TRAIN, DashboardSettings.read(tempDir));

		// Saved mode persists across reloads
		DashboardSettings.save(tempDir, DashboardMode.ROAD);
		assertEquals(DashboardMode.ROAD, DashboardSettings.read(tempDir));

		// Corrupt JSON falls back cleanly to TRAIN
		final Path broken = tempDir.resolve("broken");
		Files.createDirectories(broken.resolve("aurelia"));
		Files.writeString(broken.resolve("aurelia").resolve("settings.json"), "{broken");
		assertEquals(DashboardMode.TRAIN, DashboardSettings.read(broken));
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

		// Filter + query helper used when selecting map markers across sub-tabs
		assertTrue(RoadVehicleListModel.matchesVehicleFilterAndQuery(vehicles.get(1), Filter.BUS, "alex"));
		assertFalse(RoadVehicleListModel.matchesVehicleFilterAndQuery(vehicles.get(0), Filter.BUS, ""));

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

	@Test
	public void singlePassStationRouteIndexAndSafePlatformTeleportHeight() {
		final Data data = new Data() {
		};

		// Elevated interchange station (platform at Y=110) served by a loop train route + ferry route
		final Station elevatedHub = new Station(data);
		elevatedHub.setName("Sky Quay");
		elevatedHub.setCorners(new Position(0, 0, -10), new Position(20, 200, 10));
		data.stations.add(elevatedHub);

		// Underground metro station (platform at Y=22)
		final Station underground = new Station(data);
		underground.setName("Deep Vault");
		underground.setCorners(new Position(90, -64, -10), new Position(110, 64, 10));
		data.stations.add(underground);

		// Planned station zone without platforms yet
		final Station unplatformed = new Station(data);
		unplatformed.setName("Planned Stop");
		unplatformed.setCorners(new Position(190, 0, -10), new Position(210, 128, 10));
		data.stations.add(unplatformed);

		final Platform hubTrainA = new Platform(new Position(4, 110, 0), new Position(16, 110, 0), TransportMode.TRAIN, data);
		final Platform hubBoat = new Platform(new Position(4, 63, 6), new Position(16, 63, 6), TransportMode.BOAT, data);
		final Platform deepPlatform = new Platform(new Position(94, 22, 0), new Position(106, 22, 0), TransportMode.TRAIN, data);
		data.platforms.add(hubTrainA);
		data.platforms.add(hubBoat);
		data.platforms.add(deepPlatform);

		// Loop route visits elevatedHub twice (start and end), must still count as 1 distinct route at elevatedHub
		final Route loopTrain = new Route(TransportMode.TRAIN, data);
		loopTrain.getRoutePlatforms().add(new RoutePlatformData(hubTrainA.getId()));
		loopTrain.getRoutePlatforms().add(new RoutePlatformData(deepPlatform.getId()));
		loopTrain.getRoutePlatforms().add(new RoutePlatformData(hubTrainA.getId()));
		data.routes.add(loopTrain);

		final Route ferry = new Route(TransportMode.BOAT, data);
		ferry.getRoutePlatforms().add(new RoutePlatformData(hubBoat.getId()));
		data.routes.add(ferry);

		data.sync();

		final Map<Long, RoadVehicleListModel.StationRouteInfo> indexed = RoadVehicleListModel.indexStationRoutes(data.routes);
		assertEquals(2, indexed.get(elevatedHub.getId()).routeCount(), "loop route counted once + ferry route = 2");
		assertEquals(2, indexed.get(elevatedHub.getId()).modes().size());
		assertEquals(1, indexed.get(underground.getId()).routeCount());
		assertFalse(indexed.containsKey(unplatformed.getId()));

		final List<StationInterchangeRow> rows = RoadVehicleListModel.buildStationRows(data.stations, data.routes, 0, 0, s -> s);
		assertEquals(3, rows.size());

		final StationInterchangeRow hubRow = rows.stream().filter(r -> r.stationId() == elevatedHub.getId()).findFirst().orElseThrow();
		assertEquals(2, hubRow.routes());
		assertEquals(2, hubRow.modesCount());
		assertTrue(hubRow.isInterchange());

		final StationInterchangeRow deepRow = rows.stream().filter(r -> r.stationId() == underground.getId()).findFirst().orElseThrow();
		assertEquals(1, deepRow.routes());
		assertFalse(deepRow.isInterchange());

		final StationInterchangeRow emptyRow = rows.stream().filter(r -> r.stationId() == unplatformed.getId()).findFirst().orElseThrow();
		assertEquals(0, emptyRow.routes());

		// Station teleport stands 1 block above platform rail midpoint instead of using player's ground Y (64)
		assertEquals("tp @s 100 23 0", RoadVehicleListModel.stationTeleportCommand(underground, deepRow, 64.0));
		assertTrue(
				java.util.Set.of("tp @s 10 111 0", "tp @s 10 64 6").contains(RoadVehicleListModel.stationTeleportCommand(elevatedHub, hubRow, 64.0)),
				"elevated/multi-platform hub teleports onto one of its savedRails"
		);
		// Only falls back to player Y when savedRails is empty
		assertEquals("tp @s 200.5 64.0 0.5", RoadVehicleListModel.stationTeleportCommand(unplatformed, emptyRow, 64.0));
	}
}
