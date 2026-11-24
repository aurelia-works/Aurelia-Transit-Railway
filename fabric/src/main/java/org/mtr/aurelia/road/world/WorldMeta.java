/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.json.StrictJson;

/**
 * {@code <world>/stratos/meta.json} (world-data-format §3; task F-5): the format of the world's Stratos data and who
 * wrote it. Written last on every save, so it is the commit point of a migration (§7).
 *
 * <pre>{@code
 * { "formatVersion": 1, "dataVersion": 3465, "worldId": "…", "createdBy": "stratos 0.1.0+mc1.20.1",
 *   "lastWrittenBy": "stratos 0.1.0+mc1.20.1", "lastWrittenAt": "2026-10-08T10:00:00Z",
 *   "migrations": [ { "from": 1, "to": 2, "at": "…", "backup": "migrations/backup-v1-20261008T100000Z" } ] }
 * }</pre>
 *
 * @param formatVersion the format of every file under {@code stratos/} ({@code CoreInfo.FORMAT_VERSION} when written)
 * @param dataVersion   the game's DataVersion when written (3465 for 1.20.1): block states in the files use it
 * @param worldId       random, made once
 * @param createdBy     the Stratos that first wrote the folder, such as {@code stratos 0.1.0+mc1.20.1}
 * @param lastWrittenBy the Stratos that wrote it last
 * @param lastWrittenAt when, in UTC
 * @param migrations    every migration run on the folder, oldest first
 */
public record WorldMeta(int formatVersion, int dataVersion, UUID worldId, String createdBy, String lastWrittenBy, Instant lastWrittenAt,
        List<Migrated> migrations) {
    /** One migration step that ran, and where its backup is. */
    public record Migrated(int from, int to, Instant at, String backup) {
        public Migrated {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(backup, "backup");
        }
    }

    public WorldMeta {
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(lastWrittenBy, "lastWrittenBy");
        Objects.requireNonNull(lastWrittenAt, "lastWrittenAt");
        migrations = List.copyOf(migrations);
    }

    /** The same, written now by {@code writtenBy} with the game's {@code dataVersion}. */
    public WorldMeta written(String writtenBy, int dataVersion, Instant now) {
        return new WorldMeta(this.formatVersion, dataVersion, this.worldId, this.createdBy, writtenBy, now, this.migrations);
    }

    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("formatVersion", this.formatVersion);
        json.addProperty("dataVersion", this.dataVersion);
        json.addProperty("worldId", this.worldId.toString());
        json.addProperty("createdBy", this.createdBy);
        json.addProperty("lastWrittenBy", this.lastWrittenBy);
        json.addProperty("lastWrittenAt", this.lastWrittenAt.toString());
        JsonArray migrations = new JsonArray();
        for (Migrated migrated : this.migrations) {
            JsonObject m = new JsonObject();
            m.addProperty("from", migrated.from());
            m.addProperty("to", migrated.to());
            m.addProperty("at", migrated.at().toString());
            m.addProperty("backup", migrated.backup());
            migrations.add(m);
        }
        json.add("migrations", migrations);
        return StrictJson.write(json);
    }

    /**
     * Just the {@code formatVersion} of {@code text}, read leniently: a newer Stratos may have added fields this one
     * doesn't know, and the downgrade guard must still recognise the data as newer rather than call it unreadable.
     */
    public static OptionalInt formatVersionOf(String text) {
        try {
            JsonObject json = StrictJson.parseObject(text, "meta.json");
            if (json.get("formatVersion") instanceof JsonPrimitive version && version.isNumber()) {
                double value = version.getAsDouble();
                if (value == StrictMath.rint(value) && value >= 0 && value <= Integer.MAX_VALUE) {
                    return OptionalInt.of((int) value);
                }
            }
        } catch (JsonFormatException e) {
            // unreadable: no version
        }
        return OptionalInt.empty();
    }

    /** Just {@code lastWrittenBy}, read leniently, for the downgrade guard's log line. */
    public static Optional<String> lastWrittenByOf(String text) {
        try {
            JsonObject json = StrictJson.parseObject(text, "meta.json");
            if (json.get("lastWrittenBy") instanceof JsonPrimitive by && by.isString()) {
                return Optional.of(by.getAsString());
            }
        } catch (JsonFormatException e) {
            // unreadable
        }
        return Optional.empty();
    }

    /** The meta in {@code text}, read strictly (for data of this Stratos's format or older). */
    public static WorldMeta fromJson(String text) throws JsonFormatException {
        StrictJson.Reader json = StrictJson.read(StrictJson.parseObject(text, "meta.json"), "meta");
        int formatVersion = json.integer("formatVersion");
        int dataVersion = json.integer("dataVersion");
        UUID worldId = uuid(json.string("worldId"), "meta.worldId");
        String createdBy = json.string("createdBy");
        String lastWrittenBy = json.string("lastWrittenBy");
        Instant lastWrittenAt = instant(json.string("lastWrittenAt"), "meta.lastWrittenAt");
        List<Migrated> migrations = new ArrayList<>();
        int i = 0;
        for (JsonObject m : json.objects("migrations")) {
            String where = "meta.migrations[" + i++ + "]";
            StrictJson.Reader migrated = StrictJson.read(m, where);
            migrations.add(new Migrated(migrated.integer("from"), migrated.integer("to"), instant(migrated.string("at"), where + ".at"),
                    migrated.string("backup")));
            migrated.done();
        }
        json.done();
        return new WorldMeta(formatVersion, dataVersion, worldId, createdBy, lastWrittenBy, lastWrittenAt, migrations);
    }

    private static UUID uuid(String text, String where) throws JsonFormatException {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            throw new JsonFormatException(where + " isn't a UUID");
        }
    }

    private static Instant instant(String text, String where) throws JsonFormatException {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new JsonFormatException(where + " isn't a UTC time such as 2026-10-08T10:00:00Z");
        }
    }
}
