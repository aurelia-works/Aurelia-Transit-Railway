/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.road.LineMarking;
import org.mtr.aurelia.road.road.raster.BlockPalette;
import org.mtr.aurelia.road.road.raster.Column;

/**
 * A palette as this game resolved it (spec roads-v1 §8; task R-9): one family per role, chosen from the file's
 * candidates ({@link #resolve}), every state written as the game writes it. Roads are planned with it.
 * <ul>
 *   <li><b>Steps:</b> the coarsest of its surface roles' families ({@link Family#steps}): full blocks only anywhere makes
 *       the whole road climb in whole blocks; a role with slabs only, in halves; layers everywhere, in eighths. So every
 *       band of a road stands on the same grid.</li>
 *   <li><b>Snapshot:</b> its {@link #snapshotJson} (the file format with one candidate per role and no name) identifies it
 *       exactly; {@link #snapshotId} is {@code <id>@<the first 12 hex digits of its SHA-256>}, the name a world keeps it
 *       under (world-data-format §2) and a road's record names.</li>
 * </ul>
 */
public final class Palette implements BlockPalette {
    /** Hex digits of the SHA-256 in a snapshot id. */
    public static final int SNAPSHOT_HEX = 12;

    private final String id;
    private final Optional<String> name;
    private final int dataVersion;
    private final Map<PaletteRole, Family> families;
    private final int steps;
    private final String snapshotJson;
    private final String snapshotId;

    private Palette(String id, Optional<String> name, int dataVersion, Map<PaletteRole, Family> families) {
        this.id = id;
        this.name = name;
        this.dataVersion = dataVersion;
        this.families = Collections.unmodifiableMap(new EnumMap<>(families));
        int steps = 8;
        for (Map.Entry<PaletteRole, Family> entry : this.families.entrySet()) {
            if (entry.getKey().surface()) {
                steps = StrictMath.min(steps, entry.getValue().steps());
            }
        }
        this.steps = steps;
        this.snapshotJson = StrictJson.compact(toFile(false).toJson(false));
        this.snapshotId = id + "@" + sha256(this.snapshotJson).substring(0, SNAPSHOT_HEX);
    }

    /**
     * Resolves {@code file} with what this game knows ({@code lookup}): per role, the first family whose every block it
     * has, with its states written as the game writes them. A state the game can't read refuses the file, naming it.
     */
    public static Palette resolve(PaletteFile file, BlockLookup lookup) throws PaletteException {
        Map<PaletteRole, Family> chosen = new EnumMap<>(PaletteRole.class);
        for (Map.Entry<PaletteRole, List<Family>> entry : file.roles().entrySet()) {
            Family found = null;
            for (Family family : entry.getValue()) {
                if (family.blocks().stream().allMatch(lookup::has)) {
                    found = family;
                    break;
                }
            }
            if (found == null) {
                // The last candidate is vanilla, so only a block the game lacks under that name gets here.
                throw new PaletteException(file.id() + ": roles." + entry.getKey().id() + " has no candidate whose blocks this game has (the vanilla one names "
                        + entry.getValue().get(entry.getValue().size() - 1).blocks() + ")");
            }
            try {
                chosen.put(entry.getKey(), found.canonical(lookup::canonical));
            } catch (IllegalArgumentException e) {
                throw new PaletteException(file.id() + ": roles." + entry.getKey().id() + ": " + e.getMessage());
            }
        }
        return new Palette(file.id(), file.name(), file.dataVersion(), chosen);
    }

    /**
     * A palette already resolved, read back from {@link #snapshotJson} or {@link #json} (a world's snapshot, or the
     * server's palettes as a client receives them): its one candidate per role is taken as written.
     */
    public static Palette read(String json, String where) throws PaletteException {
        PaletteFile file = PaletteFile.readResolved(json, where);
        for (Map.Entry<PaletteRole, List<Family>> entry : file.roles().entrySet()) {
            if (entry.getValue().size() != 1) {
                throw new PaletteException(where + ": roles." + entry.getKey().id() + " should have one candidate in a resolved palette");
            }
        }
        return resolve(file, BlockLookup.RESOLVED);
    }

    /** Its id, such as {@code vanilla}: what a road's parameters name ({@code RoadPlanParams.paletteId}). */
    public String id() {
        return this.id;
    }

    /** The name its file gives, if any (built-in palettes are named in the lang file). */
    public Optional<String> name() {
        return this.name;
    }

    /** The family chosen for {@code role}, if the palette gives it. */
    public Optional<Family> family(PaletteRole role) {
        return Optional.ofNullable(this.families.get(role));
    }

    /** Every role it gives and the family chosen for it, in Stratos's order of roles. */
    public Map<PaletteRole, Family> families() {
        return this.families;
    }

    /** {@code <id>@<12 hex digits>}: the exact palette, as a world's snapshot and a road's record name it. */
    public String snapshotId() {
        return this.snapshotId;
    }

    /** The snapshot: the file format with one candidate per role, compact, without a name. */
    public String snapshotJson() {
        return this.snapshotJson;
    }

    /** As {@link #snapshotJson}, with its name: what the server sends a client ({@code Palettes}). */
    public String json() {
        return StrictJson.compact(toFile(true).toJson(true));
    }

    @Override
    public int dataVersion() {
        return this.dataVersion;
    }

    @Override
    public int steps() {
        return this.steps;
    }

    @Override
    public String state(Column column) {
        Family family = familyFor(column);
        return family.turned(family.painted(family.full(), column.chord()), column.kerb());
    }

    @Override
    public String partial(Column column, int part) {
        Family family = familyFor(column);
        return family.turned(family.painted(family.partial(part, this.steps), column.chord()), column.kerb());
    }

    @Override
    public String under(Column column) {
        return column.chord().isPresent() ? state(column.painted(Optional.empty(), OptionalInt.empty())) : state(column);
    }

    @Override
    public boolean lines(LineMarking.Colour colour) {
        return PaletteRole.byId(colour.paletteRole()).flatMap(this::family).map(Family::line).orElse(false);
    }

    /**
     * The column's family's {@link Family#raise} in this palette's steps; 0 where the steps can't show it (a family raised
     * an eighth, in a palette of half blocks), so the footway then stands level.
     */
    @Override
    public int raise(Column column) {
        int raise = familyFor(column).raise();
        return raise * this.steps % Family.MAX_RAISE == 0 ? raise * this.steps / Family.MAX_RAISE : 0;
    }

    @Override
    public boolean kerbEdges() {
        return family(PaletteRole.KERB).map(Family::edge).orElse(false);
    }

    @Override
    public boolean gullies() {
        return kerbEdges() && family(PaletteRole.GULLY).map(Family::edge).orElse(false);
    }

    @Override
    public boolean droppedKerbs() {
        return kerbEdges() && family(PaletteRole.DROPPED_KERB).map(Family::edge).orElse(false);
    }

    @Override
    public String foundation() {
        return require(PaletteRole.FOUNDATION).full();
    }

    @Override
    public String nodeMarker() {
        return require(PaletteRole.NODE_MARKER).full();
    }

    private Family familyFor(Column column) {
        return require(PaletteRole.of(column));
    }

    private Family require(PaletteRole role) {
        Family family = this.families.get(role);
        if (family == null) {
            // Only a layout of a later phase (verge, barrier...) can ask for a role a palette may leave out.
            throw new IllegalArgumentException("The palette " + this.id + " has no " + role.id());
        }
        return family;
    }

    private PaletteFile toFile(boolean withName) {
        Map<PaletteRole, List<Family>> roles = new EnumMap<>(PaletteRole.class);
        this.families.forEach((role, family) -> roles.put(role, List.of(family)));
        return new PaletteFile(this.id, withName ? this.name : Optional.empty(), this.dataVersion, roles);
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java has SHA-256", e);
        }
    }

    /** Two palettes are equal when they are the same snapshot with the same name. */
    @Override
    public boolean equals(Object other) {
        return other instanceof Palette palette && palette.snapshotId.equals(this.snapshotId) && palette.name.equals(this.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.snapshotId, this.name);
    }

    @Override
    public String toString() {
        return this.snapshotId;
    }
}
