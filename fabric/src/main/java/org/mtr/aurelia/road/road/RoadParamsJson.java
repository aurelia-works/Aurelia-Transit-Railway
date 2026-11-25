/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.road;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import org.mtr.aurelia.road.geom.Heading;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.road.raster.Crossing;

/**
 * {@link RoadPlanParams} as JSON, for operation records (task G-9): every field by name, so a record can be read by a
 * person and a road planned again from it (R-7). Optional fields are left out when empty. Read strictly
 * ({@link StrictJson}).
 *
 * <pre>{@code
 * { "tool": "CURVE", "profileId": "city-17", "preset": "GAME_SCALE", "paletteId": "vanilla", "drivingSide": "LHT",
 *   "ends": [ { "x": 0, "y": 64, "z": 0, "heading": "E" }, { "x": 40, "y": 64, "z": 40, "heading": "S" } ],
 *   "vertical": { "shape": "QUADRATIC" }, "nodeAids": "WOOL", "overlay": false }
 * }</pre>
 */
public final class RoadParamsJson {
    private RoadParamsJson() {
    }

    /** {@code params} as a JSON object. */
    public static JsonObject toJson(RoadPlanParams params) {
        JsonObject json = new JsonObject();
        json.addProperty("tool", params.tool().name());
        json.addProperty("profileId", params.profileId());
        json.addProperty("preset", params.preset().name());
        json.addProperty("paletteId", params.paletteId());
        json.addProperty("drivingSide", params.drivingSide().name());
        JsonArray ends = new JsonArray();
        for (RoadPlanParams.End end : params.ends()) {
            JsonObject e = new JsonObject();
            e.addProperty("x", end.x());
            e.addProperty("y", end.y());
            e.addProperty("z", end.z());
            e.addProperty("heading", end.heading().name());
            end.snapRoadId().ifPresent(id -> e.addProperty("snapRoadId", id.toString()));
            if (end.snapPortIndex().isPresent()) {
                e.addProperty("snapPortIndex", end.snapPortIndex().getAsInt());
            }
            ends.add(e);
        }
        json.add("ends", ends);
        JsonObject vertical = new JsonObject();
        vertical.addProperty("shape", params.vertical().shape().name());
        if (params.vertical().radius().isPresent()) {
            vertical.addProperty("radius", params.vertical().radius().getAsDouble());
        }
        json.add("vertical", vertical);
        json.addProperty("nodeAids", params.nodeAids().name());
        json.addProperty("overlay", params.overlay());
        params.junction().ifPresent(junction -> {
            JsonObject j = new JsonObject();
            j.addProperty("templateId", junction.templateId());
            j.addProperty("size", junction.size());
            JsonArray arms = new JsonArray();
            junction.armProfiles().forEach(arms::add);
            j.add("armProfiles", arms);
            json.add("junction", j);
        });
        params.targetRoadId().ifPresent(id -> json.addProperty("targetRoadId", id.toString()));
        params.manualEdits().ifPresent(answer -> json.addProperty("manualEdits", answer.name()));
        if (!params.dashes().equals(RoadPlanParams.Dashes.FROM_START)) { // R-5: left out when it is the default
            JsonObject dashes = new JsonObject();
            dashes.addProperty("phase", params.dashes().phase());
            dashes.addProperty("at", params.dashes().at().name());
            json.add("dashes", dashes);
        }
        if (!params.crossings().isEmpty()) { // #193: left out when there are none, so older records read back the same
            JsonArray crossings = new JsonArray();
            for (Crossing crossing : params.crossings()) {
                JsonObject c = new JsonObject();
                c.addProperty("at", crossing.at());
                c.addProperty("width", crossing.width());
                crossings.add(c);
            }
            json.add("crossings", crossings);
        }
        return json;
    }

    /** The parameters in {@code json}, or why they can't be read (naming the field). */
    public static RoadPlanParams fromJson(JsonObject json) throws JsonFormatException {
        StrictJson.Reader params = StrictJson.read(json, "params");
        RoadPlanParams.Tool tool = params.constant("tool", RoadPlanParams.Tool.class);
        String profileId = params.string("profileId");
        Presets.Preset preset = params.constant("preset", Presets.Preset.class);
        String paletteId = params.string("paletteId");
        RoadPlanParams.DrivingSide drivingSide = params.constant("drivingSide", RoadPlanParams.DrivingSide.class);
        List<RoadPlanParams.End> ends = new ArrayList<>();
        int i = 0;
        for (JsonObject e : params.objects("ends")) {
            String where = "params.ends[" + i++ + "]";
            StrictJson.Reader end = StrictJson.read(e, where);
            int x = end.integer("x");
            int y = end.integer("y");
            int z = end.integer("z");
            Heading heading = end.constant("heading", Heading.class);
            Optional<UUID> snapRoadId = uuid(end.optionalString("snapRoadId"), where + ".snapRoadId");
            Optional<Integer> port = end.optionalInteger("snapPortIndex");
            end.done();
            try {
                ends.add(new RoadPlanParams.End(x, y, z, heading, snapRoadId, port.map(OptionalInt::of).orElse(OptionalInt.empty())));
            } catch (IllegalArgumentException ex) {
                throw new JsonFormatException(where + ": " + ex.getMessage());
            }
        }
        StrictJson.Reader v = StrictJson.read(params.object("vertical"), "params.vertical");
        RoadPlanParams.Vertical.Shape shape = v.constant("shape", RoadPlanParams.Vertical.Shape.class);
        Optional<Double> radius = v.optionalNumber("radius");
        v.done();
        RoadPlanParams.NodeAids nodeAids = params.constant("nodeAids", RoadPlanParams.NodeAids.class);
        boolean overlay = params.bool("overlay");
        Optional<RoadPlanParams.Junction> junction = Optional.empty();
        Optional<JsonObject> j = params.optionalObject("junction");
        if (j.isPresent()) {
            StrictJson.Reader reader = StrictJson.read(j.get(), "params.junction");
            String templateId = reader.string("templateId");
            int size = reader.integer("size");
            List<String> arms = reader.strings("armProfiles");
            reader.done();
            try {
                junction = Optional.of(new RoadPlanParams.Junction(templateId, size, arms));
            } catch (IllegalArgumentException ex) {
                throw new JsonFormatException("params.junction: " + ex.getMessage());
            }
        }
        Optional<UUID> targetRoadId = uuid(params.optionalString("targetRoadId"), "params.targetRoadId");
        Optional<RoadPlanParams.ManualEdits> manualEdits = Optional.empty();
        Optional<String> answer = params.optionalString("manualEdits");
        if (answer.isPresent()) {
            try {
                manualEdits = Optional.of(RoadPlanParams.ManualEdits.valueOf(answer.get()));
            } catch (IllegalArgumentException ex) {
                throw new JsonFormatException("params.manualEdits can't be " + answer.get());
            }
        }
        RoadPlanParams.Dashes dashes = RoadPlanParams.Dashes.FROM_START;
        Optional<JsonObject> d = params.optionalObject("dashes");
        if (d.isPresent()) {
            StrictJson.Reader reader = StrictJson.read(d.get(), "params.dashes");
            double phase = reader.number("phase");
            RoadPlanParams.Dashes.Anchor at = reader.constant("at", RoadPlanParams.Dashes.Anchor.class);
            reader.done();
            try {
                dashes = new RoadPlanParams.Dashes(phase, at);
            } catch (IllegalArgumentException ex) {
                throw new JsonFormatException("params.dashes: " + ex.getMessage());
            }
        }
        List<Crossing> crossings = new ArrayList<>();
        if (params.has("crossings")) {
            int k = 0;
            for (JsonObject c : params.objects("crossings")) {
                String where = "params.crossings[" + k++ + "]";
                StrictJson.Reader reader = StrictJson.read(c, where);
                double at = reader.number("at");
                int width = reader.integer("width");
                reader.done();
                try {
                    crossings.add(new Crossing(at, width));
                } catch (IllegalArgumentException ex) {
                    throw new JsonFormatException(where + ": " + ex.getMessage());
                }
            }
        }
        params.done();
        try {
            return new RoadPlanParams(tool, profileId, preset, paletteId, drivingSide, ends,
                    new RoadPlanParams.Vertical(shape, radius.map(OptionalDouble::of).orElse(OptionalDouble.empty())), nodeAids, overlay, junction,
                    targetRoadId, manualEdits, dashes, crossings);
        } catch (IllegalArgumentException ex) {
            throw new JsonFormatException("params: " + ex.getMessage());
        }
    }

    private static Optional<UUID> uuid(Optional<String> text, String where) throws JsonFormatException {
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(text.get()));
        } catch (IllegalArgumentException e) {
            throw new JsonFormatException(where + " isn't a UUID");
        }
    }
}
