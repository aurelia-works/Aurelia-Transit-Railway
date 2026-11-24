/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.Arrays;
import java.util.HexFormat;

/** A plan hash on the wire: exactly 32 bytes (SHA-256, spec mod-suite-v1 §6.4), compared by value. */
public final class Digest {
    public static final int LENGTH = 32;

    private final byte[] bytes;

    private Digest(byte[] bytes) {
        this.bytes = bytes;
    }

    /** A copy of {@code bytes}, which must be exactly 32 long. */
    public static Digest of(byte[] bytes) {
        if (bytes.length != LENGTH) {
            throw new IllegalArgumentException("A plan hash has " + LENGTH + " bytes, not " + bytes.length);
        }
        return new Digest(bytes.clone());
    }

    public byte[] bytes() {
        return this.bytes.clone();
    }

    public String hex() {
        return HexFormat.of().formatHex(this.bytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Digest digest && Arrays.equals(this.bytes, digest.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.bytes);
    }

    @Override
    public String toString() {
        return hex();
    }
}
