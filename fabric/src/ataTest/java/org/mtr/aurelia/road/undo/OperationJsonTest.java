/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.undo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.road.Presets;
import org.mtr.aurelia.road.road.RoadPlanParams;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadsModule;

/**
 * Operation records as JSON under {@code formatVersion} 1 (spec world-data-format §6; task G-9). The golden file
 * {@code fabric/src/ataTest/resources/road/formatVersion-1/operation.json} pins the format: it must never change inside version 1,
 * and F-5's migrations start from it. If it is missing, the test writes it and fails, so a new one is always reviewed.
 * T-2 added each {@code trackRail}'s {@code shape} and {@code radius} once, before any world could hold a rail (no plan
 * had track before T-2), as F-5 did for {@code dimension}.
 */
class OperationJsonTest {
    private static final Path GOLDEN_FILE = Path.of("src", "ataTest", "resources", "road", "formatVersion-1", "operation.json");

    private static final RoadPlanParams PARAMS = new RoadPlanParams(RoadPlanParams.Tool.CURVE, "city-17", Presets.Preset.GAME_SCALE, "vanilla",
            RoadPlanParams.DrivingSide.LHT,
            List.of(new End(0, 64, 0, Heading.E, Optional.of(new UUID(1, 2)), OptionalInt.of(1)), End.at(40, 64, 40, Heading.S)),
            new RoadPlanParams.Vertical(RoadPlanParams.Vertical.Shape.TWO_RADII, OptionalDouble.of(120.5)), RoadPlanParams.NodeAids.WOOL, false,
            Optional.empty(), Optional.empty(), Optional.empty());

    private static final SideEffect RAIL_1 = new SideEffect.TrackRail(1, 65, -3, 2, 40, 65, 36, 4, 40, 0, SideEffect.TrackRail.Shape.TWO_RADII, 120.5);
    private static final SideEffect RAIL_2 = new SideEffect.TrackRail(40, 65, 44, 0, 1, 65, 3, 6, 40, 40, SideEffect.TrackRail.Shape.TWO_RADII, 120.5);

    /** A curve undone while Stratos Transit was missing: its first rail is still in the world, a leftover it owns. */
    static final OperationRecord GOLDEN = new OperationRecord(UUID.fromString("6f1c2a4e-9b7d-4e21-8c3a-5d2f0b9e7a11"), "minecraft:overworld",
            "roads", "CURVE",
            RoadsModule.INSTANCE.paramsToJson(PARAMS), "0f".repeat(32), "0.1.0", "00000000-0000-0000-0000-000000000001",
            Instant.parse("2026-10-08T10:00:00Z"), OperationRecord.Status.UNDONE, true,
            List.of(new OperationRecord.SectionHash(-1, 3, -1, "12".repeat(32)), new OperationRecord.SectionHash(0, 4, 2, "ef".repeat(32))),
            List.of(RAIL_1, RAIL_2), List.of(RAIL_1));

    private static final OperationRecord PLAIN = new OperationRecord(new UUID(0, 9), "minecraft:the_nether", "none", "none", "{}", "", "0.1.0", "gametest",
            Instant.parse("2026-10-08T23:59:59.123456789Z"), OperationRecord.Status.FAILED, false, List.of(), List.of(), List.of());

    private static OperationRecord read(String text) {
        try {
            return OperationJson.read(text);
        } catch (JsonFormatException e) {
            throw new AssertionError("unexpectedly refused: " + e.getMessage(), e);
        }
    }

    @Test
    void everyFieldRoundTripsWithAndWithoutSideEffects() {
        for (OperationRecord record : List.of(GOLDEN, PLAIN)) {
            String json = OperationJson.write(record);
            assertEquals(record, read(json));
            assertEquals(json, OperationJson.write(read(json)));
        }
    }

    @Test
    void aRebuildsInheritedAndRemovedRailsRoundTripAndAreLeftOutWhenEmpty() {
        OperationRecord rebuild = new OperationRecord(new UUID(0, 10), "minecraft:overworld", "roads", "REBUILD", "{\"tool\":\"REBUILD\"}", "0f".repeat(32),
                "0.1.0", "alice", Instant.parse("2026-10-09T10:00:00Z"), OperationRecord.Status.APPLIED, true, List.of(), List.of(RAIL_1, RAIL_2),
                List.of(RAIL_2), List.of(RAIL_1), List.of(RAIL_2));
        String json = OperationJson.write(rebuild);
        assertTrue(json.contains("\"inherited\"") && json.contains("\"removed\""), json);
        assertEquals(rebuild, read(json));
        assertTrue(!OperationJson.write(GOLDEN).contains("\"inherited\"") && !OperationJson.write(GOLDEN).contains("\"removed\""),
                "R-7's lists are written only when they aren't empty: every earlier record reads and writes as before");
    }

    @Test
    void thePalettesARecordNamesRoundTripAndAreLeftOutWhenEmpty() {
        // R-9: the snapshots of the palettes its plan used; formatVersion stays 1 (owner, 2026-10-10).
        OperationRecord road = new OperationRecord(new UUID(0, 11), "minecraft:overworld", "roads", "STRAIGHT", "{\"tool\":\"STRAIGHT\"}", "0e".repeat(32),
                "0.1.0", "alice", Instant.parse("2026-10-10T10:00:00Z"), OperationRecord.Status.APPLIED, true, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of("vanilla@0123456789ab"));
        String json = OperationJson.write(road);
        assertTrue(json.contains("\"palettes\"") && json.contains("\"vanilla@0123456789ab\""), json);
        assertEquals(road, read(json));
        assertTrue(!OperationJson.write(GOLDEN).contains("\"palettes\""), "written only when not empty: every earlier record reads and writes as before");
        assertEquals(List.of(), read(OperationJson.write(GOLDEN)).palettes(), "a record without them names none");
    }

    @Test
    void theGoldenFileReadsBackAndWritesOutByteForByte() throws IOException {
        byte[] written = OperationJson.write(GOLDEN).getBytes(StandardCharsets.UTF_8);
        if (!Files.exists(GOLDEN_FILE)) {
            Files.createDirectories(GOLDEN_FILE.getParent());
            Files.write(GOLDEN_FILE, written);
            fail("Wrote " + GOLDEN_FILE + ": review it, commit it and run the tests again");
        }
        byte[] golden = Files.readAllBytes(GOLDEN_FILE);
        assertEquals(GOLDEN, read(new String(golden, StandardCharsets.UTF_8)));
        assertEquals(new String(golden, StandardCharsets.UTF_8), new String(written, StandardCharsets.UTF_8),
                "formatVersion 1 changed: a format change needs formatVersion 2 and a migration (F-5)");
    }

    @Test
    void theParametersAreTheModulesOwnReadableJson() {
        JsonObject json = JsonParser.parseString(OperationJson.write(GOLDEN)).getAsJsonObject();
        JsonObject params = json.getAsJsonObject("params");
        assertEquals("CURVE", params.get("tool").getAsString());
        assertEquals("WOOL", params.get("nodeAids").getAsString());
        assertEquals("\"time\":\"2026-10-08T10:00:00Z\"", "\"time\":" + json.get("time"));
    }

    /** Edits the golden record's JSON with {@code change} and checks it is refused with a message containing {@code named}. */
    private static void refused(String named, Consumer<JsonObject> change) {
        JsonObject json = JsonParser.parseString(OperationJson.write(GOLDEN)).getAsJsonObject();
        change.accept(json);
        JsonFormatException e = assertThrows(JsonFormatException.class, () -> OperationJson.read(json.toString()), named);
        assertTrue(e.getMessage().contains(named), "'" + e.getMessage() + "' names " + named);
    }

    private static JsonObject firstPlanned(JsonObject json) {
        return json.getAsJsonObject("sideEffects").getAsJsonArray("planned").get(0).getAsJsonObject();
    }

    @Test
    void aMissingUnknownOrWronglyTypedFieldIsRefusedByName() {
        JsonObject good = JsonParser.parseString(OperationJson.write(GOLDEN)).getAsJsonObject();
        for (String key : good.keySet()) {
            refused("operation." + key, json -> json.remove(key));
        }
        refused("operation.colour", json -> json.addProperty("colour", "red"));
        refused("operation.status", json -> json.addProperty("status", 3));
        refused("operation.status", json -> json.addProperty("status", "DONE"));
        refused("operation.undoAvailable", json -> json.addProperty("undoAvailable", "yes"));
        refused("operation.id", json -> json.addProperty("id", "not-a-uuid"));
        refused("operation.time", json -> json.addProperty("time", "yesterday"));
        refused("operation.params", json -> json.addProperty("params", "{}"));
        refused("operation.sections[1].hash", json -> json.getAsJsonArray("sections").get(1).getAsJsonObject().remove("hash"));
        refused("operation.sections[0].y", json -> json.getAsJsonArray("sections").get(0).getAsJsonObject().addProperty("y", 3.5));
        refused("operation.sideEffects.owned", json -> json.getAsJsonObject("sideEffects").remove("owned"));
        refused("operation.sideEffects.extra", json -> json.getAsJsonObject("sideEffects").add("extra", new JsonArray()));
        refused("operation.sideEffects.planned[0].kind", json -> firstPlanned(json).addProperty("kind", "signal"));
        refused("operation.sideEffects.planned[0].aFacing", json -> firstPlanned(json).remove("aFacing"));
        refused("operation.sideEffects.planned[0].b", json -> firstPlanned(json).getAsJsonArray("b").remove(2));
        refused("operation.sideEffects.planned[0]", json -> firstPlanned(json).addProperty("bFacing", 9));
        refused("operation.sideEffects.planned[0].speedFromA", json -> firstPlanned(json).addProperty("speedFromA", "fast"));
        refused("operation.sideEffects.planned[0].shape", json -> firstPlanned(json).remove("shape"));
        refused("operation.sideEffects.planned[0].shape", json -> firstPlanned(json).addProperty("shape", "CABLE"));
        refused("operation.sideEffects.planned[0].radius", json -> firstPlanned(json).addProperty("radius", "big"));
        refused("operation.sideEffects.planned[0]", json -> firstPlanned(json).addProperty("radius", -1));
        assertThrows(JsonFormatException.class, () -> OperationJson.read("[]"));
        assertThrows(JsonFormatException.class, () -> OperationJson.read("{\"id\":"));
    }
}
