/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.raster;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.mtr.aurelia.road.road.Band;
import org.mtr.aurelia.road.road.LineMarking;

/**
 * What the rasteriser decided for one column: its band, the colour of the line marking painted on it (if any), and
 * whether a lane curve passes through it (a lane-centre column, where that lane's MTR nodes go). From R-9 the colour
 * picks the palette's role ({@code marking_white} or {@code marking_yellow}).
 *
 * <p>From B-1 a kerb column may also say where it meets the road ({@link KerbEdge}), for a palette whose kerb turns to
 * face it; from B-2 a painted column may say where the line's centre crosses it ({@code chord}, road.line.LineChords),
 * for a palette whose markings are line blocks. Both come from {@link Rasteriser#plan}; the rasteriser's own columns have
 * neither.
 */
public record Column(Band band, Optional<LineMarking.Colour> paint, boolean laneCentre, Optional<KerbEdge> kerb, OptionalInt chord) {
    public Column {
        Objects.requireNonNull(band, "band");
        Objects.requireNonNull(paint, "paint");
        Objects.requireNonNull(kerb, "kerb");
        Objects.requireNonNull(chord, "chord");
    }

    /** A column with no kerb edge and no chord. */
    public Column(Band band, Optional<LineMarking.Colour> paint, boolean laneCentre) {
        this(band, paint, laneCentre, Optional.empty(), OptionalInt.empty());
    }

    /** A column with {@code kerb} and no chord. */
    public Column(Band band, Optional<LineMarking.Colour> paint, boolean laneCentre, Optional<KerbEdge> kerb) {
        this(band, paint, laneCentre, kerb, OptionalInt.empty());
    }

    /** A column with a white line painted on it ({@code painted}) or none. */
    public Column(Band band, boolean painted, boolean laneCentre) {
        this(band, painted ? Optional.of(LineMarking.Colour.WHITE) : Optional.empty(), laneCentre);
    }

    /** Whether a line marking is painted on it. */
    public boolean painted() {
        return this.paint.isPresent();
    }

    /** This column in band {@code band}, meeting the road at {@code kerb} (or not). */
    public Column with(Band band, Optional<KerbEdge> kerb) {
        return new Column(band, this.paint, this.laneCentre, kerb, this.chord);
    }

    /** This column painted {@code paint} (or not), its line crossing it along {@code chord} (or not). */
    public Column painted(Optional<LineMarking.Colour> paint, OptionalInt chord) {
        return new Column(this.band, paint, this.laneCentre, this.kerb, chord);
    }
}
