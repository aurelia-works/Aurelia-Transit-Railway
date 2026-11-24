/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes the field encoding of editor-protocol §2: VarInts (Minecraft's LEB128 variant) for ints and lengths, UTF-8
 * strings with a VarInt length, booleans as one byte, longs and doubles as 8 bytes big-endian, UUIDs as two longs.
 * Enums are written as the VarInt of their position in the enum, and an optional field as a boolean "present" byte
 * followed by the value when it is there.
 *
 * <p>The writer checks the same maximums as {@link ProtoReader}, so Stratos never sends what it would refuse.
 */
public final class ProtoWriter {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public ProtoWriter varInt(int value) {
        int rest = value;
        while ((rest & ~0x7F) != 0) {
            this.out.write((rest & 0x7F) | 0x80);
            rest >>>= 7;
        }
        this.out.write(rest);
        return this;
    }

    /** A VarInt that must be at least 0 (counts, ids, sizes). */
    public ProtoWriter nonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected a value of at least 0, got " + value);
        }
        return varInt(value);
    }

    public ProtoWriter bool(boolean value) {
        this.out.write(value ? 1 : 0);
        return this;
    }

    public ProtoWriter longValue(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            this.out.write((int) (value >>> shift) & 0xFF);
        }
        return this;
    }

    public ProtoWriter doubleValue(double value) {
        return longValue(Double.doubleToLongBits(value));
    }

    public ProtoWriter uuid(UUID value) {
        return longValue(value.getMostSignificantBits()).longValue(value.getLeastSignificantBits());
    }

    /** A UTF-8 string of at most {@code maxBytes} bytes. */
    public ProtoWriter string(String value, int maxBytes) {
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > maxBytes) {
            throw new IllegalArgumentException("A string of " + utf8.length + " bytes is over its maximum of " + maxBytes);
        }
        varInt(utf8.length);
        this.out.writeBytes(utf8);
        return this;
    }

    /** Exactly {@code bytes.length} raw bytes, with no length (fixed-size fields such as plan hashes). */
    public ProtoWriter raw(byte[] bytes) {
        this.out.writeBytes(bytes);
        return this;
    }

    /** A VarInt length and the bytes, at most {@code maxBytes}. */
    public ProtoWriter bytes(byte[] bytes, int maxBytes) {
        if (bytes.length > maxBytes) {
            throw new IllegalArgumentException(bytes.length + " bytes are over the maximum of " + maxBytes);
        }
        varInt(bytes.length);
        return raw(bytes);
    }

    public ProtoWriter enumValue(Enum<?> value) {
        return varInt(value.ordinal());
    }

    /** The count of a list of at most {@code max} items; the caller writes the items. */
    public ProtoWriter count(Collection<?> items, int max) {
        if (items.size() > max) {
            throw new IllegalArgumentException("A list of " + items.size() + " items is over its maximum of " + max);
        }
        return varInt(items.size());
    }

    /** Whether an optional field is there; the caller writes the value if it is. */
    public ProtoWriter present(Optional<?> value) {
        return bool(value.isPresent());
    }

    public ProtoWriter optionalString(Optional<String> value, int maxBytes) {
        present(value);
        value.ifPresent(v -> string(v, maxBytes));
        return this;
    }

    public ProtoWriter optionalUuid(Optional<UUID> value) {
        present(value);
        value.ifPresent(this::uuid);
        return this;
    }

    /** A string-to-string map in its iteration order: a count, then each key and value. */
    public ProtoWriter stringMap(Map<String, String> map, int maxEntries, int maxKeyBytes, int maxValueBytes) {
        count(map.entrySet(), maxEntries);
        map.forEach((key, value) -> {
            string(key, maxKeyBytes);
            string(value, maxValueBytes);
        });
        return this;
    }

    public int size() {
        return this.out.size();
    }

    public byte[] toByteArray() {
        return this.out.toByteArray();
    }
}
