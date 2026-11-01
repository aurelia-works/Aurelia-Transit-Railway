package org.mtr.aurelia.client;

import java.util.ArrayList;
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

	private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	private final int minX;
	private final int minY;
	private final int minZ;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final BitSet standable;
	private int next;

	public PassengerWalkGrid(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
		this.minX = minX;
		this.minY = minY;
		this.minZ = minZ;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		standable = new BitSet(sizeX * sizeY * sizeZ);
	}

	/** Reads up to {@code maxCells} more cells; true once the whole grid is read. */
	public boolean scan(Probe probe, int maxCells) {
		final int total = sizeX * sizeY * sizeZ;
		final int end = Math.min(total, next + maxCells);
		for (; next < end; next++) {
			final int x = next % sizeX;
			final int z = next / sizeX % sizeZ;
			final int y = next / sizeX / sizeZ;
			standable.set(next, probe.standable(minX + x, minY + y, minZ + z));
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
					if (to < 0 || !standable.get(to)) {
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
