package org.mtr.mod.engine;

/**
 * The engine's automatic behaviours that ATR adds on top of MTR. Each has one on/off switch, on by default
 * (Jah 2026-11-23), kept per world in {@code atr_engine.dat} and shown in the Aurelia panel's Engine tab.
 * Add new ones at the end: the saved keys are the {@link #key}s, not the order.
 */
public enum EngineFeature {
	/** An airplane stays at the gate as long as a real turnaround takes for its size. */
	PLANE_TURNAROUND("plane_turnaround");

	public final String key;

	EngineFeature(String key) {
		this.key = key;
	}

	public String langKey() {
		return "gui.aurelia.engine." + key;
	}
}
