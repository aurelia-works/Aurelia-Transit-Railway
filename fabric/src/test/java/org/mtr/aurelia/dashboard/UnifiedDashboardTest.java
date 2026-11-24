package org.mtr.aurelia.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.aurelia.dashboard.DashboardLayout.ModeSlot;
import org.mtr.aurelia.dashboard.DashboardLayout.Rect;
import org.mtr.aurelia.dashboard.DashboardOpenState.PacketResolution;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.data.IGui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

		// Primary dashboard remembers Boat, Cable Car, or Plane once chosen
		assertEquals(DashboardMode.BOAT, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.BOAT));
		assertEquals(DashboardMode.CABLE_CAR, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.CABLE_CAR));
		assertEquals(DashboardMode.AIRPLANE, DashboardMode.resolveOpenMode(TransportMode.TRAIN, true, DashboardMode.AIRPLANE));

		// Legacy dashboard_2 (Boat), dashboard_3 (Cable Car), dashboard_4 (Airplane) always start on their own mode
		assertEquals(DashboardMode.BOAT, DashboardMode.resolveOpenMode(TransportMode.BOAT, true, DashboardMode.AIRPLANE));
		assertEquals(DashboardMode.CABLE_CAR, DashboardMode.resolveOpenMode(TransportMode.CABLE_CAR, true, DashboardMode.TRAIN));
		assertEquals(DashboardMode.AIRPLANE, DashboardMode.resolveOpenMode(TransportMode.AIRPLANE, true, DashboardMode.BOAT));

		// Sneak-opening a station/platform/depot/siding uses the MTR transport mode rather than the remembered one
		assertEquals(DashboardMode.TRAIN, DashboardMode.resolveOpenMode(TransportMode.TRAIN, false, DashboardMode.AIRPLANE));
		assertEquals(DashboardMode.BOAT, DashboardMode.resolveOpenMode(TransportMode.BOAT, false, DashboardMode.AIRPLANE));

		// Server open packet allows creative, op, or survival players carrying any dashboard item
		assertFalse(DashboardPackets.isAllowed(false, false, false));
		assertTrue(DashboardPackets.isAllowed(true, false, false));
		assertTrue(DashboardPackets.isAllowed(false, true, false));
		assertTrue(DashboardPackets.isAllowed(false, false, true));
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
		final PacketResolution sneakBoat = state.resolvePacket(TransportMode.BOAT, false, DashboardMode.AIRPLANE, null, 1_000L);
		assertFalse(sneakBoat.handledByUnified());
		assertNull(sneakBoat.targetMode());
		assertFalse(sneakBoat.updateLastMode());

		// Armed menu request is NOT consumed or applied when legacy dashboard_2 (BOAT) arrives
		state.arm(DashboardMode.CABLE_CAR, menuScreen, 1_000L);
		assertTrue(state.hasPending(1_500L));
		final PacketResolution legacyBoat = state.resolvePacket(TransportMode.BOAT, true, DashboardMode.TRAIN, null, 1_500L);
		assertTrue(legacyBoat.handledByUnified());
		assertEquals(DashboardMode.BOAT, legacyBoat.targetMode());
		assertFalse(legacyBoat.forceReplaceScreen());
		assertTrue(state.hasPending(1_500L), "pending menu request remains untouched by non-TRAIN legacy packet");

		// Armed menu request IS consumed on TRAIN + DEFAULT, honoring forceReplaceScreen only for null or originScreen
		final PacketResolution fromOrigin = state.resolvePacket(TransportMode.TRAIN, true, DashboardMode.TRAIN, menuScreen, 2_000L);
		assertTrue(fromOrigin.handledByUnified());
		assertEquals(DashboardMode.CABLE_CAR, fromOrigin.targetMode());
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
		final PacketResolution afterExpiry = state.resolvePacket(TransportMode.TRAIN, true, DashboardMode.BOAT, menuScreen, 8_005L);
		assertEquals(DashboardMode.BOAT, afterExpiry.targetMode());
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
		assertEquals(4, tabBar.slots().size());
		final int minBoatWidth = minWindow.modeButtons().get(1).bounds().width();

		final DashboardLayout fullHdScale2 = tabBar.rebuild(DashboardMode.TRAIN, 960, 540, true);
		assertEquals(2, tabBar.generation());
		assertEquals(4, tabBar.slots().size());
		final int hdBoatWidth = fullHdScale2.modeButtons().get(1).bounds().width();
		assertTrue(hdBoatWidth >= minBoatWidth, "resized tab bar replaces narrower 427x240 slot bounds");
		assertEquals(hdBoatWidth, tabBar.slots().get(1).bounds().width());

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
		DashboardSettings.save(tempDir, DashboardMode.CABLE_CAR);
		assertEquals(DashboardMode.CABLE_CAR, DashboardSettings.read(tempDir));

		// An old dashboard.json saved on the removed Bus & Road mode still loads, on Train
		final Path legacy = tempDir.resolve("legacy");
		Files.createDirectories(legacy.resolve("aurelia"));
		Files.writeString(legacy.resolve("aurelia").resolve("dashboard.json"), "{\"lastMode\":\"road\"}");
		assertEquals(DashboardMode.TRAIN, DashboardSettings.read(legacy));

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

				assertEquals(4, layout.modeButtons().size());
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
}
