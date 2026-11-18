package org.mtr.aurelia.dashboard;

import org.mtr.core.data.TransportMode;

import java.util.List;

/**
 * Implemented via Mixin on MTR's {@code DashboardScreen} so mode switching can read/restore map camera position & zoom,
 * query active editing state, track resized mode tabs, and dispose {@code WorldMap} tile textures without altering
 * {@code DashboardScreen.java}'s method signatures.
 */
public interface DashboardCameraAccess {

	TransportMode atr$transportMode();

	boolean atr$isEditing();

	DashboardTabBar atr$tabBar();

	List<?> atr$modeTabs();

	void atr$setModeTabs(List<?> tabs);

	DashboardMapState atr$captureMapState();

	void atr$applyMapState(DashboardMapState state);

	void atr$disposeMap();
}
