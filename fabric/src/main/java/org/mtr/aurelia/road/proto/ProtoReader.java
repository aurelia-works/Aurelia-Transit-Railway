/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the field encoding written by {@link ProtoWriter}, checking every field as it goes (editor-protocol §2). All
 * bad input throws {@link MalformedMessage}: reading past the end, a VarInt longer than 5 bytes, a boolean that isn't
 * 0 or 1, an enum position the enum doesn't have, a string, byte field or list over its maximum, invalid UTF-8, and
 * (from {@link #end()}) bytes left over after the record.
 */
public final class ProtoReader {
    private final byte[] bytes;
    private int position;

    public ProtoReader(byte[] bytes) {
        this.bytes = bytes;
    }

    private int next() throws MalformedMessage {
        if (this.position >= this.bytes.length) {
            throw new MalformedMessage("the record ends early, at byte " + this.position);
        }
        return this.bytes[this.position++] & 0xFF;
    }

    /**
     * A VarInt in its shortest form only, so every value has exactly one encoding: a last byte of 0 after other bytes,
     * or a fifth byte with more than the 4 bits an int has left, is malformed (as is a sixth byte).
     */
    public int varInt() throws MalformedMessage {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = next();
            if (i == 4 && (b & 0xF0) != 0) {
                throw new MalformedMessage("a VarInt is longer than an int");
            }
            value |= (b & 0x7F) << (7 * i);
            if ((b & 0x80) == 0) {
                if (b == 0 && i > 0) {
                    throw new MalformedMessage("a VarInt isn't in its shortest form");
                }
                return value;
            }
        }
        throw new MalformedMessage("a VarInt is longer than 5 bytes");
    }

    /** A VarInt from {@code min} to {@code max}, both included. */
    public int varInt(int min, int max, String field) throws MalformedMessage {
        int value = varInt();
        if (value < min || value > max) {
            throw new MalformedMessage(field + " is " + value + ", outside " + min + " to " + max);
        }
        return value;
    }

    /** A VarInt of at least 0. */
    public int nonNegative(String field) throws MalformedMessage {
        return varInt(0, Integer.MAX_VALUE, field);
    }

    public boolean bool() throws MalformedMessage {
        int b = next();
        if (b > 1) {
            throw new MalformedMessage("a boolean is " + b + ", not 0 or 1");
        }
        return b == 1;
    }

    public long longValue() throws MalformedMessage {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | next();
        }
        return value;
    }

    /** A double that must be finite (no NaN or infinity). */
    public double finiteDouble(String field) throws MalformedMessage {
        double value = Double.longBitsToDouble(longValue());
        if (!Double.isFinite(value)) {
            throw new MalformedMessage(field + " isn't a finite number");
        }
        return value;
    }

    public UUID uuid() throws MalformedMessage {
        return new UUID(longValue(), longValue());
    }

    /** A UTF-8 string of at most {@code maxBytes} bytes. */
    public String string(int maxBytes, String field) throws MalformedMessage {
        byte[] utf8 = raw(varInt(0, maxBytes, field + "'s length"), field);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(utf8))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new MalformedMessage(field + " isn't valid UTF-8");
        }
    }

    /** Exactly {@code count} raw bytes. */
    public byte[] raw(int count, String field) throws MalformedMessage {
        if (count > this.bytes.length - this.position) {
            throw new MalformedMessage(field + " needs " + count + " bytes, but only " + (this.bytes.length - this.position) + " are left");
        }
        byte[] out = new byte[count];
        System.arraycopy(this.bytes, this.position, out, 0, count);
        this.position += count;
        return out;
    }

    /** A VarInt length and that many bytes, at most {@code maxBytes}. */
    public byte[] bytes(int maxBytes, String field) throws MalformedMessage {
        return raw(varInt(0, maxBytes, field + "'s length"), field);
    }

    public <E extends Enum<E>> E enumValue(E[] values, String field) throws MalformedMessage {
        return values[varInt(0, values.length - 1, field)];
    }

    /** The count of a list of at most {@code max} items. */
    public int count(int max, String field) throws MalformedMessage {
        return varInt(0, max, field + "'s count");
    }

    /** Whether an optional field is there. */
    public boolean present() throws MalformedMessage {
        return bool();
    }

    public Optional<String> optionalString(int maxBytes, String field) throws MalformedMessage {
        return present() ? Optional.of(string(maxBytes, field)) : Optional.empty();
    }

    public Optional<UUID> optionalUuid() throws MalformedMessage {
        return present() ? Optional.of(uuid()) : Optional.empty();
    }

    /** A string-to-string map, in the order it was written. Refuses a key that appears twice. */
    public Map<String, String> stringMap(int maxEntries, int maxKeyBytes, int maxValueBytes, String field) throws MalformedMessage {
        int count = count(maxEntries, field);
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = string(maxKeyBytes, field + " key");
            if (map.put(key, string(maxValueBytes, field + " value")) != null) {
                throw new MalformedMessage(field + " has the key '" + key + "' twice");
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /** Checks that the whole record was read: trailing bytes make it malformed. */
    public void end() throws MalformedMessage {
        if (this.position != this.bytes.length) {
            throw new MalformedMessage((this.bytes.length - this.position) + " bytes are left over after the record");
        }
    }
}
