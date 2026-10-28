package org.mtr.mod.render;

import java.util.function.BooleanSupplier;

/**
 * Whether the current render pass is a shader pack's shadow pass. The stock check walks the whole thread stack on every
 * call (several times a frame). Here the stack is walked once at the start of each MTR render pass and the answer is
 * reused inside it. Without Iris or OptiFine on the class path the answer is always false, as in MTR 4.0.5. With one,
 * every {@link #CHECK_EVERY}th call is walked again; on a difference the detector stops answering by itself and the
 * stock check is used for the rest of the session.
 */
public final class ShadowPassDetector {

	public static final int CHECK_EVERY = 128;

	private final boolean shaderMod;
	private final BooleanSupplier walk;
	private boolean inPass;
	private boolean active;
	private boolean mismatch;
	private int calls;

	public ShadowPassDetector(boolean shaderMod, BooleanSupplier walk) {
		this.shaderMod = shaderMod;
		this.walk = walk;
	}

	public static boolean isShaderClass(String className) {
		return className.startsWith("net.irisshaders") || className.startsWith("net.optifine");
	}

	/** Start of MTR's render pass. */
	public void begin() {
		active = shaderMod && walk.getAsBoolean();
		inPass = true;
	}

	/** End of MTR's render pass. */
	public void end() {
		inPass = false;
	}

	/**
	 * @param slowCheck the stock stack-walking check
	 */
	public boolean renderingShadows(BooleanSupplier slowCheck) {
		if (!shaderMod) {
			return false;
		}
		if (!inPass || mismatch) {
			return slowCheck.getAsBoolean();
		}
		if (++calls % CHECK_EVERY == 0) {
			final boolean walked = walk.getAsBoolean();
			if (walked != active) {
				mismatch = true;
				return walked;
			}
		}
		return active;
	}

	public boolean mismatched() {
		return mismatch;
	}
}
