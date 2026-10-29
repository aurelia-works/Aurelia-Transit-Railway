package org.mtr.mod.packet;

import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.InventoryHelper;
import org.mtr.mapping.mapper.PlayerHelper;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.data.TicketCurrency;
import org.mtr.mod.data.TicketRules;
import org.mtr.mod.data.TicketSystem;

public final class PacketAddBalance extends PacketHandler {

	private final int index;

	private static final int EMERALD_TO_DOLLAR = 10;
	/** Same ten buttons the ticket machine draws, 1 through 512 items. */
	private static final int TIER_COUNT = 10;

	public PacketAddBalance(PacketBufferReceiver packetBufferReceiver) {
		index = packetBufferReceiver.readInt();
	}

	public PacketAddBalance(int index) {
		this.index = index;
	}

	@Override
	public void write(PacketBufferSender packetBufferSender) {
		packetBufferSender.writeInt(index);
	}

	@Override
	public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
		final ServerWorld serverWorld = serverPlayerEntity.getServerWorld();
		if (payWithCustomCurrency(serverPlayerEntity, serverWorld)) {
			return;
		}
		TicketSystem.addBalance(new World(serverWorld.data), new PlayerEntity(serverPlayerEntity.data), getAddAmount(index));
		InventoryHelper.remove(new Inventory(PlayerHelper.getPlayerInventory(new PlayerEntity(serverPlayerEntity.data)).data), itemStack -> itemStack.getItem().equals(Items.getEmeraldMapped()), (int) Math.pow(2, index), false);
		serverWorld.playSound((PlayerEntity) null, serverPlayerEntity.getBlockPos(), SoundEvents.getEntityExperienceOrbPickupMapped(), SoundCategory.getBlocksMapped(), 1, 1);
	}

	/**
	 * Pays with the configured item when it is not an emerald. Returns false so {@link #runServer} keeps the original emerald purchase.
	 * A custom purchase is refused, and nothing is taken, when the player does not hold the full tier.
	 */
	private boolean payWithCustomCurrency(ServerPlayerEntity serverPlayerEntity, ServerWorld serverWorld) {
		final TicketCurrency.Resolved currency = TicketCurrency.resolve();
		if (TicketRules.DEFAULT_CURRENCY_ID.equals(currency.id)) {
			return false;
		}
		if (index < 0 || index >= TIER_COUNT) {
			return true;
		}
		final int cost = (int) Math.pow(2, index);
		final PlayerEntity player = new PlayerEntity(serverPlayerEntity.data);
		final Inventory inventory = new Inventory(PlayerHelper.getPlayerInventory(player).data);
		if (InventoryHelper.remove(inventory, itemStack -> itemStack.getItem().equals(currency.item), cost, true) < cost) {
			return true;
		}
		InventoryHelper.remove(inventory, itemStack -> itemStack.getItem().equals(currency.item), cost, false);
		TicketSystem.addBalance(new World(serverWorld.data), player, getAddAmount(index));
		serverWorld.playSound((PlayerEntity) null, serverPlayerEntity.getBlockPos(), SoundEvents.getEntityExperienceOrbPickupMapped(), SoundCategory.getBlocksMapped(), 1, 1);
		return true;
	}

	public static int getAddAmount(int index) {
		return (int) Math.ceil(Math.pow(2, index) * (EMERALD_TO_DOLLAR + index));
	}
}
