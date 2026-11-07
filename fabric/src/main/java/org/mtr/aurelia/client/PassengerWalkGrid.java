package org.mtr.aurelia.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The cells around a platform a person can stand in, and the shortest walk between two of them. The scan is done in slices,
 * so it can share out its cost over frames. No Minecraft types, so it can be tested without a client.
 */
public final class PassengerWalkGrid {

	/** Whether a person can stand with their feet in this cell. */
	public interface Probe {
		boolean standable(int x, int y, int z);
	}

	/** Which way someone may change level at a cell: a plain floor is {@link #FLAT}, and a running escalator goes one way. */
	public interface Climb {
		int of(int x, int y, int z);
	}

	public static final int FLAT = 0;
	public static final int BOTH_WAYS = 1;
	public static final int UP_ONLY = 2;
	public static final int DOWN_ONLY = 3;

	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
	/** Half a person's width: a leg that skips cells keeps this far off anything. */
	private static final double BODY = 0.3;
	private static final double LEG_SAMPLE = 0.25;

	private final int minX;
	private final int minY;
	private final int minZ;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final BitSet standable;
	private final BitSet up;
	private final BitSet down;
	private int next;

	public PassengerWalkGrid(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		standable = new BitSet(sizeX * sizeY * sizeZ);
		up = new BitSet(sizeX * sizeY * sizeZ);
		down = new BitSet(sizeX * sizeY * sizeZ);
	}

	/** Reads up to {@code maxCells} more cells; true once the whole grid is read. */
	public boolean scan(Probe probe, int maxCells) {
		return scan(probe, (x, y, z) -> BOTH_WAYS, maxCells);
	}

	/** Reads up to {@code maxCells} more cells, with what each allows for changing level. */
	public boolean scan(Probe probe, Climb climb, int maxCells) {
		final int total = sizeX * sizeY * sizeZ;
		final int end = Math.min(total, next + maxCells);
		for (; next < end; next++) {
			final int x = next % sizeX;
			final int z = next / sizeX % sizeZ;
			final int y = next / sizeX / sizeZ;
			final boolean open = probe.standable(minX + x, minY + y, minZ + z);
			standable.set(next, open);
			if (open) {
				final int kind = climb.of(minX + x, minY + y, minZ + z);
				up.set(next, kind == BOTH_WAYS || kind == UP_ONLY);
				down.set(next, kind == BOTH_WAYS || kind == DOWN_ONLY);
			}
		}
		return next >= total;
	}

	/** The cells from start to end as x, y, z triples, or null when there is no way within {@code maxNodes} cells. */
	public int[] path(int x1, int y1, int z1, int x2, int y2, int z2, int maxNodes) {
		final int start = index(x1, y1, z1);
		final int goal = index(x2, y2, z2);
		if (start < 0 || goal < 0 || !standable.get(start) || !standable.get(goal)) {
			return null;
		}
		final Map<Integer, Integer> cameFrom = new HashMap<>();
		final Map<Integer, Integer> cost = new HashMap<>();
		final PriorityQueue<int[]> open = new PriorityQueue<>((a, b) -> Integer.compare(a[0], b[0]));
		cost.put(start, 0);
		open.add(new int[]{0, start});
		int visited = 0;
		while (!open.isEmpty() && visited++ < maxNodes) {
			final int cell = open.poll()[1];
			if (cell == goal) {
				return walkBack(cameFrom, goal);
			}
			final int x = cell % sizeX;
			final int z = cell / sizeX % sizeZ;
			final int y = cell / sizeX / sizeZ;
			for (final int[] side : SIDES) {
				for (int step = -1; step <= 1; step++) {
					final int to = index(minX + x + side[0], minY + y + step, minZ + z + side[1]);
					if (to < 0 || !standable.get(to) || !canStep(cell, to, step)) {
						continue;
					}
					final int newCost = cost.get(cell) + 1;
					if (newCost < cost.getOrDefault(to, Integer.MAX_VALUE)) {
						cost.put(to, newCost);
						cameFrom.put(to, cell);
						final int estimate = Math.abs(minX + x + side[0] - x2) + Math.abs(minY + y + step - y2) + Math.abs(minZ + z + side[1] - z2);
						open.add(new int[]{newCost + estimate, to});
					}
				}
			}
		}
		return null;
	}

	/** A level changes only on a step that goes that way: at least one end must allow it, and neither end may be one-way against it. */
	private boolean canStep(int from, int to, int step) {
		if (step == 0) {
			return true;
		}
		final BitSet way = step > 0 ? up : down;
		final BitSet against = step > 0 ? down : up;
		return (way.get(from) || way.get(to)) && (way.get(from) || !against.get(from)) && (way.get(to) || !against.get(to));
	}

	/**
	 * Where this floor connects to other levels: the first cell of each separate way that ends {@code rise} levels off the start,
	 * nearest first. With {@code arriving}, the cells someone can walk from to the start, not to it from the start.
	 */
	public List<int[]> exits(int x, int y, int z, int rise, int apart, boolean arriving, int maxNodes) {
		final List<int[]> found = new ArrayList<>();
		final int start = index(x, y, z);
		if (start < 0 || !standable.get(start)) {
			return found;
		}
		final BitSet seen = new BitSet();
		final ArrayDeque<Integer> open = new ArrayDeque<>();
		seen.set(start);
		open.add(start);
		int visited = 0;
		while (!open.isEmpty() && visited++ < maxNodes) {
			final int cell = open.poll();
			final int cx = cell % sizeX;
			final int cz = cell / sizeX % sizeZ;
			final int cy = cell / sizeX / sizeZ;
			if (Math.abs(minY + cy - y) >= rise) {
				final int[] exit = {minX + cx, minY + cy, minZ + cz};
				if (found.stream().noneMatch(other -> Math.abs(other[0] - exit[0]) + Math.abs(other[2] - exit[2]) < apart)) {
					found.add(exit);
				}
				continue;
			}
			for (final int[] side : SIDES) {
				for (int step = -1; step <= 1; step++) {
					final int to = index(minX + cx + side[0], minY + cy + step, minZ + cz + side[1]);
					if (to >= 0 && standable.get(to) && !seen.get(to) && (arriving ? canStep(to, cell, -step) : canStep(cell, to, step))) {
						seen.set(to);
						open.add(to);
					}
				}
			}
		}
		return found;
	}

	public boolean standable(int x, int y, int z) {
		final int cell = index(x, y, z);
		return cell >= 0 && standable.get(cell);
	}

	/** The standable cell closest to this one, on its level first, then a step off it, or null within {@code radius}. */
	public int[] nearest(int x, int y, int z, int radius) {
		for (int ring = 0; ring <= radius; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
						for (final int step : new int[]{0, -1, 1}) {
							if (standable(x + dx, y + step, z + dz)) {
								return new int[]{x + dx, y + step, z + dz};
							}
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * A {@link #path} walked from x1, z1 to x2, z2 by someone who cuts corners: x, y, z triples of the turns, y being the cell's
	 * level. A leg skips cells only when a person's width stays on standable cells of one level all along it.
	 */
	public double[] turns(int[] cells, double x1, double z1, double x2, double z2) {
		final int count = cells.length / 3;
		if (count < 2) {
			return new double[]{x1, cells[1], z1, x2, cells[1], z2};
		}
		final double[] xs = new double[count];
		final double[] zs = new double[count];
		for (int i = 0; i < count; i++) {
			xs[i] = cells[i * 3] + 0.5;
			zs[i] = cells[i * 3 + 2] + 0.5;
		}
		xs[0] = x1;
		zs[0] = z1;
		xs[count - 1] = x2;
		zs[count - 1] = z2;
		final double[] kept = new double[cells.length];
		int size = 0;
		int from = 0;
		while (true) {
			kept[size++] = xs[from];
			kept[size++] = cells[from * 3 + 1];
			kept[size++] = zs[from];
			if (from == count - 1) {
				return Arrays.copyOf(kept, size);
			}
			int to = count - 1;
			while (to > from + 1 && (cells[to * 3 + 1] != cells[from * 3 + 1] || !clear(xs[from], zs[from], xs[to], zs[to], cells[from * 3 + 1]))) {
				to--;
			}
			from = to;
		}
	}

	private boolean clear(double x1, double z1, double x2, double z2, int y) {
		final int samples = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, z2 - z1) / LEG_SAMPLE));
		for (int i = 0; i <= samples; i++) {
			final double x = x1 + (x2 - x1) * i / samples;
			final double z = z1 + (z2 - z1) * i / samples;
			for (int side = 0; side < 4; side++) {
				if (!standable((int) Math.floor(x + (side < 2 ? BODY : -BODY)), y, (int) Math.floor(z + (side % 2 == 0 ? BODY : -BODY)))) {
					return false;
				}
			}
		}
		return true;
	}

	private int[] walkBack(Map<Integer, Integer> cameFrom, int goal) {
		final List<Integer> cells = new ArrayList<>();
		for (Integer cell = goal; cell != null; cell = cameFrom.get(cell)) {
			cells.add(cell);
		}
		Collections.reverse(cells);
		final int[] result = new int[cells.size() * 3];
		for (int i = 0; i < cells.size(); i++) {
			final int cell = cells.get(i);
			result[i * 3] = minX + cell % sizeX;
			result[i * 3 + 1] = minY + cell / sizeX / sizeZ;
			result[i * 3 + 2] = minZ + cell / sizeX % sizeZ;
		}
		return result;
	}

	private int index(int x, int y, int z) {
		final int cx = x - minX;
		final int cy = y - minY;
		final int cz = z - minZ;
		return cx < 0 || cy < 0 || cz < 0 || cx >= sizeX || cy >= sizeY || cz >= sizeZ ? -1 : cx + cz * sizeX + cy * sizeX * sizeZ;
	}
}
