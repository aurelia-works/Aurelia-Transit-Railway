package org.mtr.aurelia.dashboard;

import org.mtr.core.data.TransportMode;

/**
 * Implemented via Mixin on MTR's {@code DashboardScreen} so mode switching can read/restore map camera position & zoom
 * and dispose {@code WorldMap} tile textures without altering {@code DashboardScreen.java}'s method signatures.
 */
public interface DashboardCameraAccess {

	TransportMode atr$transportMode();

	DashboardMapState atr$captureMapState();

	void atr$applyMapState(DashboardMapState state);

	void atr$disposeMap();
}
