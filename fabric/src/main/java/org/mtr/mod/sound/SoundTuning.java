package org.mtr.mod.sound;

/**
 * Pure numbers behind the train sound settings on the Aurelia panel (Train sound tab).
 */
public final class SoundTuning {

	/** Vanilla clamps every sound's pitch to 0.5-2. */
	public static final float VANILLA_MAX_PITCH = 2;
	/** With the wide range on, train sounds may go down to here. */
	public static final float WIDE_MIN_PITCH = 0.25F;

	private SoundTuning() {
	}

	/**
	 * The pitch OpenAL gets for a train sound when the wide range is on. BVE motor tables often ask for more than 2;
	 * vanilla would flatten that to 2.
	 */
	public static float pitch(float requested, float maxPitch) {
		if (!Float.isFinite(requested)) {
			return 1;
		}
		return Math.max(WIDE_MIN_PITCH, Math.min(Math.max(VANILLA_MAX_PITCH, maxPitch), requested));
	}

	/**
	 * Old-style (non-BVE) trains swap to the next motor clip at each speed step, all at pitch 1, so the sound jumps.
	 * This bends the pitch across a step, from {@code 1 - spread / 2} at its start to {@code 1 + spread / 2} at its end,
	 * so the swap meets a clip that is already close. Rounded to 0.01 so the sound scheduler keeps merging cars.
	 *
	 * @param stepPosition speed in steps, e.g. 3.4 is 40% through step 3
	 * @param topStep      the last step that has its own clip; faster stays at the top of it
	 */
	public static float motorPitch(double stepPosition, int topStep, double spread) {
		if (!(spread > 0) || !Double.isFinite(stepPosition)) {
			return 1;
		}
		final double through = stepPosition >= topStep + 1 ? 1 : stepPosition - Math.floor(stepPosition);
		return Math.round((1 + (through - 0.5) * spread) * 100) / 100F;
	}

	/** True on the frame the doors leave fully open: the moment a chime plays. */
	public static boolean doorsStartClosing(double doorValue, double oldDoorValue) {
		return oldDoorValue >= 1 && doorValue < 1;
	}
}
