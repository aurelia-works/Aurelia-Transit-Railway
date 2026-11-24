/*
 * Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
 * Used with permission — see NOTICE.md.
 */
package org.mtr.aurelia.road.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mtr.aurelia.road.CoreInfo;
import org.mtr.aurelia.road.json.JsonFormatException;
import org.mtr.aurelia.road.ops.BlockPlan;
import org.mtr.aurelia.road.ops.FakeWorld;
import org.mtr.aurelia.road.spi.SideEffectWriter;
import org.mtr.aurelia.road.undo.FakeStorage;
import org.mtr.aurelia.road.undo.OperationJson;
import org.mtr.aurelia.road.undo.OperationRecord;
import org.mtr.aurelia.road.undo.Operations;
import org.mtr.aurelia.road.undo.UndoIndexJson;

/**
 * A whole world's Stratos data at {@code formatVersion} 1 (world-data-format §7; task F-5), in
 * {@code fabric/src/ataTest/resources/road/formatVersion-1/world/}: {@code meta.json}, two records, the undo index and the images.
 * It must load in every later Stratos (through its migrations); a format change needs {@code formatVersion} 2 and a
 * migration, never a new fixture. If the folder is missing, the test makes it and fails, so a new one is always reviewed.
 */
class FormatVersion1FixtureTest {
    static final Path FOLDER = Path.of("src", "ataTest", "resources", "road", "formatVersion-1", "world");
    static final UUID ROAD = UUID.fromString("0b6c5d7e-1f2a-4b3c-8d4e-5f6a7b8c9d01");
    static final UUID UNDONE = UUID.fromString("0b6c5d7e-1f2a-4b3c-8d4e-5f6a7b8c9d02");
    static final String ALICE = "00000000-0000-0000-0000-00000000a11c";
    static final Instant TIME = Instant.parse("2026-10-08T12:00:00Z");

    static BlockPlan row(int x0, int count, String state) {
        BlockPlan.Builder plan = BlockPlan.builder(3465);
        for (int x = x0; x < x0 + count; x++) {
            plan.set(x, 64, 0, state);
        }
        return plan.build();
    }

    static WorldData.Settings settings(AtomicLong ids) {
        return new WorldData.Settings(0, 200, 1L << 30, () -> new UUID(0x5d, ids.incrementAndGet()), SideEffectWriter.UNAVAILABLE, () -> TIME, 3465,
                "stratos 0.1.0+mc1.20.1");
    }

    static void run(Operations operations, FakeWorld world) {
        for (int tick = 0; tick < 1000 && (operations.isBusy(ALICE) || !operations.scheduler().isIdle()); tick++) {
            operations.scheduler().tick(world, 5_000_000, () -> world.now);
            operations.afterTick();
        }
    }

    /** Alice placed a road (still placed) and a second one she then undid. */
    static FakeStorage make() throws IOException {
        FakeStorage storage = new FakeStorage();
        FakeWorld world = new FakeWorld();
        WorldData data = WorldData.load(storage, settings(new AtomicLong()), Migrations.ALL, line -> {
        });
        Operations overworld = data.operations(Operations.OVERWORLD);
        overworld.apply(ALICE, row(0, 20, "minecraft:gray_concrete"), ROAD, Optional.empty(), new OperationRecord.Draft("roads", "STRAIGHT",
                "{\"tool\":\"STRAIGHT\",\"profileId\":\"city-17\"}", "ab".repeat(32), List.of()));
        run(overworld, world);
        overworld.apply(ALICE, row(40, 5, "minecraft:polished_andesite"), UNDONE, Optional.empty(), new OperationRecord.Draft("roads", "CURVE",
                "{\"tool\":\"CURVE\"}", "cd".repeat(32), List.of()));
        run(overworld, world);
        overworld.undo(ALICE);
        run(overworld, world);
        data.save();
        data.stopped(); // F-2+: a clean stop, after its last save: the journal goes
        return storage;
    }

    static FakeStorage fixture() throws IOException {
        FakeStorage storage = new FakeStorage();
        try (Stream<Path> files = Files.walk(FOLDER)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                storage.files.put(FOLDER.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"), Files.readAllBytes(file));
            }
        }
        return storage;
    }

    @Test
    void theFixtureLoadsAndItsRoadCanBeUndone() throws IOException {
        if (!Files.exists(FOLDER)) {
            for (Map.Entry<String, byte[]> file : make().files.entrySet()) {
                Path target = FOLDER.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.write(target, file.getValue());
            }
            fail("Wrote " + FOLDER + ": review it, commit it and run the tests again");
        }
        FakeStorage storage = fixture();
        assertEquals(make().files.keySet(), storage.files.keySet(), "the same files as a fresh save");
        List<String> log = new ArrayList<>();
        WorldData data = WorldData.load(storage, settings(new AtomicLong(100)), Migrations.ALL, log::add);
        assertEquals(List.of(), log);
        assertEquals(Optional.empty(), data.readOnly());
        assertEquals(1, data.meta().orElseThrow().formatVersion());
        Operations overworld = data.operations(Operations.OVERWORLD);
        assertEquals(1, overworld.undoDepth(ALICE));
        assertEquals(1, overworld.redoDepth(ALICE));
        assertEquals("STRAIGHT", overworld.record(ROAD).orElseThrow().tool());
        assertEquals(OperationRecord.Status.UNDONE, overworld.record(UNDONE).orElseThrow().status());

        FakeWorld world = new FakeWorld();
        for (int x = 0; x < 20; x++) {
            world.blocks.put(FakeWorld.key(x, 64, 0), "minecraft:gray_concrete");
        }
        overworld.undo(ALICE);
        run(overworld, world);
        assertTrue(world.blocks.values().stream().allMatch("minecraft:air"::equals), "the fixture's images undo the road");
        overworld.redo(ALICE);
        run(overworld, world);
        assertEquals("minecraft:gray_concrete", world.blockState(0, 64, 0), "redo puts it back");
        overworld.redo(ALICE);
        run(overworld, world);
        assertEquals("minecraft:polished_andesite", world.blockState(42, 64, 0), "and then the one undone before the save");
    }

    @Test
    void theFixturesJsonReadsAndWritesBackByteForByte() throws IOException, JsonFormatException {
        if (!Files.exists(FOLDER)) {
            return; // the other test makes it
        }
        FakeStorage storage = fixture();
        String meta = new String(storage.read(WorldData.META), StandardCharsets.UTF_8);
        assertEquals(meta, WorldMeta.fromJson(meta).toJson());
        assertEquals(CoreInfo.FORMAT_VERSION, WorldMeta.fromJson(meta).formatVersion());
        String index = new String(storage.read(WorldData.INDEX), StandardCharsets.UTF_8);
        assertEquals(index, UndoIndexJson.write(UndoIndexJson.read(index)));
        for (UUID id : List.of(ROAD, UNDONE)) {
            String record = new String(storage.read(WorldData.RECORDS + "/" + id + ".json"), StandardCharsets.UTF_8);
            assertEquals(record, OperationJson.write(OperationJson.read(record)));
        }
    }
}
