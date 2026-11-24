/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.preview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.Locale;
import java.util.Objects;

/**
 * The preview's settings a player keeps across restarts (task UI-1c), from the hub's Settings: whether faces are shown or
 * only outlines, and how faces are drawn with a shader pack. Kept in config/aurelia/roads/client.json beside the road choices,
 * and read as leniently: anything missing, unknown or unreadable stands for the default.
 */
public record PreviewChoices(boolean outlinesOnly, ShaderFaces shaderFaces) {
    /** Faces shown, drawn as Auto says: what a new player starts with. */
    public static final PreviewChoices DEFAULT = new PreviewChoices(false, ShaderFaces.AUTO);

    /**
     * How the faces are drawn with a shader pack (owner, 2026-10-15). Stratos draws them two ways, as the pack's basic
     * geometry and as a translucent entity, so every pack shows at least one (F-4); BSL and Bliss show both, so their faces
     * look more solid than intended.
     */
    public enum ShaderFaces {
        /** Once with the packs known to show both draws (BSL and Bliss, by the pack's file name), twice with any other. */
        AUTO,
        /** Both draws, whatever the pack. */
        BOTH,
        /** One draw only, the translucent entity's, whatever the pack. */
        ONE;

        /** The packs Auto draws once with, as words in their file names (lower case). */
        static final String[] DRAWN_ONCE = {"bsl", "bliss"};

        /** Whether the faces are drawn once with the pack whose file name is {@code packName}; twice otherwise. */
        public boolean once(String packName) {
            return switch (this) {
                case BOTH -> false;
                case ONE -> true;
                case AUTO -> {
                    String name = packName == null ? "" : packName.toLowerCase(Locale.ROOT);
                    for (String known : DRAWN_ONCE) {
                        if (name.contains(known)) {
                            yield true;
                        }
                    }
                    yield false;
                }
            };
        }
    }

    public PreviewChoices {
        Objects.requireNonNull(shaderFaces, "shaderFaces");
    }

    public PreviewChoices withOutlinesOnly(boolean outlinesOnly) {
        return new PreviewChoices(outlinesOnly, this.shaderFaces);
    }

    public PreviewChoices withShaderFaces(ShaderFaces shaderFaces) {
        return new PreviewChoices(this.outlinesOnly, shaderFaces);
    }

    /** Adds the choices to the file's JSON {@code object}: {@code "faces": "shown"} or {@code "outlines_only"}, and {@code "shaderFaces"}. */
    public void writeTo(JsonObject object) {
        object.addProperty("faces", this.outlinesOnly ? "outlines_only" : "shown");
        object.addProperty("shaderFaces", this.shaderFaces.name().toLowerCase(Locale.ROOT));
    }

    /** The choices in {@code text}, each one missing or unreadable taken from {@link #DEFAULT}; all of it if {@code text} isn't a JSON object. */
    public static PreviewChoices fromJson(String text) {
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
        String faces = string(object, "faces");
        String shaderFaces = string(object, "shaderFaces");
        ShaderFaces drawn = DEFAULT.shaderFaces;
        if (shaderFaces != null) {
            for (ShaderFaces value : ShaderFaces.values()) {
                if (value.name().equalsIgnoreCase(shaderFaces)) {
                    drawn = value;
                }
            }
        }
        return new PreviewChoices("outlines_only".equals(faces), drawn);
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
    }
}
