package com.aureliatransit.architecture.bridge;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BridgeStampPlanTest {

	@Test
	public void samplesTheRailOncePerBlockAndDedupes() {
		final BridgeTemplate template = BridgeTemplate.of(3, 1, 1, List.of("minecraft:stone"),
				List.of(new BridgeTemplate.Cell(1, 0, 0, 0)));
		final BridgeStampPlan plan = BridgeStampPlan.of(template, straight(3), 1, 0);
		Assertions.assertEquals(4, plan.size());
		Assertions.assertEquals(new BridgeStampPlan.Placement(1, 64, 0, 0, 0), plan.placements().get(0));
		Assertions.assertEquals(new BridgeStampPlan.Placement(1, 64, -3, 0, 0), plan.placements().get(3));
	}

	@Test
	public void heightMovesTheWholeSection() {
		final BridgeTemplate template = BridgeTemplate.of(1, 1, 1, List.of("minecraft:stone"),
				List.of(new BridgeTemplate.Cell(0, 0, 0, 0)));
		final BridgeStampPlan plan = BridgeStampPlan.of(template, straight(0), 1, 5);
		Assertions.assertEquals(new BridgeStampPlan.Placement(0, 69, 0, 0, 0), plan.placements().get(0));
	}

	@Test
	public void tangentPicksTheNearestQuarterTurn() {
		Assertions.assertEquals(0, BridgeStampPlan.quarterTurns(0, -1), "north");
		Assertions.assertEquals(1, BridgeStampPlan.quarterTurns(1, 0), "east");
		Assertions.assertEquals(2, BridgeStampPlan.quarterTurns(0, 1), "south");
		Assertions.assertEquals(3, BridgeStampPlan.quarterTurns(-1, 0), "west");
	}

	private static BridgeStampPlan.Path straight(double length) {
		return new BridgeStampPlan.Path() {
			@Override
			public double length() {
				return length;
			}

			@Override
			public BridgeStampPlan.Sample at(double distance) {
				return new BridgeStampPlan.Sample(0.5, 64.2, -distance, 0, -1);
			}
		};
	}
}
