package org.mtr.mod.packet;

import net.minecraft.util.Hand;
import org.mtr.mapping.holder.MinecraftServer;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.Items;
import org.mtr.mod.item.ItemRailBuilder;

/**
 * The Aurelia panel's Builder tab sets the Rail Builder in the player's hand.
 */
public final class PacketUpdateRailBuilder extends PacketHandler {

	private final boolean offHand;
	private final ItemRailBuilder.Settings settings;

	public PacketUpdateRailBuilder(PacketBufferReceiver packetBufferReceiver) {
		offHand = packetBufferReceiver.readBoolean();
		settings = new ItemRailBuilder.Settings(packetBufferReceiver.readInt(), packetBufferReceiver.readInt(), packetBufferReceiver.readBoolean(), packetBufferReceiver.readBoolean(), packetBufferReceiver.readBoolean());
	}

	public PacketUpdateRailBuilder(boolean offHand, ItemRailBuilder.Settings settings) {
		this.offHand = offHand;
		this.settings = settings;
	}

	@Override
	public void write(PacketBufferSender packetBufferSender) {
		packetBufferSender.writeBoolean(offHand);
		packetBufferSender.writeInt(settings.width());
		packetBufferSender.writeInt(settings.height());
		packetBufferSender.writeBoolean(settings.deck());
		packetBufferSender.writeBoolean(settings.clear());
		packetBufferSender.writeBoolean(settings.walls());
	}

	@Override
	public void runServer(MinecraftServer minecraftServer, ServerPlayerEntity serverPlayerEntity) {
		final net.minecraft.item.ItemStack itemStack = serverPlayerEntity.data.getStackInHand(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
		if (itemStack.getItem() == Items.RAIL_BUILDER.get().data) {
			ItemRailBuilder.write(itemStack, settings);
		}
	}
}
