/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import java.util.EnumMap;
import java.util.Map;

/**
 * The design limits of each road group under each preset (spec roads-v1 §3, ADR-0012). The minimum radius applies
 * to the inner lane ({@code R − d}), not to the reference line. Where UK-faithful has no DMRB value, the game-scale
 * limit applies (owner, 2026-10-04). Vertical-curve K values are not checked in v1.
 */
public final class Presets {
    /** Which set of limits a road uses: game-scale is the default, UK-faithful a toggle. */
    public enum Preset {
        GAME_SCALE,
        UK_FAITHFUL
    }

    /** A group's limits under one preset: the inner lane's minimum radius in blocks, and the steepest grade. */
    public record Limits(int minInnerLaneRadius, Grade maxGrade) {
    }

    private static final Map<RoadGroup, Map<Preset, Limits>> LIMITS = new EnumMap<>(RoadGroup.class);

    static {
        // City street (G-4): 30 and 1:12; UK-faithful 127 (DMRB one step below 180 at 50 km/h; owner, 2026-10-04)
        // with the game-scale grade, as DMRB gives none.
        put(RoadGroup.CITY_STREET, new Limits(30, Grade.oneIn(12)), new Limits(127, Grade.oneIn(12)));
        // Residential and alley (G-4+), roads-v1 §3's values: residential 15 and 1:10, UK-faithful 90 (DMRB two steps
        // below 180 at 50 km/h) with the game-scale grade; alley 8 and 1:6, with no DMRB values at all.
        put(RoadGroup.RESIDENTIAL, new Limits(15, Grade.oneIn(10)), new Limits(90, Grade.oneIn(10)));
        put(RoadGroup.ALLEY, new Limits(8, Grade.oneIn(6)), new Limits(8, Grade.oneIn(6)));
        // The others come with their profiles in Phase 2 (G-4+).
    }

    private Presets() {
    }

    private static void put(RoadGroup group, Limits gameScale, Limits ukFaithful) {
        Map<Preset, Limits> byPreset = new EnumMap<>(Preset.class);
        byPreset.put(Preset.GAME_SCALE, gameScale);
        byPreset.put(Preset.UK_FAITHFUL, ukFaithful);
        LIMITS.put(group, byPreset);
    }

    /** The limits of {@code group} under {@code preset}. Throws for a group whose limits aren't defined yet. */
    public static Limits limits(RoadGroup group, Preset preset) {
        Map<Preset, Limits> byPreset = LIMITS.get(group);
        if (byPreset == null) {
            throw new IllegalArgumentException("No preset limits for " + group + " yet: they come with its profiles (task G-4+)");
        }
        return byPreset.get(preset);
    }
}
