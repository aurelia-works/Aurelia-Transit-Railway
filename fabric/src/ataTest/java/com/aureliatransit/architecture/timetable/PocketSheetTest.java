package com.aureliatransit.architecture.timetable;

import com.aureliatransit.architecture.terminal.MapLine;
import com.aureliatransit.architecture.terminal.MapStop;
import com.aureliatransit.architecture.terminal.StationInfo;
import com.aureliatransit.architecture.terminal.SystemMap;
import com.aureliatransit.architecture.transit.PlatformReference;
import com.aureliatransit.architecture.wayfinding.LineBadge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketSheetTest {

	@Test
	void timetablePrintsCodePlatformsAndSortedTimes() {
		final StationInfo info = new StationInfo("Central", "CEN", List.of(new PlatformReference(1, "2", 9)), List.of(), List.of(), "", "", List.of());
		final PocketSheet.Sheet sheet = PocketSheet.timetable(info, List.of(3_600_000L, 60_000L, 60_000L));
		assertEquals("Central", sheet.title());
		assertEquals(PocketSheet.Kind.TIMETABLE, sheet.kind());
		assertEquals(List.of("CEN", "2", "00:01", "01:00"), sheet.lines());
	}

	@Test
	void lineMapMarksTheCurrentStopAndDropsFormatting() {
		final LineBadge badge = new LineBadge("R", 0xFF0000, null);
		final SystemMap map = new SystemMap(List.of(new MapLine(1, badge, "Red", List.of(
				new MapStop(1, "Alpha", false, false),
				new MapStop(2, "§cBeta", true, true)))));
		final PocketSheet.Sheet sheet = PocketSheet.lineMap("Central", map);
		assertEquals(PocketSheet.Kind.MAP, sheet.kind());
		assertEquals(1, sheet.lines().size());
		assertTrue(sheet.lines().get(0).startsWith("R "));
		assertTrue(sheet.lines().get(0).contains("Beta"));
		assertTrue(!sheet.lines().get(0).contains("§"));
	}

	@Test
	void emptyInputStillPrintsASheet() {
		assertEquals(List.of("No departures"), PocketSheet.timetable(StationInfo.EMPTY, List.of()).lines());
		assertEquals(List.of("No lines"), PocketSheet.lineMap("", SystemMap.EMPTY).lines());
	}
}
