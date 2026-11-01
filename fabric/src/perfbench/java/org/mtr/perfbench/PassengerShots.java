package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.mtr.aurelia.client.PassengerClient;
import org.mtr.mod.passenger.PassengerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dev-only proof, run on top of {@link PassengerDemoClient}: a screenshot from the middle of the platform, one from its
 * far end, then it waits for a real train to board people, photographs that and writes the counts.
 * Run with {@code JAVA_TOOL_OPTIONS="-Datr.passengershots=true" ./gradlew :fabric:runPassengerDemo}.
 * Never part of the release jar.
 */
public final class PassengerShots implements ClientModInitializer {

	static final boolean ACTIVE = Boolean.getBoolean("atr.passengershots");
	private static final Logger LOGGER = LoggerFactory.getLogger("ATR passenger shots");
	private static final int SETTLE_TICKS = 60;
	private static final int TRAIN_TIMEOUT_TICKS = 20 * 240;

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
		if (client.player == null || client.world == null || !PassengerDemoClient.ready) {
			return;
		}
		ticks++;
		final PassengerService.Stats stats = PassengerClient.stats();
		switch (stage) {
			case 0 -> {
				if (ticks >= SETTLE_TICKS) {
					screenshot(client, "passenger-demo-1", stats);
					client.player.networkHandler.sendCommand("tp @s 293 -59 8 facing 276 -58 3");
					waitUntil = ticks + SETTLE_TICKS;
					stage = 1;
				}
			}
			case 1 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-2", stats);
					client.player.networkHandler.sendCommand("tp @s 276 -59 8 facing 276 -58 0");
					waitUntil = ticks + TRAIN_TIMEOUT_TICKS;
					stage = 2;
				}
			}
			case 2 -> {
				if (stats.boarded() > 0) {
					screenshot(client, "passenger-demo-3", stats);
					waitUntil = ticks + TRAIN_TIMEOUT_TICKS;
					stage = 3;
				} else if (ticks >= waitUntil) {
					results.add("no boarding within the timeout");
					finish(client, stats);
				}
			}
			case 3 -> {
				if (stats.alighted() > 0 || ticks >= waitUntil) {
					finish(client, stats);
				}
			}
			default -> {
			}
		}
	}

	private void screenshot(MinecraftClient client, String name, PassengerService.Stats stats) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(), message -> {
		});
		results.add(name + ".png waiting=" + stats.waiting() + " riding=" + stats.riding() + " boarded=" + stats.boarded());
	}

	private void finish(MinecraftClient client, PassengerService.Stats stats) {
		results.add("final " + stats);
		try {
			Files.write(Path.of("passengershots.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write passengershots.txt", e);
		}
		LOGGER.info("Passenger shots done: {}", results);
		stage = 4;
		client.scheduleStop();
	}
}
