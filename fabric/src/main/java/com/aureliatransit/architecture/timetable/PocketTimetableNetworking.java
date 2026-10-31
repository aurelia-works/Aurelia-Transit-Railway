package com.aureliatransit.architecture.timetable;

import com.aureliatransit.architecture.AureliaTransitArchitecture;
import com.aureliatransit.architecture.registry.ModItems;
import com.aureliatransit.architecture.text.TextSanitizer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Client sends the lines it read off a platform; the server sanitises them and gives a pocket timetable. */
public final class PocketTimetableNetworking {

	public static final Identifier PRINT = AureliaTransitArchitecture.id("pocket_timetable");
	public static final int MAX_PACKET_LINES = PocketSheet.MAX_LINES;

	private PocketTimetableNetworking() {
	}

	public static void registerServer() {
		ServerPlayNetworking.registerGlobalReceiver(PRINT, (server, player, handler, buf, response) -> {
			final String title = buf.readString(PocketSheet.MAX_CHARS * 4);
			final boolean map = buf.readBoolean();
			final int count = buf.readVarInt();
			final List<String> lines = new ArrayList<>();
			final int capped = Math.min(Math.max(0, count), MAX_PACKET_LINES);
			for (int i = 0; i < capped; i++) {
				lines.add(buf.readString(PocketSheet.MAX_CHARS * 4));
			}
			// A hostile count must not desync the rest of the buffer: drain extras.
			for (int i = capped; i < count && i < 256; i++) {
				buf.readString(PocketSheet.MAX_CHARS * 4);
			}
			server.execute(() -> give(player, title, map, lines));
		});
	}

	public static PacketByteBuf write(PocketSheet.Sheet sheet) {
		final PacketByteBuf buf = PacketByteBufs.create();
		buf.writeString(TextSanitizer.sanitize(sheet.title(), PocketSheet.MAX_CHARS), PocketSheet.MAX_CHARS * 4);
		buf.writeBoolean(sheet.kind() == PocketSheet.Kind.MAP);
		buf.writeVarInt(sheet.lines().size());
		for (final String line : sheet.lines()) {
			buf.writeString(line, PocketSheet.MAX_CHARS * 4);
		}
		return buf;
	}

	private static void give(ServerPlayerEntity player, String title, boolean map, List<String> lines) {
		final PocketSheet.Sheet sheet = new PocketSheet.Sheet(title, map ? PocketSheet.Kind.MAP : PocketSheet.Kind.TIMETABLE, lines);
		final ItemStack held = player.getMainHandStack();
		if (held.isOf(ModItems.POCKET_TIMETABLE)) {
			PocketTimetableItem.write(held, sheet);
			return;
		}
		final ItemStack off = player.getOffHandStack();
		if (off.isOf(ModItems.POCKET_TIMETABLE)) {
			PocketTimetableItem.write(off, sheet);
			return;
		}
		player.getInventory().offerOrDrop(PocketTimetableItem.printed(sheet));
	}
}
