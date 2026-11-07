package org.mtr.mod.render;

/**
 * How many block entities were drawn in the last frame, for the dev panel. The dispatcher counts them; the world render
 * end resets the counter once per frame. Render thread only.
 */
public final class BlockEntityCounter {

	private static int current;
	private static int last;

	private BlockEntityCounter() {
	}

	public static void draw() {
		current++;
	}

	/** End of one rendered frame: keep the count and start the next frame at zero. */
	public static void endFrame() {
		last = current;
		current = 0;
	}

	public static int lastFrame() {
		return last;
	}
}
