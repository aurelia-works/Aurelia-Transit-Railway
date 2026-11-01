package org.mtr.aurelia.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.mtr.aurelia.client.screen.AureliaPanelScreen;
import org.mtr.aurelia.client.ui.AureliaUiSettings.ScreenId;
import org.mtr.mod.screen.ConfigScreen;
import org.mtr.mod.screen.DashboardScreen;
import org.mtr.mod.screen.EditDepotScreen;
import org.mtr.mod.screen.EditRouteScreen;
import org.mtr.mod.screen.EditStationScreen;
import org.mtr.mod.screen.SidingScreen;

/** Which of the seven restyled screens is open, and whether its Look switch is on. */
public final class AureliaUi {

	private AureliaUi() {
	}

	public static boolean active() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client != null && enabled(client.currentScreen);
	}

	public static boolean enabled(Screen screen) {
		final ScreenId id = id(screen);
		return id != null && AureliaUiSettings.enabled(id);
	}

	public static ScreenId id(Screen screen) {
		if (screen instanceof DashboardScreen) {
			return ScreenId.DASHBOARD;
		}
		if (screen instanceof EditStationScreen) {
			return ScreenId.STATION;
		}
		if (screen instanceof EditRouteScreen) {
			return ScreenId.ROUTE;
		}
		if (screen instanceof EditDepotScreen) {
			return ScreenId.DEPOT;
		}
		if (screen instanceof SidingScreen) {
			return ScreenId.SIDING;
		}
		if (screen instanceof ConfigScreen) {
			return ScreenId.OPTIONS;
		}
		if (screen instanceof AureliaPanelScreen) {
			return ScreenId.PANEL;
		}
		return null;
	}
}
