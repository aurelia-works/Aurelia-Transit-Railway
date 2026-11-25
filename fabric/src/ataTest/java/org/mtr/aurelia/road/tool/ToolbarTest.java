package org.mtr.aurelia.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The hub's toolbar on the smallest screens Minecraft gives a GUI. */
class ToolbarTest {
    /**
     * Audit 2026-11-24: on a 320-pixel-wide GUI (auto GUI scale on a 640x480, 1280x960 or 1600x1200 window) the layout
     * strip had 39 pixels, too few for one tile between its arrows, so it showed the arrows and no layouts at all.
     */
    @Test
    void theNarrowestGuiStillShowsALayoutBetweenTheArrows() {
        Toolbar.Sections sections = Toolbar.sections(Toolbar.width(320), 4);
        Toolbar.Strip strip = Toolbar.Strip.of(4, sections.stripWidth(), 0);
        assertEquals(1, strip.shown());
        assertEquals(true, strip.pages());
        assertEquals(true, sections.stripX() + sections.stripWidth() < sections.paletteDivider(), "the strip stays clear of Palette's divider");
    }

    /** A toolbar with room keeps its usual spacing: 1920x1080 at GUI scale 4, as in the design mockups. */
    @Test
    void aToolbarWithRoomKeepsItsSpacing() {
        Toolbar.Sections sections = Toolbar.sections(Toolbar.width(480), 4);
        assertEquals(141, sections.toolsDivider());
        assertEquals(199, sections.stripWidth());
        assertEquals(358, sections.paletteX());
    }
}
