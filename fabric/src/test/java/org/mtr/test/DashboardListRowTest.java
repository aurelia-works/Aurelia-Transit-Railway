package org.mtr.test;

import org.junit.jupiter.api.Test;
import org.mtr.mod.screen.DashboardList;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DashboardListRowTest {

	// list at x 100, rows start at y 50, 20 px rows, 5 rows a page, 12 items, 20 px strip at the right left to the hover buttons
	private static int row(double mouseX, double mouseY, int page) {
		return DashboardList.rowIndexAt(mouseX, mouseY, 100, 50, 180, 20, 5, page, 12);
	}

	@Test
	public void anyPointInTheRowCountsNotJustTheText() {
		assertEquals(0, row(101, 50, 0));
		assertEquals(0, row(279, 69, 0));
		assertEquals(2, row(190, 90, 0));
	}

	@Test
	public void theHoverButtonStripIsLeftToTheButtons() {
		assertEquals(-1, row(280, 60, 0));
	}

	@Test
	public void outsideTheListIsNoRow() {
		assertEquals(-1, row(99, 60, 0));
		assertEquals(-1, row(150, 49, 0));
		assertEquals(-1, row(150, 150, 0));
	}

	@Test
	public void secondPageContinuesTheCountAndEmptyRowsAreNoRow() {
		assertEquals(5, row(150, 55, 1));
		assertEquals(10, row(150, 55, 2));
		assertEquals(11, row(150, 75, 2));
		assertEquals(-1, row(150, 95, 2));
	}
}
