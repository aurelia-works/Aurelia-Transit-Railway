package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.tool.Utilities;
import org.mtr.mod.cargo.TrainCargo;
import org.mtr.mod.cargo.TrainCargoStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainCargoTest {

	@Test
	public void saveRoundTripKeepsEachDimensionAndSkipsACorruptFile(@TempDir Path directory) throws Exception {
		final Path file = directory.resolve("atr-cargo.json");
		final TrainCargoStore store = new TrainCargoStore();
		final String overworld = "minecraft/overworld";
		final String nether = "minecraft/the_nether";
		final long stoneTrain = 0xabcL;
		final long netherTrain = -2L;
		store.getOrCreate(overworld, stoneTrain, 9).insert("minecraft:stone", "", 64, 12);
		store.getOrCreate(nether, netherTrain, 9).insert("minecraft:netherrack", "{a:1}", 64, 3);
		store.markDirty(overworld, stoneTrain);
		store.save(file);

		final TrainCargoStore loaded = new TrainCargoStore();
		loaded.load(file);
		Assertions.assertEquals(12, loaded.itemCount(stoneTrain));
		Assertions.assertEquals(3, loaded.itemCount(netherTrain));
		Assertions.assertEquals("{a:1}", loaded.getOrCreate(nether, netherTrain, 9).peek().nbt());
		Assertions.assertTrue(loaded.dimensionJson(overworld).contains(Utilities.numberToPaddedHexString(stoneTrain)));
		Assertions.assertFalse(loaded.dimensionJson(overworld).contains("netherrack"));

		final ConcurrentHashMap<Long, TrainCargo> client = new ConcurrentHashMap<>();
		TrainCargoStore.readInto(loaded.dimensionJson(overworld), client, true);
		Assertions.assertEquals(12, client.get(stoneTrain).itemCount());
		final String cleared = "{\"vehicles\":{\"" + Utilities.numberToPaddedHexString(stoneTrain) + "\":{\"slots\":0,\"stacks\":[]}}}";
		TrainCargoStore.readInto(cleared, client, false);
		Assertions.assertFalse(client.containsKey(stoneTrain));

		Files.writeString(file, "{not json");
		final TrainCargoStore corrupt = new TrainCargoStore();
		corrupt.load(file);
		corrupt.save(file);
		Assertions.assertEquals("{not json", Files.readString(file), "a failed read must not overwrite the file");
		corrupt.getOrCreate(overworld, 1L, 9).insert("minecraft:dirt", "", 64, 1);
		corrupt.markDirty(overworld, 1L);
		corrupt.save(file);
		Assertions.assertTrue(Files.readString(file).contains("minecraft:dirt"));
	}

	@Test
	public void pruneDropsOnlyMissingTrainsInThatDimension() {
		final TrainCargoStore store = new TrainCargoStore();
		store.getOrCreate("minecraft/overworld", 1L, 9).insert("minecraft:stone", "", 64, 1);
		store.getOrCreate("minecraft/overworld", 2L, 9).insert("minecraft:dirt", "", 64, 1);
		store.getOrCreate("minecraft/the_nether", 3L, 9).insert("minecraft:netherrack", "", 64, 1);
		Assertions.assertEquals(1, store.prune("minecraft/overworld", Set.of(1L)));
		Assertions.assertEquals(1, store.itemCount(1L));
		Assertions.assertEquals(0, store.itemCount(2L));
		Assertions.assertEquals(1, store.itemCount(3L));
		Assertions.assertEquals(List.of(2L), store.drainDirty("minecraft/overworld"));
	}

	@Test
	public void couplingMovesTheRearHoldOntoTheJoinedTrain() {
		final TrainCargoStore store = new TrainCargoStore();
		store.getOrCreate("minecraft/overworld", 1L, 1).insert("minecraft:stone", "", 64, 64);
		store.getOrCreate("minecraft/overworld", 2L, 1).insert("minecraft:dirt", "{a:1}", 64, 30);
		store.moveInto(2L, 1L);
		Assertions.assertEquals(94, store.itemCount(1L), "a full lead hold still takes the rear's stacks");
		Assertions.assertEquals(0, store.itemCount(2L));
		store.prune("minecraft/overworld", Set.of(1L));
		Assertions.assertEquals(94, store.itemCount(1L), "pruning the rear train's id loses nothing");
	}
}
