package org.mtr.atr.mixin;

import org.mtr.aurelia.client.ui.AureliaPalette;
import org.mtr.aurelia.client.ui.AureliaUiSettings;
import org.mtr.aurelia.dashboard.DashboardCameraAccess;
import org.mtr.aurelia.dashboard.DashboardMapState;
import org.mtr.aurelia.dashboard.DashboardTabBar;
import org.mtr.aurelia.dashboard.WidgetMapCameraAccess;
import org.mtr.core.data.AreaBase;
import org.mtr.core.data.Route;
import org.mtr.core.data.TransportMode;
import org.mtr.mod.screen.DashboardScreen;
import org.mtr.mod.screen.WidgetMap;
import org.mtr.mod.screen.WorldMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Collections;
import java.util.List;

/**
 * Styles the dashboard's left panel with AureliaMap's background and exposes camera, editing, and tab-bar state for
 * unified mode switching without modifying {@link DashboardScreen}'s MTR 4.0.5 method signatures.
 */
@Mixin(value = DashboardScreen.class, remap = false)
public abstract class AureliaDashboardMixin implements DashboardCameraAccess {

	@Shadow
	private AreaBase<?, ?> editingArea;

	@Shadow
	private Route editingRoute;

	@Shadow
	@Final
	private TransportMode transportMode;

	@Shadow
	@Final
	private WidgetMap widgetMap;

	@Shadow
	private void toggleButtons() {
	}

	@Unique
	private final DashboardTabBar atr$tabBar = new DashboardTabBar();

	@Unique
	private List<?> atr$modeTabs = Collections.emptyList();

	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lorg/mtr/mapping/mapper/GuiDrawing;drawRectangle(DDDDI)V"), index = 4)
	private int atr$panelColour(int color) {
		if (color == 0xFF121212 && AureliaUiSettings.enabled(AureliaUiSettings.ScreenId.DASHBOARD)) {
			return AureliaPalette.BG;
		}
		return color;
	}

	@Override
	public TransportMode atr$transportMode() {
		return this.transportMode;
	}

	@Override
	public boolean atr$isEditing() {
		return this.editingArea != null || this.editingRoute != null;
	}

	@Override
	public DashboardTabBar atr$tabBar() {
		return this.atr$tabBar;
	}

	@Override
	public List<?> atr$modeTabs() {
		return this.atr$modeTabs;
	}

	@Override
	public void atr$setModeTabs(List<?> tabs) {
		this.atr$modeTabs = tabs == null ? Collections.emptyList() : List.copyOf(tabs);
	}

	@Override
	public DashboardMapState atr$captureMapState() {
		final WidgetMapCameraAccess mapAccess = (WidgetMapCameraAccess) (Object) this.widgetMap;
		return new DashboardMapState(
				mapAccess.atr$centerX(),
				mapAccess.atr$centerZ(),
				mapAccess.atr$scale(),
				this.widgetMap.isMapOverlayMode(WorldMap.MapOverlayMode.CURRENT_Y)
		);
	}

	@Override
	public void atr$applyMapState(DashboardMapState state) {
		if (state == null) {
			return;
		}
		final WidgetMapCameraAccess mapAccess = (WidgetMapCameraAccess) (Object) this.widgetMap;
		mapAccess.atr$setView(state.centerX(), state.centerZ(), state.scale());
		final WorldMap.MapOverlayMode desired = state.currentY() ? WorldMap.MapOverlayMode.CURRENT_Y : WorldMap.MapOverlayMode.TOP_VIEW;
		if (!this.widgetMap.isMapOverlayMode(desired)) {
			this.widgetMap.setMapOverlayMode(desired);
		}
		this.toggleButtons();
	}

	@Override
	public void atr$disposeMap() {
		this.widgetMap.onClose();
	}
}
