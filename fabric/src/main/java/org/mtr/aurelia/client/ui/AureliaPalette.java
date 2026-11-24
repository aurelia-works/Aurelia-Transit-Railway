package org.mtr.aurelia.client.ui;

/**
 * The one colour set every ATR screen draws from, as packed ARGB. Screens name a role here instead of writing a hex
 * value, so a change lands everywhere at once.
 * <p>
 * Contrast is checked against {@link #SURFACE} (and {@link #RAISED} for text on a row or button): body text 16:1,
 * {@link #TEXT_DIM} 7:1, the status colours and {@link #ACCENT_LIGHT} at least 5.5:1, {@link #CONTROL_BORDER} 3.9:1 so a
 * field or a box can be found without colour. {@link #ACCENT} is a fill only: white on it is 8.7:1, but as a line on the
 * dark face it is 2:1, so outlines, focus rings and markers use {@link #ACCENT_LIGHT}.
 * <p>
 * Content colours (a route's own colour, a signal aspect, the Codex book, a terminal face) are not UI and stay where
 * they are.
 */
public final class AureliaPalette {

	// Surfaces, darkest first

	/** An inset track or text field, one step under the face. */
	public static final int FIELD = 0xFF0F1215;
	/** The screen and panel face. */
	public static final int SURFACE = 0xFF15181C;
	/** A row, card or button on the face. */
	public static final int RAISED = 0xFF1E2228;
	/** A raised thing under the pointer. */
	public static final int RAISED_HOVER = 0xFF272C33;
	/** What sits behind a screen while the world is loaded: dark enough that text on it reads, light enough to keep the place in view. */
	public static final int SCRIM = 0xD80D0F12;

	// Lines

	/** A hairline between rows or sections. Decorative, so it can be quiet. */
	public static final int LINE = 0xFF2A2F36;
	/** The outline of a card or a button, which also has its own face. */
	public static final int OUTLINE = 0xFF3A414A;
	/** The outline of a field, box or track, which has nothing else to show where it is: 3.9:1 on the face. */
	public static final int CONTROL_BORDER = 0xFF6E7782;

	// Text

	public static final int TEXT = 0xFFFFFFFF;
	/** Secondary text: hints, units, captions. 7:1 on the face. */
	public static final int TEXT_DIM = 0xFF9AA3AD;
	/** A control that cannot be used now. Plainly dimmer than {@link #TEXT_DIM}, still legible. */
	public static final int TEXT_DISABLED = 0xFF6B737D;

	// Accent and status

	/** The brand blue, from AureliaMap's switch. A fill under white text only. */
	public static final int ACCENT = 0xFF00489D;
	/** The brand blue's light step, for anything drawn as a line or as text on the dark face. */
	public static final int ACCENT_LIGHT = 0xFF4F93EA;
	/** The keyboard focus ring. */
	public static final int FOCUS = ACCENT_LIGHT;
	public static final int SUCCESS = 0xFF4CC38A;
	public static final int CAUTION = 0xFFF2B84B;
	public static final int DANGER = 0xFFFF6B5E;

	// Shape

	/** Corner radius of a button, field or chip, in GUI pixels. */
	public static final int RADIUS_SMALL = 2;
	/** Corner radius of a card or row. */
	public static final int RADIUS = 4;
	/** Corner radius of a panel or dialog. */
	public static final int RADIUS_LARGE = 6;

	// Older names, kept so existing screens keep compiling. They point at the roles above.

	/** {@link #SURFACE}. */
	public static final int BG = SURFACE;
	/** {@link #RAISED}. */
	public static final int BUTTON = RAISED;
	/** {@link #OUTLINE}. */
	public static final int BORDER = OUTLINE;
	/** {@link #TEXT}. */
	public static final int FG = TEXT;
	/** {@link #TEXT_DIM}. */
	public static final int FG_LIGHT = TEXT_DIM;
	/** {@link #DANGER}. */
	public static final int WARN = DANGER;

	private AureliaPalette() {
	}
}
