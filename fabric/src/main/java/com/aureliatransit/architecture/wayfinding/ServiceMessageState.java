package com.aureliatransit.architecture.wayfinding;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.List;

/**
 * World-level (overworld) persistent holder of the {@link ServiceMessages}. No MTR or client types.
 */
public final class ServiceMessageState extends PersistentState {

	public static final String ID = "ata_service_messages";

	private ServiceMessages messages = ServiceMessages.EMPTY;
	private List<RouteHold> holds = List.of();

	public static ServiceMessageState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(ServiceMessageState::fromNbt, ServiceMessageState::new, ID);
	}

	public static ServiceMessageState fromNbt(NbtCompound nbt) {
		final ServiceMessageState state = new ServiceMessageState();
		state.messages = ServiceMessages.fromNbt(nbt);
		state.holds = readHolds(nbt);
		return state;
	}

	public List<RouteHold> holds() {
		return holds;
	}

	public void setHolds(List<RouteHold> value) {
		final List<RouteHold> next = List.copyOf(value == null ? List.of() : value);
		if (!next.equals(holds)) {
			holds = next;
			markDirty();
		}
	}

	/** Drops holds whose time has passed and removes the service message that announced each one. */
	public boolean expire(long now) {
		final List<RouteHold> left = new ArrayList<>();
		ServiceMessages next = messages;
		boolean changed = false;
		for (final RouteHold hold : holds) {
			if (hold.active(now)) {
				left.add(hold);
			} else {
				changed = true;
				final ServiceMessages removed = next.remove(hold.messageId());
				if (removed != next) {
					next = removed;
				}
			}
		}
		if (changed) {
			holds = List.copyOf(left);
			messages = next;
			markDirty();
		}
		return changed;
	}

	private static List<RouteHold> readHolds(NbtCompound nbt) {
		if (!nbt.contains("Holds", NbtElement.LIST_TYPE)) {
			return List.of();
		}
		final NbtList list = nbt.getList("Holds", NbtElement.COMPOUND_TYPE);
		final List<RouteHold> holds = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			final NbtCompound item = list.getCompound(i);
			holds.add(new RouteHold(item.getInt("Message"), item.getString("Route"), item.getLong("Until")));
		}
		return List.copyOf(holds);
	}

	public ServiceMessages messages() {
		return messages;
	}

	public void set(ServiceMessages value) {
		if (!value.equals(messages)) {
			messages = value;
			markDirty();
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		nbt.copyFrom(messages.toNbt());
		final NbtList list = new NbtList();
		for (final RouteHold hold : holds) {
			final NbtCompound item = new NbtCompound();
			item.putInt("Message", hold.messageId());
			item.putString("Route", hold.route());
			item.putLong("Until", hold.untilEpochMillis());
			list.add(item);
		}
		nbt.put("Holds", list);
		return nbt;
	}
}
