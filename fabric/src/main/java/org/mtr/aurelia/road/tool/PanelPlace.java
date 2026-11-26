package org.mtr.aurelia.road.tool;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Where the player dragged the tool panel to (2026-11-26: it covered other mods' HUDs at the top right, so it moves like
 * Xaero's minimap). The panel's top left corner as a share of the screen's width and height, so it stays put across window
 * sizes and GUI scales. Kept in config/aurelia/roads/client.json beside the road choices; missing or unreadable means the
 * panel was never moved and keeps its place at the top right.
 */
public record PanelPlace(boolean moved, double x, double y) {
    /** Never moved: the top right. */
    public static final PanelPlace DEFAULT = new PanelPlace(false, 1, 0);

    public PanelPlace {
        x = clamp(x);
        y = clamp(y);
    }

    /** Dragged so its top left corner is at ({@code left}, {@code top}) on a {@code width} by {@code height} screen. */
    public static PanelPlace at(int left, int top, int width, int height) {
        return new PanelPlace(true, width <= 0 ? 0 : (double) left / width, height <= 0 ? 0 : (double) top / height);
    }

    /** The panel's left edge on a {@code width} wide screen, keeping a {@code panelWidth} panel {@code margin} inside it. */
    public int left(int width, int panelWidth, int margin) {
        return within((int) Math.round(this.x * width), margin, width - margin - panelWidth);
    }

    /** The panel's top edge on a {@code height} tall screen, keeping a {@code panelHeight} panel between {@code top} and {@code bottom}. */
    public int top(int height, int panelHeight, int top, int bottom) {
        return within((int) Math.round(this.y * height), top, bottom - panelHeight);
    }

    /** Adds the place to the file's JSON {@code object}, only once it was moved, so an unmoved file stays as it was. */
    public void writeTo(JsonObject object) {
        if (this.moved) {
            object.addProperty("panelX", this.x);
            object.addProperty("panelY", this.y);
        }
    }

    /** The place in {@code text}; {@link #DEFAULT} when either share is missing or unreadable, or {@code text} isn't a JSON object. */
    public static PanelPlace fromJson(String text) {
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed.isJsonObject() && number(parsed.getAsJsonObject().get("panelX")) && number(parsed.getAsJsonObject().get("panelY"))) {
                JsonObject object = parsed.getAsJsonObject();
                return new PanelPlace(true, object.get("panelX").getAsDouble(), object.get("panelY").getAsDouble());
            }
        } catch (RuntimeException e) {
            // unreadable: the panel stays at the top right
        }
        return DEFAULT;
    }

    private static boolean number(JsonElement value) {
        return value instanceof JsonPrimitive primitive && primitive.isNumber() && Double.isFinite(primitive.getAsDouble());
    }

    private static double clamp(double share) {
        return Double.isFinite(share) ? Math.max(0, Math.min(1, share)) : 0;
    }

    /** {@code value} kept between {@code low} and {@code high}; {@code low} wins when the room is too small. */
    private static int within(int value, int low, int high) {
        return Math.max(low, Math.min(value, high));
    }
}
