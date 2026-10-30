package org.mtr.test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.mtr.mapping.tool.PacketBufferReceiver;
import org.mtr.mapping.tool.PacketBufferSender;
import org.mtr.mod.packet.PacketOpenTicketMachineScreen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A plain MTR server sends only the balance. An ATR client must still open the ticket machine. */
public final class PacketOpenTicketMachineScreenTest {

	@Test
	public void balanceOnlyPacketFromPlainMtrServerReadsWithDefaultCurrency() throws Exception {
		final PacketOpenTicketMachineScreen packet = read(sender -> sender.writeInt(42));
		assertEquals(42, field(packet, "balance"));
		assertNull(field(packet, "currencyId"), "no currency was sent, so the default applies");
	}

	@Test
	public void packetFromAtrServerKeepsItsCurrency() throws Exception {
		final PacketOpenTicketMachineScreen packet = read(sender -> {
			sender.writeInt(7);
			sender.writeString("minecraft:diamond");
		});
		assertEquals(7, field(packet, "balance"));
		assertEquals("minecraft:diamond", field(packet, "currencyId"));
	}

	private static PacketOpenTicketMachineScreen read(java.util.function.Consumer<PacketBufferSender> write) {
		final PacketBufferSender sender = new PacketBufferSender(Unpooled::buffer);
		write.accept(sender);
		final List<ByteBuf> buffers = new ArrayList<>();
		sender.send(buffers::add, Runnable::run);
		assertFalse(buffers.isEmpty());
		final PacketOpenTicketMachineScreen[] result = new PacketOpenTicketMachineScreen[1];
		for (final ByteBuf buffer : buffers) {
			PacketBufferReceiver.receive(buffer, receiver -> result[0] = new PacketOpenTicketMachineScreen(receiver), Runnable::run);
		}
		assertNotNull(result[0], "the packet was read");
		return result[0];
	}

	private static Object field(Object object, String name) throws Exception {
		final Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}
}
