/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.proto;

import com.google.gson.JsonObject;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;
import org.mtr.aurelia.road.proto.Message.Limits;

/**
 * A server's rate and volume limits (editor-protocol §8, F-6), for every player except the singleplayer world's owner
 * and players with {@code stratos.limits.bypass}. 0 means no limit. Undo and redo are neither limited nor counted
 * (owner, 2026-10-12): they only reverse what the player was allowed to do.
 *
 * <p>The server keeps them in {@code config/stratos/server.json} ({@link #FILE}), written with {@link #DEFAULTS} on its
 * first start. A missing key keeps its default.
 *
 * @param maxBlocksPerOperation the most blocks one plan may change ({@code VOLUME_LIMIT}); a rebuild counts its whole road
 * @param maxBlocksPerMinute    the most blocks a player's accepted plans may change in any 60 seconds ({@code VOLUME_PER_MINUTE})
 * @param planRequestsPerSecond how fast a player may send plan requests, as a token bucket ({@code RATE_LIMITED})
 * @param planRequestBurst      how many plan requests a player may send at once before the rate applies
 */
public record EditorLimits(int maxBlocksPerOperation, int maxBlocksPerMinute, int planRequestsPerSecond, int planRequestBurst) {
    /** The file in the game's config folder. */
    public static final String FILE = "stratos/server.json";
    /** No limits at all. */
    public static final EditorLimits NONE = new EditorLimits(0, 0, 0, 0);
    /** The defaults (owner, 2026-10-12: the spec's proposal; S12 showed a million blocks doesn't freeze the server). */
    public static final EditorLimits DEFAULTS = new EditorLimits(250_000, 1_000_000, 2, 5);

    public EditorLimits {
        if (maxBlocksPerOperation < 0 || maxBlocksPerMinute < 0 || planRequestsPerSecond < 0 || planRequestBurst < 0) {
            throw new IllegalArgumentException("limits can't be negative");
        }
        if (planRequestsPerSecond > 0 && planRequestBurst == 0) {
            throw new IllegalArgumentException("planRequestBurst must be at least 1 when planRequestsPerSecond is set");
        }
    }

    /** What {@code Hello} tells a limited player. */
    public Limits hello() {
        return new Limits(this.maxBlocksPerOperation, this.maxBlocksPerMinute);
    }

    /** The file's text: an object with the four limits, in order. */
    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("maxBlocksPerOperation", this.maxBlocksPerOperation);
        json.addProperty("maxBlocksPerMinute", this.maxBlocksPerMinute);
        json.addProperty("planRequestsPerSecond", this.planRequestsPerSecond);
        json.addProperty("planRequestBurst", this.planRequestBurst);
        return StrictJson.write(json);
    }

    /** The limits in a file's {@code text}; a key that is missing keeps its default. */
    public static EditorLimits fromJson(String text) throws JsonFormatException {
        StrictJson.Reader reader = StrictJson.read(StrictJson.parseObject(text, "server.json"), "server.json");
        int perOperation = reader.optionalInteger("maxBlocksPerOperation").orElse(DEFAULTS.maxBlocksPerOperation);
        int perMinute = reader.optionalInteger("maxBlocksPerMinute").orElse(DEFAULTS.maxBlocksPerMinute);
        int rate = reader.optionalInteger("planRequestsPerSecond").orElse(DEFAULTS.planRequestsPerSecond);
        int burst = reader.optionalInteger("planRequestBurst").orElse(DEFAULTS.planRequestBurst);
        reader.done();
        try {
            return new EditorLimits(perOperation, perMinute, rate, burst);
        } catch (IllegalArgumentException e) {
            throw new JsonFormatException("server.json: " + e.getMessage());
        }
    }
}
