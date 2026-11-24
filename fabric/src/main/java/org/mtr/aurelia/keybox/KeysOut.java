package org.mtr.aurelia.keybox;

/**
 * When each key a driver key box hands out comes back to its hook: the epoch millis at which the last one it dropped runs
 * out, 0 when none is out. Index 0 is the basic key, 1 the advanced key, 2 the guard key. Added to MTR's block entity by
 * a mixin and saved under keys of ATR's own, so MTR worlds load unchanged.
 */
public interface KeysOut {

	int BASIC = 0;
	int ADVANCED = 1;
	int GUARD = 2;

	long atr$keyBackAt(int index);

	/** Whether the key at {@code index} is out at {@code now} (epoch millis). */
	default boolean atr$keyOut(int index, long now) {
		return atr$keyBackAt(index) > now;
	}
}
