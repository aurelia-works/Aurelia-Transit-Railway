package org.mtr.aurelia.client.ui;

import net.minecraft.util.Util;

/**
 * Timing for the modern screens' animations. Every value is worked out from the clock while drawing, so nothing ticks
 * and a closed screen costs nothing.
 */
public final class AureliaMotion {

	private AureliaMotion() {
	}

	public static long now() {
		return Util.getMeasuringTimeMs();
	}

	/** How far through an animation of {@code millis} that started at {@code start}, from 0 to 1. */
	public static float progress(long start, long millis) {
		return millis <= 0 ? 1 : Math.max(0, Math.min(1, (now() - start) / (float) millis));
	}

	public static float easeOut(float t) {
		final float u = 1 - t;
		return 1 - u * u * u;
	}

	public static float easeInOut(float t) {
		return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	}

	/** Overshoots a little and settles, for something that lands. */
	public static float easeOutBack(float t) {
		final float c = 1.4F;
		final float u = t - 1;
		return 1 + (c + 1) * u * u * u + c * u * u;
	}

	/** A swing that dies away: {@code degrees} at its widest, {@code hertz} swings a second, gone in about a second. */
	public static float swing(long start, float degrees, float hertz) {
		final float seconds = Math.max(0, now() - start) / 1000F;
		return (float) (degrees * Math.exp(-seconds / 0.45) * Math.sin(seconds * 2 * Math.PI * hertz));
	}
}
