package org.mtr.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mtr.core.tool.Utilities;
import org.mtr.mod.cargo.CargoExchange;
import org.mtr.mod.cargo.StoppedTrain;
import org.mtr.mod.cargo.TrainCargo;
import org.mtr.mod.cargo.TrainCargoStore;
import org.mtr.mod.cargo.TrainProximity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainCargoTest {

	@Test
	public void slotsFollowTheCarsAndNeverShrink() {
		Assertions.assertEquals(9, TrainCargo.slotsForCars(0));
		Assertions.assertEquals(9, TrainCargo.slotsForCars(-3));
		Assertions.assertEquals(18, TrainCargo.slotsForCars(2));
		final TrainCargo cargo = new TrainCargo(TrainCargo.slotsForCars(1));
		cargo.ensureSlots(4);
		Assertions.assertEquals(9, cargo.slots(), "a smaller consist does not drop slots");
		cargo.ensureSlots(18);
		Assertions.assertEquals(18, cargo.slots());
	}

	@Test
	public void loadMergesThenOpensASlotAndStopsWhenFull() {
		final TrainCargo hold = new TrainCargo(2);
		final List<CargoExchange.Pile> sources = new ArrayList<>();
		sources.add(new CargoExchange.Pile("minecraft:stone", "", 3, 64));
		sources.add(new CargoExchange.Pile("minecraft:stone", "", 200, 64));
		Assertions.assertEquals(8, CargoExchange.load(sources, hold, 8));
		Assertions.assertEquals(0, sources.get(0).count());
		Assertions.assertEquals(195, sources.get(1).count());
		Assertions.assertEquals(8, hold.itemCount());

		Assertions.assertEquals(120, CargoExchange.load(sources, hold, 200));
		Assertions.assertEquals(75, sources.get(1).count());
		Assertions.assertEquals(128, hold.itemCount(), "two slots of 64");
		Assertions.assertEquals(0, CargoExchange.load(List.of(new CargoExchange.Pile("minecraft:dirt", "", 5, 64)), hold, 5));
		Assertions.assertEquals(128, hold.itemCount());
	}

	@Test
	public void differentNbtDoesNotMerge() {
		final TrainCargo hold = new TrainCargo(2);
		Assertions.assertEquals(1, hold.insert("minecraft:stone", "", 64, 1));
		Assertions.assertEquals(1, hold.insert("minecraft:stone", "{display:{Name:\"Waybill\"}}", 64, 1));
		Assertions.assertEquals(2, hold.copyStacks().size());
		Assertions.assertEquals(1, hold.extract("minecraft:stone", "", 5));
		Assertions.assertEquals(1, hold.itemCount());
		Assertions.assertEquals("{display:{Name:\"Waybill\"}}", hold.peek().nbt());
	}

	@Test
	public void unloadMergesBeforeUsingAnEmptySlotAndStopsWhenTheContainerIsFull() {
		final TrainCargo hold = new TrainCargo(1);
		hold.insert("minecraft:stone", "", 64, 10);
		final List<CargoExchange.Pile> destinations = new ArrayList<>();
		destinations.add(new CargoExchange.Pile("minecraft:dirt", "", 64, 64));
		destinations.add(new CargoExchange.Pile("minecraft:stone", "", 60, 64));
		destinations.add(CargoExchange.Pile.empty(64));
		Assertions.assertEquals(8, CargoExchange.unload(hold, destinations, 8));
		Assertions.assertEquals(64, destinations.get(1).count(), "the matching slot fills first");
		Assertions.assertEquals(4, destinations.get(2).count());
		Assertions.assertEquals(2, hold.itemCount());

		final TrainCargo stuck = new TrainCargo(1);
		stuck.insert("minecraft:stone", "", 64, 3);
		final List<CargoExchange.Pile> full = List.of(new CargoExchange.Pile("minecraft:dirt", "", 1, 64));
		Assertions.assertEquals(0, CargoExchange.unload(stuck, full, 8));
		Assertions.assertEquals(3, stuck.itemCount());
	}

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
	public void proximityUsesTheNearestPointAndTheSmallerIdOnATie() {
		final StoppedTrain far = new StoppedTrain(1L, 9L, 2, List.of(new double[]{0, 64, 0}, new double[]{10, 64, 0}));
		final StoppedTrain near = new StoppedTrain(5L, 9L, 1, List.of(new double[]{3, 64, 0}));
		final StoppedTrain tied = new StoppedTrain(4L, 9L, 1, List.of(new double[]{3, 64, 0}));
		final StoppedTrain none = new StoppedTrain(8L, 0L, 1, List.of());
		Assertions.assertEquals(2.5, far.distanceTo(2.5, 64.5, 0.5), 1.0E-9);
		Assertions.assertSame(near, TrainProximity.closest(List.of(far, near), 3.5, 64.5, 0.5, TrainProximity.REACH));
		Assertions.assertSame(tied, TrainProximity.closest(List.of(near, tied), 3.5, 64.5, 0.5, TrainProximity.REACH));
		Assertions.assertNull(TrainProximity.closest(List.of(far), 20, 64, 0, TrainProximity.REACH));
		Assertions.assertNull(TrainProximity.closest(List.of(none), 0, 0, 0, TrainProximity.REACH));
	}
}
