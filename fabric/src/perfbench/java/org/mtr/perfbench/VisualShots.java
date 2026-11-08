package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Dev only: {@code -Datr.visualshots=<script file>} with the perf bench run plays a shot script and quits. One step per line:
 * {@code wait <ticks>}, {@code cmd <server command>} (run as op, no slash), {@code tp <x> <y> <z> <yaw> <pitch>},
 * {@code shaders on|off}, {@code hud on|off}, {@code shot <name>} (saves {@code screenshots/vis-<name>.png}). Lines starting
 * with {@code #} are comments. Writes visualshots.txt with each step and its result, so the same views can be re-shot after
 * every change.
 */
public final class VisualShots implements ClientModInitializer {

	static final String SCRIPT = System.getProperty("atr.visualshots", "");
	static final boolean ACTIVE = !SCRIPT.isEmpty();

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR visual shots");
	private static final int START_TICKS = 20 * 20;
	/** After a teleport or a shader switch: chunks load and the shader pack compiles before the shot. */
	private static final int SETTLE_TICKS = 3 * 20;

	private final List<String> steps = new ArrayList<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int index;
	private int waitUntil = START_TICKS;

	@Override
	public void onInitializeClient() {
		if (!ACTIVE) {
			return;
		}
		try {
			for (final String line : Files.readAllLines(Path.of(SCRIPT), StandardCharsets.UTF_8)) {
				final String step = line.strip();
				if (!step.isEmpty() && !step.startsWith("#")) {
					steps.add(step);
				}
			}
		} catch (IOException e) {
			LOGGER.error("Could not read {}", SCRIPT, e);
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		if (ticks < waitUntil) {
			return;
		}
		if (index >= steps.size()) {
			finish(client);
			return;
		}
		final String step = steps.get(index++);
		final String[] parts = step.split(" ", 2);
		final String arg = parts.length > 1 ? parts[1] : "";
		try {
			switch (parts[0].toLowerCase(Locale.ROOT)) {
				case "wait" -> waitUntil = ticks + Integer.parseInt(arg.strip());
				case "cmd" -> runCommand(client, arg);
				case "tp" -> {
					final String[] p = arg.split(" ");
					runCommand(client, String.format(Locale.ROOT, "tp @s %s %s %s %s %s", p[0], p[1], p[2], p[3], p[4]));
					waitUntil = ticks + SETTLE_TICKS;
				}
				case "shaders" -> {
					shaders(arg.strip().equals("on"));
					waitUntil = ticks + SETTLE_TICKS * 2;
				}
				case "hud" -> client.options.hudHidden = arg.strip().equals("off");
				case "shot" -> {
					final String name = arg.strip().replaceAll("[^A-Za-z0-9_-]+", "_");
					ScreenshotRecorder.saveScreenshot(client.runDirectory, "vis-" + name + ".png", client.getFramebuffer(), message -> {
					});
					waitUntil = ticks + 10;
				}
				default -> throw new IllegalArgumentException("unknown step");
			}
			results.add(step + ": ok");
		} catch (RuntimeException e) {
			LOGGER.error("Step failed: {}", step, e);
			results.add(step + ": FAILED " + e);
		}
	}

	/** As op on the integrated server: the bench player has no cheats. */
	private void runCommand(MinecraftClient client, String command) {
		final MinecraftServer server = client.getServer();
		final UUID id = client.player.getUuid();
		if (server == null) {
			throw new IllegalStateException("no integrated server");
		}
		server.execute(() -> {
			final ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
			if (player != null) {
				server.getCommandManager().executeWithPrefix(player.getCommandSource().withLevel(4), command);
			}
		});
	}

	private static void shaders(boolean on) {
		try {
			final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			final Object config = api.getMethod("getConfig").invoke(api.getMethod("getInstance").invoke(null));
			Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Iris not available", e);
		}
	}

	private void finish(MinecraftClient client) {
		try {
			Files.write(Path.of("visualshots.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write visualshots.txt", e);
		}
		LOGGER.info("Visual shots done: {} steps", results.size());
		client.scheduleStop();
	}
}
