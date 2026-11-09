package com.aureliatransit.architecture.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.mtr.aurelia.client.screen.AureliaPanelScreen;

/**
 * Adds the station build pace slider to the Aurelia dev panel's Architecture tab (where the station and airport wrenches
 * stand) without editing the panel: the panel is not rebuilt through the path that fires {@code AFTER_INIT}, so a per-screen
 * tick event puts the slider back whenever that tab is showing and it is missing. The active tab is the only tab button the
 * panel disabled.
 */
public final class StationDevPanel {

	private static final int WIDTH = 220;

	private StationDevPanel() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof AureliaPanelScreen) {
				ScreenEvents.afterTick(screen).register(panel -> ensureSlider(client, panel));
				ensureSlider(client, screen);
			}
		});
	}

	private static void ensureSlider(MinecraftClient client, Screen screen) {
		if (!architectureTab(screen) || client.player == null || !client.player.hasPermissionLevel(2) || hasSlider(screen)) {
			return;
		}
		final StationPaceSlider slider = new StationPaceSlider((screen.width - WIDTH) / 2, screen.height - 56, WIDTH);
		slider.setTooltip(Tooltip.of(Text.translatable("screen.aurelia_transit_architecture.station_wrench.pace.tip")));
		Screens.getButtons(screen).add(slider);
	}

	private static boolean hasSlider(Screen screen) {
		for (final ClickableWidget widget : Screens.getButtons(screen)) {
			if (widget instanceof StationPaceSlider) {
				return true;
			}
		}
		return false;
	}

	/** The active tab is the only tab button the panel disabled. */
	private static boolean architectureTab(Screen screen) {
		final String architecture = Text.translatable("gui.aurelia.panel.tab.architecture").getString();
		for (final ClickableWidget widget : Screens.getButtons(screen)) {
			if (widget instanceof ButtonWidget button && !button.active && button.getMessage().getString().equals(architecture)) {
				return true;
			}
		}
		return false;
	}
}
