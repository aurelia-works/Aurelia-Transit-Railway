package org.mtr.aurelia.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.mtr.aurelia.client.screen.AureliaPanelScreen;
import org.mtr.aurelia.client.screen.RoadDashboardScreen;
import org.mtr.aurelia.client.ui.AureliaUiSettings.ScreenId;
import org.mtr.mod.screen.ConfigScreen;
import org.mtr.mod.screen.DashboardScreen;
import org.mtr.mod.screen.EditDepotScreen;
import org.mtr.mod.screen.EditRouteScreen;
import org.mtr.mod.screen.EditStationScreen;
import org.mtr.mod.screen.SidingScreen;

/**
 * Which ATR screen is open, and whether its Look switch is on. Seven screens have their own switch; every other screen
 * the mod opens shares the {@link ScreenId#OTHER} switch, so the whole mod draws its controls one way. Vanilla and other
 * mods' screens are never touched.
 */
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
		if (screen instanceof DashboardScreen || screen instanceof RoadDashboardScreen) {
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
		return screen != null && OWN_SCREEN.get(screen.getClass()) ? ScreenId.OTHER : null;
	}

	/** Whether the screen gets the Aurelia controls but keeps the world in view behind it: every screen without its own switch. */
	public static boolean keepsWorldBehind(Screen screen) {
		return id(screen) == ScreenId.OTHER;
	}

	/** Whether the player is moving between controls with the keyboard, so a focus ring is worth drawing. */
	public static boolean keyboardNavigation() {
		final MinecraftClient client = MinecraftClient.getInstance();
		return client != null && client.getNavigationType().isKeyboard();
	}

	/**
	 * Screens this mod ships: a class in one of the mod's packages that was also loaded from the mod's own jar. The jar
	 * check keeps an add-on's screen on its own look even when it subclasses an MTR screen or borrows a package name, so
	 * add-on screens are drawn exactly as before. The camera panel is left out: it exists to look at the world. The
	 * mapping library's own classes are left out because other mods' screens extend them too.
	 */
	private static final ClassValue<Boolean> OWN_SCREEN = new ClassValue<>() {
		@Override
		protected Boolean computeValue(Class<?> type) {
			final String name = type.getName();
			if (name.startsWith("org.mtr.camera.")) {
				return false;
			}
			if (!name.startsWith("org.mtr.mod.") && !name.startsWith("org.mtr.aurelia.") && !name.startsWith("org.mtr.atr.") && !name.startsWith("com.aureliatransit.")) {
				return false;
			}
			final String home = jar(AureliaUi.class);
			final String where = jar(type);
			return home == null || home.equals(where);
		}
	};

	/** Where a class was loaded from, or null when the class loader does not say. */
	private static String jar(Class<?> type) {
		try {
			final java.security.CodeSource source = type.getProtectionDomain().getCodeSource();
			return source == null || source.getLocation() == null ? null : source.getLocation().toExternalForm();
		} catch (SecurityException e) {
			return null;
		}
	}
}
