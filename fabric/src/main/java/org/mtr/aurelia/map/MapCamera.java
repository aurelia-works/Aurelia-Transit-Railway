package org.mtr.aurelia.map;

/**
 * Where a map view looks and how close: the world point at the middle of the view and the screen pixels per block.
 * Turns screen points into world points and back, and zooms about a point so the block under the cursor stays put.
 * Pure logic.
 */
public final class MapCamera {

	public static final double MIN_SCALE = 0.25;
	public static final double MAX_SCALE = 8;

	private double centerX;
	private double centerZ;
	private double scale;

	public MapCamera(double centerX, double centerZ, double scale) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.scale = clampScale(scale);
	}

	public static double clampScale(double scale) {
		return Double.isFinite(scale) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale)) : 1;
	}

	public double centerX() {
		return centerX;
	}

	public double centerZ() {
		return centerZ;
	}

	public double scale() {
		return scale;
	}

	public void center(double x, double z) {
		centerX = x;
		centerZ = z;
	}

	/** Moves the view by a drag of {@code dx, dy} screen pixels (the world follows the cursor). */
	public void drag(double dx, double dy) {
		centerX -= dx / scale;
		centerZ -= dy / scale;
	}

	/** World X under screen x, for a view of width {@code viewWidth} starting at {@code viewLeft}. */
	public double worldX(double screenX, double viewLeft, double viewWidth) {
		return centerX + (screenX - viewLeft - viewWidth / 2) / scale;
	}

	public double worldZ(double screenY, double viewTop, double viewHeight) {
		return centerZ + (screenY - viewTop - viewHeight / 2) / scale;
	}

	public double screenX(double worldX, double viewLeft, double viewWidth) {
		return viewLeft + viewWidth / 2 + (worldX - centerX) * scale;
	}

	public double screenY(double worldZ, double viewTop, double viewHeight) {
		return viewTop + viewHeight / 2 + (worldZ - centerZ) * scale;
	}

	/** Zooms by {@code factor} keeping the world point under the screen point where it is. */
	public void zoomAt(double factor, double screenX, double screenY, double viewLeft, double viewTop, double viewWidth, double viewHeight) {
		final double worldX = worldX(screenX, viewLeft, viewWidth);
		final double worldZ = worldZ(screenY, viewTop, viewHeight);
		scale = clampScale(scale * factor);
		centerX = worldX - (screenX - viewLeft - viewWidth / 2) / scale;
		centerZ = worldZ - (screenY - viewTop - viewHeight / 2) / scale;
	}
}
