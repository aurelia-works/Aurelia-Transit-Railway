/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mtr.aurelia.road.spi.Storage;

/**
 * Brings a world's Stratos data up to this Stratos's {@code formatVersion} (world-data-format §7; task F-5):
 * <ol>
 *   <li>copy everything under {@code aurelia_roads/} except {@code undo/}, {@code tmp/} and {@code migrations/} to
 *       {@code migrations/backup-v<from>-<UTC time>/}, then mark it finished ({@code backup.done}). If an earlier run
 *       stopped after its backup was finished, that backup is used again: the files may be half-migrated since;</li>
 *   <li>run each step {@code M(k → k+1)} in turn (every write atomic, through {@code tmp/});</li>
 *   <li>write {@code meta.json} <b>last</b>, with the new version and a {@code migrations} entry per step: the commit
 *       point. A crash before it leaves the old version in {@code meta.json}, and the steps run again.</li>
 * </ol>
 * Undo images are left as they are: every step so far keeps them readable, and a step that can't must drop them itself.
 */
public final class Migrations {
    /** Every step this Stratos knows, in order. Empty: {@code formatVersion} 1 is the first. */
    public static final List<Migration> ALL = List.of();

    /** The file that marks a backup as finished. */
    static final String DONE = "backup.done";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private Migrations() {
    }

    /**
     * Migrates the data described by {@code meta} to {@code target} with {@code steps}, and returns the new meta, already
     * written. Throws if a step is missing or fails; {@code meta.json} is then unchanged.
     */
    public static WorldMeta run(Storage storage, WorldMeta meta, int target, List<Migration> steps, Instant now, String writtenBy, int dataVersion)
            throws IOException {
        List<Migration> chain = new ArrayList<>();
        for (int version = meta.formatVersion(); version < target; version++) {
            int from = version;
            chain.add(steps.stream().filter(step -> step.from() == from).findFirst()
                    .orElseThrow(() -> new IOException("No migration from formatVersion " + from + " to " + (from + 1))));
        }
        if (chain.isEmpty()) {
            return meta;
        }
        String backup = finishedBackup(storage, meta).orElse("migrations/backup-v" + meta.formatVersion() + "-" + STAMP.format(now));
        if (!storage.exists(backup + "/" + DONE)) {
            backUp(storage, backup);
        }
        List<WorldMeta.Migrated> migrated = new ArrayList<>(meta.migrations());
        for (Migration step : chain) {
            step.migrate(storage);
            migrated.add(new WorldMeta.Migrated(step.from(), step.from() + 1, now, backup));
        }
        WorldMeta result = new WorldMeta(target, dataVersion, meta.worldId(), meta.createdBy(), writtenBy, now, migrated);
        storage.write(WorldData.META, result.toJson().getBytes(StandardCharsets.UTF_8));
        return result;
    }

    /** A finished backup of {@code meta}'s version that no migration in {@code meta} names: an interrupted run's. */
    private static Optional<String> finishedBackup(Storage storage, WorldMeta meta) throws IOException {
        String prefix = "migrations/backup-v" + meta.formatVersion() + "-";
        for (String file : storage.list("migrations")) {
            if (file.startsWith(prefix) && file.endsWith("/" + DONE)) {
                String folder = file.substring(0, file.length() - DONE.length() - 1);
                if (folder.indexOf('/', prefix.length()) < 0 && meta.migrations().stream().noneMatch(m -> m.backup().equals(folder))) {
                    return Optional.of(folder);
                }
            }
        }
        return Optional.empty();
    }

    /** Copies every file but the undo images, temp files and older backups into {@code backup}, then marks it finished. */
    private static void backUp(Storage storage, String backup) throws IOException {
        storage.delete(backup); // a backup half-made before a crash is made again
        for (String file : storage.list("")) {
            if (file.startsWith("undo/") || file.startsWith("tmp/") || file.startsWith("migrations/")) {
                continue;
            }
            storage.write(backup + "/" + file, storage.read(file));
        }
        storage.write(backup + "/" + DONE, new byte[0]);
    }
}
