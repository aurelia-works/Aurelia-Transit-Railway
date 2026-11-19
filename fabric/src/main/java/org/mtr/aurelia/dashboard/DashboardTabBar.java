package org.mtr.aurelia.dashboard;

import java.util.List;

/**
 * Pure per-screen tab bar state for {@code DashboardScreen}: tracks the active mode's computed {@link DashboardLayout}
 * across window resizes and locks tab switching whenever a station/depot/route edit or MAGIC overlay menu is active.
 */
public final class DashboardTabBar {

	private DashboardMode activeMode = DashboardMode.TRAIN;
	private List<DashboardLayout.ModeSlot> slots = List.of();
	private boolean enabled = true;
	private int generation;

	public static boolean canSwitchMode(boolean editing, boolean magicOverlayOpen) {
		return !editing && !magicOverlayOpen;
	}

	public DashboardLayout rebuild(DashboardMode nextActiveMode, int scaledWidth, int scaledHeight, boolean magicLoaded) {
		this.activeMode = nextActiveMode != null ? nextActiveMode : DashboardMode.TRAIN;
		final DashboardLayout layout = DashboardLayout.compute(scaledWidth, scaledHeight, magicLoaded);
		this.slots = layout.modeButtons();
		this.generation++;
		return layout;
	}

	public boolean updateEnabled(boolean editing, boolean magicOverlayOpen) {
		this.enabled = canSwitchMode(editing, magicOverlayOpen);
		return this.enabled;
	}

	public DashboardMode activeMode() {
		return activeMode;
	}

	public List<DashboardLayout.ModeSlot> slots() {
		return slots;
	}

	public boolean enabled() {
		return enabled;
	}

	public int generation() {
		return generation;
	}
}
