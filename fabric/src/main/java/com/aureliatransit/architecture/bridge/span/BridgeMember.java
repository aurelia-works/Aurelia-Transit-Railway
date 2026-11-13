package com.aureliatransit.architecture.bridge.span;

/**
 * A straight piece drawn by a renderer rather than built of blocks: a stay, hanger, main-cable segment, truss diagonal, mast or
 * wire. Ends are in blocks relative to the anchor block's lower north-west corner, so a member keeps its exact line on any
 * curve. {@code thickness} is the square section's side.
 */
public record BridgeMember(Material material, float x0, float y0, float z0, float x1, float y1, float z1, float thickness) {

	/** What a member is drawn with. Ordinal is saved, so add new ones at the end. */
	public enum Material {
		CONCRETE,
		WHITE,
		STEEL,
		CABLE,
		RED,
		/** An aviation warning light: drawn full bright. */
		LIGHT,
		STONE
	}

	public double length() {
		final double dx = x1 - x0;
		final double dy = y1 - y0;
		final double dz = z1 - z0;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
