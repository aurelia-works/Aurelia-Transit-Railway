package org.mtr.mod.render;

import org.mtr.mapping.render.tool.Utilities;
import org.mtr.mod.config.Client;
import org.mtr.mod.config.Config;

import java.util.List;
import java.util.Locale;

/**
 * MTR's draw calls and CPU time per frame, averaged once per second for the F3 screen. Render thread only.
 * Ported from Aurelia Transit Utilities 1.4.0 (PerfStats, PerfOverlay).
 */
public final class MtrFrameStats {

	public static final MtrFrameStats INSTANCE = new MtrFrameStats();

	private static final long WINDOW_NANOS = 1_000_000_000L;

	private long draws;
	private long mtrNanos;
	private int frames;
	private long windowStart = -1;

	private double drawsPerFrame;
	private double mtrMillisPerFrame;
	private boolean hasResult;

	/** One draw call of MTR's batched renderer. */
	public void draw() {
		draws++;
	}

	/** One whole {@code MainRenderer.render} (everything MTR does on the CPU for one pass). */
	public void mtrRender(long nanos) {
		mtrNanos += nanos;
	}

	/** Called once per rendered frame with the current time. */
	public void frame(long now) {
		if (windowStart < 0) {
			windowStart = now;
			draws = 0;
			mtrNanos = 0;
			return;
		}
		frames++;
		if (now - windowStart >= WINDOW_NANOS) {
			drawsPerFrame = (double) draws / frames;
			mtrMillisPerFrame = mtrNanos / 1e6 / frames;
			hasResult = true;
			draws = 0;
			mtrNanos = 0;
			frames = 0;
			windowStart = now;
		}
	}

	public boolean hasResult() {
		return hasResult;
	}

	public double drawsPerFrame() {
		return drawsPerFrame;
	}

	public double mtrMillisPerFrame() {
		return mtrMillisPerFrame;
	}

	/** Lines for the right side of the F3 screen. */
	public void addLines(List<String> lines) {
		final Client client = Config.getClient();
		if (!client.getPerfOnF3()) {
			return;
		}
		lines.add("");
		if (hasResult) {
			lines.add(String.format(Locale.ROOT, "[ATR] MTR draws/frame: %.0f, MTR CPU: %.2f ms/frame", drawsPerFrame, mtrMillisPerFrame));
		}
		lines.add(String.format(Locale.ROOT, "[ATR] merge %s, shader-pack path %s (%s), train LOD %s, rail LOD %s", onOff(client.getMergeTrainParts()), onOff(client.getShaderPackFastPath()), Utilities.canUseCustomShader() ? "no shader pack" : "shader pack", onOff(client.getTrainLod()), onOff(client.getRailLod())));
	}

	private static String onOff(boolean value) {
		return value ? "on" : "off";
	}
}
