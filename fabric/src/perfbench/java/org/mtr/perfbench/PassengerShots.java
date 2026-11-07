package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.mtr.aurelia.client.PassengerClient;
import org.mtr.aurelia.client.PassengerDrawSettings;
import org.mtr.aurelia.client.PassengerRenderer;
import org.mtr.aurelia.client.PassengerWalk;
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
	private int routes;
	private int routesCrossing;
	private int straightCrossing;
	private int mostTurns;
	private int alighters;
	/** Stairs, escalator, lift. */
	private final int[] arrivedBy = new int[3];
	private final int[] leftBy = new int[3];

	@Override
	public void onInitializeClient() {
		if (!ACTIVE) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		PassengerRenderer.walkListener = this::walk;
	}

	/** Checks the walk against the blocks it passes, and the straight leg it replaced: the proof that none cross a pillar. */
	private void walk(PassengerWalk walk) {
		final double[] points = walk.points();
		routes++;
		alighters += walk.boarding() ? 0 : 1;
		if (walk.arriving() && points.length == 6) {
			results.add(String.format("straight arrival (%.1f %.1f %.1f) to (%.1f %.1f %.1f)", points[0], points[1], points[2], points[3], points[4], points[5]));
		}
		if (walk.arriving()) {
			count(arrivedBy, points[0], points[2]);
		} else if (!walk.boarding()) {
			count(leftBy, points[points.length - 3], points[points.length - 1]);
		}
		mostTurns = Math.max(mostTurns, points.length / 3 - 4);
		if (crosses(points)) {
			routesCrossing++;
			final StringBuilder route = new StringBuilder(walk.boarding() ? "crossing boarder" : "crossing alighter");
			for (int i = 0; i < points.length; i += 3) {
				route.append(String.format(" (%.1f %.1f %.1f)", points[i], points[i + 1], points[i + 2]));
			}
			results.add(route.toString());
		}
		final int spot = walk.boarding() ? 0 : points.length - 3;
		final int outside = walk.boarding() ? points.length - 9 : 6;
		if (!walk.arriving() && crosses(new double[]{points[spot], points[spot + 1], points[spot + 2], points[outside], points[spot + 1], points[outside + 2]})) {
			straightCrossing++;
		}
	}

	/** Which exit a walk used, from where the demo builds them at both stops. */
	private static void count(int[] by, double x, double z) {
		if (z >= 9) {
			final double along = x < 200 ? x + 130 : x;
			by[along < 268 ? 0 : along < 276 ? 1 : 2]++;
		}
	}

	private static boolean crosses(double[] points) {
		final MinecraftClient client = MinecraftClient.getInstance();
		final BlockPos.Mutable mutable = new BlockPos.Mutable();
		for (int i = 3; i < points.length; i += 3) {
			// A leg up a stair cuts the corner of the step by design
			if (Math.abs(points[i + 1] - points[i - 2]) > 0.1) {
				continue;
			}
			final int samples = Math.max(1, (int) Math.ceil(Math.hypot(points[i] - points[i - 3], points[i + 2] - points[i - 1]) * 10));
			for (int sample = 0; sample <= samples; sample++) {
				final double t = sample / (double) samples;
				mutable.set(MathHelper.floor(points[i - 3] + (points[i] - points[i - 3]) * t), MathHelper.floor(points[i - 2] + (points[i + 1] - points[i - 2]) * t + 0.1), MathHelper.floor(points[i - 1] + (points[i + 2] - points[i - 1]) * t));
				if (!(client.world.getBlockState(mutable).getBlock() instanceof org.mtr.mod.block.BlockEscalatorSide) && !client.world.getBlockState(mutable).getCollisionShape(client.world, mutable).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null || !PassengerDemoClient.ready) {
			return;
		}
		ticks++;
		client.options.hudHidden = true;
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
					PassengerDrawSettings.setMaxPerPlace(PassengerDrawSettings.MAX_PER_PLACE_MAX);
					waitUntil = ticks + 100;
					stage = 6;
				}
			}
			case 2 -> {
				if (stats.boarded() > 0) {
					screenshot(client, "passenger-demo-3", stats);
					waitUntil = ticks + 30;
					stage = 5;
				} else if (ticks >= waitUntil) {
					results.add("no boarding within the timeout");
					finish(client, stats);
				}
			}
			case 3 -> {
				if (stats.alighted() > 0) {
					waitUntil = ticks + 60;
					stage = 9;
				} else if (ticks >= waitUntil) {
					finish(client, stats);
				}
			}
			case 9 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-alighting", stats);
					waitUntil = ticks + 70;
					stage = 11;
				}
			}
			case 11 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-exits", stats);
					final org.mtr.aurelia.client.screen.AureliaPanelScreen panel = new org.mtr.aurelia.client.screen.AureliaPanelScreen(null, org.mtr.aurelia.client.screen.AureliaPanelScreen.Tab.PASSENGERS);
					client.setScreen(panel);
					for (int i = 0; i < 20; i++) {
						panel.mouseScrolled(0, 0, -1);
					}
					waitUntil = ticks + 20;
					stage = 10;
				}
			}
			case 10 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-panel-routes", stats);
					finish(client, stats);
				}
			}
			case 6 -> {
				if (ticks >= waitUntil) {
					results.add("frame with " + PassengerRenderer.lastFigures + " people: fps=" + client.getCurrentFps() + " renderMicros=" + Math.round(PassengerRenderer.renderMicros));
					PassengerDrawSettings.setDraw(false);
					waitUntil = ticks + 100;
					stage = 7;
				}
			}
			case 7 -> {
				if (ticks >= waitUntil) {
					results.add("figures off: fps=" + client.getCurrentFps());
					PassengerDrawSettings.setDraw(true);
					PassengerDrawSettings.setMaxWalkers(PassengerDrawSettings.MAX_WALKERS_MAX);
					client.player.networkHandler.sendCommand("atr_passengers demo 60");
					// Eye level, along the platform towards the exits
					client.player.networkHandler.sendCommand("tp @s 289.5 -59 7.5 facing 276 -58.5 2");
					waitUntil = ticks + TRAIN_TIMEOUT_TICKS;
					stage = 2;
				}
			}
			case 8 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-riders", stats);
					// Riders get off at the next stop: walkers are only drawn near the camera
					client.player.networkHandler.sendCommand("tp @s 125.5 -58 8.5 facing 140 -57 11");
					waitUntil = ticks + TRAIN_TIMEOUT_TICKS;
					stage = 3;
				}
			}
			case 5 -> {
				if (ticks >= waitUntil) {
					screenshot(client, "passenger-demo-walking", stats);
					results.add("walkers started " + PassengerRenderer.walksStarted);
					waitUntil = ticks + 80;
					stage = 8;
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
		results.add("final " + stats + " walkers started " + PassengerRenderer.walksStarted);
		results.add("routes " + routes + " (" + alighters + " alighting) crossing a block " + routesCrossing + ", straight legs would have crossed " + straightCrossing + ", most turns " + mostTurns);
		results.add("arrived by stairs " + arrivedBy[0] + " escalator " + arrivedBy[1] + " lift " + arrivedBy[2] + "; left by stairs " + leftBy[0] + " escalator " + leftBy[1] + " lift " + leftBy[2] + "; exits on last map " + PassengerRenderer.exitsLeaving + " out, " + PassengerRenderer.exitsArriving + " in");
		for (final org.mtr.core.data.Lift lift : org.mtr.mod.client.MinecraftClientData.getInstance().lifts) {
			lift.iterateFloors(floor -> results.add("lift " + lift.getId() + " floor " + floor.getPosition().getX() + " " + floor.getPosition().getY() + " " + floor.getPosition().getZ() + " angle " + lift.getAngle() + " depth " + lift.getDepth()));
		}
		results.add("routed " + PassengerRenderer.walksRouted + " straight " + PassengerRenderer.walksStraight + " scanMicros=" + Math.round(PassengerRenderer.scanMicros) + " worst routeMicros=" + Math.round(PassengerRenderer.routeMicros) + " worst exitMicros=" + Math.round(PassengerRenderer.exitMicros));
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
