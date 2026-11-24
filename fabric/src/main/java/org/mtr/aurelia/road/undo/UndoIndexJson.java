/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.ops.BlockPlan;

/**
 * {@code undo/index.json} (world-data-format §6; task F-5), {@code formatVersion} 1. Written field by field, read
 * strictly ({@link StrictJson}).
 *
 * <pre>{@code
 * {
 *   "operations": [ { "id": "…", "owner": "<player UUID>", "dataVersion": 3465, "generation": 1, "blocks": 2856,
 *                     "bytes": 4120, "sections": [ [0, 3, -1], [0, 4, -1] ] } ],
 *   "histories": [ { "dimension": "minecraft:overworld", "owner": "<player UUID>", "undo": [ "<newest>", … ], "redo": [] } ],
 *   "journal": [ "<run id>", … ]
 * }
 * }</pre>
 * Every id in a history must be one of the operations, and each operation may appear in one history only.
 *
 * <p><b>From F-2+</b> {@code journal} lists the journal's runs whose results the index holds (inside {@code formatVersion}
 * 1, owner 2026-10-09). It is written only when there are some, so an index without runs is byte for byte as before,
 * and a missing field reads as none (F-5's fixture still loads).
 */
public final class UndoIndexJson {
    private UndoIndexJson() {
    }

    public static String write(UndoIndex index) {
        JsonObject json = new JsonObject();
        JsonArray operations = new JsonArray();
        for (UndoStore.Saved saved : index.operations()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", saved.id().toString());
            o.addProperty("owner", saved.owner());
            o.addProperty("dataVersion", saved.dataVersion());
            o.addProperty("generation", saved.generation());
            o.addProperty("blocks", saved.blocks());
            o.addProperty("bytes", saved.bytes());
            JsonArray sections = new JsonArray();
            for (BlockPlan.SectionPos pos : saved.sections()) {
                JsonArray p = new JsonArray();
                p.add(pos.x());
                p.add(pos.y());
                p.add(pos.z());
                sections.add(p);
            }
            o.add("sections", sections);
            operations.add(o);
        }
        json.add("operations", operations);
        JsonArray histories = new JsonArray();
        for (UndoIndex.History history : index.histories()) {
            JsonObject h = new JsonObject();
            h.addProperty("dimension", history.dimension());
            h.addProperty("owner", history.owner());
            h.add("undo", ids(history.undo()));
            h.add("redo", ids(history.redo()));
            histories.add(h);
        }
        json.add("histories", histories);
        if (!index.journal().isEmpty()) {
            json.add("journal", ids(index.journal()));
        }
        return StrictJson.write(json);
    }

    private static JsonArray ids(List<UUID> ids) {
        JsonArray array = new JsonArray();
        ids.forEach(id -> array.add(id.toString()));
        return array;
    }

    /** The index in {@code text}, or why it can't be read (naming the field). */
    public static UndoIndex read(String text) throws JsonFormatException {
        StrictJson.Reader json = StrictJson.read(StrictJson.parseObject(text, "the undo index"), "index");
        List<UndoStore.Saved> operations = new ArrayList<>();
        Set<UUID> known = new HashSet<>();
        int i = 0;
        for (JsonObject o : json.objects("operations")) {
            String where = "index.operations[" + i++ + "]";
            StrictJson.Reader op = StrictJson.read(o, where);
            UUID id = uuid(op.string("id"), where + ".id");
            String owner = op.string("owner");
            int dataVersion = op.integer("dataVersion");
            int generation = op.integer("generation");
            int blocks = op.integer("blocks");
            long bytes = op.integer("bytes");
            List<BlockPlan.SectionPos> sections = new ArrayList<>();
            for (int[] pos : op.integerLists("sections", 3)) {
                sections.add(new BlockPlan.SectionPos(pos[0], pos[1], pos[2]));
            }
            op.done();
            if (!known.add(id)) {
                throw new JsonFormatException(where + ".id " + id + " is listed twice");
            }
            try {
                operations.add(new UndoStore.Saved(id, owner, dataVersion, generation, blocks, bytes, sections));
            } catch (IllegalArgumentException e) {
                throw new JsonFormatException(where + ": " + e.getMessage());
            }
        }
        List<UndoIndex.History> histories = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        i = 0;
        for (JsonObject h : json.objects("histories")) {
            String where = "index.histories[" + i++ + "]";
            StrictJson.Reader history = StrictJson.read(h, where);
            String dimension = history.string("dimension");
            String owner = history.string("owner");
            List<UUID> undo = historyIds(history, "undo", where, known, used);
            List<UUID> redo = historyIds(history, "redo", where, known, used);
            history.done();
            histories.add(new UndoIndex.History(dimension, owner, undo, redo));
        }
        List<UUID> journal = new ArrayList<>();
        if (json.has("journal")) {
            i = 0;
            Set<UUID> runs = new HashSet<>();
            for (String runText : json.strings("journal")) {
                String field = "index.journal[" + i++ + "]";
                UUID run = uuid(runText, field);
                if (!runs.add(run)) {
                    throw new JsonFormatException(field + " " + run + " is listed twice");
                }
                journal.add(run);
            }
        }
        json.done();
        return new UndoIndex(operations, histories, journal);
    }

    private static List<UUID> historyIds(StrictJson.Reader reader, String key, String where, Set<UUID> known, Set<UUID> used)
            throws JsonFormatException {
        List<UUID> ids = new ArrayList<>();
        int i = 0;
        for (String text : reader.strings(key)) {
            String field = where + "." + key + "[" + i++ + "]";
            UUID id = uuid(text, field);
            if (!known.contains(id)) {
                throw new JsonFormatException(field + " " + id + " isn't one of the index's operations");
            }
            if (!used.add(id)) {
                throw new JsonFormatException(field + " " + id + " is in a history twice");
            }
            ids.add(id);
        }
        return ids;
    }

    private static UUID uuid(String text, String where) throws JsonFormatException {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new JsonFormatException(where + " isn't a UUID");
        }
    }
}
