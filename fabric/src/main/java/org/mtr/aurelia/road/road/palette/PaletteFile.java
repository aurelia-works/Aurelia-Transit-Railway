/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road.palette;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;

/**
 * A palette file as written (spec roads-v1 §8; task R-9): each role's candidate families, in order. The first family
 * whose blocks the game has wins ({@link Palette#resolve}); the last is always vanilla, so a road can always be built.
 * <pre>
 * {
 *   "format": 1,
 *   "id": "vanilla",
 *   "name": "Vanilla",                  (optional; built-in palettes are named in the lang file)
 *   "dataVersion": 3465,
 *   "roles": {
 *     "surface": [ { "full": "minecraft:polished_deepslate", "half": "minecraft:polished_deepslate_slab[type=bottom,waterlogged=false]" } ],
 *     "foundation": [ { "full": "minecraft:stone" } ],
 *     ...
 *   }
 * }
 * </pre>
 * A family may also say {@code "raise": 1} (eighths of a block above the road, B-1's footway and kerb) and, for the kerb,
 * {@code "edge": true} (its {@code facing} and {@code shape} set to face the road; the gully and dropped kerb roles too,
 * with {@code facing} only), and, from B-2, for a marking,
 * {@code "line": true} (its {@code chord} set where the line crosses each block); see {@link Family}.
 * Read strictly like Stratos's other files: an unknown field or role, a missing required role, an empty list, a partial
 * block or a raise for the foundation or the marker, an edge on another role than the kerb, gully or dropped kerb, a line on another role than
 * a marking, or (in a file people write,
 * {@link #read}) a last candidate that isn't all {@code minecraft:} blocks refuses the file. A snapshot ({@link Palette#snapshotJson}) is the same format with one
 * candidate per role, the one the game chose, and no name.
 */
public record PaletteFile(String id, Optional<String> name, int dataVersion, Map<PaletteRole, List<Family>> roles) {
    /** The palette file format. */
    public static final int FORMAT = 1;
    /** Minecraft 1.20.1's DataVersion: palettes name states of that version. */
    public static final int DATA_VERSION = 3465;
    /** Palette ids: lowercase letters, digits, {@code _} and {@code -}, at most 32 (they name files and snapshots). */
    public static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,32}");
    /** The longest name, in characters. */
    public static final int MAX_NAME = 32;

    public PaletteFile {
        Objects.requireNonNull(id, "id");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("id must be 1 to 32 lowercase letters, digits, _ or -: '" + id + "'");
        }
        Objects.requireNonNull(name, "name");
        if (name.isPresent() && (name.get().isBlank() || name.get().length() > MAX_NAME || name.get().chars().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("name must be 1 to " + MAX_NAME + " characters on one line");
        }
        if (dataVersion != DATA_VERSION) {
            throw new IllegalArgumentException("dataVersion is " + dataVersion + "; Stratos for Minecraft 1.20.1 reads " + DATA_VERSION);
        }
        Map<PaletteRole, List<Family>> copy = new EnumMap<>(PaletteRole.class);
        for (Map.Entry<PaletteRole, List<Family>> entry : roles.entrySet()) {
            PaletteRole role = entry.getKey();
            List<Family> families = List.copyOf(entry.getValue());
            if (families.isEmpty()) {
                throw new IllegalArgumentException("roles." + role.id() + " has no blocks");
            }
            for (Family family : families) {
                if (!role.surface() && (family.steps() > 1 || family.raise() > 0)) {
                    throw new IllegalArgumentException("roles." + role.id() + " is a full block only: it has no half, layers or raise");
                }
                if (family.edge() && role != PaletteRole.KERB && role != PaletteRole.GULLY && role != PaletteRole.DROPPED_KERB) {
                    throw new IllegalArgumentException("roles." + role.id() + " can't have edge: only the kerb, gully and dropped kerb turn to face the road");
                }
                if (family.edge() && role == PaletteRole.KERB && !family.hasProperty(Family.EDGE_PROPERTIES.get(1))) {
                    throw new IllegalArgumentException("roles.kerb: an edge kerb's states have " + String.join(" and ", Family.EDGE_PROPERTIES) + ": " + family.full());
                }
                if (family.line() && role != PaletteRole.MARKING_WHITE && role != PaletteRole.MARKING_YELLOW) {
                    throw new IllegalArgumentException("roles." + role.id() + " can't have line: only the markings are painted along a chord");
                }
            }
            copy.put(role, families);
        }
        for (PaletteRole role : PaletteRole.values()) {
            if (role.required() && !copy.containsKey(role)) {
                throw new IllegalArgumentException("roles." + role.id() + " is missing");
            }
        }
        roles = Collections.unmodifiableMap(copy);
    }

    /**
     * The file {@code text}, named {@code where} in messages (its file name), or why it can't be used. Each role's last
     * candidate must be vanilla (all {@code minecraft:} blocks), so a road can always be built.
     */
    public static PaletteFile read(String text, String where) throws PaletteException {
        PaletteFile file = readAny(text, where);
        for (Map.Entry<PaletteRole, List<Family>> entry : file.roles().entrySet()) {
            if (!entry.getValue().get(entry.getValue().size() - 1).vanilla()) {
                throw new PaletteException(where + ": roles." + entry.getKey().id()
                        + "'s last candidate must be vanilla (minecraft: blocks), so the road can always be built");
            }
        }
        return file;
    }

    /**
     * As {@link #read}, without the vanilla rule: for palettes already resolved (a snapshot, or the server's palettes as a
     * client receives them), whose one candidate per role is whatever the game chose.
     */
    static PaletteFile readResolved(String text, String where) throws PaletteException {
        return readAny(text, where);
    }

    private static PaletteFile readAny(String text, String where) throws PaletteException {
        try {
            JsonObject object = StrictJson.parseObject(text, where);
            StrictJson.Reader reader = StrictJson.read(object, where);
            int format = reader.integer("format");
            if (format != FORMAT) {
                throw new PaletteException(where + ": format " + format + " isn't one this Stratos reads (" + FORMAT + ")");
            }
            String id = reader.string("id");
            Optional<String> name = reader.optionalString("name");
            int dataVersion = reader.integer("dataVersion");
            JsonObject rolesJson = reader.object("roles");
            reader.done();
            Map<PaletteRole, List<Family>> roles = new EnumMap<>(PaletteRole.class);
            for (Map.Entry<String, JsonElement> entry : rolesJson.entrySet()) {
                String field = where + ".roles." + entry.getKey();
                PaletteRole role = PaletteRole.byId(entry.getKey())
                        .orElseThrow(() -> new PaletteException(field + " isn't a role Stratos knows"));
                if (!entry.getValue().isJsonArray()) {
                    throw new PaletteException(field + " should be a list of candidates");
                }
                List<Family> families = new ArrayList<>();
                int i = 0;
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    String at = field + "[" + i++ + "]";
                    if (!element.isJsonObject()) {
                        throw new PaletteException(at + " should be an object");
                    }
                    StrictJson.Reader familyReader = StrictJson.read(element.getAsJsonObject(), at);
                    String full = familyReader.string("full");
                    Optional<String> half = familyReader.optionalString("half");
                    Optional<String> layers = familyReader.optionalString("layers");
                    int raise = familyReader.optionalInteger("raise").orElse(0);
                    boolean edge = familyReader.has("edge") && familyReader.bool("edge");
                    boolean line = familyReader.has("line") && familyReader.bool("line");
                    familyReader.done();
                    try {
                        families.add(new Family(full, half, layers, raise, edge, line));
                    } catch (IllegalArgumentException e) {
                        throw new PaletteException(at + ": " + e.getMessage());
                    }
                }
                roles.put(role, families);
            }
            return new PaletteFile(id, name, dataVersion, roles);
        } catch (JsonFormatException e) {
            throw new PaletteException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new PaletteException(where + ": " + e.getMessage());
        }
    }

    /** The file as JSON, roles in Stratos's order; with its name if {@code withName}. */
    public JsonObject toJson(boolean withName) {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("id", this.id);
        if (withName) {
            this.name.ifPresent(n -> json.addProperty("name", n));
        }
        json.addProperty("dataVersion", this.dataVersion);
        JsonObject roles = new JsonObject();
        for (Map.Entry<PaletteRole, List<Family>> entry : this.roles.entrySet()) {
            JsonArray families = new JsonArray();
            for (Family family : entry.getValue()) {
                JsonObject f = new JsonObject();
                f.addProperty("full", family.full());
                family.half().ifPresent(half -> f.addProperty("half", half));
                family.layers().ifPresent(layers -> f.addProperty("layers", layers));
                // Left out when unset, so R-9's palettes keep their snapshots.
                if (family.raise() > 0) {
                    f.addProperty("raise", family.raise());
                }
                if (family.edge()) {
                    f.addProperty("edge", true);
                }
                if (family.line()) {
                    f.addProperty("line", true);
                }
                families.add(f);
            }
            roles.add(entry.getKey().id(), families);
        }
        json.add("roles", roles);
        return json;
    }
}
