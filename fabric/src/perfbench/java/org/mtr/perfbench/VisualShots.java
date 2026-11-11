package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import com.aureliatransit.architecture.station.Heading;
import com.aureliatransit.architecture.station.LocalFrame;
import com.aureliatransit.architecture.station.world.StationSessions;
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
 * {@code tpst <across> <height> <along> <yaw> <pitch>} (relative to the player's last built ATA station, else its pending plan:
 * across and along are fractions of its footprint, so 0.5 0.5 is its middle and -0.2 stands outside it; height is blocks above
 * rail level; yaw 0 looks down the station's length), {@code shaders on|off}, {@code hud on|off}, {@code view <chunks>}
 * (render distance), {@code settle <ticks>} (wait after each later tp, tpst or shader switch; 60 by default),
 * {@code tpblock <block id part> <n> <dx> <dy> <dz> <yaw> <pitch>} (stands dx/dy/dz blocks from the n-th planned block whose id
 * contains the text, in world axes), {@code shot <name>} (saves {@code screenshots/vis-<name>.png}), {@code fov <degrees>}, {@code window <width> <height>} (window size in screen points), {@code guiscale <n>} (0 is automatic), {@code key forward|back|left|right|sneak on|off} (hold a key), {@code use} (one right click at the crosshair), {@code screen panel <tab>|wrench|menu [tab]|close}, {@code mouse <x> <y>} (cursor, in window points), {@code click <x> <y>} (GUI-scaled), {@code quit}. Lines starting with {@code #} are comments. Writes visualshots.txt with each step and its result, so the same views can be re-shot after
 * every change.
 * <p>
 * With {@code -Datr.visualshots.watch=<folder>} the client stays open after the script: every {@code .txt} script dropped
 * into the folder is played in name order and renamed to {@code .done}, so a re-shoot needs no restart.
 */
public final class VisualShots implements ClientModInitializer {

	static final String SCRIPT = System.getProperty("atr.visualshots", "");
	static final boolean ACTIVE = !SCRIPT.isEmpty();
	static final String WATCH = System.getProperty("atr.visualshots.watch", "");

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR visual shots");
	private static final int START_TICKS = 20 * 20;
	/** After a teleport or a shader switch: chunks load and the shader pack compiles before the shot. */
	private int settleTicks = 3 * 20;

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
			if (WATCH.isEmpty() || !nextScript()) {
				if (WATCH.isEmpty()) {
					finish(client);
				} else {
					if (!steps.isEmpty()) {
						// mark the script played: results out, steps cleared so they are written once
						writeResults();
						LOGGER.info("Visual shots done: {} steps", results.size());
						steps.clear();
						index = 0;
					}
					waitUntil = ticks + 40;
				}
			}
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
					waitUntil = ticks + settleTicks;
				}
				case "tpst" -> {
					results.add(step + ": " + tpStation(client, arg.split(" ")));
					waitUntil = ticks + settleTicks;
					return;
				}
				case "tpblock" -> {
					results.add(step + ": " + tpBlock(client, arg.split(" ")));
					waitUntil = ticks + settleTicks;
					return;
				}
				case "shaders" -> {
					shaders(arg.strip().equals("on"));
					waitUntil = ticks + settleTicks * 2;
				}
				case "settle" -> settleTicks = Integer.parseInt(arg.strip());
				case "view" -> client.options.getViewDistance().setValue(Integer.parseInt(arg.strip()));
				case "hud" -> client.options.hudHidden = arg.strip().equals("off");
				case "fov" -> client.options.getFov().setValue(Integer.parseInt(arg.strip()));
				case "window" -> {
					final String[] size = arg.strip().split("\\s+");
					org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), Integer.parseInt(size[0]), Integer.parseInt(size[1]));
				}
				case "screen" -> openScreen(client, arg.strip());
				case "click" -> {
					// a left click at GUI-scaled x y on the open screen
					final String[] at = arg.strip().split("\\s+");
					if (client.currentScreen != null) {
						client.currentScreen.mouseClicked(Double.parseDouble(at[0]), Double.parseDouble(at[1]), 0);
						client.currentScreen.mouseReleased(Double.parseDouble(at[0]), Double.parseDouble(at[1]), 0);
					}
				}
				case "mouse" -> {
					final String[] at = arg.strip().split("\\s+");
					org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getHandle(), Double.parseDouble(at[0]), Double.parseDouble(at[1]));
					// GLFW sends no move event for a cursor set from code, so tell the game's mouse handler directly (dev names)
					try {
						final java.lang.reflect.Method move = net.minecraft.client.Mouse.class.getDeclaredMethod("onCursorPos", long.class, double.class, double.class);
						move.setAccessible(true);
						move.invoke(client.mouse, client.getWindow().getHandle(), Double.parseDouble(at[0]), Double.parseDouble(at[1]));
					} catch (ReflectiveOperationException e) {
						LOGGER.warn("Could not move the game's cursor", e);
					}
				}
				case "key" -> {
					// hold or let go of a movement key, as a player at the keyboard would: key forward on, key forward off
					final String[] words = arg.strip().split("\\s+");
					final net.minecraft.client.option.KeyBinding key = switch (words[0]) {
						case "forward" -> client.options.forwardKey;
						case "back" -> client.options.backKey;
						case "left" -> client.options.leftKey;
						case "right" -> client.options.rightKey;
						case "sneak" -> client.options.sneakKey;
						default -> throw new IllegalArgumentException("unknown key " + words[0]);
					};
					key.setPressed(words[1].equals("on"));
				}
				case "use" -> {
					// one right click at the crosshair, as the use key would make it
					net.minecraft.client.option.KeyBinding.onKeyPressed(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(client.options.useKey));
				}
				case "guiscale" -> {
					client.options.getGuiScale().setValue(Integer.parseInt(arg.strip()));
					client.onResolutionChanged();
				}
				case "quit" -> {
					finish(client);
					return;
				}
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

	/** {@code panel <TAB>} the Aurelia panel on a tab, {@code wrench} the wrench in the main hand, {@code menu} the ATR menu, {@code close}. */
	private static void openScreen(MinecraftClient client, String arg) {
		final String[] words = arg.split("\\s+");
		switch (words[0]) {
			case "panel" -> client.setScreen(new org.mtr.aurelia.client.screen.AureliaPanelScreen(null,
					org.mtr.aurelia.client.screen.AureliaPanelScreen.Tab.valueOf(words[1].toUpperCase(Locale.ROOT))));
			case "wrench" -> com.aureliatransit.architecture.client.StationWrenchScreen.open(net.minecraft.util.Hand.MAIN_HAND);
			case "menu" -> com.aureliatransit.architecture.client.AtrMenuScreen.open(null, words.length > 1
					? com.aureliatransit.architecture.client.AtrMenuScreen.Tab.valueOf(words[1].toUpperCase(Locale.ROOT))
					: com.aureliatransit.architecture.client.AtrMenuScreen.Tab.BUILD);
			case "close" -> client.setScreen(null);
			default -> throw new IllegalArgumentException("unknown screen " + arg);
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

	/** Teleports relative to the last station this player built; returns where, or why not. */
	private String tpStation(MinecraftClient client, String[] p) {
		// The last built station, else the pending plan (built stations are kept in memory only, so after a restart re-plan from
		// the same spot to get the same frame)
		final StationSessions.Built built = StationSessions.built(client.player.getUuid());
		final StationSessions.Pending pending = StationSessions.pending(client.player.getUuid());
		if (built == null && pending == null) {
			throw new IllegalStateException("no built or planned station");
		}
		final var layout = built != null ? built.plan().layout() : pending.plan().layout();
		final LocalFrame frame = built != null ? built.frame() : pending.frame();
		final int u = Math.round(Float.parseFloat(p[0]) * layout.width());
		final int v = layout.vMin() + Math.round(Float.parseFloat(p[2]) * (layout.vMax() - layout.vMin()));
		final int x = frame.x(u, v);
		final int z = frame.z(u, v);
		final double y = frame.oy() + Double.parseDouble(p[1]);
		final Heading forward = frame.forward();
		final float yaw = (float) Math.toDegrees(Math.atan2(-forward.dx(), forward.dz())) + Float.parseFloat(p[3]);
		runCommand(client, String.format(Locale.ROOT, "tp @s %d.5 %.1f %d.5 %.1f %s", x, y, z, yaw, p[4]));
		return String.format(Locale.ROOT, "ok at %d %.1f %d (footprint %d wide, v %d..%d, facing %s)", x, y, z, layout.width(), layout.vMin(), layout.vMax(), forward);
	}

	/** Teleports next to the n-th planned block of the last built station whose id contains the given text. */
	private String tpBlock(MinecraftClient client, String[] p) {
		final StationSessions.Built built = StationSessions.built(client.player.getUuid());
		if (built == null) {
			throw new IllegalStateException("no built station");
		}
		final var blocks = built.plan().blocks().stream().filter(b -> b.blockId().contains(p[0])).toList();
		final int n = Integer.parseInt(p[1]);
		if (n >= blocks.size()) {
			throw new IllegalStateException(blocks.size() + " blocks match " + p[0]);
		}
		final var block = blocks.get(n);
		final LocalFrame frame = built.frame();
		final double x = frame.x(block.u(), block.v()) + 0.5 + Double.parseDouble(p[2]);
		final double y = frame.oy() + block.y() + Double.parseDouble(p[3]);
		final double z = frame.z(block.u(), block.v()) + 0.5 + Double.parseDouble(p[4]);
		runCommand(client, String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %s %s", x, y, z, p[5], p[6]));
		return String.format(Locale.ROOT, "ok at %.1f %.1f %.1f (%s u%d y%d v%d, %d match)", x, y, z, block.blockId(), block.u(), block.y(), block.v(), blocks.size());
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

	/** Loads the first script waiting in the watch folder, if any; writes the results of the last one first. */
	private boolean nextScript() {
		try (var files = Files.list(Path.of(WATCH))) {
			final Path next = files.filter(f -> f.toString().endsWith(".txt")).sorted().findFirst().orElse(null);
			if (next == null) {
				return false;
			}
			final List<String> lines = Files.readAllLines(next, StandardCharsets.UTF_8);
			Files.move(next, next.resolveSibling(next.getFileName() + ".done"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			steps.clear();
			results.clear();
			index = 0;
			for (final String line : lines) {
				final String step = line.strip();
				if (!step.isEmpty() && !step.startsWith("#")) {
					steps.add(step);
				}
			}
			LOGGER.info("Visual shots: playing {} ({} steps)", next.getFileName(), steps.size());
			return true;
		} catch (IOException e) {
			LOGGER.error("Could not read the watch folder {}", WATCH, e);
			return false;
		}
	}

	private void writeResults() {
		try {
			Files.write(Path.of("visualshots.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write visualshots.txt", e);
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
