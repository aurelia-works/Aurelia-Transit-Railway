package com.aureliatransit.architecture.client;

/**
 * The station hologram's client switches, set on the dev panel and kept for the session: whether the hologram draws, whether
 * the old particle outline is used instead (sent to the server), whether planned blocks that replace existing ones are tinted
 * red, and which single plan height is shown ({@link #ALL_LAYERS} for the whole station). Changing a drawing switch rebuilds
 * the cached mesh once; nothing here touches the frame loop.
 */
public final class HologramSettings {

	/** Show every plan height. */
	public static final int ALL_LAYERS = Integer.MIN_VALUE;

	private static boolean enabled = true;
	private static boolean particles;
	private static boolean tint;
	private static int layer = ALL_LAYERS;

	private HologramSettings() {
	}

	public static boolean enabled() {
		return enabled;
	}

	public static boolean particles() {
		return particles;
	}

	public static boolean tint() {
		return tint;
	}

	public static int layer() {
		return layer;
	}

	public static void setEnabled(boolean on) {
		if (enabled != on) {
			enabled = on;
			StationHologramRenderer.settingsChanged();
		}
	}

	public static void setParticles(boolean on) {
		if (particles != on) {
			particles = on;
			StationHologramRenderer.sendParticlesSetting();
		}
	}

	public static void setTint(boolean on) {
		if (tint != on) {
			tint = on;
			StationHologramRenderer.settingsChanged();
		}
	}

	public static void setLayer(int y) {
		if (layer != y) {
			layer = y;
			StationHologramRenderer.settingsChanged();
		}
	}

	/** Keeps a chosen height inside a new plan's range. */
	static void clampLayer(int min, int max) {
		if (layer != ALL_LAYERS && (layer < min || layer > max)) {
			layer = Math.max(min, Math.min(max, layer));
		}
	}
}
