/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Strict JSON for Stratos's own files (task G-9): written with explicit field names, read back refusing anything
 * missing, unknown or of the wrong type, with a message naming the field. Inside one {@code formatVersion} neither can
 * happen except through a bug; newer data is caught by the downgrade guard (F-5) before it is read.
 */
public final class StrictJson {
    /** Pretty-printed, no HTML escaping, so a file reads as written. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private StrictJson() {
    }

    /** {@code value} as indented JSON text, with a final line break. */
    public static String write(JsonElement value) {
        return GSON.toJson(value) + "\n";
    }

    /** {@code value} as compact JSON text (one line), for nesting one document inside another. */
    public static String compact(JsonElement value) {
        return value.toString();
    }

    /** The JSON object in {@code text}, or why there is none; {@code what} names it in the message. */
    public static JsonObject parseObject(String text, String what) throws JsonFormatException {
        JsonElement element;
        try {
            element = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            throw new JsonFormatException(what + " isn't JSON: " + e.getMessage());
        }
        if (!element.isJsonObject()) {
            throw new JsonFormatException(what + " isn't a JSON object");
        }
        return element.getAsJsonObject();
    }

    /** A reader for {@code object}, whose fields are named {@code where.<name>} in messages. */
    public static Reader read(JsonObject object, String where) {
        return new Reader(object, where);
    }

    /** Reads one object's fields; {@link #done} refuses any it didn't read. */
    public static final class Reader {
        private final JsonObject object;
        private final String where;
        private final Set<String> read = new HashSet<>();

        private Reader(JsonObject object, String where) {
            this.object = object;
            this.where = where;
        }

        private JsonElement required(String key) throws JsonFormatException {
            this.read.add(key);
            JsonElement value = this.object.get(key);
            if (value == null || value.isJsonNull()) {
                throw new JsonFormatException(this.where + "." + key + " is missing");
            }
            return value;
        }

        private Optional<JsonElement> optional(String key) {
            this.read.add(key);
            JsonElement value = this.object.get(key);
            return value == null || value.isJsonNull() ? Optional.empty() : Optional.of(value);
        }

        private JsonPrimitive primitive(String key, JsonElement value) throws JsonFormatException {
            if (!value.isJsonPrimitive()) {
                throw new JsonFormatException(this.where + "." + key + " should be a single value");
            }
            return value.getAsJsonPrimitive();
        }

        /** Whether {@code key} is there with a value; reading it is still up to the caller (F-2+: an optional list). */
        public boolean has(String key) {
            JsonElement value = this.object.get(key);
            return value != null && !value.isJsonNull();
        }

        public String string(String key) throws JsonFormatException {
            return asString(key, required(key));
        }

        public Optional<String> optionalString(String key) throws JsonFormatException {
            Optional<JsonElement> value = optional(key);
            return value.isPresent() ? Optional.of(asString(key, value.get())) : Optional.empty();
        }

        private String asString(String key, JsonElement value) throws JsonFormatException {
            JsonPrimitive primitive = primitive(key, value);
            if (!primitive.isString()) {
                throw new JsonFormatException(this.where + "." + key + " should be a string");
            }
            return primitive.getAsString();
        }

        public int integer(String key) throws JsonFormatException {
            return asInteger(key, required(key));
        }

        public Optional<Integer> optionalInteger(String key) throws JsonFormatException {
            Optional<JsonElement> value = optional(key);
            return value.isPresent() ? Optional.of(asInteger(key, value.get())) : Optional.empty();
        }

        private int asInteger(String key, JsonElement value) throws JsonFormatException {
            JsonPrimitive primitive = primitive(key, value);
            if (!primitive.isNumber()) {
                throw new JsonFormatException(this.where + "." + key + " should be a number");
            }
            double number = primitive.getAsDouble();
            if (number != StrictMath.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new JsonFormatException(this.where + "." + key + " should be a whole number, not " + primitive);
            }
            return (int) number;
        }

        public double number(String key) throws JsonFormatException {
            return asNumber(key, required(key));
        }

        public Optional<Double> optionalNumber(String key) throws JsonFormatException {
            Optional<JsonElement> value = optional(key);
            return value.isPresent() ? Optional.of(asNumber(key, value.get())) : Optional.empty();
        }

        private double asNumber(String key, JsonElement value) throws JsonFormatException {
            JsonPrimitive primitive = primitive(key, value);
            if (!primitive.isNumber()) {
                throw new JsonFormatException(this.where + "." + key + " should be a number");
            }
            return primitive.getAsDouble();
        }

        public boolean bool(String key) throws JsonFormatException {
            JsonPrimitive primitive = primitive(key, required(key));
            if (!primitive.isBoolean()) {
                throw new JsonFormatException(this.where + "." + key + " should be true or false");
            }
            return primitive.getAsBoolean();
        }

        public JsonObject object(String key) throws JsonFormatException {
            JsonElement value = required(key);
            if (!value.isJsonObject()) {
                throw new JsonFormatException(this.where + "." + key + " should be an object");
            }
            return value.getAsJsonObject();
        }

        public Optional<JsonObject> optionalObject(String key) throws JsonFormatException {
            Optional<JsonElement> value = optional(key);
            if (value.isEmpty()) {
                return Optional.empty();
            }
            if (!value.get().isJsonObject()) {
                throw new JsonFormatException(this.where + "." + key + " should be an object");
            }
            return Optional.of(value.get().getAsJsonObject());
        }

        /** The array {@code key}'s elements, each an object. */
        public List<JsonObject> objects(String key) throws JsonFormatException {
            List<JsonObject> objects = new ArrayList<>();
            int i = 0;
            for (JsonElement element : array(key)) {
                if (!element.isJsonObject()) {
                    throw new JsonFormatException(this.where + "." + key + "[" + i + "] should be an object");
                }
                objects.add(element.getAsJsonObject());
                i++;
            }
            return objects;
        }

        /** The array {@code key}'s elements, each a string. */
        public List<String> strings(String key) throws JsonFormatException {
            List<String> strings = new ArrayList<>();
            int i = 0;
            for (JsonElement element : array(key)) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new JsonFormatException(this.where + "." + key + "[" + i + "] should be a string");
                }
                strings.add(element.getAsString());
                i++;
            }
            return strings;
        }

        /** The array {@code key}: exactly {@code count} whole numbers, such as a block position. */
        public int[] integers(String key, int count) throws JsonFormatException {
            return wholeNumbers(array(key), count, this.where + "." + key);
        }

        /** The array {@code key}: lists of exactly {@code count} whole numbers each, such as section positions. */
        public List<int[]> integerLists(String key, int count) throws JsonFormatException {
            List<int[]> lists = new ArrayList<>();
            int i = 0;
            for (JsonElement element : array(key)) {
                String field = this.where + "." + key + "[" + i++ + "]";
                if (!element.isJsonArray()) {
                    throw new JsonFormatException(field + " should be a list");
                }
                lists.add(wholeNumbers(element.getAsJsonArray(), count, field));
            }
            return lists;
        }

        private static int[] wholeNumbers(JsonArray array, int count, String field) throws JsonFormatException {
            if (array.size() != count) {
                throw new JsonFormatException(field + " should hold " + count + " numbers, not " + array.size());
            }
            int[] values = new int[count];
            for (int i = 0; i < count; i++) {
                JsonElement element = array.get(i);
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                    throw new JsonFormatException(field + "[" + i + "] should be a number");
                }
                double number = element.getAsDouble();
                if (number != StrictMath.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                    throw new JsonFormatException(field + "[" + i + "] should be a whole number");
                }
                values[i] = (int) number;
            }
            return values;
        }

        private JsonArray array(String key) throws JsonFormatException {
            JsonElement value = required(key);
            if (!value.isJsonArray()) {
                throw new JsonFormatException(this.where + "." + key + " should be a list");
            }
            return value.getAsJsonArray();
        }

        /** An enum constant named by the string {@code key}. */
        public <E extends Enum<E>> E constant(String key, Class<E> type) throws JsonFormatException {
            String name = string(key);
            try {
                return Enum.valueOf(type, name);
            } catch (IllegalArgumentException e) {
                throw new JsonFormatException(this.where + "." + key + " can't be " + name);
            }
        }

        /** Refuses a field this reader didn't read. */
        public void done() throws JsonFormatException {
            for (String key : this.object.keySet()) {
                if (!this.read.contains(key)) {
                    throw new JsonFormatException(this.where + "." + key + " isn't a field Stratos knows");
                }
            }
        }
    }
}
