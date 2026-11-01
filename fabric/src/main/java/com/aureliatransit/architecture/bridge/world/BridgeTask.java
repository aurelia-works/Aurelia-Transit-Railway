package com.aureliatransit.architecture.bridge.world;

/**
 * Work the bridge generator does over several server ticks. {@link BridgeSessions} runs tasks until their time budget is
 * spent, so a long bridge or undo never holds one tick for its whole length.
 */
interface BridgeTask {

	/** Works until done or {@code deadline} ({@link System#nanoTime()}); true when done. */
	boolean step(long deadline);

	/** Called once after {@link #step} returns true. */
	void finish();
}
