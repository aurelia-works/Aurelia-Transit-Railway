package org.mtr.mod.data;

import org.mtr.core.data.Rail;

/**
 * The Rail Builder's work along one rail: the bridge, tunnel and tunnel wall creators in one, deciding block by block
 * from the ground that is there. Walls go on the tunnel's outline only where there is ground, ground in the clearance is
 * dug out, and a deck goes under the track. Inside rock that is a lined tunnel, on a hillside a cutting walled on the
 * hill side, and in the air a bridge. Any of the three parts can be switched off.
 */
public final class RailBuilder {

	private int phase;
	private int done;

	private final boolean isSlab;
	private final boolean[] enabled;
	/**
	 * The outline first, so walls are decided from the ground as it was; then the clearance, then the deck.
	 */
	private final RailActionPlan[] plans;

	public RailBuilder(Rail rail, int radius, int height, boolean isSlab, boolean deck, boolean clear, boolean walls) {
		this.isSlab = isSlab;
		enabled = new boolean[]{walls, clear, deck};
		plans = new RailActionPlan[]{
				RailActionPlan.tunnel(rail, radius + 1, height + 1, false),
				RailActionPlan.tunnel(rail, radius, height, true),
				RailActionPlan.bridge(rail, radius, isSlab),
		};
	}

	/**
	 * Walks and builds until done or {@code deadline} ({@link System#nanoTime}) has passed, at least one step.
	 *
	 * @return whether the whole rail is built
	 */
	public boolean step(long deadline, Terrain terrain) {
		do {
			while (phase < plans.length && !enabled[phase]) {
				phase++;
			}
			if (phase == plans.length) {
				return true;
			}
			final RailActionPlan plan = plans[phase];
			if (plan.walk(deadline)) {
				while (done < plan.size() && System.nanoTime() < deadline) {
					apply(phase, plan.get(done++), terrain);
				}
				if (done == plan.size()) {
					phase++;
					done = 0;
				}
			}
		} while (System.nanoTime() < deadline);
		while (phase < plans.length && !enabled[phase]) {
			phase++;
		}
		return phase == plans.length;
	}

	/**
	 * How far the build is, from 0 to 1.
	 */
	public double progress() {
		double total = 0;
		double finished = 0;
		for (int i = 0; i < plans.length; i++) {
			if (enabled[i]) {
				total++;
				if (i < phase) {
					finished++;
				} else if (i == phase) {
					final RailActionPlan plan = plans[i];
					finished += 0.5 * plan.walked() + (plan.size() == 0 ? 0 : 0.5 * done / plan.size());
				}
			}
		}
		return total == 0 ? 1 : finished / total;
	}

	private void apply(int phase, RailActionPlan.Op op, Terrain terrain) {
		final int x = op.x();
		final int y = op.y();
		final int z = op.z();
		switch (phase) {
			case 0:
				if (terrain.isGround(x, y, z) && terrain.canChange(x, y, z)) {
					terrain.set(x, y, z, Part.WALL, false);
				}
				break;
			case 1:
				if (terrain.isGround(x, y, z) && terrain.canChange(x, y, z)) {
					terrain.set(x, y, z, Part.AIR, false);
				}
				break;
			default:
				if (op.clearAbove() && terrain.isGround(x, y + 1, z) && terrain.canChange(x, y + 1, z)) {
					terrain.set(x, y + 1, z, Part.AIR, false);
				}
				if (terrain.canChange(x, y, z)) {
					terrain.set(x, y, z, Part.DECK, isSlab && op.topHalf());
				}
				break;
		}
	}

	public enum Part {DECK, WALL, AIR}

	/**
	 * The world as the builder sees it. {@code isGround}: not air and not a fluid. {@code canChange}: not a rail node and not
	 * a block entity.
	 */
	public interface Terrain {

		boolean isGround(int x, int y, int z);

		boolean canChange(int x, int y, int z);

		/**
		 * {@code topHalf}: for a slab deck, the slab's half.
		 */
		void set(int x, int y, int z, Part part, boolean topHalf);
	}
}
