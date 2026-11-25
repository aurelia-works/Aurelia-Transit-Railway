/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.road.RoadPlanParams.End;
import org.mtr.aurelia.road.road.RoadPlanParams.Junction;
import org.mtr.aurelia.road.road.RoadPlanParams.Vertical;

/** The roads module's parameters as JSON, for operation records (task G-9). */
class RoadParamsJsonTest {
    private static <E extends Enum<E>> E any(Random random, Class<E> type) {
        E[] values = type.getEnumConstants();
        return values[random.nextInt(values.length)];
    }

    /** An id of 1 to 64 UTF-8 bytes, sometimes with characters JSON or HTML would escape. */
    private static String id(Random random) {
        String[] pieces = {"city", "-", "17", "r", "é", "\"", "<", "&", "\\", "/", " "};
        StringBuilder id = new StringBuilder();
        for (int i = 1 + random.nextInt(8); i > 0; i--) {
            id.append(pieces[random.nextInt(pieces.length)]);
        }
        return id.toString();
    }

    /** From {@code min} to {@code max}, both included (the ranges here fit in an int). */
    private static int between(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    static RoadPlanParams random(Random random) {
        RoadPlanParams.Tool tool = any(random, RoadPlanParams.Tool.class);
        int endCount = tool == RoadPlanParams.Tool.STRAIGHT ? 2 : random.nextInt(RoadPlanParams.MAX_ENDS + 1);
        List<End> ends = new ArrayList<>();
        for (int i = 0; i < endCount; i++) {
            ends.add(new End(between(random, -RoadPlanParams.MAX_XZ, RoadPlanParams.MAX_XZ), between(random, RoadPlanParams.MIN_Y, RoadPlanParams.MAX_Y),
                    between(random, -RoadPlanParams.MAX_XZ, RoadPlanParams.MAX_XZ), any(random, Heading.class),
                    random.nextBoolean() ? Optional.of(new UUID(random.nextLong(), random.nextLong())) : Optional.empty(),
                    random.nextBoolean() ? OptionalInt.of(random.nextInt(Integer.MAX_VALUE)) : OptionalInt.empty()));
        }
        OptionalDouble radius = random.nextBoolean() ? OptionalDouble.of(random.nextInt(4) == 0 ? random.nextInt(500) + 1 : random.nextDouble() * 1e4 + 1e-9)
                : OptionalDouble.empty();
        Optional<Junction> junction = Optional.empty();
        if (random.nextBoolean()) {
            List<String> arms = new ArrayList<>();
            for (int i = random.nextInt(RoadPlanParams.MAX_ARMS + 1); i > 0; i--) {
                arms.add(id(random));
            }
            junction = Optional.of(new Junction(id(random), random.nextInt(Integer.MAX_VALUE), arms));
        }
        return new RoadPlanParams(tool, id(random), any(random, Presets.Preset.class), id(random), any(random, RoadPlanParams.DrivingSide.class), ends,
                new Vertical(any(random, Vertical.Shape.class), radius), any(random, RoadPlanParams.NodeAids.class), random.nextBoolean(), junction,
                random.nextBoolean() ? Optional.of(new UUID(random.nextLong(), random.nextLong())) : Optional.empty(),
                random.nextBoolean() ? Optional.of(any(random, RoadPlanParams.ManualEdits.class)) : Optional.empty(),
                random.nextInt(3) == 0 ? new RoadPlanParams.Dashes(random.nextDouble() * 400 - 200, any(random, RoadPlanParams.Dashes.Anchor.class))
                        : RoadPlanParams.Dashes.FROM_START,
                List.of());
    }

    @Test
    void aThousandRandomParametersRoundTrip() throws JsonFormatException {
        Random random = new Random(9);
        for (int i = 0; i < 1000; i++) {
            RoadPlanParams params = random(random);
            String json = RoadsModule.INSTANCE.paramsToJson(params);
            assertEquals(params, RoadsModule.INSTANCE.paramsFromJson(params.tool().name(), json), json);
            assertEquals(json, RoadsModule.INSTANCE.paramsToJson(RoadsModule.INSTANCE.paramsFromJson(params.tool().name(), json)), "written the same again");
        }
    }

    @Test
    void theJsonNamesEveryFieldAndLeavesOutEmptyOnes() {
        RoadPlanParams simple = RoadPlanParams.straight("city-17", End.at(10, 64, -20, Heading.E), End.at(30, 64, -20, Heading.E));
        assertEquals("{\"tool\":\"STRAIGHT\",\"profileId\":\"city-17\",\"preset\":\"GAME_SCALE\",\"paletteId\":\"vanilla\",\"drivingSide\":\"LHT\","
                + "\"ends\":[{\"x\":10,\"y\":64,\"z\":-20,\"heading\":\"E\"},{\"x\":30,\"y\":64,\"z\":-20,\"heading\":\"E\"}],"
                + "\"vertical\":{\"shape\":\"QUADRATIC\"},\"nodeAids\":\"NONE\",\"overlay\":false}", RoadsModule.INSTANCE.paramsToJson(simple));
    }

    @Test
    void missingUnknownOrWronglyTypedFieldsAreRefusedByName() {
        RoadPlanParams params = RoadPlanParams.straight("city-17", End.at(10, 64, -20, Heading.E), End.at(30, 64, -20, Heading.E));
        JsonObject good = RoadParamsJson.toJson(params);
        for (String key : good.keySet()) {
            JsonObject missing = good.deepCopy();
            missing.remove(key);
            JsonFormatException e = assertThrows(JsonFormatException.class, () -> RoadParamsJson.fromJson(missing), key);
            assertTrue(e.getMessage().contains("params." + key), e.getMessage());
        }
        JsonObject unknown = good.deepCopy();
        unknown.addProperty("colour", "red");
        assertTrue(assertThrows(JsonFormatException.class, () -> RoadParamsJson.fromJson(unknown)).getMessage().contains("params.colour"));

        JsonObject wrongType = good.deepCopy();
        wrongType.addProperty("overlay", "no");
        assertTrue(assertThrows(JsonFormatException.class, () -> RoadParamsJson.fromJson(wrongType)).getMessage().contains("params.overlay"));

        JsonObject badEnd = good.deepCopy();
        badEnd.getAsJsonArray("ends").get(1).getAsJsonObject().addProperty("y", 64.5);
        assertTrue(assertThrows(JsonFormatException.class, () -> RoadParamsJson.fromJson(badEnd)).getMessage().contains("params.ends[1].y"));

        JsonObject badHeading = good.deepCopy();
        badHeading.getAsJsonArray("ends").get(0).getAsJsonObject().addProperty("heading", "UP");
        assertTrue(assertThrows(JsonFormatException.class, () -> RoadParamsJson.fromJson(badHeading)).getMessage().contains("params.ends[0].heading"));

        JsonObject oneEnd = good.deepCopy();
        oneEnd.getAsJsonArray("ends").remove(1);
        assertThrows(JsonFormatException.class, () -> RoadParamsJson.fromJson(oneEnd), "a straight road with one end");
    }

    @Test
    void theModuleRefusesParametersForAnotherToolOrThatArentJson() {
        RoadPlanParams params = RoadPlanParams.straight("city-17", End.at(10, 64, -20, Heading.E), End.at(30, 64, -20, Heading.E));
        String json = RoadsModule.INSTANCE.paramsToJson(params);
        assertThrows(JsonFormatException.class, () -> RoadsModule.INSTANCE.paramsFromJson("CURVE", json));
        assertThrows(JsonFormatException.class, () -> RoadsModule.INSTANCE.paramsFromJson("STRAIGHT", "[1, 2]"));
        assertThrows(JsonFormatException.class, () -> RoadsModule.INSTANCE.paramsFromJson("STRAIGHT", "{\"tool\":"));
        assertEquals(StrictJson.compact(RoadParamsJson.toJson(params)), json);
    }
}
