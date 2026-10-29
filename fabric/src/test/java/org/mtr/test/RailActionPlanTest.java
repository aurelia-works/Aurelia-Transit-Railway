package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.mtr.mod.data.RailActionPlan;

public class RailActionPlanTest {

	private static final Rail RAIL = Rail.newRail(new Position(0, 64, 0), Angle.E, new Position(120, 68, 40), Angle.E, Rail.Shape.QUADRATIC, 0, new ObjectArrayList<>(), 80, 80, false, false, true, false, false, TransportMode.TRAIN);

	@Test
	public void bridgeVisitsEachHalfBlockOnce() {
		final RailActionPlan plan = RailActionPlan.bridge(RAIL, 3, false);
		while (!plan.walk(Long.MAX_VALUE)) {
		}
		final ObjectOpenHashSet<String> seen = new ObjectOpenHashSet<>();
		plan.forEachOp(op -> Assertions.assertTrue(seen.add(op.halfKey()), "planned twice: " + op.halfKey()));
		Assertions.assertTrue(seen.size() > 500, "a 3-wide bridge over this rail has hundreds of blocks, got " + seen.size());
	}

	@Test
	public void slabBridgeKeepsBothHalvesOfABlock() {
		final RailActionPlan plan = RailActionPlan.bridge(RAIL, 1, true);
		while (!plan.walk(Long.MAX_VALUE)) {
		}
		final boolean[] halves = new boolean[2];
		plan.forEachOp(op -> halves[op.topHalf() ? 1 : 0] = true);
		Assertions.assertTrue(halves[0] && halves[1], "a rising rail needs top and bottom slabs");
	}

	@Test
	public void tunnelVisitsEachBlockOnce() {
		final RailActionPlan plan = RailActionPlan.tunnel(RAIL, 2, 4, true);
		while (!plan.walk(Long.MAX_VALUE)) {
		}
		final ObjectOpenHashSet<String> seen = new ObjectOpenHashSet<>();
		plan.forEachOp(op -> Assertions.assertTrue(seen.add(op.halfKey()), "planned twice: " + op.halfKey()));
	}

	@Test
	public void walkStopsAtTheDeadlineAndResumes() {
		final RailActionPlan plan = RailActionPlan.bridge(RAIL, 3, false);
		Assertions.assertFalse(plan.walk(System.nanoTime() - 1), "a passed deadline still walks one step and stops");
		int ticks = 1;
		while (!plan.walk(System.nanoTime() + 1_000_000)) {
			ticks++;
		}
		Assertions.assertTrue(ticks > 1);
	}
}
