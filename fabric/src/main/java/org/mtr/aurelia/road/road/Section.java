/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.Objects;
import java.util.Optional;

/** A run of identical columns in a profile, listed left to right looking along the reference direction. */
public record Section(Band band, int width, Optional<LineMarking> marking) {
    public Section {
        Objects.requireNonNull(band, "band");
        Objects.requireNonNull(marking, "marking");
    }

    /** {@code width} columns of {@code band}, unmarked. */
    public static Section of(Band band, int width) {
        return new Section(band, width, Optional.empty());
    }

    /** {@code width} columns of {@code band} carrying {@code marking}. */
    public static Section marked(Band band, int width, LineMarking marking) {
        return new Section(band, width, Optional.of(marking));
    }
}
