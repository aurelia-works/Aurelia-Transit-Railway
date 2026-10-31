package org.mtr.mod.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-depot switches MTR has no field for: open every doorway at each stop, and no announcements on the depot's trains.
 * Kept in their own saved file (not in MTR's depot data), so old worlds and MTR itself never see them.
 */
public final class DepotOptions {

	public enum Option {ALL_DOORS, NO_ANNOUNCEMENTS}

	public record Flags(boolean allDoors, boolean noAnnouncements) {

		public static final Flags NONE = new Flags(false, false);

		public boolean get(Option option) {
			return option == Option.ALL_DOORS ? allDoors : noAnnouncements;
		}

		public Flags with(Option option, boolean value) {
			return option == Option.ALL_DOORS ? new Flags(value, noAnnouncements) : new Flags(allDoors, value);
		}

		public boolean isNone() {
			return !allDoors && !noAnnouncements;
		}
	}

	/** What this client was last sent by the server. */
	private static volatile Map<Long, Flags> client = Map.of();

	private final Map<Long, Flags> flags = new HashMap<>();

	public Flags get(long depotId) {
		return flags.getOrDefault(depotId, Flags.NONE);
	}

	/** @return whether anything changed */
	public boolean set(long depotId, Option option, boolean value) {
		final Flags old = get(depotId);
		final Flags next = old.with(option, value);
		if (next.equals(old)) {
			return false;
		}
		if (next.isNone()) {
			flags.remove(depotId);
		} else {
			flags.put(depotId, next);
		}
		return true;
	}

	public Map<Long, Flags> all() {
		return Collections.unmodifiableMap(flags);
	}

	public NbtCompound toNbt(NbtCompound nbt) {
		final NbtList list = new NbtList();
		flags.forEach((depotId, depotFlags) -> {
			final NbtCompound entry = new NbtCompound();
			entry.putLong("depot", depotId);
			entry.putBoolean("all_doors", depotFlags.allDoors());
			entry.putBoolean("no_announcements", depotFlags.noAnnouncements());
			list.add(entry);
		});
		nbt.put("depots", list);
		return nbt;
	}

	public static DepotOptions fromNbt(NbtCompound nbt) {
		final DepotOptions options = new DepotOptions();
		final NbtList list = nbt.getList("depots", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < list.size(); i++) {
			final NbtCompound entry = list.getCompound(i);
			final Flags depotFlags = new Flags(entry.getBoolean("all_doors"), entry.getBoolean("no_announcements"));
			if (!depotFlags.isNone()) {
				options.flags.put(entry.getLong("depot"), depotFlags);
			}
		}
		return options;
	}

	public void write(PacketByteBuf buf) {
		buf.writeVarInt(flags.size());
		flags.forEach((depotId, depotFlags) -> {
			buf.writeLong(depotId);
			buf.writeBoolean(depotFlags.allDoors());
			buf.writeBoolean(depotFlags.noAnnouncements());
		});
	}

	public static Map<Long, Flags> read(PacketByteBuf buf) {
		final int count = buf.readVarInt();
		final Map<Long, Flags> read = new HashMap<>();
		for (int i = 0; i < count; i++) {
			read.put(buf.readLong(), new Flags(buf.readBoolean(), buf.readBoolean()));
		}
		return read;
	}

	public static void setClient(Map<Long, Flags> value) {
		client = Map.copyOf(value);
	}

	public static Flags client(long depotId) {
		return client.getOrDefault(depotId, Flags.NONE);
	}

	public static boolean allDoorsClient(long depotId) {
		return client(depotId).allDoors();
	}

	public static boolean noAnnouncementsClient(long depotId) {
		return client(depotId).noAnnouncements();
	}
}
