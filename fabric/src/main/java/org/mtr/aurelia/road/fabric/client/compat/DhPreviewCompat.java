/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.compat;

import java.util.function.Consumer;
import org.joml.Matrix4f;

/**
 * Stratos draws the preview's far part inside Distant Horizons' own pass. That needs DH's API at compile time, which ATR
 * doesn't build against, so here the hook does nothing: with DH the preview shows out to vanilla's render distance, and
 * DH's far terrain doesn't cover it. Nothing else changes ({@code PreviewRenderer} only skips sections DH's pass drew).
 */
public final class DhPreviewCompat {
    /** One DH frame: its projection and model-view, and its near and far planes. */
    public record Frame(Matrix4f projection, Matrix4f modelView, float nearClip, float farClip) {
    }

    private DhPreviewCompat() {
    }

    public static void register(Consumer<Frame> far) {
    }

    public static int depthTexture() {
        return 0;
    }
}
