package org.mtr.mod.cargo;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The Minecraft half of a transfer: chests, barrels and hoppers touching the cargo block. Other inventories (furnaces,
 * droppers, dispensers) are left alone. One cycle moves up to {@link #ITEMS_PER_CYCLE} items, and the block entity
 * waits {@link #CYCLE_TICKS} ticks between cycles, which is about twenty items a second while the train is stopped.
 */
public final class CargoMachines {

	public static final int CYCLE_TICKS = 8;
	public static final int ITEMS_PER_CYCLE = 8;

	private CargoMachines() {
	}

	public static int transfer(ServerWorld world, BlockPos pos, boolean load, TrainCargo hold, int limit) {
		final List<SlotBinding> bindings = adjacentSlots(world, pos);
		if (bindings.isEmpty() || limit <= 0) {
			return 0;
		}
		final List<CargoExchange.Pile> piles = new ArrayList<>(bindings.size());
		for (final SlotBinding binding : bindings) {
			piles.add(binding.pile());
		}
		final int moved = load ? CargoExchange.load(piles, hold, limit) : CargoExchange.unload(hold, piles, limit);
		if (moved <= 0) {
			return 0;
		}
		final Set<Inventory> dirty = new HashSet<>();
		for (int i = 0; i < bindings.size(); i++) {
			if (bindings.get(i).apply(piles.get(i))) {
				dirty.add(bindings.get(i).inventory);
			}
		}
		for (final Inventory inventory : dirty) {
			inventory.markDirty();
		}
		return moved;
	}

	private static List<SlotBinding> adjacentSlots(ServerWorld world, BlockPos pos) {
		final List<SlotBinding> bindings = new ArrayList<>();
		for (final Direction direction : Direction.values()) {
			final BlockEntity blockEntity = world.getBlockEntity(pos.offset(direction));
			if (!isCargoContainer(blockEntity) || !(blockEntity instanceof Inventory)) {
				continue;
			}
			final Inventory inventory = (Inventory) blockEntity;
			for (int slot = 0; slot < inventory.size(); slot++) {
				bindings.add(new SlotBinding(inventory, slot));
			}
		}
		return bindings;
	}

	static boolean isCargoContainer(BlockEntity blockEntity) {
		return blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof HopperBlockEntity;
	}

	private static final class SlotBinding {

		private final Inventory inventory;
		private final int slot;

		private SlotBinding(Inventory inventory, int slot) {
			this.inventory = inventory;
			this.slot = slot;
		}

		private CargoExchange.Pile pile() {
			final ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty()) {
				return CargoExchange.Pile.empty(TrainCargo.DEFAULT_MAX_STACK);
			}
			final String nbt = stack.hasNbt() ? stack.getNbt().toString() : "";
			return new CargoExchange.Pile(Registries.ITEM.getId(stack.getItem()).toString(), nbt, stack.getCount(), stack.getMaxCount());
		}

		private boolean apply(CargoExchange.Pile pile) {
			final ItemStack before = inventory.getStack(slot);
			final ItemStack after = toStack(pile);
			if (same(before, after)) {
				return false;
			}
			inventory.setStack(slot, after);
			return true;
		}

		private static boolean same(ItemStack before, ItemStack after) {
			if (before.isEmpty() && after.isEmpty()) {
				return true;
			}
			return before.getCount() == after.getCount() && before.getItem() == after.getItem() && Objects.equals(before.getNbt(), after.getNbt());
		}

		private static ItemStack toStack(CargoExchange.Pile pile) {
			if (pile.isEmpty()) {
				return ItemStack.EMPTY;
			}
			final Identifier identifier = Identifier.tryParse(pile.itemId());
			if (identifier == null) {
				return ItemStack.EMPTY;
			}
			final Item item = Registries.ITEM.get(identifier);
			if (item == Items.AIR) {
				return ItemStack.EMPTY;
			}
			final ItemStack stack = new ItemStack(item, pile.count());
			if (!pile.nbt().isEmpty()) {
				try {
					final NbtCompound nbt = StringNbtReader.parse(pile.nbt());
					stack.setNbt(nbt);
				} catch (Exception ignored) {
				}
			}
			return stack;
		}
	}
}
