/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.preview;

import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.preview.Handle;
import org.mtr.aurelia.road.preview.Preview;
import org.mtr.aurelia.road.preview.PreviewChoices;
import org.mtr.aurelia.road.preview.PreviewModel;
import org.mtr.aurelia.road.fabric.client.compat.DhPreviewCompat;
import org.mtr.aurelia.road.fabric.client.compat.IrisCompat;

/**
 * The preview on screen (spec mod-suite-v1 §6.3): shows the plan core worked out, the same plan the server will place
 * (editor-protocol §1). The API the tools (through core's {@code Preview}), the hub's Settings (UI-1c: faces or outlines
 * only, the shader-pack face draw, draw time; {@code /stratos preview} is gone) and the harness use. Render thread only.
 */
public final class StratosPreview {
    private static final PreviewRenderer RENDERER = new PreviewRenderer();
    private static final Preview PREVIEW = new Preview(RENDERER);
    private static long modelMillis;

    /** What {@link #stats()} reports. CPU and GPU times are per frame, averaged over the last 120 frames that drew it. */
    public record Stats(int blocks, int sections, int faces, int edges, long modelMillis, long buildMillis, boolean shaderPack, boolean outlinesOnly,
            long shadowPassSkips, long faceFrames, long outlineFrames, long farFrames, boolean timing, double cpuAvgMs, double cpuMaxMs,
            double gpuAvgMs, double gpuMaxMs, int timedFrames, boolean facesDrawnOnce) {
        @Override
        public String toString() {
            String timingText = this.timing
                    ? String.format(Locale.ROOT, "CPU %.3f ms avg (%.3f max), GPU %.3f ms avg (%.3f max) over %d frames", this.cpuAvgMs, this.cpuMaxMs,
                            this.gpuAvgMs, this.gpuMaxMs, this.timedFrames)
                    : "timers off";
            return String.format(Locale.ROOT, "%,d blocks in %d sections (%,d faces, %,d edges; model %d ms, buffers %d ms); %s; %s; shadow-pass "
                    + "draws skipped: %,d; far part (Distant Horizons) drawn in %,d frames; %s", this.blocks, this.sections, this.faces, this.edges,
                    this.modelMillis, this.buildMillis, this.outlinesOnly ? "outlines only" : "faces + outlines",
                    this.shaderPack ? "shader pack in use, faces drawn " + (this.facesDrawnOnce ? "once" : "twice") : "no shader pack",
                    this.shadowPassSkips, this.farFrames, timingText);
        }
    }

    private StratosPreview() {
    }

    /** Hooks the renderer into Fabric's world render events. Called once, from the client initialiser. */
    public static void register() {
        WorldRenderEvents.START.register(context -> {
            RENDERER.timers.frameStart();
            RENDERER.frameStart();
        });
        DhPreviewCompat.register(RENDERER::renderFar);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(RENDERER::renderFaces);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(RENDERER::renderOutlines);
        // The buffers belong to the render thread; free them when leaving a world.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(StratosPreview::clear));
    }

    /** Shows {@code plan} with {@code handles} in place of the current preview; only the sections that changed are rebuilt. */
    public static PreviewModel.Diff show(BlockPlan plan, List<Handle> handles) {
        long start = System.nanoTime();
        PreviewModel model = PreviewModel.of(plan, handles);
        modelMillis = (System.nanoTime() - start) / 1_000_000;
        return PREVIEW.show(model);
    }

    public static void clear() {
        PREVIEW.clear();
    }

    /** The preview itself, for the tools (R-1), which show their output through it. */
    public static Preview preview() {
        return PREVIEW;
    }

    public static boolean showing() {
        return !PREVIEW.current().isEmpty();
    }

    /** Outlines only, the fallback (S7): faces aren't drawn. */
    public static void setOutlinesOnly(boolean outlinesOnly) {
        RENDERER.setOutlinesOnly(outlinesOnly);
    }

    public static boolean outlinesOnly() {
        return RENDERER.outlinesOnly();
    }

    /** Hides or shows the preview without forgetting it (the harness measures with and without). */
    public static void setVisible(boolean visible) {
        RENDERER.setVisible(visible);
    }

    /** How faces are drawn with a shader pack (UI-1c; Settings' "Faces with a shader pack"). */
    public static void setShaderFaces(PreviewChoices.ShaderFaces shaderFaces) {
        RENDERER.setShaderFaces(shaderFaces);
    }

    public static PreviewChoices.ShaderFaces shaderFaces() {
        return RENDERER.shaderFaces();
    }

    /** Whether the last frame drawn with a shader pack drew the faces once (UI-1c). */
    public static boolean facesDrawnOnce() {
        return RENDERER.drawnOnce();
    }

    /** The settings kept in config/stratos/client.json (UI-1c). */
    public static PreviewChoices choices() {
        return new PreviewChoices(RENDERER.outlinesOnly(), RENDERER.shaderFaces());
    }

    /** Takes back the settings kept across restarts (UI-1c). */
    public static void restore(PreviewChoices choices) {
        RENDERER.setOutlinesOnly(choices.outlinesOnly());
        RENDERER.setShaderFaces(choices.shaderFaces());
    }

    /** Settings' Measure (UI-1c): times the next 120 frames that draw the preview, then turns the timers off again. */
    public static void measure() {
        RENDERER.timers.measure();
    }

    /** Whether Measure is timing frames now, and how many it has (of 120). */
    public static boolean measuring() {
        return RENDERER.timers.measuring();
    }

    public static int measuredFrames() {
        return RENDERER.timers.measuredFrames();
    }

    /** The draw time Measure found last, CPU and GPU milliseconds per frame on average; empty before one. */
    public static java.util.Optional<double[]> measured() {
        PreviewTimers.Summary summary = RENDERER.timers.measured();
        return summary == null ? java.util.Optional.empty() : java.util.Optional.of(new double[] {summary.cpuAvg(), summary.gpuAvg()});
    }

    /** Turns the draw-time measurements on (they are off unless {@code -Dstratos.debug.previewTimers=true}). */
    public static void enableTimers() {
        RENDERER.timers.enable();
    }

    public static boolean timersEnabled() {
        return RENDERER.timers.enabled();
    }

    /** Starts a new measuring window. */
    public static void resetTimers() {
        RENDERER.timers.reset();
    }

    public static Stats stats() {
        PreviewRenderer.Counts counts = RENDERER.counts();
        PreviewTimers.Summary timing = RENDERER.timers.summary();
        return new Stats(counts.blocks(), counts.sections(), counts.faces(), counts.edges(), modelMillis, counts.buildMillis(),
                IrisCompat.shaderPackInUse(), RENDERER.outlinesOnly(), counts.shadowPassSkips(), counts.faceFrames(), counts.outlineFrames(),
                counts.farFrames(), RENDERER.timers.enabled(), timing.cpuAvg(), timing.cpuMax(), timing.gpuAvg(), timing.gpuMax(), timing.frames(),
                RENDERER.drawnOnce());
    }
}
