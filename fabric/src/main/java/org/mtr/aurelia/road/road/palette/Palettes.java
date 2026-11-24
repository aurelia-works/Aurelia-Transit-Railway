/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The palettes a planner may use (task R-9), part of the world's facts it is given ({@code WorldLimits.palettes}).
 * <ul>
 *   <li><b>Current:</b> by id, in the order the hub offers them (built-ins first). A road's {@code paletteId} names one;
 *       the server's are loaded when a world opens and sent to each player, so both sides plan with the same ones.</li>
 *   <li><b>Snapshots:</b> by snapshot id ({@link Palette#snapshotId}): every current palette's, the provisional palette
 *       Stratos used before R-9, and on the server the world's ({@code <world>/aurelia_roads/palettes/}). A road's record names
 *       the snapshot it was built with, so the server can plan it exactly as placed ({@link #pinned}).</li>
 * </ul>
 */
public final class Palettes {
    /** Stratos's own palettes only: what a planner uses before the server's arrive, and in tests. */
    public static final Palettes BUILT_IN = of(BuiltInPalettes.all(), List.of(BuiltInPalettes.provisional()));

    private final Map<String, Palette> current;
    private final Map<String, Palette> snapshots;

    private Palettes(Map<String, Palette> current, Map<String, Palette> snapshots) {
        this.current = current;
        this.snapshots = snapshots;
    }

    /** {@code current} (ids distinct, in the hub's order) with their snapshots, and {@code snapshots} besides. */
    public static Palettes of(List<Palette> current, Collection<Palette> snapshots) {
        Map<String, Palette> byId = new LinkedHashMap<>();
        Map<String, Palette> bySnapshot = new LinkedHashMap<>();
        for (Palette palette : current) {
            if (byId.putIfAbsent(palette.id(), palette) != null) {
                throw new IllegalArgumentException("Two palettes have the id " + palette.id());
            }
            bySnapshot.put(palette.snapshotId(), palette);
        }
        snapshots.forEach(palette -> bySnapshot.putIfAbsent(palette.snapshotId(), palette));
        return new Palettes(byId, bySnapshot);
    }

    /** The current palette with id {@code id}. */
    public Optional<Palette> byId(String id) {
        return Optional.ofNullable(this.current.get(id));
    }

    /** The current palettes, in the hub's order. */
    public List<Palette> all() {
        return List.copyOf(this.current.values());
    }

    /** The palette snapshot {@code snapshotId} names, if it is known here. */
    public Optional<Palette> snapshot(String snapshotId) {
        return Optional.ofNullable(this.snapshots.get(snapshotId));
    }

    /** Every snapshot known here. */
    public Collection<Palette> snapshots() {
        return List.copyOf(this.snapshots.values());
    }

    /** These palettes with {@code more} snapshots known too (the world's). */
    public Palettes withSnapshots(Collection<Palette> more) {
        Map<String, Palette> bySnapshot = new LinkedHashMap<>(this.snapshots);
        more.forEach(palette -> bySnapshot.putIfAbsent(palette.snapshotId(), palette));
        return new Palettes(this.current, bySnapshot);
    }

    /**
     * These palettes with {@code snapshot} standing for its id: a road planned exactly as it was placed (R-7's "as
     * placed", from its record's snapshot), even where the palette with that id has changed or gone since.
     */
    public Palettes pinned(Palette snapshot) {
        Map<String, Palette> byId = new LinkedHashMap<>(this.current);
        byId.put(snapshot.id(), snapshot);
        Map<String, Palette> bySnapshot = new LinkedHashMap<>(this.snapshots);
        bySnapshot.putIfAbsent(snapshot.snapshotId(), snapshot);
        return new Palettes(byId, bySnapshot);
    }

    /** The ids of the current palettes after {@code id} in the hub's order, wrapping round (the hub's palette button). */
    public String next(String id) {
        List<String> ids = new ArrayList<>(this.current.keySet());
        if (ids.isEmpty()) {
            return id;
        }
        int at = ids.indexOf(id);
        return ids.get((at + 1) % ids.size());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Palettes palettes && palettes.current.equals(this.current) && palettes.snapshots.keySet().equals(this.snapshots.keySet());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.current, this.snapshots.keySet());
    }

    @Override
    public String toString() {
        return "Palettes" + this.current.keySet();
    }
}
