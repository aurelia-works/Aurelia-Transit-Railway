package org.mtr.aurelia.dashboard;

import org.mtr.aurelia.map.MapCamera;

/**
 * Shared map camera coordinates and zoom preserved across all five unified dashboard modes.
 * Both MTR's {@code WidgetMap} and Aurelia's {@link MapCamera} measure {@code (centerX, centerZ)} in world blocks
 * and {@code scale} in GUI pixels per block.
 */
public record DashboardMapState(double centerX, double centerZ, double scale, boolean currentY) {

	public static final double MIN_MTR_SCALE = 1.0 / 128.0;
	public static final double MAX_MTR_SCALE = 64.0;
	public static final double DEFAULT_SCALE = 1.0;

	public DashboardMapState {
		centerX = Double.isFinite(centerX) ? centerX : 0;
		centerZ = Double.isFinite(centerZ) ? centerZ : 0;
		scale = Double.isFinite(scale) && scale > 0 ? Math.max(MIN_MTR_SCALE, Math.min(MAX_MTR_SCALE, scale)) : DEFAULT_SCALE;
	}

	public double scaleForRoadCamera() {
		return MapCamera.clampScale(scale);
	}

	public DashboardMapState withView(double nextCenterX, double nextCenterZ, double nextScale) {
		return new DashboardMapState(nextCenterX, nextCenterZ, nextScale, currentY);
	}
}
