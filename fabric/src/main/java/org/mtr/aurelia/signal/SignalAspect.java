package org.mtr.aurelia.signal;

/**
 * The four aspects a UK signal head can show, and the sequence rules that pick one from the number of clear signal
 * blocks beyond the signal. The sequences are the ones in the Rule Book:
 * <ul>
 * <li>two aspect: red, green (the block beyond the signal is clear or it is not);</li>
 * <li>three aspect: red, yellow, green (green needs two clear blocks);</li>
 * <li>four aspect: red, yellow, double yellow, green (green needs three clear blocks).</li>
 * </ul>
 * A signal with no set route ahead is red. MTR has no interlocking, so a route counts as set when a signalled block
 * exists on the signal's facing side; see {@link SignalWalk}.
 */
public enum SignalAspect {

	RED, YELLOW, DOUBLE_YELLOW, GREEN;

	/**
	 * @param aspects      the number of lamps the physical head has: 2, 3 or 4
	 * @param routeSet     whether a signalled route exists beyond the signal
	 * @param clearBlocks  how many consecutive blocks beyond the signal are clear, capped by the caller
	 */
	public static SignalAspect forBlocks(int aspects, boolean routeSet, int clearBlocks) {
		if (!routeSet || clearBlocks <= 0) {
			return RED;
		}
		if (aspects <= 2) {
			return GREEN;
		}
		if (clearBlocks == 1) {
			return YELLOW;
		}
		if (aspects == 3) {
			return GREEN;
		}
		if (clearBlocks == 2) {
			return DOUBLE_YELLOW;
		}
		return GREEN;
	}

	/** MTR's lamp index: 0 clear, 1 red, 2 yellow, 3 double yellow. The 2- and 3-aspect renderers ignore 3. */
	public int mtrAspect() {
		switch (this) {
			case RED:
				return 1;
			case YELLOW:
				return 2;
			case DOUBLE_YELLOW:
				return 3;
			default:
				return 0;
		}
	}

	/** Red, yellow and double yellow are restrictive; only green is a proceed aspect. */
	public boolean isRestrictive() {
		return this != GREEN;
	}
}
