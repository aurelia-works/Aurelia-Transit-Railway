package org.mtr.perfbench;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.tool.Vector;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.MtrFrameStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Dev-only perf bench (src/perfbench, never in the release jar). {@code ./gradlew :fabric:runPerfbench} boots into
 * fabric/run/perf/saves/ATU Test (ATU's bench world: NS Civity, Complementary on Iris), parks the camera 40, 100 and
 * 170 m from the trains with every ATR fix off and on, shaders off and on, and writes perfbench.txt in the run folder.
 * Same views and numbers as ATU 1.4.0's PerfBench, so the two can be compared. Closes the game when done.
 */
public final class PerfBench implements ClientModInitializer {

	private static final Logger LOGGER = LoggerFactory.getLogger("ATR perf bench");
	private static final int SECONDS = 20;
	private static final int START_TICKS = 30 * SECONDS;
	private static final int SETTLE_TICKS = 4 * SECONDS;
	private static final int SAMPLE_TICKS = 10 * SECONDS;
	private static final int[] DISTANCES = {40, 100, 170};

	private record Fixes(String label, boolean merge, boolean fastPath, boolean cheapShadows, boolean trainLod, boolean railLod, boolean doors) {
	}

	private record Step(boolean shaders, Fixes fixes, int distance) {
	}

	private static final Fixes NONE = new Fixes("MTR only", false, false, false, false, false, false);
	private static final Fixes MERGE = new Fixes("merge", true, false, false, false, false, false);
	private static final Fixes MERGE_FAST = new Fixes("merge + fast path", true, true, false, false, false, false);
	private static final Fixes ALL = new Fixes("all fixes", true, true, true, true, true, true);

	private final List<Step> steps = new ArrayList<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int stepIndex = -1;
	private int stepTicks;
	private long frames;
	private long sampleStart;
	private double drawSum;
	private double mtrSum;
	private int statCount;
	private boolean sampling;
	private Vec3d center = Vec3d.ZERO;
	private Vec3d side = new Vec3d(1, 0, 0);
	private CompletableFuture<Void> reload;

	@Override
	public void onInitializeClient() {
		for (final boolean shaders : new boolean[]{false, true}) {
			for (final Fixes fixes : new Fixes[]{NONE, ALL}) {
				for (final int distance : DISTANCES) {
					steps.add(new Step(shaders, fixes, distance));
				}
			}
			if (shaders) {
				steps.add(new Step(true, MERGE, 40));
				steps.add(new Step(true, MERGE_FAST, 40));
			}
		}
		WorldRenderEvents.END.register(context -> {
			if (sampling) {
				frames++;
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(MinecraftClient minecraftClient) {
		if (minecraftClient.player == null || minecraftClient.world == null) {
			return;
		}
		ticks++;
		if (stepIndex < 0) {
			if (ticks < START_TICKS) {
				return;
			}
			final List<VehicleExtension> trains = new ArrayList<>(MinecraftClientData.getInstance().vehicles);
			if (trains.isEmpty()) {
				results.add("No trains in view after 30 s; nothing measured.");
				finish(minecraftClient);
				return;
			}
			locate(trains);
			stepIndex = 0;
			startStep(minecraftClient);
			return;
		}
		if (reload != null) {
			if (!reload.isDone() || minecraftClient.getOverlay() != null) {
				return;
			}
			// Models rebuild after the reload; give them time before settling
			reload = null;
			stepTicks = -10 * SECONDS;
		}
		stepTicks++;
		if (stepTicks == SETTLE_TICKS) {
			sampling = true;
			frames = 0;
			drawSum = 0;
			mtrSum = 0;
			statCount = 0;
			sampleStart = System.nanoTime();
		} else if (stepTicks > SETTLE_TICKS) {
			if ((stepTicks - SETTLE_TICKS) % SECONDS == 0 && MtrFrameStats.INSTANCE.hasResult()) {
				drawSum += MtrFrameStats.INSTANCE.drawsPerFrame();
				mtrSum += MtrFrameStats.INSTANCE.mtrMillisPerFrame();
				statCount++;
			}
			if (stepTicks >= SETTLE_TICKS + SAMPLE_TICKS) {
				sampling = false;
				final Step step = steps.get(stepIndex);
				final double fps = frames / ((System.nanoTime() - sampleStart) / 1e9);
				final String line = String.format(Locale.ROOT, "shaders %-3s %-20s %3d m: %7.1f fps, %6.2f ms/frame, %6.0f MTR draws/frame, %5.2f ms all of MTR, %d vehicles", step.shaders() ? "on" : "off", step.fixes().label(), step.distance(), fps, 1000 / fps,
						statCount == 0 ? 0 : drawSum / statCount, statCount == 0 ? 0 : mtrSum / statCount, MinecraftClientData.getInstance().vehicles.size());
				LOGGER.info(line);
				results.add(line);
				stepIndex++;
				if (stepIndex >= steps.size()) {
					finish(minecraftClient);
				} else {
					startStep(minecraftClient);
				}
			}
		}
	}

	private void startStep(MinecraftClient minecraftClient) {
		final Step step = steps.get(stepIndex);
		stepTicks = 0;
		shaders(step.shaders());
		if (configure(step.fixes())) {
			// Merged parts only apply to models built from now on
			reload = minecraftClient.reloadResources();
		}
		final Vec3d eye = center.add(side.multiply(step.distance())).add(0, Math.max(8, step.distance() * 0.25), 0);
		minecraftClient.player.networkHandler.sendCommand(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f facing %.2f %.2f %.2f", eye.x, eye.y, eye.z, center.x, center.y, center.z));
	}

	/** Centre of all bogies, and the direction across the track from the first car. */
	private void locate(List<VehicleExtension> trains) {
		double x = 0, y = 0, z = 0;
		int n = 0;
		Vec3d along = null;
		for (final VehicleExtension vehicle : trains) {
			for (final var car : vehicle.getVehicleCarsAndPositions()) {
				for (final var bogie : car.right()) {
					for (final Vector v : List.of(bogie.left(), bogie.right())) {
						x += v.x;
						y += v.y;
						z += v.z;
						n++;
					}
					if (along == null) {
						along = new Vec3d(bogie.right().x - bogie.left().x, 0, bogie.right().z - bogie.left().z);
					}
				}
			}
		}
		center = new Vec3d(x / n, y / n + 2, z / n);
		if (along != null && along.lengthSquared() > 1e-6) {
			side = new Vec3d(-along.z, 0, along.x).normalize();
		}
		results.add(String.format(Locale.ROOT, "%d train(s), centre %.0f %.0f %.0f", trains.size(), center.x, center.y, center.z));
	}

	/** Returns whether the merge setting changed (models must be rebuilt). */
	private static boolean configure(Fixes fixes) {
		final Client client = Config.getClient();
		final boolean mergeChanged = client.getMergeTrainParts() != fixes.merge();
		if (mergeChanged) {
			client.toggleMergeTrainParts();
		}
		if (client.getShaderPackFastPath() != fixes.fastPath()) {
			client.toggleShaderPackFastPath();
		}
		if (client.getCheapShadows() != fixes.cheapShadows()) {
			client.toggleCheapShadows();
		}
		if (client.getTrainLod() != fixes.trainLod()) {
			client.toggleTrainLod();
		}
		if (client.getRailLod() != fixes.railLod()) {
			client.toggleRailLod();
		}
		if (client.getFasterDoorChecks() != fixes.doors()) {
			client.toggleFasterDoorChecks();
		}
		return mergeChanged;
	}

	/** Iris API by reflection: the bench does not compile against Iris. */
	private static void shaders(boolean on) {
		try {
			final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
			final Object config = api.getMethod("getConfig").invoke(api.getMethod("getInstance").invoke(null));
			Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig").getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
		} catch (ReflectiveOperationException e) {
			LOGGER.warn("Iris not available", e);
		}
	}

	private void finish(MinecraftClient minecraftClient) {
		try {
			Files.write(Path.of("perfbench.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("Could not write perfbench.txt", e);
		}
		LOGGER.info("Perf bench done: {} lines in perfbench.txt", results.size());
		minecraftClient.scheduleStop();
	}
}
