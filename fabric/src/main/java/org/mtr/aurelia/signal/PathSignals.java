package org.mtr.aurelia.signal;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.mtr.core.data.PathData;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.mod.block.BlockSignalBase;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.VehicleExtension;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The signals ahead on a train's own path. MTR gives the client the whole path of the train it is in, so unlike the
 * lineside heads this can follow the set route exactly rather than guessing at a junction.
 *
 * <p>A boundary is a rail whose signal colours differ from the rail before it: that is where the next signal stands.
 * Its head is looked up near the boundary node to know whether it has two, three or four aspects, so a three-aspect
 * green is not mistaken for a four-aspect double yellow. The aspect of the signal before the train reaches it is what
 * the AWS magnet and the TPWS train stop sensor need.</p>
 */
public final class PathSignals {

	public static final int MAX_RAILS = 160;
	private static final int DEFAULT_ASPECTS = 4;
	private static final long ASPECT_CACHE_MILLIS = 10_000;
	private static final int MAX_ASPECT_CACHE = 2048;
	private static final Object2ObjectOpenHashMap<Position, CachedAspects> ASPECT_CACHE = new Object2ObjectOpenHashMap<>();
	private static ClientWorld cachedWorld;

	private PathSignals() {
	}

	public record Boundary(int railIndex, SignalAspect aspect, double metresFromHead, int aspects, boolean signal) {
	}

	private static final class CachedAspects {

		private final long time;
		private final int aspects;

		private CachedAspects(long time, int aspects) {
			this.time = time;
			this.aspects = aspects;
		}
	}

	public static List<Boundary> ahead(VehicleExtension vehicle, int headIndex) {
		final List<PathData> path = vehicle.vehicleExtraData.immutablePath;
		if (headIndex < 0 || headIndex >= path.size()) {
			return Collections.emptyList();
		}

		final int end = Math.min(path.size(), headIndex + MAX_RAILS);
		final int[] signatures = new int[end - headIndex];
		final boolean[] blocked = new boolean[end - headIndex];
		final double[] lengths = new double[end - headIndex];
		for (int i = headIndex; i < end; i++) {
			final Rail rail = path.get(i).getRail();
			if (rail == null) {
				signatures[i - headIndex] = 0;
				lengths[i - headIndex] = Math.max(0, path.get(i).getEndDistance() - path.get(i).getStartDistance());
			} else {
				final IntAVLTreeSet colors = rail.getSignalColors();
				signatures[i - headIndex] = SignalLookahead.signature(colors);
				blocked[i - headIndex] = SignalLookahead.blocked(MinecraftClientData.getInstance(), rail, colors);
				lengths[i - headIndex] = rail.railMath.getLength();
			}
		}

		final List<Boundary> boundaries = new ArrayList<>();
		double metres = 0;
		for (int i = 0; i + headIndex < end; i++) {
			if (i > 0 && signatures[i] != 0 && signatures[i] != signatures[i - 1]) {
				final int foundAspects = aspectsAt(path.get(headIndex + i));
				final boolean signal = foundAspects > 0;
				final int aspects = signal ? foundAspects : DEFAULT_ASPECTS;
				boundaries.add(new Boundary(headIndex + i, aspect(signatures, blocked, lengths, i, end - headIndex, aspects, end == path.size()), metres, aspects, signal));
				if (boundaries.size() >= 4) {
					break;
				}
			}
			metres += lengths[i];
		}
		return boundaries;
	}

	private static SignalAspect aspect(int[] signatures, boolean[] blocked, double[] lengths, int from, int size, int aspects, boolean routeEnded) {
		final List<SignalWalk.Step> steps = new ArrayList<>();
		for (int i = from; i < size; i++) {
			steps.add(new SignalWalk.Step(signatures[i], blocked[i], lengths[i]));
		}
		return SignalWalk.aspect(aspects, true, SignalWalk.walk(steps, routeEnded));
	}

	/** Two, three or four, from the signal block next to the boundary node; four when no head is found. */
	private static int aspectsAt(PathData pathData) {
		final Position start = pathData.getOrderedPosition1();
		final ClientWorld world = MinecraftClient.getInstance().world;
		if (world != cachedWorld) {
			cachedWorld = world;
			ASPECT_CACHE.clear();
		}
		final long now = System.currentTimeMillis();
		final CachedAspects cached = ASPECT_CACHE.get(start);
		if (cached != null && now - cached.time < ASPECT_CACHE_MILLIS) {
			return cached.aspects;
		}
		final int aspects = scanAspects(start);
		if (ASPECT_CACHE.size() > MAX_ASPECT_CACHE) {
			ASPECT_CACHE.clear();
		}
		ASPECT_CACHE.put(start, new CachedAspects(now, aspects));
		return aspects;
	}

	private static int scanAspects(Position start) {
		final ClientWorld world = MinecraftClient.getInstance().world;
		if (world == null) {
			return DEFAULT_ASPECTS;
		}
		final BlockPos pos = new BlockPos((int) start.getX(), (int) start.getY(), (int) start.getZ());
		int closest = Integer.MAX_VALUE;
		int aspects = 0;
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = -5; y <= 5; y++) {
					final BlockPos checkPos = pos.add(x, y, z);
					final Block block = world.getBlockState(checkPos).getBlock();
					if (block instanceof BlockSignalBase) {
						final int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
						if (distance < closest) {
							closest = distance;
							aspects = aspectsOf(block);
						}
					}
				}
			}
		}
		return aspects;
	}

	private static int aspectsOf(Block block) {
		final String name = block.getClass().getSimpleName();
		if (name.contains("4Aspect")) {
			return 4;
		}
		if (name.contains("3Aspect")) {
			return 3;
		}
		return 2;
	}

	/** The index MTR uses for the rail under the front of the train. */
	public static int headIndex(VehicleExtension vehicle) {
		return Utilities.getIndexFromConditionalList(vehicle.vehicleExtraData.immutablePath, vehicle.getRailProgress() - 1);
	}

	/** Distance from the front of the train to the next platform stop, or NaN when none is ahead. */
	public static double metresToStop(VehicleExtension vehicle) {
		final PathData pathData = stopPath(vehicle);
		return pathData == null ? Double.NaN : pathData.getEndDistance() - vehicle.getRailProgress();
	}

	/** The next dwell path the front of the train will stop at, found the same way MTR finds its stopping point. */
	@Nullable
	public static PathData stopPath(VehicleExtension vehicle) {
		final List<PathData> path = vehicle.vehicleExtraData.immutablePath;
		final int headIndex = headIndex(vehicle);
		final double railProgress = vehicle.getRailProgress();
		for (int i = Math.min(path.size() - 1, headIndex + 1); i >= 0; i--) {
			final PathData pathData = path.get(i);
			if (i <= headIndex) {
				if (pathData.getEndDistance() <= railProgress - vehicle.vehicleExtraData.getTotalVehicleLength() || pathData.getSavedRailBaseId() == vehicle.vehicleExtraData.getSidingId()) {
					break;
				}
				if (pathData.getDwellTime() > 0) {
					return pathData;
				}
			}
		}
		return null;
	}
}
