/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.fabric.client.ui;

import org.mtr.aurelia.client.ui.AureliaPalette;

/**
 * The road editor's colours, packed ARGB. Stratos had its own brand colours; in ATR the road editor wears ATR's UI
 * ({@link AureliaPalette}), so these names keep Stratos's roles and take ATR's values.
 */
public final class RoadColours {
    public static final int TEXT_MAIN = AureliaPalette.FG;
    public static final int TEXT_MUTED = AureliaPalette.TEXT_DIM;
    public static final int BG_MID = AureliaPalette.SURFACE;
    public static final int BG_HOVER = AureliaPalette.RAISED;
    public static final int BG_HOVER_STRONG = AureliaPalette.RAISED_HOVER;
    public static final int ACCENT = AureliaPalette.ACCENT_LIGHT;
    public static final int ACCENT_DARK = AureliaPalette.ACCENT;
    public static final int ACCENT_SOFT = 0xFF8DB9F2;
    public static final int ACCENT_STRONG = 0xFF2F7BDB;
    public static final int ON_ACCENT = AureliaPalette.FG;
    public static final int BORDER_ACCENT_SUBTLE = 0x664F93EA;
    public static final int STATUS_ERROR = AureliaPalette.WARN;
    public static final int STATUS_WARNING = 0xFFF2B84B;
    public static final int STATUS_SUCCESS = 0xFF5CC98A;

    private RoadColours() {
    }
}
