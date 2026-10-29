package org.mtr.mod.data;

import org.mtr.core.data.Rail;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.function.Consumer;

/**
 * The blocks a {@link RailAction} changes, walked along the rail before any is placed. The walk samples the rail every
 * {@link #INCREMENT} along and across, so each block is reached about a hundred times; the plan keeps the first visit
 * only, in walk order, and the world is touched once per block. The walk is pure, so it is spread over ticks by deadline.
 */
public final class RailActionPlan {

	private double distance;
	private boolean done;

	private final Rail rail;
	private final double length;
	private final int radius;
	private final int height;
	private final boolean includeMiddle;
	private final boolean bridge;
	private final boolean isSlab;
	private final LongOpenHashSet seen = new LongOpenHashSet();
	private final ObjectArrayList<Op> ops = new ObjectArrayList<>();

	private static final double INCREMENT = 0.1;

	private RailActionPlan(Rail rail, int radius, int height, boolean includeMiddle, boolean bridge, boolean isSlab) {
		this.rail = rail;
		this.radius = radius;
		this.height = height;
		this.includeMiddle = includeMiddle;
		this.bridge = bridge;
		this.isSlab = isSlab;
		length = rail.railMath.getLength();
	}

	public static RailActionPlan bridge(Rail rail, int radius, boolean isSlab) {
		return new RailActionPlan(rail, radius, 0, false, true, isSlab);
	}

	public static RailActionPlan tunnel(Rail rail, int radius, int height, boolean includeMiddle) {
		return new RailActionPlan(rail, radius, height, includeMiddle, false, false);
	}

	/**
	 * Walks until the rail is done or {@code deadline} ({@link System#nanoTime}) has passed, at least one step.
	 *
	 * @return whether the whole rail is walked
	 */
	public boolean walk(long deadline) {
		if (done) {
			return true;
		}
		do {
			final Vector pos1 = rail.railMath.getPosition(distance, false);
			distance += INCREMENT;
			final Vector pos2 = rail.railMath.getPosition(distance, false);
			final Vector vec3 = new Vector(pos2.x - pos1.x, 0, pos2.z - pos1.z).normalize().rotateY((float) Math.PI / 2);

			for (double x = -radius; x <= radius; x += INCREMENT) {
				final Vector editPos = pos1.add(vec3.multiply(x, 0, x));
				final boolean wholeNumber = Math.floor(editPos.y) == Math.ceil(editPos.y);
				if (includeMiddle || Math.abs(x) > radius - INCREMENT || radius == 0) {
					for (int y = 0; y <= height; y++) {
						if (y < height || !wholeNumber || (height == 0 && radius == 0)) {
							add(editPos.add(0, y, 0));
						}
					}
				} else {
					add(editPos.add(0, Math.max(0, wholeNumber ? height - 1 : height), 0));
				}
			}

			if (length - distance < INCREMENT) {
				done = true;
				return true;
			}
		} while (System.nanoTime() < deadline);
		return false;
	}

	/**
	 * How far the walk is, from 0 to 1.
	 */
	public double walked() {
		return done ? 1 : Math.min(1, distance / length);
	}

	public int size() {
		return ops.size();
	}

	public Op get(int index) {
		return ops.get(index);
	}

	public void forEachOp(Consumer<Op> consumer) {
		ops.forEach(consumer);
	}

	private void add(Vector vector) {
		final int x = (int) Math.floor(vector.x);
		final int y = (int) Math.floor(vector.y);
		final int z = (int) Math.floor(vector.z);
		final Op op;
		if (bridge) {
			// A slab goes in the top half of the block below the rail, or the bottom half of the rail's block when the rail is
			// in its upper half; anything else fills the block below and clears the rail's block
			if (isSlab && vector.y - y >= 0.5) {
				op = new Op(x, y, z, false, false);
			} else {
				op = new Op(x, y - 1, z, true, true);
			}
		} else {
			op = new Op(x, y, z, false, false);
		}
		if (seen.add(op.key())) {
			ops.add(op);
		}
	}

	/**
	 * One block to change at {@code x, y, z}. For a bridge, {@code topHalf} is the slab half to place and {@code clearAbove}
	 * means the block above (the rail's block) is cleared to air first.
	 */
	public record Op(int x, int y, int z, boolean topHalf, boolean clearAbove) {

		private long key() {
			return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (long) (y * 2 + (topHalf ? 1 : 0)) & 0xFFF;
		}

		public String halfKey() {
			return x + "," + (y * 2 + (topHalf ? 1 : 0)) + "," + z;
		}
	}
}
