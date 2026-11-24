/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

import java.util.Objects;
import org.mtr.aurelia.road.spi.PreviewSink;

/**
 * The preview on screen: keeps the current {@link PreviewModel} and tells the {@link PreviewSink} only what changed
 * when a new one is shown. Render thread only.
 */
public final class Preview {
    private final PreviewSink sink;
    private PreviewModel current = PreviewModel.EMPTY;

    public Preview(PreviewSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Shows {@code model} in place of the current preview, and returns what changed. */
    public PreviewModel.Diff show(PreviewModel model) {
        PreviewModel.Diff diff = model.diff(this.current);
        this.current = model;
        if (!diff.isEmpty()) {
            this.sink.update(model, diff);
        }
        return diff;
    }

    /** Removes the preview. */
    public void clear() {
        if (!this.current.isEmpty()) {
            this.current = PreviewModel.EMPTY;
            this.sink.clear();
        }
    }

    public PreviewModel current() {
        return this.current;
    }
}
