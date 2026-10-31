package org.mtr.aurelia.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.mtr.aurelia.client.screen.AureliaPanelScreen;
import org.mtr.mod.data.IGui;
import org.mtr.mod.screen.DashboardScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** The Aurelia panel and tools (formerly Aurelia Transit Utilities), folded into ATR. */
public final class AureliaClient implements ClientModInitializer {

	/** Asset namespace and config folder ({@code config/aurelia}). */
	public static final String MOD_ID = "aurelia";
	public static final Logger LOGGER = LoggerFactory.getLogger("ATR Aurelia");

	// 20x20 logo button; the texture holds the normal state on top and the hovered state below it.
	private static final Identifier DASHBOARD_BUTTON_TEXTURE = new Identifier(MOD_ID, "textures/gui/dashboard_button.png");
	private static final int DASHBOARD_BUTTON_SIZE = 20;
	// MAGIC (mod id "jme") puts its own 20x20 menu button 6px inside the map's top-left corner, so ours goes under it.
	private static final boolean MAGIC_LOADED = FabricLoader.getInstance().isModLoaded("jme");
	private static final int DASHBOARD_BUTTON_Y = MAGIC_LOADED ? 6 + 20 + 4 : 6;

	@Override
	public void onInitializeClient() {
		AtuImport.run(FabricLoader.getInstance().getConfigDir());
		// Fabric API screen events: the panel button on MTR's dashboard. MTR fills its dashboard data when that screen
		// opens, so the tools always start from fresh data. Top-left corner of the map: MTR draws the cursor coordinates
		// top-right and its own buttons along the bottom.
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof DashboardScreen) {
				final TexturedButtonWidget button = new TexturedButtonWidget(IGui.PANEL_WIDTH + 6, DASHBOARD_BUTTON_Y, DASHBOARD_BUTTON_SIZE, DASHBOARD_BUTTON_SIZE, 0, 0, DASHBOARD_BUTTON_SIZE, DASHBOARD_BUTTON_TEXTURE, DASHBOARD_BUTTON_SIZE, DASHBOARD_BUTTON_SIZE * 2, pressed -> client.setScreen(new AureliaPanelScreen(screen, AureliaPanelScreen.Tab.TOOLS)), Text.translatable("gui.aurelia.dashboard_button"));
				button.setTooltip(Tooltip.of(Text.translatable("gui.aurelia.panel.title")));
				Screens.getButtons(screen).add(button);
				// MTR's map widget is registered before the button and takes every click over the map, so the button
				// handles its own clicks before the dashboard sees them. Skipped while MAGIC's menu is open over it.
				ScreenMouseEvents.allowMouseClick(screen).register((clickedScreen, mouseX, mouseY, mouseButton) -> magicMenuOpen(clickedScreen) || !button.mouseClicked(mouseX, mouseY, mouseButton));
			}
		});
		// Unbound by default, like ATU's hub.
		final KeyBinding openPanel = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.aurelia.open_panel", InputUtil.UNKNOWN_KEY.getCode(), "key.categories.mtr"));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openPanel.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new AureliaPanelScreen(null, AureliaPanelScreen.Tab.RENDERING));
				}
			}
		});
		Announcer.register();
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(org.mtr.mod.data.DepotOptionsSync.SYNC, (client, handler, buf, response) -> {
			final java.util.Map<Long, org.mtr.mod.data.DepotOptions.Flags> read = org.mtr.mod.data.DepotOptions.read(buf);
			client.execute(() -> org.mtr.mod.data.DepotOptions.setClient(read));
		});
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(org.mtr.mod.data.DepotSchedulesSync.SYNC, (client, handler, buf, response) -> {
			final java.util.Map<Long, org.mtr.mod.data.DepotSchedulesSync.Summary> read = org.mtr.mod.data.DepotSchedulesSync.read(buf);
			client.execute(() -> org.mtr.mod.data.DepotSchedulesSync.setClient(read));
		});
		RideClient.register();
		LOGGER.info("[ATR] Aurelia panel ready");
	}

	private static Method magicMenuOpenMethod;
	private static boolean magicMenuOpenLookedUp;

	/** MAGIC adds a public {@code jme$isOverlayMenuOpen()} to MTR's dashboard; its dropdown opens where the button is. */
	private static boolean magicMenuOpen(Screen screen) {
		if (!MAGIC_LOADED) {
			return false;
		}
		try {
			if (!magicMenuOpenLookedUp) {
				magicMenuOpenLookedUp = true;
				magicMenuOpenMethod = screen.getClass().getMethod("jme$isOverlayMenuOpen");
			}
			return magicMenuOpenMethod != null && (boolean) magicMenuOpenMethod.invoke(screen);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}
}
