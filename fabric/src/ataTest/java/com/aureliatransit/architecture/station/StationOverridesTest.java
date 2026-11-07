package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the fine-edit map: it replaces the planner's block at its cell and leaves every other cell as planned. */
class StationOverridesTest {

	@Test
	void anOverrideReplacesItsCellAndKeepsTheRest() {
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().platformLength(40).name("Test").build());
		final PlannedBlock first = plan.blocks().get(0);
		final StationPlanner.Cell cell = new StationPlanner.Cell(first.u(), first.y(), first.v());
		final StationPlan edited = plan.withOverrides(Map.of(cell, new StationOverride("minecraft:gold_block", LocalDir.PLUS_V)));
		assertEquals(plan.blocks().size(), edited.blocks().size());
		assertEquals("minecraft:gold_block", edited.blocks().stream()
				.filter(b -> b.u() == cell.u() && b.y() == cell.y() && b.v() == cell.v()).findFirst().orElseThrow().blockId());
		assertTrue(edited.blocks().stream().filter(b -> !(b.u() == cell.u() && b.y() == cell.y() && b.v() == cell.v()))
				.allMatch(plan.blocks()::contains));
	}

	@Test
	void airClearsACellAndAnEmptyCellCanBeAdded() {
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().platformLength(40).build());
		final PlannedBlock first = plan.blocks().get(0);
		final StationPlanner.Cell cell = new StationPlanner.Cell(first.u(), first.y(), first.v());
		final StationPlan cleared = plan.withOverrides(Map.of(cell, new StationOverride("minecraft:air", LocalDir.PLUS_V)));
		assertEquals(plan.blocks().size() - 1, cleared.blocks().size());
		assertFalse(cleared.blocks().stream().anyMatch(b -> b.u() == cell.u() && b.y() == cell.y() && b.v() == cell.v()));

		final StationPlanner.Cell empty = new StationPlanner.Cell(0, 40, 0);
		final StationPlan added = plan.withOverrides(Map.of(empty, new StationOverride("minecraft:lantern", LocalDir.PLUS_V)));
		assertEquals(plan.blocks().size() + 1, added.blocks().size());
		assertTrue(added.blocks().stream().anyMatch(b -> b.u() == 0 && b.y() == 40 && b.v() == 0 && b.blockId().equals("minecraft:lantern")));
	}

	@Test
	void noOverridesLeaveThePlanUntouched() {
		final StationPlan plan = StationPlanner.plan(StationSpec.builder().platformLength(32).build());
		assertTrue(plan.withOverrides(Map.of()) == plan);
	}
}
