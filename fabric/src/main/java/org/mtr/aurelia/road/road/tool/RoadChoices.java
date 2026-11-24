/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import java.util.Objects;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadProfiles;
import org.mtr.aurelia.road.road.palette.BuiltInPalettes;

/**
 * The road choices a player keeps across restarts (task UI-1a; owner, 2026-10-15): the layout, the palette and the node
 * aid, by id. Read leniently, unlike Stratos's world data: it is the player's own convenience file, so anything missing,
 * unknown or unreadable stands for the default. A layout, palette or aid this game or server hasn't got is the
 * settings' business ({@link RoadSettings#restore}).
 */
public record RoadChoices(String layoutId, String paletteId, RoadPlanParams.NodeAids nodeAids) {
    /** City street, Stratos UK and Track: what a new player starts with. */
    public static final RoadChoices DEFAULT = new RoadChoices(RoadProfiles.CITY_17.id(), BuiltInPalettes.DEFAULT_ID, RoadPlanParams.NodeAids.TRACK);

    public RoadChoices {
        Objects.requireNonNull(layoutId, "layoutId");
        Objects.requireNonNull(paletteId, "paletteId");
        Objects.requireNonNull(nodeAids, "nodeAids");
    }

    /** As JSON: {@code {"layout": "city-17", "palette": "stratos_uk", "nodeAids": "track"}}. */
    public String toJson() {
        return StrictJson.write(toJsonObject());
    }

    /** As a JSON object, for a file that keeps other choices beside them (UI-1c: the preview's, {@code PreviewChoices}). */
    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        object.addProperty("layout", this.layoutId);
        object.addProperty("palette", this.paletteId);
        object.addProperty("nodeAids", this.nodeAids.name().toLowerCase(Locale.ROOT));
        return object;
    }

    /** The choices in {@code text}, each one missing or unreadable taken from {@link #DEFAULT}; all of it if {@code text} isn't a JSON object. */
    public static RoadChoices fromJson(String text) {
        JsonObject object;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return DEFAULT;
            }
            object = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return DEFAULT;
        }
        String layout = string(object, "layout");
        String palette = string(object, "palette");
        String aids = string(object, "nodeAids");
        RoadPlanParams.NodeAids nodeAids = DEFAULT.nodeAids;
        if (aids != null) {
            for (RoadPlanParams.NodeAids value : RoadPlanParams.NodeAids.values()) {
                if (value.name().equalsIgnoreCase(aids)) {
                    nodeAids = value;
                }
            }
        }
        return new RoadChoices(layout == null || layout.isBlank() ? DEFAULT.layoutId : layout,
                palette == null || palette.isBlank() ? DEFAULT.paletteId : palette, nodeAids);
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
    }
}
