package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only proof for the passenger demo: boots the bench world, puts the camera above a platform, runs
 * {@code /atr_passengers demo 40}, turns passenger drawing on, and saves three screenshots while the network runs.
 * Run with {@code JAVA_TOOL_OPTIONS="-Datr.passengershots=true" ./gradlew :fabric:runPerfbench -Patr.world="ATU Test"}.
 * Never part of the release jar.
 */
public final class PassengerShots implements ClientModInitializer {

	static final boolean ACTIVE = Boolean.getBoolean("atr.passengershots");
	private static final Logger LOGGER = LoggerFactory.getLogger("ATR passenger shots");
	private static final int START_TICKS = 100;
	private static final int FIRST_WAIT = 40;
	private static final int NEXT_WAIT = 100;

	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int stage;
	private int waitUntil;

	@Override
	public void onInitializeClient() {
		if (!ACTIVE) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		if (ticks < START_TICKS) {
			return;
		}
		switch (stage) {
			case 0 -> {
				PassengerDrawSettings.setDraw(true);
				client.player.networkHandler.sendCommand("tp @s 276 -58 8 facing 276 -59 0");
				client.player.networkHandler.sendCommand("atr_passengers demo 40");
				results.add("demo started above the bench platform");
				stage = 1;
				waitUntil = ticks + FIRST_WAIT;
			}
			case 1 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-1");
					stage = 2;
					waitUntil = ticks + NEXT_WAIT;
				}
			}
			case 2 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-2");
					stage = 3;
					waitUntil = ticks + NEXT_WAIT;
				}
			}
			case 3 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-3");
					finish(client);
				}
			}
			default -> {
			}
		}
	}

	private void screenshot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(), message -> {
		});
		results.add(name + ".png");
	}

	private void finish(MinecraftClient client) {
		try {
			Files.write(Path.of("passengershots.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write passengershots.txt", e);
		}
		LOGGER.info("Passenger shots done: {}", results);
		client.scheduleStop();
	}
}
