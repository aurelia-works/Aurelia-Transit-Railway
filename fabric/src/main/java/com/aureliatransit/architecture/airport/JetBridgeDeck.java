package com.aureliatransit.architecture.airport;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a docked jet bridge lays its walkable deck, in the bridge block's model frame: {@code d} blocks out along its facing
 * (1 is the first cell past its face), {@code x} across (0 is its own column, -1 the column toward the plane, 0..2 the walkway),
 * {@code y} up from the walking level. Pure, so the block entity and the tests share it.
 *
 * @param solid false for the cells in the door opening, which you walk through and which open the plane's door
 */
public record JetBridgeDeck(int x, int y, int d, boolean solid) {

	/** The walkway's clear width, the moving section's: the block's column and two away from the plane. */
	public static final int WIDTH = 3;
	/** The depth of the cab's front sill, drawn inside the glass. */
	private static final double FRONT_SILL = 0.3;

	/**
	 * The floor under the section and the cab, walls on both sides two blocks high, an end wall at the cab's front, and the door
	 * opening on the plane's side: floor, and a passable cell at the walking level, where the cab's door frame stands (from 0.1 to
	 * 2.9 blocks behind the cab's front, which is {@code reach} out). The floor stops at the cell nearest the inside of the cab's
	 * front sill, never more than 0.2 past the glass, so nobody walks out through the window onto floor that isn't drawn.
	 */
	public static List<JetBridgeDeck> cells(double reach) {
		final List<JetBridgeDeck> out = new ArrayList<>();
		final int end = Math.max(1, (int) Math.round(reach + JetBridgeMotion.CAB_DEPTH - FRONT_SILL));
		final int door0 = (int) Math.floor(reach + 0.1) + 1;
		final int door1 = Math.min(end, (int) Math.ceil(reach + JetBridgeMotion.CAB_DEPTH - 0.1));
		for (int d = 1; d <= end; d++) {
			final boolean door = d >= door0 && d <= door1;
			for (int x = 0; x < WIDTH; x++) {
				out.add(new JetBridgeDeck(x, -1, d, true));
			}
			for (int y = 0; y < 2; y++) {
				out.add(new JetBridgeDeck(WIDTH, y, d, true));
			}
			if (door) {
				out.add(new JetBridgeDeck(-1, -1, d, true));
				out.add(new JetBridgeDeck(-1, 0, d, false));
			} else {
				for (int y = 0; y < 2; y++) {
					out.add(new JetBridgeDeck(-1, y, d, true));
				}
			}
		}
		for (int x = 0; x < WIDTH; x++) {
			for (int y = 0; y < 2; y++) {
				out.add(new JetBridgeDeck(x, y, end + 1, true));
			}
		}
		return out;
	}
}
