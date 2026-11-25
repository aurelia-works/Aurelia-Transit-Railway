/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.ops.BlockPlan;

/** {@code undo/index.json} (world-data-format §6; task F-5). */
class UndoIndexJsonTest {
    static final UUID A = new UUID(7, 1);
    static final UUID B = new UUID(7, 2);
    static final UUID C = new UUID(7, 3);

    static final UndoIndex INDEX = new UndoIndex(
            List.of(new UndoStore.Saved(A, "alice", 3465, 1, 2856, 4120, List.of(new BlockPlan.SectionPos(0, 3, -1), new BlockPlan.SectionPos(0, 4, -1))),
                    new UndoStore.Saved(B, "alice", 3465, 3, 10, 99, List.of(new BlockPlan.SectionPos(-2, 4, 0))),
                    new UndoStore.Saved(C, "bob", 3465, 1, 5, 40, List.of(new BlockPlan.SectionPos(31, 4, 0)))),
            List.of(new UndoIndex.History("minecraft:overworld", "alice", List.of(A), List.of(B)),
                    new UndoIndex.History("minecraft:the_nether", "bob", List.of(C), List.of())));

    static UndoIndex read(String text) {
        try {
            return UndoIndexJson.read(text);
        } catch (JsonFormatException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    @Test
    void anIndexRoundTrips() {
        for (UndoIndex index : List.of(INDEX, UndoIndex.EMPTY)) {
            String json = UndoIndexJson.write(index);
            assertEquals(index, read(json));
            assertEquals(json, UndoIndexJson.write(read(json)));
        }
    }

    @Test
    void theJournalsRunsAreListedOnlyWhenThereAreSome() {
        assertFalse(UndoIndexJson.write(INDEX).contains("journal"), "an index without runs is as before F-2+");
        UndoIndex withRuns = new UndoIndex(INDEX.operations(), INDEX.histories(), List.of(new UUID(3, 1), new UUID(3, 2)));
        String json = UndoIndexJson.write(withRuns);
        assertTrue(json.contains("\"journal\": ["), json);
        assertEquals(withRuns, read(json));
        assertEquals(List.of(), read(UndoIndexJson.write(INDEX)).journal(), "a missing list reads as none");
        refused("index.journal[1]", j -> {
            JsonArray runs = new JsonArray();
            runs.add(A.toString());
            runs.add(A.toString());
            j.add("journal", runs);
        });
    }

    private static void refused(String named, Consumer<JsonObject> change) {
        JsonObject json = JsonParser.parseString(UndoIndexJson.write(INDEX)).getAsJsonObject();
        change.accept(json);
        JsonFormatException e = assertThrows(JsonFormatException.class, () -> UndoIndexJson.read(json.toString()), named);
        assertTrue(e.getMessage().contains(named), "'" + e.getMessage() + "' names " + named);
    }

    private static JsonObject operation(JsonObject json, int i) {
        return json.getAsJsonArray("operations").get(i).getAsJsonObject();
    }

    private static JsonObject history(JsonObject json, int i) {
        return json.getAsJsonArray("histories").get(i).getAsJsonObject();
    }

    @Test
    void aBrokenIndexIsRefusedByName() {
        refused("index.histories", json -> json.remove("histories"));
        refused("index.extra", json -> json.add("extra", new JsonArray()));
        refused("index.operations[1]", json -> operation(json, 1).addProperty("generation", 0));
        refused("index.operations[0].sections[1]", json -> operation(json, 0).getAsJsonArray("sections").get(1).getAsJsonArray().remove(0));
        refused("index.operations[2].id", json -> operation(json, 2).addProperty("id", A.toString()));
        refused("index.histories[1].undo[0]", json -> history(json, 1).getAsJsonArray("undo").set(0, new JsonPrimitive(new UUID(9, 9)
                .toString())));
        refused("index.histories[1].undo[0]", json -> history(json, 1).getAsJsonArray("undo").set(0, new JsonPrimitive(A.toString())));
        refused("index.histories[0].owner", json -> history(json, 0).addProperty("owner", 5));
    }
}
