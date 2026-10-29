package org.mtr.mod.packet;

import org.mtr.mapping.registry.PacketHandler;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.data.TicketCurrency;
import org.mtr.mod.data.TicketRules;

public final class PacketOpenTicketMachineScreen extends PacketHandler {

	private final int balance;
	private final String currencyId;

	public PacketOpenTicketMachineScreen(PacketBufferReceiver packetBufferReceiver) {
		balance = packetBufferReceiver.readInt();
		currencyId = packetBufferReceiver.readString();
	}

	public PacketOpenTicketMachineScreen(int balance) {
		this.balance = balance;
		// Resolved on the server, where the config lives. ATA's vending machine uses this constructor.
		currencyId = TicketCurrency.resolve().id;
	}

	@Override
	public void write(PacketBufferSender packetBufferSender) {
		packetBufferSender.writeInt(balance);
		packetBufferSender.writeString(currencyId == null ? TicketRules.DEFAULT_CURRENCY_ID : currencyId);
	}

	@Override
	public void runClient() {
		ClientPacketHelper.openTicketMachineScreen(balance, currencyId);
	}
}
