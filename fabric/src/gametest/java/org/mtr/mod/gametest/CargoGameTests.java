package org.mtr.mod.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.FurnaceBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.mtr.mod.block.BlockTrainCargoLoader;
import org.mtr.mod.cargo.CargoMachines;
import org.mtr.mod.cargo.TrainCargo;

/**
 * Inventory transfers against real chests, barrels and hoppers. The simulation is not running here; the pure hold and
 * the block entity tick (which no-ops without a train snapshot) are what a loaded chunk actually calls.
 */
public final class CargoGameTests implements FabricGameTest {

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void chestLoadsEightItemsPerCycle(TestContext context) {
		final BlockPos loader = place(context, 1, 2, 2, Blocks.AIR);
		final BlockPos chestPos = place(context, 1, 2, 1, Blocks.CHEST);
		final ChestBlockEntity chest = chest(context, chestPos);
		chest.setStack(0, new ItemStack(Items.COBBLESTONE, 20));
		final TrainCargo hold = new TrainCargo(TrainCargo.slotsForCars(1));
		final int moved = CargoMachines.transfer(context.getWorld(), loader, true, hold, CargoMachines.ITEMS_PER_CYCLE);
		check(moved == 8, "moved " + moved);
		check(chest.getStack(0).getCount() == 12, "chest left " + chest.getStack(0).getCount());
		check(hold.itemCount() == 8, "hold " + hold.itemCount());
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void hopperLoadsAndBarrelUnloads(TestContext context) {
		final BlockPos loadAt = place(context, 1, 2, 2, Blocks.AIR);
		final BlockPos hopperPos = place(context, 1, 2, 3, Blocks.HOPPER);
		final HopperBlockEntity hopper = (HopperBlockEntity) context.getWorld().getBlockEntity(hopperPos);
		hopper.setStack(0, new ItemStack(Items.IRON_INGOT, 5));
		final TrainCargo hold = new TrainCargo(TrainCargo.slotsForCars(1));
		final int loaded = CargoMachines.transfer(context.getWorld(), loadAt, true, hold, CargoMachines.ITEMS_PER_CYCLE);
		check(loaded == 5 && hopper.getStack(0).isEmpty() && hold.itemCount() == 5, "hopper load " + loaded);

		final BlockPos unloadAt = place(context, 4, 2, 2, Blocks.AIR);
		final BlockPos barrelPos = place(context, 5, 2, 2, Blocks.BARREL);
		final BarrelBlockEntity barrel = (BarrelBlockEntity) context.getWorld().getBlockEntity(barrelPos);
		final int unloaded = CargoMachines.transfer(context.getWorld(), unloadAt, false, hold, CargoMachines.ITEMS_PER_CYCLE);
		check(unloaded == 5 && hold.isEmpty() && barrel.getStack(0).getCount() == 5 && barrel.getStack(0).isOf(Items.IRON_INGOT), "barrel " + barrel.getStack(0) + " hold " + hold.itemCount());
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void dropperAndFurnaceAreNotCargoContainers(TestContext context) {
		final BlockPos loader = place(context, 1, 2, 2, Blocks.AIR);
		final BlockPos dropperPos = place(context, 0, 2, 2, Blocks.DROPPER);
		final DropperBlockEntity dropper = (DropperBlockEntity) context.getWorld().getBlockEntity(dropperPos);
		dropper.setStack(0, new ItemStack(Items.COBBLESTONE, 10));
		final BlockPos furnacePos = place(context, 1, 3, 2, Blocks.FURNACE);
		final FurnaceBlockEntity furnace = (FurnaceBlockEntity) context.getWorld().getBlockEntity(furnacePos);
		furnace.setStack(1, new ItemStack(Items.COAL, 4));
		final TrainCargo hold = new TrainCargo(TrainCargo.slotsForCars(1));
		final int moved = CargoMachines.transfer(context.getWorld(), loader, true, hold, CargoMachines.ITEMS_PER_CYCLE);
		check(moved == 0 && hold.isEmpty(), "moved " + moved);
		check(dropper.getStack(0).getCount() == 10, "dropper");
		check(furnace.getStack(1).getCount() == 4, "furnace");
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void namedStackSurvivesLoadAndUnload(TestContext context) {
		final BlockPos loadAt = place(context, 1, 2, 2, Blocks.AIR);
		final BlockPos chestPos = place(context, 1, 2, 1, Blocks.CHEST);
		final ItemStack named = new ItemStack(Items.PAPER, 1);
		named.setCustomName(Text.literal("Waybill"));
		chest(context, chestPos).setStack(0, named);
		final TrainCargo hold = new TrainCargo(TrainCargo.slotsForCars(1));
		CargoMachines.transfer(context.getWorld(), loadAt, true, hold, CargoMachines.ITEMS_PER_CYCLE);
		check(hold.itemCount() == 1 && hold.peek() != null && hold.peek().nbt().contains("Waybill"), "hold lost the name: " + (hold.peek() == null ? "empty" : hold.peek().nbt()));
		final BlockPos unloadAt = place(context, 4, 2, 2, Blocks.AIR);
		final BlockPos barrelPos = place(context, 5, 2, 2, Blocks.BARREL);
		CargoMachines.transfer(context.getWorld(), unloadAt, false, hold, CargoMachines.ITEMS_PER_CYCLE);
		final ItemStack out = ((BarrelBlockEntity) context.getWorld().getBlockEntity(barrelPos)).getStack(0);
		check(out.getCount() == 1 && "Waybill".equals(out.getName().getString()), "name was " + out.getName().getString());
		context.complete();
	}

	@GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
	public void loaderTickWithoutATrainLeavesTheChest(TestContext context) {
		final net.minecraft.block.Block loaderBlock = Registries.BLOCK.get(new Identifier("mtr", "train_cargo_loader"));
		check(loaderBlock != Blocks.AIR, "cargo loader is not registered");
		context.setBlockState(1, 2, 2, loaderBlock.getDefaultState());
		final BlockPos loaderPos = context.getAbsolutePos(new BlockPos(1, 2, 2));
		final BlockPos chestPos = place(context, 1, 2, 1, Blocks.CHEST);
		chest(context, chestPos).setStack(0, new ItemStack(Items.COBBLESTONE, 20));
		final BlockEntity blockEntity = context.getWorld().getBlockEntity(loaderPos);
		check(blockEntity instanceof BlockTrainCargoLoader.BlockEntity, "loader block entity missing");
		final BlockTrainCargoLoader.BlockEntity loader = (BlockTrainCargoLoader.BlockEntity) blockEntity;
		for (int i = 0; i < CargoMachines.CYCLE_TICKS * 3; i++) {
			loader.blockEntityTick();
		}
		check(chest(context, chestPos).getStack(0).getCount() == 20, "chest changed with no train");
		context.complete();
	}

	private static BlockPos place(TestContext context, int x, int y, int z, net.minecraft.block.Block block) {
		context.setBlockState(x, y, z, block);
		return context.getAbsolutePos(new BlockPos(x, y, z));
	}

	private static ChestBlockEntity chest(TestContext context, BlockPos pos) {
		final BlockEntity blockEntity = context.getWorld().getBlockEntity(pos);
		check(blockEntity instanceof ChestBlockEntity, "chest missing");
		return (ChestBlockEntity) blockEntity;
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}
