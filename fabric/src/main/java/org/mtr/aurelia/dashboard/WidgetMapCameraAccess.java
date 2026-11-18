package org.mtr.aurelia.dashboard;

/**
 * Implemented via Mixin on MTR's {@code WidgetMap} to read and apply map center & zoom.
 */
public interface WidgetMapCameraAccess {

	double atr$centerX();

	double atr$centerZ();

	double atr$scale();

	void atr$setView(double centerX, double centerZ, double scale);
}
