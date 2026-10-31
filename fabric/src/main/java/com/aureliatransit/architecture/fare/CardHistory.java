package com.aureliatransit.architecture.fare;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * Tap-in and tap-out lines stored on the transit card. The balance stays in MTR; this is only the record the card
 * screen shows. Older cards have no list and read as empty. At most {@link #MAX} taps are kept, newest last.
 */
public final class CardHistory {

	public static final int MAX = 16;
	public static final long DAY_MILLIS = 86_400_000L;
	private static final String KEY = "AtaTaps";

	public record Tap(String station, boolean entry, int fare, long epochMillis) {
	}

	private CardHistory() {
	}

	public static List<Tap> read(ItemStack stack) {
		final NbtCompound tag = stack.getNbt();
		if (tag == null || !tag.contains(KEY, NbtElement.LIST_TYPE)) {
			return List.of();
		}
		final NbtList list = tag.getList(KEY, NbtElement.COMPOUND_TYPE);
		final List<Tap> taps = new ArrayList<>();
		for (int i = 0; i < list.size(); i++) {
			final NbtCompound item = list.getCompound(i);
			taps.add(new Tap(item.getString("Station"), item.getBoolean("Entry"), item.getInt("Fare"), item.getLong("At")));
		}
		return List.copyOf(taps);
	}

	public static List<Tap> append(List<Tap> taps, Tap tap) {
		final List<Tap> next = new ArrayList<>(taps == null ? List.of() : taps);
		next.add(tap);
		if (next.size() > MAX) {
			return List.copyOf(next.subList(next.size() - MAX, next.size()));
		}
		return List.copyOf(next);
	}

	public static void write(ItemStack stack, List<Tap> taps) {
		final NbtList list = new NbtList();
		for (final Tap tap : taps == null ? List.<Tap>of() : taps) {
			final NbtCompound item = new NbtCompound();
			item.putString("Station", tap.station() == null ? "" : tap.station());
			item.putBoolean("Entry", tap.entry());
			item.putInt("Fare", Math.max(0, tap.fare()));
			item.putLong("At", tap.epochMillis());
			list.add(item);
		}
		stack.getOrCreateNbt().put(KEY, list);
	}

	/** Sum of fares whose epoch falls on the same Minecraft-independent UTC day as {@code now}. */
	public static int spentOnDay(List<Tap> taps, long now) {
		final long day = Math.floorDiv(now, DAY_MILLIS);
		int spent = 0;
		for (final Tap tap : taps) {
			if (!tap.entry() && Math.floorDiv(tap.epochMillis(), DAY_MILLIS) == day) {
				spent += Math.max(0, tap.fare());
			}
		}
		return spent;
	}

	public static void remember(ItemStack stack, Tap tap) {
		write(stack, append(read(stack), tap));
	}
}
