package org.mtr.aurelia.road.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

/** The moved tool panel's place in config/aurelia/roads/client.json: old files keep the top right, a moved place comes back. */
class PanelPlaceTest {
    @Test
    void aFileFromBeforeThePanelMovedKeepsItAtTheTopRight() {
        assertEquals(PanelPlace.DEFAULT, PanelPlace.fromJson("{\"layout\":\"stratos:uk_two_lane\",\"faces\":\"shown\"}"));
        assertEquals(PanelPlace.DEFAULT, PanelPlace.fromJson("{\"panelX\":\"left\",\"panelY\":0.5}"));
        assertEquals(PanelPlace.DEFAULT, PanelPlace.fromJson("not json"));
    }

    @Test
    void aMovedPlaceIsKeptAndStaysOnScreen() {
        PanelPlace place = PanelPlace.at(20, 100, 400, 200);
        JsonObject json = new JsonObject();
        place.writeTo(json);
        PanelPlace back = PanelPlace.fromJson(json.toString());
        assertEquals(place, back);
        assertEquals(20, back.left(400, 150, 6));
        assertEquals(100, back.top(200, 50, 6, 175));
        // a smaller window: the panel is pushed back inside rather than drawn off the edge
        assertEquals(94, PanelPlace.at(390, 0, 400, 200).left(250, 150, 6));
    }
}
