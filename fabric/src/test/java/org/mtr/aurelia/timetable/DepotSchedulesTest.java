package org.mtr.aurelia.timetable;

import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mtr.mod.data.DepotSchedules;

import java.time.LocalDate;

/**
 * The weekly timetable picks the right plan for the day, and a cancelled run is cancelled only until its time has passed.
 * Both are saved in their own file, which a world must load back exactly.
 */
public final class DepotSchedulesTest {

	@Test
	public void dayTypeAndPlanFallback() {
		// 2026-10-31 is a Saturday, 2026-11-02 a Monday
		Assertions.assertEquals(DepotSchedules.DayType.WEEKEND, DepotSchedules.dayType(true, LocalDate.of(2026, 10, 31), 0));
		Assertions.assertEquals(DepotSchedules.DayType.WEEKDAY, DepotSchedules.dayType(true, LocalDate.of(2026, 11, 2), 6));
		// World days 0-4 are weekdays, 5-6 the weekend, then it repeats
		Assertions.assertEquals(DepotSchedules.DayType.WEEKDAY, DepotSchedules.dayType(false, LocalDate.of(2026, 10, 31), 4));
		Assertions.assertEquals(DepotSchedules.DayType.WEEKEND, DepotSchedules.dayType(false, LocalDate.of(2026, 10, 31), 12));
		Assertions.assertEquals(DepotSchedules.DayType.WEEKDAY, DepotSchedules.dayType(false, LocalDate.of(2026, 10, 31), 14));

		final DepotSchedules schedules = new DepotSchedules();
		final DepotSchedules.Plan weekday = new DepotSchedules.Plan(false, new long[0], new int[]{0, 0, 0, 0, 0, 0, 4, 8, 8, 4, 4, 4, 4, 4, 4, 4, 4, 8, 8, 4, 2, 2, 0, 0});
		schedules.savePlan(3, DepotSchedules.DayType.WEEKDAY, weekday);
		Assertions.assertEquals(weekday, schedules.get(3).planFor(DepotSchedules.DayType.WEEKEND), "no weekend plan yet: run the weekday one");
		final DepotSchedules.Plan weekend = new DepotSchedules.Plan(true, new long[]{36_000_000, 50_400_000}, new int[24]);
		schedules.savePlan(3, DepotSchedules.DayType.WEEKEND, weekend);
		schedules.setEnabled(3, true);

		// Weekday by frequency, weekend by timed departures: the calendar stays the weekday plan's (world days)
		Assertions.assertFalse(schedules.get(3).usesRealCalendar(), "the calendar must not follow the plan just written");

		final DepotSchedules loaded = DepotSchedules.fromNbt(schedules.toNbt(new NbtCompound()));
		Assertions.assertTrue(loaded.get(3).enabled());
		Assertions.assertEquals(weekday, loaded.get(3).planFor(DepotSchedules.DayType.WEEKDAY));
		Assertions.assertEquals(weekend, loaded.get(3).planFor(DepotSchedules.DayType.WEEKEND));
		Assertions.assertTrue(DepotSchedules.fromNbt(new NbtCompound()).all().isEmpty(), "a world from before this feature has none");
	}

	@Test
	public void cancelledRunLastsUntilItsTimeHasPassed() {
		final DepotSchedules schedules = new DepotSchedules();
		schedules.cancel(11, 2, 1_000_000);
		Assertions.assertTrue(schedules.isCancelled(11, 2, 999_000));
		Assertions.assertFalse(schedules.isCancelled(11, 2, 1_000_001), "the same departure on a later day runs");
		Assertions.assertFalse(schedules.isCancelled(11, 3, 999_000));
		Assertions.assertFalse(schedules.isCancelled(12, 2, 999_000));

		final DepotSchedules loaded = DepotSchedules.fromNbt(schedules.toNbt(new NbtCompound()));
		Assertions.assertTrue(loaded.isCancelled(11, 2, 999_000));
		Assertions.assertTrue(loaded.expire(1_000_001));
		Assertions.assertFalse(loaded.isCancelled(11, 2, 999_000));
	}
}
