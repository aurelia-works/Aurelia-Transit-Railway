package org.mtr.aurelia.client.ui;

/**
 * AureliaMap's dark theme, as packed ARGB. The names match the custom properties in
 * {@code aureliamap/common/webapp/src/scss/variables.scss}.
 */
public final class AureliaPalette {

	/** {@code --theme-bg}. */
	public static final int BG = 0xFF181818;
	/** {@code --theme-bg-hover}, used for a button face. */
	public static final int BUTTON = 0xFF222222;
	/** {@code --theme-bg-light}, used for a border. */
	public static final int BORDER = 0xFF444444;
	/** {@code --theme-fg}. */
	public static final int FG = 0xFFFFFFFF;
	/** {@code --theme-fg-light}. */
	public static final int FG_LIGHT = 0xFFAAAAAA;
	/** {@code --theme-switch-button-on}. */
	public static final int ACCENT = 0xFF00489D;
	/** One step under the background, for an inset track or field. From AureliaMap's black theme hover. */
	public static final int FIELD = 0xFF111111;

	private AureliaPalette() {
	}
}
