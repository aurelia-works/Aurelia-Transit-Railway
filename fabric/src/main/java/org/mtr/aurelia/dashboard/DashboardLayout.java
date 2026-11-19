package org.mtr.aurelia.dashboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure geometry for the unified Transit Dashboard header bar and surrounding chrome.
 * Guarantees zero overlap with {@code IGui.PANEL_WIDTH} (144 px), the Aurelia panel button, the ATR menu button,
 * MAGIC's overlay button ({@code jme}), MTR's bottom map buttons, and top-right world coordinates at both
 * {@code 854x480} (min window, {@code 427x240} at auto GUI scale) and {@code 1920x1080}.
 */
public final class DashboardLayout {

	public static final int PANEL_WIDTH = 144;
	public static final int ICON_BUTTON_SIZE = 20;
	public static final int TOP_MARGIN = 6;
	public static final int RIGHT_MARGIN = 6;
	public static final int ICON_GAP = 4;
	public static final int MAGIC_BUTTON_X = PANEL_WIDTH + TOP_MARGIN;
	public static final int ATU_BUTTON_X = PANEL_WIDTH + TOP_MARGIN;
	public static final int ATR_MENU_BUTTON_X = PANEL_WIDTH + 30;
	public static final int MODE_BAR_LEFT = ATR_MENU_BUTTON_X + ICON_BUTTON_SIZE + TOP_MARGIN;
	public static final int MODE_BAR_Y = TOP_MARGIN;
	public static final int MODE_BAR_HEIGHT = ICON_BUTTON_SIZE;
	public static final int MODE_BUTTON_GAP = 2;
	public static final int BOTTOM_CONTROLS_HEIGHT = 40;
	public static final int MOUSE_COORDS_Y = MODE_BAR_Y + MODE_BAR_HEIGHT + ICON_GAP;

	public record Rect(int x, int y, int width, int height) {

		public int right() {
			return x + width;
		}

		public int bottom() {
			return y + height;
		}

		public boolean contains(double px, double py) {
			return px >= x && py >= y && px < right() && py < bottom();
		}

		public boolean overlaps(Rect other) {
			return other != null && width > 0 && height > 0 && other.width > 0 && other.height > 0
					&& x < other.right() && right() > other.x
					&& y < other.bottom() && bottom() > other.y;
		}
	}

	public record ModeSlot(DashboardMode mode, Rect bounds) {
	}

	private final int screenWidth;
	private final int screenHeight;
	private final boolean magicLoaded;
	private final Rect leftPanel;
	@Nullable
	private final Rect magicButton;
	private final Rect atuButton;
	private final Rect atrMenuButton;
	private final List<ModeSlot> modeButtons;
	private final Rect modeBarBounds;
	private final Rect bottomControls;
	private final int mouseCoordsY;
	private final int editHintY;

	private DashboardLayout(
			int screenWidth,
			int screenHeight,
			boolean magicLoaded,
			Rect leftPanel,
			@Nullable Rect magicButton,
			Rect atuButton,
			Rect atrMenuButton,
			List<ModeSlot> modeButtons,
			Rect modeBarBounds,
			Rect bottomControls,
			int mouseCoordsY,
			int editHintY
	) {
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		this.magicLoaded = magicLoaded;
		this.leftPanel = leftPanel;
		this.magicButton = magicButton;
		this.atuButton = atuButton;
		this.atrMenuButton = atrMenuButton;
		this.modeButtons = modeButtons;
		this.modeBarBounds = modeBarBounds;
		this.bottomControls = bottomControls;
		this.mouseCoordsY = mouseCoordsY;
		this.editHintY = editHintY;
	}

	public static DashboardLayout compute(int screenWidth, int screenHeight, boolean magicLoaded) {
		final Rect leftPanel = new Rect(0, 0, PANEL_WIDTH, Math.max(0, screenHeight));
		final int iconY = iconButtonY(magicLoaded);
		final Rect magicButton = magicLoaded ? new Rect(MAGIC_BUTTON_X, TOP_MARGIN, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE) : null;
		final Rect atuButton = new Rect(ATU_BUTTON_X, iconY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE);
		final Rect atrMenuButton = new Rect(ATR_MENU_BUTTON_X, iconY, ICON_BUTTON_SIZE, ICON_BUTTON_SIZE);

		final DashboardMode[] modes = DashboardMode.values();
		final int totalGaps = (modes.length - 1) * MODE_BUTTON_GAP;
		final int rawAvailable = screenWidth - RIGHT_MARGIN - MODE_BAR_LEFT;
		final int available = Math.max(modes.length * 10 + totalGaps, rawAvailable);
		final int buttonBudget = available - totalGaps;

		int baseSum = 0;
		int idealSum = 0;
		for (final DashboardMode mode : modes) {
			baseSum += mode.baseTextWidth;
			idealSum += mode.idealButtonWidth;
		}

		final int[] widths = new int[modes.length];
		if (buttonBudget >= idealSum) {
			for (int i = 0; i < modes.length; i++) {
				widths[i] = modes[i].idealButtonWidth;
			}
		} else if (buttonBudget >= baseSum) {
			final int extra = buttonBudget - baseSum;
			final int perButton = extra / modes.length;
			final int remainder = extra % modes.length;
			for (int i = 0; i < modes.length; i++) {
				widths[i] = modes[i].baseTextWidth + perButton + (i < remainder ? 1 : 0);
			}
		} else {
			int assigned = 0;
			for (int i = 0; i < modes.length; i++) {
				if (i == modes.length - 1) {
					widths[i] = Math.max(10, buttonBudget - assigned);
				} else {
					widths[i] = Math.max(10, buttonBudget * modes[i].baseTextWidth / baseSum);
					assigned += widths[i];
				}
			}
		}

		final List<ModeSlot> slots = new ArrayList<>(modes.length);
		int x = MODE_BAR_LEFT;
		for (int i = 0; i < modes.length; i++) {
			slots.add(new ModeSlot(modes[i], new Rect(x, MODE_BAR_Y, widths[i], MODE_BAR_HEIGHT)));
			x += widths[i] + (i < modes.length - 1 ? MODE_BUTTON_GAP : 0);
		}
		final Rect barBounds = new Rect(MODE_BAR_LEFT, MODE_BAR_Y, x - MODE_BAR_LEFT, MODE_BAR_HEIGHT);
		final int bottomH = Math.min(BOTTOM_CONTROLS_HEIGHT, Math.max(0, screenHeight));
		final Rect bottomControls = new Rect(PANEL_WIDTH, Math.max(0, screenHeight - bottomH), Math.max(0, screenWidth - PANEL_WIDTH), bottomH);
		final int editHintY = iconY + ICON_BUTTON_SIZE + ICON_GAP;

		return new DashboardLayout(
				screenWidth,
				screenHeight,
				magicLoaded,
				leftPanel,
				magicButton,
				atuButton,
				atrMenuButton,
				Collections.unmodifiableList(slots),
				barBounds,
				bottomControls,
				MOUSE_COORDS_Y,
				editHintY
		);
	}

	public static int iconButtonY(boolean magicLoaded) {
		return magicLoaded ? TOP_MARGIN + ICON_BUTTON_SIZE + ICON_GAP : TOP_MARGIN;
	}

	public static int editHintY(boolean magicLoaded) {
		return iconButtonY(magicLoaded) + ICON_BUTTON_SIZE + ICON_GAP;
	}

	/**
	 * Matches Minecraft 1.20.1 {@code Window.calculateScaleFactor(int guiScale, boolean forceUnicodeFont)} (non-unicode).
	 * {@code guiScale == 0} means Auto.
	 */
	public static int guiScaleFactor(int framebufferWidth, int framebufferHeight, int guiScale) {
		int factor = 1;
		while (factor != guiScale
				&& factor < framebufferWidth
				&& factor < framebufferHeight
				&& framebufferWidth / (factor + 1) >= 320
				&& framebufferHeight / (factor + 1) >= 240) {
			factor++;
		}
		return factor;
	}

	/** Scaled GUI {@code {width, height}} matching Minecraft's {@code Window.onFramebufferSizeChanged} ceil division. */
	public static int[] guiScaledSize(int framebufferWidth, int framebufferHeight, int guiScale) {
		final int factor = guiScaleFactor(framebufferWidth, framebufferHeight, guiScale);
		final int scaledW = (framebufferWidth + factor - 1) / factor;
		final int scaledH = (framebufferHeight + factor - 1) / factor;
		return new int[]{scaledW, scaledH};
	}

	public int screenWidth() {
		return screenWidth;
	}

	public int screenHeight() {
		return screenHeight;
	}

	public boolean magicLoaded() {
		return magicLoaded;
	}

	public Rect leftPanel() {
		return leftPanel;
	}

	@Nullable
	public Rect magicButton() {
		return magicButton;
	}

	public Rect atuButton() {
		return atuButton;
	}

	public Rect atrMenuButton() {
		return atrMenuButton;
	}

	public List<ModeSlot> modeButtons() {
		return modeButtons;
	}

	public Rect modeBarBounds() {
		return modeBarBounds;
	}

	public Rect bottomControls() {
		return bottomControls;
	}

	public int mouseCoordsY() {
		return mouseCoordsY;
	}

	public int editHintY() {
		return editHintY;
	}
}
