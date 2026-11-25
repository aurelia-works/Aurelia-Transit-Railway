/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import java.util.Base64;
import java.util.List;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.ops.WorldLimits;
import org.mtr.aurelia.road.undo.OperationRecord;

/**
 * A module that turns its own parameter record into block plans, such as roads ({@code RoadPlanParams}). The client
 * and the server use the same instance: the client to preview and to hash, the server to recompute (editor-protocol §1).
 *
 * @param <P> the module's parameter record
 */
public interface PlanModule<P> {
    /** The module's name in {@code PlanRequest.module}, such as {@code roads}. */
    String id();

    /** The tool {@code params} are for, as sent in {@code PlanRequest.tool}. */
    String tool(P params);

    byte[] encode(P params);

    /**
     * The parameters in {@code bytes}, for {@code tool}, with every field bounds-checked (editor-protocol §2). Bad input,
     * including a tool that doesn't match the parameters, throws {@link MalformedMessage}.
     */
    P decode(String tool, byte[] bytes) throws MalformedMessage;

    /**
     * The plan for {@code params} in a world with {@code limits}, or why there is none. Deterministic, and safe to call
     * off the server thread; it may stop early with an exception if its thread is interrupted (the time limit).
     */
    PlanResult plan(P params, WorldLimits limits);

    /**
     * The permission nodes a request with {@code params} needs beside {@code stratos.use} (editor-protocol §7, F-6), in the
     * order they are checked. By default {@code stratos.<id>.apply}.
     */
    default List<String> nodes(P params) {
        return List.of("stratos." + id() + ".apply");
    }

    /**
     * P9: whether a request with {@code params} keeps to the world's driving side ({@code rightHand}); one that doesn't is refused
     * {@code DRIVING_SIDE_MISMATCH}, nothing changed. By default every request does: only a module with sides overrides it.
     */
    default boolean keepsSide(P params, boolean rightHand) {
        return true;
    }

    /**
     * Whether planning {@code params} needs the world's applied operations (R-5: a road whose end joins another road's).
     * The server then hands them to {@link #plan(Object, WorldLimits, List)}.
     */
    default boolean usesPlaced(P params) {
        return false;
    }

    /**
     * The snapshot ids of the palettes {@code params} are planned with in a world with {@code limits} (R-9), which the
     * operation's record names and the world keeps. None by default.
     */
    default List<String> palettes(P params, WorldLimits limits) {
        return List.of();
    }

    /**
     * As {@link #plan(Object, WorldLimits)}, where {@code placed} are the records of the operations applied in the world
     * now (R-5: the server's stand-in for the road graph until G-10), copied on the server thread. Safe to call off it.
     */
    default PlanResult plan(P params, WorldLimits limits, List<OperationRecord> placed) {
        return plan(params, limits);
    }

    /**
     * {@code params} as the server records and plans them, given the records {@code placed} (as for
     * {@link #plan(Object, WorldLimits, List)}; empty when {@link #usesPlaced} is false): what the server works out for
     * itself replaces what the client sent. By default {@code params} unchanged.
     */
    default P settle(P params, List<OperationRecord> placed) {
        return params;
    }

    /**
     * {@code params} as the JSON object text an operation record keeps (task G-9), enough to plan it again. Modules
     * override it with readable fields (roads: {@code RoadParamsJson}); by default it is the protocol encoding in base64.
     */
    default String paramsToJson(P params) {
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("encoded", Base64.getEncoder().encodeToString(encode(params)));
        return StrictJson.compact(json);
    }

    /** The parameters in a record's JSON object text, for {@code tool}, or why they can't be read. */
    default P paramsFromJson(String tool, String json) throws JsonFormatException {
        StrictJson.Reader reader = StrictJson.read(StrictJson.parseObject(json, "params"), "params");
        String encoded = reader.string("encoded");
        reader.done();
        try {
            return decode(tool, Base64.getDecoder().decode(encoded));
        } catch (MalformedMessage | IllegalArgumentException e) {
            throw new JsonFormatException("params.encoded: " + e.getMessage());
        }
    }
}
