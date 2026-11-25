/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** The field checks shared by the {@link Message} records, with the maximums the decoder also enforces. */
final class Fields {
    private Fields() {
    }

    static void nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " is " + value + "; it can't be negative");
        }
    }

    static void name(String value, String field) {
        Objects.requireNonNull(value, field);
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > Message.MAX_NAME_BYTES) {
            throw new IllegalArgumentException(field + " has " + bytes + " bytes; the most is " + Message.MAX_NAME_BYTES);
        }
    }

    static void optionalName(Optional<String> value, String field) {
        Objects.requireNonNull(value, field).ifPresent(v -> name(v, field));
    }

    static List<String> names(List<String> values, int max, String field) {
        if (values.size() > max) {
            throw new IllegalArgumentException(field + " has " + values.size() + " entries; the most is " + max);
        }
        values.forEach(v -> name(v, field));
        return List.copyOf(values);
    }

    static Map<String, String> args(Map<String, String> args) {
        if (args.size() > Message.MAX_ARGS) {
            throw new IllegalArgumentException("A reason has " + args.size() + " arguments; the most is " + Message.MAX_ARGS);
        }
        Map<String, String> copy = new LinkedHashMap<>();
        args.forEach((key, value) -> {
            name(key, "argument name");
            Objects.requireNonNull(value, key);
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > Message.MAX_ARG_BYTES) {
                throw new IllegalArgumentException("Argument " + key + " has " + bytes + " bytes; the most is " + Message.MAX_ARG_BYTES);
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
