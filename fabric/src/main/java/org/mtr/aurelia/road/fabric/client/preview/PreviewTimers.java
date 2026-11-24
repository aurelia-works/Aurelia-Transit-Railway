/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.preview;

import java.util.Locale;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

/**
 * How long the preview's draws take per frame, on the CPU and on the GPU, averaged over the last 120 frames (spike S7).
 * The GPU time comes from timestamp queries, read back 4 frames later, so the game never waits for them. Off unless
 * {@code -Dstratos.debug.previewTimers=true} turns it on, or (UI-1c) Settings' Measure does for one window of
 * {@link #WINDOW} frames. Render thread only.
 */
final class PreviewTimers {
    static final int FACES = 0;
    static final int OUTLINES = 1;
    /** The far part, drawn in Distant Horizons' pass (F-7b). */
    static final int FAR = 2;
    private static final int SLOTS = 3;
    static final int WINDOW = 120;
    private static final int RING = 4;

    /** Averages and maxima over the last {@link #WINDOW} frames that drew the preview, in milliseconds. */
    record Summary(double cpuAvg, double cpuMax, double gpuAvg, double gpuMax, int frames) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "CPU %.3f ms avg (%.3f max), GPU %.3f ms avg (%.3f max), over %d frames",
                    this.cpuAvg, this.cpuMax, this.gpuAvg, this.gpuMax, this.frames);
        }
    }

    private final boolean debug = Boolean.getBoolean("stratos.debug.previewTimers");
    private boolean enabled = this.debug;
    /** Settings' Measure (UI-1c): a window being measured, and the last one measured. */
    private boolean measuring;
    private Summary measured;
    private int[] queries; // [ring][slot][begin, end]
    private final boolean[] issued = new boolean[RING * SLOTS];
    private int ring;
    private final long[] cpuBegin = new long[SLOTS];
    private long cpuThisFrame;
    private boolean drewThisFrame;
    private final double[] cpuMs = new double[WINDOW];
    private final double[] gpuMs = new double[WINDOW];
    private int cpuCount;
    private int cpuNext;
    private int gpuCount;
    private int gpuNext;

    boolean enabled() {
        return this.enabled;
    }

    void enable() {
        this.enabled = true;
    }

    /** Measures the next {@link #WINDOW} frames that draw the preview, then turns the timers off again unless debugging. */
    void measure() {
        this.enabled = true;
        this.measuring = true;
        java.util.Arrays.fill(this.issued, false);
        reset();
    }

    /** Whether a window is being measured (UI-1c), and how many of its frames are in. */
    boolean measuring() {
        return this.measuring;
    }

    int measuredFrames() {
        return this.cpuCount;
    }

    /** The last window measured, or null. */
    Summary measured() {
        return this.measured;
    }

    /** Once at the start of every frame: files last frame's CPU time and any GPU results that have arrived. */
    void frameStart() {
        if (!this.enabled) {
            return;
        }
        if (this.queries == null) {
            this.queries = new int[RING * SLOTS * 2];
            GL15C.glGenQueries(this.queries);
        }
        if (this.drewThisFrame) {
            this.cpuMs[this.cpuNext] = this.cpuThisFrame / 1e6;
            this.cpuNext = (this.cpuNext + 1) % WINDOW;
            this.cpuCount = StrictMath.min(this.cpuCount + 1, WINDOW);
            if (this.measuring && this.cpuCount == WINDOW) {
                this.measured = summary();
                this.measuring = false;
                this.enabled = this.debug;
            }
        }
        this.drewThisFrame = false;
        this.cpuThisFrame = 0;
        this.ring = (this.ring + 1) % RING;
        long gpuNanos = 0;
        boolean any = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            int index = this.ring * SLOTS + slot;
            if (!this.issued[index]) {
                continue;
            }
            this.issued[index] = false;
            int begin = this.queries[index * 2];
            int end = this.queries[index * 2 + 1];
            if (GL15C.glGetQueryObjecti(end, GL15C.GL_QUERY_RESULT_AVAILABLE) == GL11C.GL_TRUE) {
                gpuNanos += GL33C.glGetQueryObjecti64(end, GL15C.GL_QUERY_RESULT) - GL33C.glGetQueryObjecti64(begin, GL15C.GL_QUERY_RESULT);
                any = true;
            }
        }
        if (any) {
            this.gpuMs[this.gpuNext] = gpuNanos / 1e6;
            this.gpuNext = (this.gpuNext + 1) % WINDOW;
            this.gpuCount = StrictMath.min(this.gpuCount + 1, WINDOW);
        }
    }

    void begin(int slot) {
        if (!this.enabled || this.queries == null) {
            return;
        }
        this.cpuBegin[slot] = System.nanoTime();
        GL33C.glQueryCounter(this.queries[(this.ring * SLOTS + slot) * 2], GL33C.GL_TIMESTAMP);
    }

    void end(int slot) {
        if (!this.enabled || this.queries == null) {
            return;
        }
        int index = this.ring * SLOTS + slot;
        GL33C.glQueryCounter(this.queries[index * 2 + 1], GL33C.GL_TIMESTAMP);
        this.issued[index] = true;
        this.cpuThisFrame += System.nanoTime() - this.cpuBegin[slot];
        this.drewThisFrame = true;
    }

    /** Forgets the measurements so far (a new preview, or a new measuring window). */
    void reset() {
        this.cpuCount = 0;
        this.cpuNext = 0;
        this.gpuCount = 0;
        this.gpuNext = 0;
    }

    Summary summary() {
        return new Summary(average(this.cpuMs, this.cpuCount), max(this.cpuMs, this.cpuCount), average(this.gpuMs, this.gpuCount),
                max(this.gpuMs, this.gpuCount), this.cpuCount);
    }

    private static double average(double[] values, int count) {
        double sum = 0;
        for (int i = 0; i < count; i++) {
            sum += values[i];
        }
        return count == 0 ? 0 : sum / count;
    }

    private static double max(double[] values, int count) {
        double max = 0;
        for (int i = 0; i < count; i++) {
            max = StrictMath.max(max, values[i]);
        }
        return max;
    }
}
