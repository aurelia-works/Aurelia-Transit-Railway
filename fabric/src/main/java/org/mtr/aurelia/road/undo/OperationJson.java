/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;

/**
 * An {@link OperationRecord}'s JSON form (spec world-data-format §6; task G-9), {@code formatVersion} 1. Written field by
 * field, so the format changes only when this class does; read strictly ({@link StrictJson}). F-5 keeps one file per
 * operation, {@code operations/records/<id>.json}.
 *
 * <pre>{@code
 * {
 *   "id": "…", "dimension": "minecraft:overworld", "module": "roads", "tool": "STRAIGHT", "params": { … the module's own JSON … },
 *   "planHash": "<64 hex>", "coreVersion": "0.1.0", "author": "<player UUID>", "time": "2026-10-08T10:00:00Z",
 *   "status": "APPLIED", "undoAvailable": true,
 *   "sections": [ { "x": 0, "y": 4, "z": 0, "hash": "<64 hex>" } ],
 *   "sideEffects": { "planned": [ { "kind": "trackRail", "a": [x, y, z], "aFacing": 0, "b": [x, y, z], "bFacing": 0,
 *                                   "speedFromA": 40, "speedFromB": 0, "shape": "QUADRATIC", "radius": 0.0 } ],
 *                    "owned": [ … ] }
 * }
 * }</pre>
 * From R-7 {@code sideEffects} may also have {@code "inherited"} and {@code "removed"} (a rebuild's or delete's), each
 * written only when it isn't empty and read as empty when missing, so every record before R-7 reads and writes as it did.
 *
 * <p>From R-9 a record may have {@code "palettes": ["vanilla@1a2b3c4d5e6f"]} after {@code params}: the snapshots of the
 * palettes its plan used (world-data-format §2). Written only when not empty and read as empty when missing, so every
 * record before R-9 reads and writes as it did ({@code formatVersion} stays 1; owner, 2026-10-10).
 */
public final class OperationJson {
    private OperationJson() {
    }

    /** {@code record} as JSON text. */
    public static String write(OperationRecord record) {
        JsonObject json = new JsonObject();
        json.addProperty("id", record.id().toString());
        json.addProperty("dimension", record.dimension());
        json.addProperty("module", record.module());
        json.addProperty("tool", record.tool());
        try {
            json.add("params", StrictJson.parseObject(record.params(), "params"));
        } catch (JsonFormatException e) {
            throw new IllegalArgumentException("The record's parameters aren't a JSON object: " + e.getMessage(), e);
        }
        if (!record.palettes().isEmpty()) {
            JsonArray palettes = new JsonArray();
            record.palettes().forEach(palettes::add);
            json.add("palettes", palettes);
        }
        json.addProperty("planHash", record.planHash());
        json.addProperty("coreVersion", record.coreVersion());
        json.addProperty("author", record.author());
        json.addProperty("time", record.time().toString());
        json.addProperty("status", record.status().name());
        json.addProperty("undoAvailable", record.undoAvailable());
        JsonArray sections = new JsonArray();
        for (OperationRecord.SectionHash section : record.sections()) {
            JsonObject s = new JsonObject();
            s.addProperty("x", section.x());
            s.addProperty("y", section.y());
            s.addProperty("z", section.z());
            s.addProperty("hash", section.hash());
            sections.add(s);
        }
        json.add("sections", sections);
        JsonObject sideEffects = new JsonObject();
        sideEffects.add("planned", sideEffects(record.planned()));
        sideEffects.add("owned", sideEffects(record.owned()));
        if (!record.inherited().isEmpty()) {
            sideEffects.add("inherited", sideEffects(record.inherited()));
        }
        if (!record.removed().isEmpty()) {
            sideEffects.add("removed", sideEffects(record.removed()));
        }
        json.add("sideEffects", sideEffects);
        return StrictJson.write(json);
    }

    private static JsonArray sideEffects(List<SideEffect> effects) {
        JsonArray array = new JsonArray();
        for (SideEffect effect : effects) {
            JsonObject e = new JsonObject();
            // Java 17 has no pattern switch; each new kind of side effect adds a branch here and in the reader.
            if (effect instanceof SideEffect.TrackRail rail) {
                e.addProperty("kind", "trackRail");
                e.add("a", position(rail.ax(), rail.ay(), rail.az()));
                e.addProperty("aFacing", rail.aFacing());
                e.add("b", position(rail.bx(), rail.by(), rail.bz()));
                e.addProperty("bFacing", rail.bFacing());
                e.addProperty("speedFromA", rail.speedFromA());
                e.addProperty("speedFromB", rail.speedFromB());
                e.addProperty("shape", rail.shape().name());
                e.addProperty("radius", rail.radius());
            } else {
                throw new IllegalArgumentException("No JSON form for " + effect);
            }
            array.add(e);
        }
        return array;
    }

    private static JsonArray position(int x, int y, int z) {
        JsonArray position = new JsonArray();
        position.add(x);
        position.add(y);
        position.add(z);
        return position;
    }

    /** The record in {@code text}, or why it can't be read (naming the field). */
    public static OperationRecord read(String text) throws JsonFormatException {
        StrictJson.Reader json = StrictJson.read(StrictJson.parseObject(text, "the operation record"), "operation");
        UUID id = uuid(json.string("id"), "operation.id");
        String dimension = json.string("dimension");
        String module = json.string("module");
        String tool = json.string("tool");
        String params = StrictJson.compact(json.object("params"));
        List<String> palettes = json.has("palettes") ? json.strings("palettes") : List.of();
        String planHash = json.string("planHash");
        String coreVersion = json.string("coreVersion");
        String author = json.string("author");
        Instant time;
        try {
            time = Instant.parse(json.string("time"));
        } catch (DateTimeParseException e) {
            throw new JsonFormatException("operation.time isn't a UTC time such as 2026-10-08T10:00:00Z");
        }
        OperationRecord.Status status = json.constant("status", OperationRecord.Status.class);
        boolean undoAvailable = json.bool("undoAvailable");
        List<OperationRecord.SectionHash> sections = new ArrayList<>();
        int i = 0;
        for (JsonObject s : json.objects("sections")) {
            StrictJson.Reader section = StrictJson.read(s, "operation.sections[" + i++ + "]");
            sections.add(new OperationRecord.SectionHash(section.integer("x"), section.integer("y"), section.integer("z"), section.string("hash")));
            section.done();
        }
        StrictJson.Reader sideEffects = StrictJson.read(json.object("sideEffects"), "operation.sideEffects");
        List<SideEffect> planned = sideEffects(sideEffects, "planned");
        List<SideEffect> owned = sideEffects(sideEffects, "owned");
        List<SideEffect> inherited = sideEffects.has("inherited") ? sideEffects(sideEffects, "inherited") : List.of();
        List<SideEffect> removed = sideEffects.has("removed") ? sideEffects(sideEffects, "removed") : List.of();
        sideEffects.done();
        json.done();
        return new OperationRecord(id, dimension, module, tool, params, planHash, coreVersion, author, time, status, undoAvailable, sections, planned, owned,
                inherited, removed, palettes);
    }

    private static List<SideEffect> sideEffects(StrictJson.Reader parent, String key) throws JsonFormatException {
        List<SideEffect> effects = new ArrayList<>();
        int i = 0;
        for (JsonObject e : parent.objects(key)) {
            String where = "operation.sideEffects." + key + "[" + i++ + "]";
            StrictJson.Reader effect = StrictJson.read(e, where);
            String kind = effect.string("kind");
            if (!kind.equals("trackRail")) {
                throw new JsonFormatException(where + ".kind can't be " + kind);
            }
            int[] a = effect.integers("a", 3);
            int aFacing = effect.integer("aFacing");
            int[] b = effect.integers("b", 3);
            int bFacing = effect.integer("bFacing");
            int speedFromA = effect.integer("speedFromA");
            int speedFromB = effect.integer("speedFromB");
            SideEffect.TrackRail.Shape shape = effect.constant("shape", SideEffect.TrackRail.Shape.class);
            double radius = effect.number("radius");
            effect.done();
            try {
                effects.add(new SideEffect.TrackRail(a[0], a[1], a[2], aFacing, b[0], b[1], b[2], bFacing, speedFromA, speedFromB, shape, radius));
            } catch (IllegalArgumentException ex) {
                throw new JsonFormatException(where + " isn't a rail: " + ex.getMessage());
            }
        }
        return effects;
    }

    private static UUID uuid(String text, String where) throws JsonFormatException {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new JsonFormatException(where + " isn't a UUID");
        }
    }
}
