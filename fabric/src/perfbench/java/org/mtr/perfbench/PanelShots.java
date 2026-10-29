package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.mtr.aurelia.client.MtrBridge;
import org.mtr.aurelia.client.screen.*;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.screen.ConfigScreen;
import org.mtr.mod.screen.DashboardScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Dev only, {@code -Datr.panelshots=true} with the perf bench run: opens MTR's dashboard, MTR's options, every tab of the
 * Aurelia panel and every tool, screenshots each into {@code screenshots/panel-*.png}, writes panelshots.txt and quits.
 */
public final class PanelShots implements ClientModInitializer {

	static final boolean ACTIVE = Boolean.getBoolean("atr.panelshots");

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR panel shots");
	private static final int START_TICKS = 30 * 20;
	private static final int SCREEN_TICKS = 2 * 20;

	private record Shot(String name, Function<Screen, Screen> open) {
	}

	private final List<Shot> shots = new ArrayList<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int index = -1;
	private int screenTicks;
	private Screen panel;

	@Override
	public void onInitializeClient() {
		if (!ACTIVE) {
			return;
		}
		shots.add(new Shot("00-dashboard", parent -> new DashboardScreen(TransportMode.TRAIN)));
		shots.add(new Shot("01-mtr-options", parent -> new ConfigScreen(null)));
		shots.add(new Shot("02-panel-rendering", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.RENDERING)));
		shots.add(new Shot("03-panel-sound", parent -> new AureliaPanelScreen(null, AureliaPanelScreen.Tab.SOUND)));
		shots.add(new Shot("04-panel-tools", parent -> panel = new AureliaPanelScreen(null, AureliaPanelScreen.Tab.TOOLS)));
		shots.add(new Shot("05-presets", TrainPresetScreen::new));
		shots.add(new Shot("06-stops", RouteStopsScreen::new));
		shots.add(new Shot("07-return", ReturnRouteScreen::new));
		shots.add(new Shot("08-dwell", PlatformDwellScreen::new));
		shots.add(new Shot("09-departures", DepartureEditorScreen::new));
		shots.add(new Shot("10-overlap", StationOverlapScreen::new));
		shots.add(new Shot("11-height", ZoneHeightScreen::new));
		shots.add(new Shot("12-codes", StationCodesScreen::new));
		shots.add(new Shot("13-depots", DepotHealthScreen::new));
		shots.add(new Shot("14-speed", SpeedAuditScreen::new));
		shots.add(new Shot("15-trains", TrainInspectorScreen::new));
		shots.add(new Shot("16-jump", JumpScreen::new));
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		if (index < 0) {
			if (ticks == START_TICKS - 5 * 20 && client.getServer() != null) {
				// Edit tools need MTR edit permission (creative or survival); the bench world's player has neither
				final UUID id = client.player.getUuid();
				client.getServer().execute(() -> {
					final ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(id);
					if (player != null) {
						player.changeGameMode(GameMode.CREATIVE);
					}
				});
			}
			if (ticks >= START_TICKS) {
				index = 0;
				open(client);
			}
			return;
		}
		if (++screenTicks < SCREEN_TICKS) {
			return;
		}
		final Shot shot = shots.get(index);
		final String screenName = client.currentScreen == null ? "none" : client.currentScreen.getClass().getSimpleName();
		ScreenshotRecorder.saveScreenshot(client.runDirectory, "panel-" + shot.name() + ".png", client.getFramebuffer(), message -> {
		});
		results.add(shot.name() + ": open " + screenName + ", canEdit " + MtrBridge.canEdit());
		index++;
		if (index >= shots.size()) {
			finish(client);
		} else {
			open(client);
		}
	}

	private void open(MinecraftClient client) {
		screenTicks = 0;
		final Shot shot = shots.get(index);
		try {
			client.setScreen(shot.open().apply(panel));
		} catch (RuntimeException e) {
			LOGGER.error("Could not open {}", shot.name(), e);
			results.add(shot.name() + ": FAILED " + e);
			// No screenshot of whatever screen is still open
			index++;
			if (index >= shots.size()) {
				finish(client);
			} else {
				open(client);
			}
		}
	}

	private void finish(MinecraftClient client) {
		try {
			Files.write(Path.of("panelshots.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write panelshots.txt", e);
		}
		LOGGER.info("Panel shots done: {}", results.size());
		client.scheduleStop();
	}
}
