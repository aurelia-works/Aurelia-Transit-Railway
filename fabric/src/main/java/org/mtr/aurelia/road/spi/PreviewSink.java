/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.spi;

import org.mtr.aurelia.road.preview.PreviewModel;

/**
 * Where core sends the preview (spec mod-suite-v1 §5): the client's renderer. Core works out what changed, so the
 * renderer only rebuilds what it must. Render thread only.
 */
public interface PreviewSink {
    /**
     * Shows {@code model}: (re)build the sections in {@code diff.rebuild()}, drop those in {@code diff.drop()}, keep the
     * others as they are, and redraw the handles if they changed.
     */
    void update(PreviewModel model, PreviewModel.Diff diff);

    /** Removes the whole preview. */
    void clear();
}
