package com.aureliatransit.architecture.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The realism pass of 10-25 (batch b): a city's real station, built when the station is named after it. */
class CityLandmarksTest {

	static StationSpec spec(String words) {
		return StationDescriptionParser.parse(words).spec();
	}

	static long count(StationPlan plan, String block) {
		return plan.blocks().stream().filter(b -> b.blockId().equals(block)).count();
	}

	@Test
	void tokyoIsRedBrickWithTwoDomedEndsFollowingItsLength() {
		for (final String words : new String[]{"tokyo station, 6 tracks, 120 long, named Tokyo",
				"ten track tokyo station, five island platforms, 300 long, station building with footbridge, named Tokyo"}) {
			final StationSpec spec = spec(words);
			assertEquals(Landmarks.TOKYO, Landmarks.of(spec), words);
			final StationPlan plan = StationPlanner.plan(spec);
			final int bw = plan.layout().buildingWidth();
			final var finials = plan.blocks().stream().filter(b -> b.u() < bw && b.blockId().equals("minecraft:lightning_rod")).toList();
			assertEquals(2, finials.size(), words);
			assertTrue(count(plan, "minecraft:bricks") > 1000, words);
			final var span = plan.blocks().stream().filter(b -> b.u() < bw && b.blockId().equals("minecraft:bricks")).mapToInt(PlannedBlock::v).summaryStatistics();
			// the domes stand at the two ends of the building, and the brick runs the whole way between them
			for (final PlannedBlock f : finials) {
				assertTrue(Math.min(f.v() - span.getMin(), span.getMax() - f.v()) <= 12, "finial at " + f.v() + " in " + span);
				assertTrue(f.y() > 30, "finial at " + f.y());
			}
		}
	}

	@Test
	void aBuildingTooShortForTokyoEndPavilionsStillBuilds() {
		final StationPlan plan = StationPlanner.plan(spec("tokyo station, 2 tracks, 48 long, named Tokyo"));
		assertNotNull(plan);
		assertTrue(plan.blocks().size() > 1000);
	}

	@Test
	void everySheetPassesTheAuditOverASpreadOfSizes() {
		final String[][] cities = {{"tokyo", "Tokyo"}, {"beijing", "Beijing South"}, {"kuala lumpur", "Kuala Lumpur"}, {"surbiton", "Surbiton"}};
		for (final String[] city : cities) {
			for (final int tracks : new int[]{1, 3, 4, 6, 10}) {
				for (final int length : new int[]{96, 120, 160, 300}) {
					for (final String extra : new String[]{"", ", side platforms", ", compact building", ", grand building"}) {
						final String words = tracks + " track " + city[0] + " station, " + length + " long" + extra + ", named " + city[1];
						final StationPlan plan = StationPlanner.plan(spec(words));
						assertEquals(java.util.List.of(), LayoutValidator.validate(plan), words);
						assertTrue(StationAudit.audit(plan).buildable(), words + " " + StationAudit.audit(plan).problems());
					}
				}
			}
		}
	}

	@Test
	void beijingSouthHasItsOwnLookAndThe1959SheetStaysOnBeijingRailwayStation() {
		// Beijing South's deep-roof look, not the 1959 Beijing Railway Station sheet
		final StationSpec south = spec("ten track beijing station, 300 long, named Beijing South");
		assertNull(Landmarks.of(south));
		assertEquals(BuildingStyle.BEIJING, south.building());
		final StationPlan southPlan = StationPlanner.plan(south);
		assertEquals(0, count(southPlan, "minecraft:prismarine_bricks"), "no 1959 glazed tile towers");

		// the 1959 sheet still takes its own names
		assertEquals(Landmarks.BEIJING, Landmarks.of(spec("ten track beijing station, 300 long, named Beijing Railway Station")));
		final StationPlan beijing = StationPlanner.plan(spec("ten track beijing station, 300 long, named Beijing Railway Station"));
		assertTrue(count(beijing, "minecraft:prismarine_bricks") > 100, "blue-green glazed tile towers");
		assertEquals(2, StationRealismTest.streetClocks(beijing).size(), "Beijing 1959's twin clock towers flank its central portal");
		assertEquals(java.util.List.of(), LayoutValidator.validate(beijing));
		assertEquals(java.util.List.of(), StationAudit.audit(beijing).problems());
	}

	@Test
	void kualaLumpurAndSurbitonHaveTheirSheets() {
		assertEquals(Landmarks.KUALA_LUMPUR, Landmarks.of(spec("four track kuala lumpur station, 160 long, named Kuala Lumpur")));
		assertEquals(Landmarks.SURBITON, Landmarks.of(spec("four track surbiton station, 250 long, named Surbiton")));
		assertEquals(Landmarks.COVENTRY, Landmarks.of(spec("three track coventry station, 250 long, named Coventry")));
		final StationPlan kl = StationPlanner.plan(spec("four track kuala lumpur station, 160 long, named Kuala Lumpur"));
		assertTrue(count(kl, "minecraft:calcite") > 1000, "white Moorish walls");
		final StationPlan coventry = StationPlanner.plan(spec("three track coventry station, island and side platforms, 250 long, city station building with footbridge, named Coventry"));
		assertEquals(java.util.List.of(), LayoutValidator.validate(coventry));
		assertEquals(java.util.List.of(), StationAudit.audit(coventry).problems());
		final StationPlan munich = StationPlanner.plan(spec("eight track real munich terminus, four island platforms, 200 long, 15 wide platforms, train shed"));
		assertEquals(java.util.List.of(), LayoutValidator.validate(munich));
		assertEquals(java.util.List.of(), StationAudit.audit(munich).problems());
	}

	/**
	 * "paris style" builds Gare du Nord's arched centre (in-game tour, 11-24: the arch was planned but the middle pavilion's
	 * pilasters, string courses and columns stood in front of it, and the glass touching them turned to posts, so it read as
	 * a square grid). Below the clock, the opening is arch glass and its bars only, with nothing in front of it.
	 */
	@Test
	void parisStyleFrontShowsItsArchedCentre() {
		final StationSpec spec = spec("6 track paris style station");
		assertEquals("paris", Landmarks.shapeOf(spec).key());
		final Landmark.Arch arch = Landmarks.PARIS.masses().stream().filter(m -> m instanceof Landmark.Arch).map(m -> (Landmark.Arch) m)
				.filter(a -> a.half() >= 9).findFirst().orElseThrow();
		final String glass = arch.skin().glass();
		final String bar = arch.skin().trim();
		final StationPlan plan = StationPlanner.plan(spec);
		final java.util.Map<String, String> at = new java.util.HashMap<>();
		final java.util.Map<String, Role> roles = new java.util.HashMap<>();
		plan.blocks().forEach(b -> at.put(b.u() + "," + b.y() + "," + b.v(), b.blockId()));
		plan.blocks().forEach(b -> roles.put(b.u() + "," + b.y() + "," + b.v(), b.role()));
		final var panes = plan.blocks().stream().filter(b -> b.blockId().equals(glass)).toList();
		assertTrue(panes.size() > 50, "arch glass " + panes.size());
		final int u = panes.get(0).u();
		assertTrue(panes.stream().allMatch(b -> b.u() == u), "the arch stands in one wall plane");
		final var span = panes.stream().mapToInt(PlannedBlock::v).summaryStatistics();
		final int mid = (span.getMin() + span.getMax()) / 2;
		final java.util.List<String> wrong = new java.util.ArrayList<>();
		// from above the entrance canopy up to the springing line, below the clock
		for (int y = StationGeometry.CANOPY_Y + 2; y <= arch.spring(); y++) {
			for (int v = mid - arch.half() + 1; v <= mid + arch.half() - 1; v++) {
				final String wall = at.get(u + "," + y + "," + v);
				// a post where a wall inside meets the glass stays (a wall's end against a window, as everywhere)
				final boolean partition = roles.get((u + 1) + "," + y + "," + v) == Role.WALL;
				if (!glass.equals(wall) && !bar.equals(wall) && !partition) {
					wrong.add("y" + y + " v" + v + " " + wall + " behind: " + at.get((u + 1) + "," + y + "," + v));
				}
				final String front = at.get((u - 1) + "," + y + "," + v);
				if (front != null) {
					wrong.add("y" + y + " v" + v + " in front: " + front);
				}
			}
		}
		assertEquals(java.util.List.of(), wrong.subList(0, Math.min(12, wrong.size())), wrong.size() + " cells");
	}
}
