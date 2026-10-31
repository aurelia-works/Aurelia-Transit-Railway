package org.mtr.mod.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import javax.annotation.Nullable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Weekly timetables and cancelled runs, which MTR has no fields for. Saved in their own file, never in MTR's depot data.
 * <ul>
 * <li>A depot with a weekly timetable keeps a weekday plan and a weekend plan (MTR's departures or hourly frequencies);
 * the server writes the plan for today into the depot when the day type changes.</li>
 * <li>A cancelled run is one departure of one siding, until a set time: it does not leave the depot, and departure
 * screens show it as cancelled.</li>
 * </ul>
 * Cancellations are read on MTR's simulation thread, so their methods are synchronized.
 */
public final class DepotSchedules {

	public static final String CANCELLED = "Cancelled";

	public enum DayType {WEEKDAY, WEEKEND}

	/** What MTR's depot screen sets: real-time departures (millis after midnight) or a train count for each hour. */
	public record Plan(boolean realTime, long[] departures, int[] frequencies) {

		@Override
		public boolean equals(Object object) {
			return object instanceof Plan plan && plan.realTime == realTime && Arrays.equals(plan.departures, departures) && Arrays.equals(plan.frequencies, frequencies);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(departures) * 31 + Arrays.hashCode(frequencies) + (realTime ? 1 : 0);
		}

		NbtCompound toNbt() {
			final NbtCompound nbt = new NbtCompound();
			nbt.putBoolean("real_time", realTime);
			nbt.putLongArray("departures", departures);
			nbt.putIntArray("frequencies", frequencies);
			return nbt;
		}

		static Plan fromNbt(NbtCompound nbt) {
			final int[] frequencies = Arrays.copyOf(nbt.getIntArray("frequencies"), 24);
			return new Plan(nbt.getBoolean("real_time"), nbt.getLongArray("departures"), frequencies);
		}
	}

	public record Schedule(boolean enabled, @Nullable Plan weekday, @Nullable Plan weekend) {

		/** The weekend falls back to the weekday plan until one is saved. */
		@Nullable
		public Plan planFor(DayType dayType) {
			return dayType == DayType.WEEKEND && weekend != null ? weekend : weekday;
		}

		/**
		 * Real-time plans follow the real calendar, others the world's days. Decided by the weekday plan (or the
		 * weekend one if that is all there is), so a depot whose two plans differ in mode does not flip between them.
		 */
		public boolean usesRealCalendar() {
			final Plan basis = weekday != null ? weekday : weekend;
			return basis != null && basis.realTime();
		}
	}

	private final Map<Long, Schedule> schedules = new HashMap<>();
	/** Siding ID to departure index to the time (millis) the cancellation ends. */
	private final Map<Long, Map<Integer, Long>> cancelled = new HashMap<>();

	/**
	 * Real-time depots follow the real calendar. Others follow the world's day count: days 6 and 7 of each 7 are the weekend.
	 */
	public static DayType dayType(boolean realTime, LocalDate realDate, long worldDay) {
		if (realTime) {
			final DayOfWeek day = realDate.getDayOfWeek();
			return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY ? DayType.WEEKEND : DayType.WEEKDAY;
		} else {
			return Math.floorMod(worldDay, 7) >= 5 ? DayType.WEEKEND : DayType.WEEKDAY;
		}
	}

	public Schedule get(long depotId) {
		return schedules.getOrDefault(depotId, new Schedule(false, null, null));
	}

	public Map<Long, Schedule> all() {
		return schedules;
	}

	public void setEnabled(long depotId, boolean enabled) {
		final Schedule old = get(depotId);
		schedules.put(depotId, new Schedule(enabled, old.weekday(), old.weekend()));
	}

	public void savePlan(long depotId, DayType dayType, Plan plan) {
		final Schedule old = get(depotId);
		schedules.put(depotId, dayType == DayType.WEEKDAY ? new Schedule(old.enabled(), plan, old.weekend()) : new Schedule(old.enabled(), old.weekday(), plan));
	}

	public synchronized void cancel(long sidingId, int departureIndex, long untilMillis) {
		cancelled.computeIfAbsent(sidingId, id -> new HashMap<>()).put(departureIndex, untilMillis);
	}

	/** @param timeMillis the time the run would be at (a deployment time or an arrival time) */
	public synchronized boolean isCancelled(long sidingId, long departureIndex, long timeMillis) {
		final Map<Integer, Long> runs = cancelled.get(sidingId);
		if (runs == null) {
			return false;
		}
		final Long until = runs.get((int) departureIndex);
		return until != null && timeMillis <= until;
	}

	public synchronized boolean restore(Iterable<Long> sidingIds) {
		boolean changed = false;
		for (final long sidingId : sidingIds) {
			changed |= cancelled.remove(sidingId) != null;
		}
		return changed;
	}

	public synchronized int cancelledCount(Iterable<Long> sidingIds, long now) {
		int count = 0;
		for (final long sidingId : sidingIds) {
			for (final long until : cancelled.getOrDefault(sidingId, Map.of()).values()) {
				if (until >= now) {
					count++;
				}
			}
		}
		return count;
	}

	/** @return whether anything was dropped */
	public synchronized boolean expire(long now) {
		boolean changed = false;
		final Iterator<Map<Integer, Long>> sidings = cancelled.values().iterator();
		while (sidings.hasNext()) {
			final Map<Integer, Long> runs = sidings.next();
			changed |= runs.values().removeIf(until -> until < now);
			if (runs.isEmpty()) {
				sidings.remove();
			}
		}
		return changed;
	}

	public synchronized NbtCompound toNbt(NbtCompound nbt) {
		final NbtList scheduleList = new NbtList();
		schedules.forEach((depotId, schedule) -> {
			final NbtCompound entry = new NbtCompound();
			entry.putLong("depot", depotId);
			entry.putBoolean("enabled", schedule.enabled());
			if (schedule.weekday() != null) {
				entry.put("weekday", schedule.weekday().toNbt());
			}
			if (schedule.weekend() != null) {
				entry.put("weekend", schedule.weekend().toNbt());
			}
			scheduleList.add(entry);
		});
		nbt.put("schedules", scheduleList);
		final NbtList cancelledList = new NbtList();
		cancelled.forEach((sidingId, runs) -> runs.forEach((index, until) -> {
			final NbtCompound entry = new NbtCompound();
			entry.putLong("siding", sidingId);
			entry.putInt("departure", index);
			entry.putLong("until", until);
			cancelledList.add(entry);
		}));
		nbt.put("cancelled", cancelledList);
		return nbt;
	}

	public static DepotSchedules fromNbt(NbtCompound nbt) {
		final DepotSchedules schedules = new DepotSchedules();
		final NbtList scheduleList = nbt.getList("schedules", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < scheduleList.size(); i++) {
			final NbtCompound entry = scheduleList.getCompound(i);
			schedules.schedules.put(entry.getLong("depot"), new Schedule(
					entry.getBoolean("enabled"),
					entry.contains("weekday") ? Plan.fromNbt(entry.getCompound("weekday")) : null,
					entry.contains("weekend") ? Plan.fromNbt(entry.getCompound("weekend")) : null
			));
		}
		final NbtList cancelledList = nbt.getList("cancelled", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < cancelledList.size(); i++) {
			final NbtCompound entry = cancelledList.getCompound(i);
			schedules.cancel(entry.getLong("siding"), entry.getInt("departure"), entry.getLong("until"));
		}
		return schedules;
	}
}
