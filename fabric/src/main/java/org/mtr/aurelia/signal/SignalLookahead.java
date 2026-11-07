package org.mtr.aurelia.signal;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.mapping.holder.*;
import org.mtr.mod.Init;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.client.MinecraftClientData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The signal blocks a signal can see, from the rails the client already has.
 *
 * <p>MTR's old lamp logic was "red while occupied, yellow for five seconds, then green". This walks the rail graph
 * from the signal's node instead, so the aspect follows the real spacing: red when the first block is occupied,
 * yellow when the next one is, double yellow the block after that, green beyond. A block is the run of rails sharing
 * a set of signal colours, which is how MTR stores signal blocks.</p>
 *
 * <p>MTR has no interlocking, so "route set" means a rail exists on the signal's facing side. At a junction the
 * straightest matching rail is followed; a set route would choose the branch instead. Results are cached for a few
 * hundred milliseconds per head so this is not computed per frame.</p>
 */
public final class SignalLookahead {

	private static final int MAX_RAILS = 160;
	private static final long CACHE_MILLIS = 400;
	private static final int MAX_CACHE_ENTRIES = 2048;
	private static final Long2ObjectOpenHashMap<Cached> CACHE = new Long2ObjectOpenHashMap<>();
	private static final IntAVLTreeSet EMPTY = new IntAVLTreeSet();

	private SignalLookahead() {
	}

	private record First(Rail rail, Position end) {
	}

	private record Next(Rail rail, Position end) {
	}

	private static final class Cached {

		private final long time;
		private final int filterHash;
		private final int lamp;
		private final long posLong;
		private final boolean back;

		private Cached(long time, int filterHash, int lamp, long posLong, boolean back) {
			this.time = time;
			this.filterHash = filterHash;
			this.lamp = lamp;
			this.posLong = posLong;
			this.back = back;
		}
	}

	/**
	 * @param signalPos   the signal block's position
	 * @param degrees     the facing direction to look along, the same angle {@code RenderSignalBase} uses
	 * @param aspectCount 2, 3 or 4
	 * @param filterColors the signal's colour filter; empty for an unfiltered head
	 * @param fallback    MTR's own aspect, used before the client has rail data
	 * @return MTR's lamp index: 0 clear, 1 red, 2 yellow, 3 double yellow
	 */
	public static int lamp(BlockPos signalPos, float degrees, boolean isBackSide, int aspectCount, IntAVLTreeSet filterColors, int fallback) {
		final ClientWorld world = MinecraftClient.getInstance().getWorldMapped();
		if (world == null) {
			return fallback;
		}

		final long key = signalPos.asLong() * 31 + (isBackSide ? 1 : 0);
		final long now = System.currentTimeMillis();
		final int filterHash = filterColors == null ? 0 : filterColors.hashCode();
		final Cached cached = CACHE.get(key);
		if (cached != null && now - cached.time < CACHE_MILLIS && cached.filterHash == filterHash && cached.back == isBackSide && cached.posLong == signalPos.asLong()) {
			return cached.lamp;
		}

		final int lamp = compute(world, signalPos, degrees, aspectCount, filterColors == null ? EMPTY : filterColors, fallback);
		if (CACHE.size() > MAX_CACHE_ENTRIES) {
			CACHE.clear();
		}
		CACHE.put(key, new Cached(now, filterHash, lamp, signalPos.asLong(), isBackSide));
		return lamp;
	}

	private static int compute(ClientWorld world, BlockPos signalPos, float degrees, int aspectCount, IntAVLTreeSet filterColors, int fallback) {
		final MinecraftClientData data = MinecraftClientData.getInstance();
		if (data.positionsToRail.isEmpty()) {
			return fallback;
		}

		final BlockPos node = getNodePos(world, signalPos, Direction.fromRotation(degrees));
		if (node == null) {
			return fallback;
		}

		final Position start = Init.blockPosToPosition(node);
		final First first = findFirst(data, start, degrees, filterColors);
		if (first == null) {
			return SignalAspect.RED.mtrAspect();
		}

		final List<SignalWalk.Step> steps = new ArrayList<>();
		final ObjectOpenHashSet<String> visited = new ObjectOpenHashSet<>();
		Position from = start;
		Position to = first.end;
		Rail rail = first.rail;
		boolean routeEnded = true;

		for (int i = 0; i < MAX_RAILS; i++) {
			if (!visited.add(rail.getHexId())) {
				routeEnded = false;
				break;
			}
			final IntAVLTreeSet colors = rail.getSignalColors();
			final IntAVLTreeSet blockedColors = steps.isEmpty() && !filterColors.isEmpty() ? filterColors : colors;
			steps.add(new SignalWalk.Step(signature(colors), blocked(data, rail, blockedColors), rail.railMath.getLength()));
			final double heading = heading(from, to);
			final Next next = findNext(data, to, heading, rail, visited, filterColors);
			if (next == null) {
				break;
			}
			from = to;
			to = next.end;
			rail = next.rail;
			if (i == MAX_RAILS - 1) {
				routeEnded = false;
			}
		}

		return SignalWalk.aspect(aspectCount, true, SignalWalk.walk(steps, routeEnded)).mtrAspect();
	}

	@Nullable
	private static First findFirst(MinecraftClientData data, Position start, float degrees, IntAVLTreeSet filterColors) {
		final Object2ObjectOpenHashMap<Position, Rail> connected = data.positionsToRail.get(start);
		if (connected == null) {
			return null;
		}

		First best = null;
		double bestScore = 720;
		for (final Object2ObjectMap.Entry<Position, Rail> entry : connected.object2ObjectEntrySet()) {
			final Position end = entry.getKey();
			final Rail rail = entry.getValue();
			final double difference = angleDifference(start, end, degrees);
			if (difference >= 90) {
				continue;
			}
			double score = difference;
			if (!filterColors.isEmpty() && intersects(rail.getSignalColors(), filterColors)) {
				score -= 360;
			}
			if (score < bestScore) {
				bestScore = score;
				best = new First(rail, end);
			}
		}
		return best;
	}

	@Nullable
	private static Next findNext(MinecraftClientData data, Position from, double heading, Rail current, ObjectOpenHashSet<String> visited, IntAVLTreeSet filterColors) {
		final Object2ObjectOpenHashMap<Position, Rail> connected = data.positionsToRail.get(from);
		if (connected == null) {
			return null;
		}

		Next best = null;
		double bestScore = 720;
		final int currentSignature = signature(current);
		for (final Object2ObjectMap.Entry<Position, Rail> entry : connected.object2ObjectEntrySet()) {
			final Rail candidate = entry.getValue();
			if (candidate == current || visited.contains(candidate.getHexId())) {
				continue;
			}
			final Position end = entry.getKey();
			final double difference = angleDifference(from, end, (float) heading);
			if (difference >= 90) {
				continue;
			}
			double score = difference;
			if (signature(candidate) == currentSignature) {
				score -= 360;
			}
			if (!filterColors.isEmpty() && intersects(candidate.getSignalColors(), filterColors)) {
				score -= 180;
			}
			if (score < bestScore) {
				bestScore = score;
				best = new Next(candidate, end);
			}
		}
		return best;
	}

	private static double angleDifference(Position from, Position to, float degrees) {
		final double heading = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
		return Math.abs(Utilities.circularDifference(Math.round(heading), Math.round(degrees), 360));
	}

	private static double heading(Position from, Position to) {
		return Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
	}

	public static int signature(Rail rail) {
		return signature(rail.getSignalColors());
	}

	public static int signature(IntAVLTreeSet colors) {
		int signature = 0;
		for (final int color : colors) {
			signature = signature * 31 + color;
		}
		return signature;
	}

	public static boolean blocked(MinecraftClientData data, Rail rail) {
		return blocked(data, rail, rail.getSignalColors());
	}

	public static boolean blocked(MinecraftClientData data, Rail rail, IntAVLTreeSet colors) {
		final LongArrayList blockedColors = data.railIdToCurrentlyBlockedSignalColors.get(rail.getHexId());
		if (blockedColors == null) {
			return false;
		}
		for (final long color : blockedColors) {
			if (colors.contains((int) color)) {
				return true;
			}
		}
		return false;
	}

	private static boolean intersects(IntAVLTreeSet colors, IntAVLTreeSet filter) {
		for (final int color : colors) {
			if (filter.contains(color)) {
				return true;
			}
		}
		return false;
	}

	@Nullable
	private static BlockPos getNodePos(ClientWorld world, BlockPos pos, Direction facing) {
		int closestDistance = Integer.MAX_VALUE;
		BlockPos closestPos = null;
		for (int z = -4; z <= 4; z++) {
			for (int x = -4; x <= 4; x++) {
				for (int y = -5; y <= 5; y++) {
					final BlockPos checkPos = pos.up(y).offset(facing.rotateYClockwise(), x).offset(facing, z);
					final BlockState checkState = world.getBlockState(checkPos);
					final int distance = checkPos.getManhattanDistance(new Vector3i(pos.data));
					if (checkState.getBlock().data instanceof BlockNode && distance < closestDistance) {
						closestDistance = distance;
						closestPos = checkPos;
					}
				}
			}
		}
		return closestPos;
	}
}
