/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.tool;

/**
 * The hub's toolbar along the bottom of the screen (task UI-1a; owner, 2026-10-15; design in docs/design/ui-1), in GUI
 * pixels: where its sections go across the toolbar, and which of a strip's tiles show.
 * <ul>
 *   <li>From the left: the tools, the strip of the tool's options (the layouts), then Palette and Nodes on the right,
 *       each a single folded tile with its caption. Dividers sit between them.</li>
 *   <li>The strip takes whatever width is left, so smaller GUI scales show more tiles, up to {@link #MAX_WIDTH}.</li>
 *   <li>Tiles never shrink (design rule 1). A strip with more tiles than fit shows whole tiles between two arrows and
 *       pages through the rest a tile at a time (the arrows and the mouse wheel).</li>
 * </ul>
 */
public final class Toolbar {
    /** The space between the toolbar and the screen's edges. */
    public static final int MARGIN = 6;
    /** The padding inside the toolbar, and the space each side of a divider. */
    public static final int PAD = 6;
    /** The narrowest space each side of a divider, on a toolbar too narrow for the usual {@link #PAD}. */
    public static final int TIGHT_PAD = 3;
    /** A tool tile's side, and a strip tile's width. */
    public static final int TILE = 30;
    /** The space between two tiles. */
    public static final int GAP = 3;
    /** A strip arrow's width, each side, while it pages. */
    public static final int ARROW = 10;
    /** The Palette section's width: its tile and caption. */
    public static final int PALETTE_WIDTH = 48;
    /** The Nodes section's width: its tile and caption. */
    public static final int NODES_WIDTH = 44;
    /** The widest the toolbar gets inside, so small GUI scales keep it together rather than spread across the screen. */
    public static final int MAX_WIDTH = 720;

    private Toolbar() {
    }

    /** The toolbar's width inside on a screen {@code screenWidth} GUI pixels wide: the screen less the margins, at most {@link #MAX_WIDTH}. Centred. */
    public static int width(int screenWidth) {
        return StrictMath.max(0, StrictMath.min(screenWidth - 2 * MARGIN, MAX_WIDTH));
    }

    /**
     * Where the sections go across a toolbar {@code width} GUI pixels wide inside, from its left edge.
     *
     * @param toolsX       the first tool tile
     * @param toolsWidth   the tools' tiles and the gaps between them
     * @param toolsDivider the divider after the tools
     * @param stripX       the strip's left edge
     * @param stripWidth   the strip's width, arrows included (never below 0)
     * @param paletteDivider the divider before Palette
     * @param paletteX     the Palette section
     * @param nodesDivider the divider before Nodes
     * @param nodesX       the Nodes section
     */
    public record Sections(int toolsX, int toolsWidth, int toolsDivider, int stripX, int stripWidth,
                           int paletteDivider, int paletteX, int nodesDivider, int nodesX) {
    }

    /**
     * The sections of a toolbar {@code width} GUI pixels wide inside, with {@code tools} tool tiles. When the strip would
     * be too narrow for one tile between its arrows (a 320-pixel GUI), the space each side of the dividers shrinks, down
     * to {@link #TIGHT_PAD}, to make room for it.
     */
    public static Sections sections(int width, int tools) {
        Sections sections = sections(width, tools, PAD);
        int missing = TILE + 2 * ARROW - sections.stripWidth();
        if (missing <= 0 || sections.stripWidth() <= 0) {
            return sections;
        }
        // six gaps sit each side of the three dividers
        return sections(width, tools, StrictMath.max(TIGHT_PAD, PAD - (missing + 5) / 6));
    }

    private static Sections sections(int width, int tools, int gap) {
        int toolsWidth = tools <= 0 ? 0 : tools * TILE + (tools - 1) * GAP;
        int toolsDivider = PAD + toolsWidth + gap;
        int stripX = toolsDivider + gap;
        int nodesX = width - PAD - NODES_WIDTH;
        int nodesDivider = nodesX - gap;
        int paletteX = nodesDivider - gap - PALETTE_WIDTH;
        int paletteDivider = paletteX - gap;
        int stripWidth = StrictMath.max(0, paletteDivider - gap - stripX);
        return new Sections(PAD, toolsWidth, toolsDivider, stripX, stripWidth, paletteDivider, paletteX, nodesDivider, nodesX);
    }

    /** How many whole tiles fit in {@code width} GUI pixels. */
    public static int fit(int width) {
        return width < TILE ? 0 : (width + GAP) / (TILE + GAP);
    }

    /**
     * Which of a strip's {@code count} tiles show: {@code shown} of them from {@code first}. While {@link #pages()}, the
     * arrows take {@link #ARROW} each side and page through the rest.
     */
    public record Strip(int count, int shown, int first) {
        /** A strip of {@code count} tiles in {@code width} GUI pixels, showing from tile {@code first} (kept in range). */
        public static Strip of(int count, int width, int first) {
            int shown = count <= fit(width) ? count : fit(width - 2 * ARROW);
            return new Strip(count, shown, clamp(first, count, shown));
        }

        /** The strip that shows tile {@code index}, moving as little as it can (the chosen layout after a rebuild). */
        public Strip showing(int index) {
            if (index < this.first) {
                return new Strip(this.count, this.shown, clamp(index, this.count, this.shown));
            }
            if (index >= this.first + this.shown) {
                return new Strip(this.count, this.shown, clamp(index - this.shown + 1, this.count, this.shown));
            }
            return this;
        }

        /** {@code tiles} tiles on (negative: back), stopping at either end. */
        public Strip scroll(int tiles) {
            return new Strip(this.count, this.shown, clamp(this.first + tiles, this.count, this.shown));
        }

        /** Whether some tiles don't show, so the arrows page. */
        public boolean pages() {
            return this.shown < this.count;
        }

        public boolean canBack() {
            return this.first > 0;
        }

        public boolean canForward() {
            return this.first + this.shown < this.count;
        }

        private static int clamp(int first, int count, int shown) {
            return StrictMath.max(0, StrictMath.min(first, count - shown));
        }
    }
}
